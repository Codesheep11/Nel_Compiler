package midend.Analysis;

import midend.Analysis.result.AliasInfo;
import mir.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class AliaAnalysis {

    private static class InheritEdge {
        private Value dst;
        private Value src1;
        private Value src2;

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

    private static int allocID;
    public static AliasInfo aliasInfo;

    public static void runOnFunc(Function function) {
        allocID = 0;
        aliasInfo = new AliasInfo();
        int globalID = allocID++;
        int stackID = allocID++;
        aliasInfo.addPair(globalID, stackID);
        HashSet<GlobalVariable> globals = new HashSet<>();
        HashSet<Integer> globalGroups = new HashSet<>();
        HashSet<Integer> stackGroups = new HashSet<>();
        ArrayList<Instruction.GetElementPtr> geps = new ArrayList<>();
        HashSet<InheritEdge>inheritGraph=new HashSet<>();
        for (GlobalVariable glo : function.module.getGlobalValues()) {
            globals.add(glo);
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
                } else if (instruction instanceof Instruction.GetElementPtr gep) {

                } else if (instruction instanceof Instruction.BitCast bitcast) {
                    aliasInfo.addValue(bitcast, new ArrayList<>());
                } else if (instruction instanceof Instruction.Load ||
                        instruction instanceof Instruction.Call) {
                    aliasInfo.addValue(instruction, new ArrayList<>());
                } else if (instruction instanceof Instruction.Phi phi) {
                    aliasInfo.addValue(phi, new ArrayList<>());
                    HashSet<Value> inheritSet;

                }
            }
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
        } else {
            throw new RuntimeException("unreachable!");
        }
    }

    private static boolean TBAAisDistinct(Type a, Type b) {
        return !TBAAincludes(a, b) && !TBAAincludes(b, a);
    }
}
