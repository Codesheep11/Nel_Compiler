package midend.Analysis;

import midend.Analysis.result.AliasInfo;
import mir.*;

import java.util.*;

/**
 * 参考了LLVM官方 和 CMMC 的别名分析设计
 *
 * @see <a href="https://llvm.org/docs/AliasAnalysis.html">Alias Analysis</a>
 * @see <a href=“https://gitlab.eduxiji.net/educg-group-17291-1894922/202314325201374-1031/-/blob/riscv_fix/src/cmmc/Analysis/AliasAnalysis.cpp”></a>
 */

public class AliasAnalysis {

    private static class InheritEdge {
        private final Value dst;
        private final Value src1;
        private final Value src2;

        private InheritEdge(Value dst, Value src) {
            this.dst = dst;
            this.src1 = src;
            this.src2 = null;
        }

        private InheritEdge(Value dst, Value src1, Value src2) {
            this.dst = dst;
            this.src1 = src1;
            this.src2 = src2;
        }
    }

    private static AliasInfo aliasInfo;

    public static boolean isDistinct(Value v1, Value v2) {
        return aliasInfo.isDistinct(v1, v2);
    }

    public static void runOnFunc(Function function) {
        int allocID = 0;
        aliasInfo = new AliasInfo();
        int globalID = allocID++;
        int stackID = allocID++;
        aliasInfo.addPair(globalID, stackID);
        HashSet<Integer> globalGroups = new HashSet<>();
        HashSet<Integer> stackGroups = new HashSet<>();
        HashSet<InheritEdge> inheritGraph = new HashSet<>();
        for (GlobalVariable glo : function.module.getGlobalValues()) {
            int id = allocID++;
            aliasInfo.addValue(glo, new ArrayList<>(List.of(globalID, id)));
            globalGroups.add(id);
        }
        int argID = allocID++;
        for (Function.Argument arg : function.getFuncRArguments()) {
            if (!arg.getType().isPointerTy()) continue;
            else {
                aliasInfo.addValue(arg, new ArrayList<>(List.of(argID)));
            }
        }
        for (BasicBlock block : function.getDomTreeLayerSort()) {
            for (Instruction instruction : block.getInstructions()) {
                if (!instruction.getType().isPointerTy()) continue;
                if (instruction instanceof Instruction.Alloc alloc) {
                    int id = allocID++;
                    stackGroups.add(id);
                    aliasInfo.addPair(id, argID);
                    aliasInfo.addValue(alloc, new ArrayList<>(List.of(stackID, id)));
                }
                else if (instruction instanceof Instruction.GetElementPtr gep) {
                    //todo:由于GEP被规约,因此判断优化时有巨大的局限性
                    ArrayList<Integer> attrs = new ArrayList<>();
                    Instruction.GetElementPtr cur = gep;
                    while (true) {
                        Value base = gep.getBase();
                        Value off = gep.getOffsets().get(gep.getOffsets().size() - 1);
                        if (off instanceof Constant.ConstantInt c && c.getIntValue() == 0) {
                            inheritGraph.add(new InheritEdge(gep, base));
                            break;
                        }
                        if (cur == gep) {
                            if (off instanceof Constant.ConstantInt c && c.getIntValue() != 0) {
                                int a1 = allocID++;
                                int a2 = allocID++;
                                aliasInfo.addPair(a1, a2);
                                attrs.add(a1);
                                aliasInfo.appendAttr(cur.getBase(), a2);
                            }
                        }
                        if (base instanceof Instruction.GetElementPtr) {
                            cur = (Instruction.GetElementPtr) base;
                        }
                        else {
                            break;
                        }
                        aliasInfo.addValue(gep, attrs);
                    }
                }
                else if (instruction instanceof Instruction.BitCast bitcast) {
                    aliasInfo.addValue(bitcast, new ArrayList<>());
                }
                else if (instruction instanceof Instruction.Load ||
                        instruction instanceof Instruction.Call)
                {
                    aliasInfo.addValue(instruction, new ArrayList<>());
                }
                else if (instruction instanceof Instruction.Phi phi) {
                    aliasInfo.addValue(phi, new ArrayList<>());
                    HashSet<Value> inheritSet = new HashSet<>();
                    for (Value value : phi.getIncomingValues()) {
                        if (value instanceof Constant) continue;
                        inheritSet.add(value);
                        if (inheritSet.size() > 2) break;
                    }
                    Iterator<Value> iterator = inheritSet.iterator();
                    if (inheritSet.size() == 1) {
                        inheritGraph.add(new InheritEdge(phi, iterator.next()));
                    }
                    else if (inheritSet.size() == 2) {
                        inheritGraph.add(new InheritEdge(phi, iterator.next(), iterator.next()));
                    }
                }
            }
        }
        //TBAA
        HashMap<Type.PointerType, Integer> types = new HashMap<>();
        for (var key : aliasInfo.mPointerAttributes.keySet()) {
            if (types.containsKey((Type.PointerType) key.getType())) continue;
            types.put((Type.PointerType) key.getType(), allocID++);
        }
        for (var typei : types.keySet()) {
            Type inner1 = typei.getInnerType();
            for (var typej : types.keySet()) {
                Type inner2 = typej.getInnerType();
                if (TBAAisDistinct(inner1, inner2)) {
                    aliasInfo.addPair(types.get(typei), types.get(typej));
                }
            }
        }
        for (var key : aliasInfo.mPointerAttributes.keySet()) {
            int attr = types.get((Type.PointerType) key.getType());
            aliasInfo.appendAttr(key, attr);
        }
        //stack/global
        aliasInfo.addDistinctGroup(globalGroups);
        aliasInfo.addDistinctGroup(stackGroups);
        // trans
        while (true) {
            boolean modify = false;
            for (InheritEdge inheritEdge : inheritGraph) {
                if (inheritEdge.src2 != null) {
                    var lhs = aliasInfo.inheritFrom(inheritEdge.src1);
                    Collections.sort(lhs);
                    var rhs = aliasInfo.inheritFrom(inheritEdge.src2);
                    Collections.sort(rhs);
                    ArrayList<Integer> insc = new ArrayList<>(lhs);
                    insc.retainAll(rhs);
                    modify |= aliasInfo.appendAttr(inheritEdge.dst, insc);
                }
                else {
                    modify |= aliasInfo.appendAttr(inheritEdge.dst, aliasInfo.inheritFrom(inheritEdge.src1));
                }
            }
            if (!modify) break;
        }
    }

    private static boolean TBAAincludes(Type a, Type b)//A类型是否可能包含B?例如对二维数组的指针和对一维数组的指针
    {
        if (a.equals(b)) return true;
        if (a.isValueType()) return false;
        else if (a.isArrayTy()) {
            Type.ArrayType arrayType = (Type.ArrayType) a;
            Type next = b.isArrayTy() ? arrayType.getEleType() : arrayType.getBasicEleType();
            return TBAAincludes(next, b);
        }
        else {
            throw new RuntimeException("unreachable!");
        }
    }

    private static boolean TBAAisDistinct(Type a, Type b) {
        return !TBAAincludes(a, b) && !TBAAincludes(b, a);
    }
}
