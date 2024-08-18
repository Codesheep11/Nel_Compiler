package midend.Transform.Array;

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 数组飞升
 */
public class LocalArrayLift {
    private static final HashMap<Instruction.Alloc, HashMap<Integer, Constant>> arrayInitMap = new HashMap<>();

    private static final ArrayList<Instruction> delList = new ArrayList<>();

    private static Function func;

    private static int count = 0;

    private static Module module;

    public static void run(Module module) {
        LocalArrayLift.module = module;
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            runOnFunc(func);
        }
    }

    // 对于每个可提升的局部数组，应当满足：
    // 1. 所有写入均在支配树的一条根向路径上
    // 2. 所有写入均写入常数（含memset）
    // 3. 所有读取，应当在支配树上最后一次写入之后，即位于最后一次写入的基本块的子树内
    //
    // 无法追踪的地址来源包括：
    // 1. 函数传参
    // 2. Phi
    private static void runOnFunc(Function func) {
        clear();
        LocalArrayLift.func = func;
        FindAllLiftArray();
        if (func.getName().equals("main")) FindOnceLiftArray();
        LiftArray();
    }

    private static void clear() {
        arrayInitMap.clear();
        delList.clear();
    }

    private static void FindAllLiftArray() {
        for (BasicBlock bb : func.getBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof Instruction.Alloc alloc) {
                    CollectArrayInfo(alloc);
                }
            }
        }
    }

    private static void CollectArrayInfo(Instruction.Alloc alloc) {
        ArrayList<Instruction> stores = new ArrayList<>();
        ArrayList<Instruction> loads = new ArrayList<>();
        ArrayList<Instruction> use = new ArrayList<>(alloc.getUsers());
        while (!use.isEmpty()) {
            Instruction useInst = use.remove(0);
            if (useInst instanceof Instruction.Load) loads.add(useInst);
            else if (useInst instanceof Instruction.Store store) {
                if (store.getAddr() instanceof Instruction.GetElementPtr addr) {
                    Value idx = addr.getIdx();
                    Value val = store.getValue();
                    if (idx instanceof Constant && val instanceof Constant) stores.add(useInst);
                    else return;
                }
                else
                    return;
            }
            else if (useInst instanceof Instruction.Call call) {
                Function callee = call.getDestFunction();
                FuncInfo calleeInfo = AnalysisManager.getFuncInfo(callee);
                if (callee.getName().equals("memset")) stores.add(useInst);
                else if (calleeInfo.hasSideEffect) return;
            }
            else if (useInst instanceof Instruction.GetElementPtr || useInst instanceof Instruction.BitCast)
                use.addAll(useInst.getUsers());
        }
        //获得最后一个支配store指令
        Instruction lastStoreInst = null;
        for (Instruction store : stores) {
            if (lastStoreInst == null) {
                lastStoreInst = store;
                continue;
            }
            BasicBlock storeBB = store.getParentBlock();
            BasicBlock lastStoreBB = lastStoreInst.getParentBlock();
            if (AnalysisManager.dominate(lastStoreBB, storeBB)) lastStoreInst = store;
            else if (AnalysisManager.dominate(storeBB, lastStoreBB)) continue;
            else return;
        }
        stores.sort((i, j) -> {
            if (AnalysisManager.dominate(i, j)) return 1;
            if (AnalysisManager.dominate(j, i)) return -1;
            else throw new RuntimeException("store not in a root path!");
        });
        //确保所有load在最后一个store后面
        for (Instruction load : loads) {
            if (!AnalysisManager.dominate(lastStoreInst, load)) return;
        }
        //满足上述条件，记录数组信息，默认此处已经消除了冗余store
        HashMap<Integer, Constant> init = new HashMap<>();
//        Collections.reverse(stores);
        for (Instruction store : stores) {
            if (store instanceof Instruction.Call) continue;
            Instruction.GetElementPtr addr = (Instruction.GetElementPtr) ((Instruction.Store) store).getAddr();
            int idx = ((Constant.ConstantInt) addr.getIdx()).getIntValue();
            Value val = ((Instruction.Store) store).getValue();
            if (init.containsKey(idx)) continue;
            init.put(idx, (Constant) val);
        }
        arrayInitMap.put(alloc, init);
        delList.addAll(stores);
    }

    private static void FindOnceLiftArray() {
        for (BasicBlock block : func.getBlocks()) {
            if (block.getLoopDepth() != 0) continue;
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof Instruction.Alloc alloc) {
                    CollectOnceArrayInfo(alloc);
                }
            }
        }
    }

    private static void CollectOnceArrayInfo(Instruction.Alloc alloc) {
        if (arrayInitMap.containsKey(alloc)) return;
        arrayInitMap.put(alloc, new HashMap<>());
        BasicBlock block = alloc.getParentBlock();
        //收集当前基本快中所有常量和常下标store，遇到load和非常下标store则停止
        ArrayList<Instruction> snap = block.getInstructionsSnap();
        int beginIdx = snap.indexOf(alloc);
        for (int i = beginIdx + 1; i < snap.size(); i++) {
            Instruction inst = snap.get(i);
            if (inst instanceof Instruction.Load load) {
                if (getBaseAddr(load.getAddr()).equals(alloc)) break;
            }
            if (inst instanceof Instruction.Store store) {
                Value addr = store.getAddr();
                if (addr instanceof Instruction.GetElementPtr gep) {
                    if (getBaseAddr(gep).equals(alloc)) {
                        if (store.getValue() instanceof Constant && gep.getIdx() instanceof Constant) {
                            arrayInitMap.get(alloc).put(((Constant.ConstantInt) gep.getIdx()).getIntValue(), (Constant) store.getValue());
                            delList.add(store);
                        }
                        else break;
                    }
                }
            }
            else if (inst instanceof Instruction.Call call) {
                if (call.getDestFunction().getName().equals("memset")) {
                    Value arg = call.getParams().get(0);
                    if (getBaseAddr(arg).equals(alloc)) {
                        delList.add(call);
                    }
                }
                else break;
            }
        }
    }

    private static Value getBaseAddr(Value inst) {
        Value ret = inst;
        while (ret instanceof Instruction.GetElementPtr || ret instanceof Instruction.BitCast) {
            if (ret instanceof Instruction.GetElementPtr gep) {
                ret = gep.getBase();
            }
            if (ret instanceof Instruction.BitCast) {
                Instruction.BitCast bitCast = (Instruction.BitCast) ret;
                ret = bitCast.getSrc();
            }
        }
        return ret;
    }

    private static void LiftArray() {
        //对于每一个要提升的数组，生成全局变量
        for (Instruction.Alloc alloc : arrayInitMap.keySet()) {
            Type.ArrayType arrayType = (Type.ArrayType) ((Type.PointerType) alloc.getType()).getInnerType();
            Constant constant = new Constant.ConstantArray(arrayType);
            HashMap<Integer, Constant> idxMap = arrayInitMap.get(alloc);
            for (int idx : idxMap.keySet()) {
                ((Constant.ConstantArray) constant).setIdxEle(idx, idxMap.get(idx));
            }
            if (constant.isZero()) constant = new Constant.ConstantZeroInitializer(arrayType);
            GlobalVariable gv = new GlobalVariable(constant, "_lift_array_" + count++);
//            System.out.println(gv.label);
            module.addGlobalValue(gv);
            alloc.replaceAllUsesWith(gv);
            delList.add(alloc);
        }
        delList.forEach(Value::delete);
    }
}