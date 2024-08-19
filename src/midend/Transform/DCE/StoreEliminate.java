package midend.Transform.DCE;

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashMap;


/**
 * * 存在的store:
 * * 1.局部数组的store
 * * 2.全局标量的store
 * * 3.全局数组的store
 * * 全局数组和全局标量的store依靠call指令和use进行分割
 * * 局部数组的store的call必须包含Pointer参数，use一致
 * * <p>
 * * 冗余的store:
 * * 一个store的所有的idoms都没有在下一次store前use的时候,才能删除
 *  todo:先做基本块内的store消除
 */
public class StoreEliminate {

    private static final HashMap<Value, HashMap<Value, Instruction.Store>> StoreMap = new HashMap<>();
    private static final HashMap<Value, Instruction.Store> GlobalStoreMap = new HashMap<>();
    private static final ArrayList<Instruction> delList = new ArrayList<>();

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    private static void clear() {
        StoreMap.clear();
        GlobalStoreMap.clear();
    }

    private static void runOnFunc(Function function) {
        for (BasicBlock block : function.getBlocks()) {
            clear();
            delList.clear();
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof Instruction.Store store) handleStore(store);
                else if (inst instanceof Instruction.Call call) handleCall(call);
                else if (inst instanceof Instruction.Load load) handleLoad(load);
            }
            delList.forEach(Value::delete);
        }
    }

    private static void handleStore(Instruction.Store store) {
        Value addr = store.getAddr();
        while (addr instanceof Instruction.BitCast bitCast) {
            addr = bitCast.getSrc();
        }
        if (addr instanceof Instruction.Load) {
            return;
        }
        if (addr instanceof GlobalVariable) {
            if (GlobalStoreMap.containsKey(addr)) delList.add(GlobalStoreMap.get(addr));
            GlobalStoreMap.put(addr, store);
        }
        else if (addr instanceof Instruction.GetElementPtr gep) {
            Value base = gep.getBase();
            Value idx = gep.getIdx();
            StoreMap.putIfAbsent(base, new HashMap<>());
            if (idx instanceof Constant.ConstantInt) {
                if (StoreMap.get(base).containsKey(idx)) delList.add(StoreMap.get(base).get(idx));
                StoreMap.get(base).put(idx, store);
            }
            else {
                if (StoreMap.get(base).containsKey(idx)) delList.add(StoreMap.get(base).get(idx));
                StoreMap.get(base).clear();
                StoreMap.get(base).put(idx, store);
            }
        }
        else {
            throw new RuntimeException("store addr error");
        }
    }

    private static void handleLoad(Instruction.Load load) {
        Value addr = load.getAddr();
        while (addr instanceof Instruction.BitCast bitCast) {
            addr = bitCast.getSrc();
        }
        if (addr instanceof Instruction.Load) {
            return;
        }
        if (addr instanceof GlobalVariable) {
            GlobalStoreMap.remove(addr);
        }
        else if (addr instanceof Instruction.GetElementPtr gep) {
            Value base = gep.getBase();
            Value idx = gep.getIdx();
            StoreMap.putIfAbsent(base, new HashMap<>());
            if (idx instanceof Constant.ConstantInt) StoreMap.get(base).remove(idx);
            else StoreMap.get(base).clear();
        }
        else throw new RuntimeException("load addr error");

    }

    private static void handleCall(Instruction.Call call) {
        Function callee = call.getDestFunction();
        //传入数组写
        FuncInfo calleeInfo = AnalysisManager.getFuncInfo(callee);
        for (Value arg : call.getParams()) {
            if (arg.getType().isPointerTy()) {
                Value baseAddr = getBaseAddr(arg);
                StoreMap.remove(baseAddr);
            }
        }
        //全局变量写
        //todo:之后考虑重构到具体的全局变量
        if (calleeInfo.hasMemoryWrite) {
            GlobalStoreMap.clear();
            for (Value key : StoreMap.keySet()) {
                if (key instanceof GlobalVariable) StoreMap.get(key).clear();
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
}
