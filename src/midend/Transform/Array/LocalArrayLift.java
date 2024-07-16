package midend.Transform.Array;

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 数组飞升
 */
public class LocalArrayLift {
    private static HashMap<Instruction.Alloc, HashMap<Integer, Constant>> arrayInitMap = new HashMap<>();

    private static ArrayList<Instruction> delList = new ArrayList<>();

    private static Function func;

    public static void run(Module module) {
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
        LiftArray();
    }

    private static void clear() {
        arrayInitMap.clear();
        delList.clear();
    }

    private static void FindAllLiftArray() {
        for (BasicBlock bb : func.getBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof Instruction.Alloc) {
                    Instruction.Alloc alloc = (Instruction.Alloc) inst;
                    CollectArrayInfo(alloc);
                }
            }
        }
    }

    private static void CollectArrayInfo(Instruction.Alloc alloc) {
        ArrayList<Instruction> stores = new ArrayList<>();
        ArrayList<Instruction> loads = new ArrayList<>();
        ArrayList<Instruction> use = new ArrayList<>();
        use.addAll(alloc.getUsers());
        while (!use.isEmpty()) {
            Instruction useInst = use.remove(0);
            if (useInst instanceof Instruction.Load) loads.add(useInst);
            else if (useInst instanceof Instruction.Store store) {
                Instruction.GetElementPtr addr = (Instruction.GetElementPtr) store.getAddr();
                Value idx = addr.getOffsets().get(addr.getOffsets().size() - 1);
                Value val = store.getValue();
                if (idx instanceof Constant && val instanceof Constant) stores.add(useInst);
                else return;
            }
            else if (useInst instanceof Instruction.Call) {
                if (((Instruction.Call) useInst).getDestFunction().getName().equals("memset")) stores.add(useInst);
                if (FuncInfo.hasSideEffect.get(((Instruction.Call) useInst).getDestFunction())) return;
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
        Collections.reverse(stores);
        for (Instruction store : stores) {
            Instruction.GetElementPtr addr = (Instruction.GetElementPtr) ((Instruction.Store) store).getAddr();
            Value idx = addr.getOffsets().get(addr.getOffsets().size() - 1);
            Value val = ((Instruction.Store) store).getValue();
            if (init.containsKey(idx)) continue;
            init.put(((Constant.ConstantInt) idx).getIntValue(), (Constant) val);
        }
        arrayInitMap.put(alloc, new HashMap<>());
        delList.addAll(stores);
    }

    private static void LiftArray() {
        //对于每一个要提升的数组，生成全局变量
        for (Instruction.Alloc alloc : arrayInitMap.keySet()) {
            Type.ArrayType arrayType = (Type.ArrayType) ((Type.PointerType) alloc.getType()).getInnerType();

//            GlobalVariable gv = new GlobalVariable(arrayType);
            delList.add(alloc);
        }
        delList.forEach(Value::delete);
    }
}