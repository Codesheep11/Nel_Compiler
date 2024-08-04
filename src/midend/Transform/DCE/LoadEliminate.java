package midend.Transform.DCE;

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.*;
import mir.Function;
import mir.Module;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 移除冗余的Load语句
 * 冗余的Load:
 * 1.在下次Store之前以及存在相同的Load地址
 * 2.可以识别到的Store的值
 */
public class LoadEliminate {
    //Address(alloc/global/Arg) - <idx - Value>
    private static HashMap<Value, HashMap<Value, Value>> Address2Idx2Store = new HashMap<>();
    private static HashMap<Value, HashMap<Value, Value>> Address2Idx2Load = new HashMap<>();
    //全局标量
    private static HashMap<GlobalVariable, Value> Global2Store = new HashMap<>();
    private static HashMap<GlobalVariable, Value> Global2Load = new HashMap<>();
    //指针 - <idx - Load>
    private static ArrayList<Instruction> delList = new ArrayList<>();


    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    private static void clear() {
        Address2Idx2Store.clear();
        Address2Idx2Load.clear();
        Global2Store.clear();
        Global2Load.clear();
    }


    private static void runOnFunc(Function function) {
        clear();
        delList.clear();
        dfs(function.getEntry());
        delList.forEach(Value::delete);
    }

    private static void dfs(BasicBlock block) {
        HashMap<Value, HashMap<Value, Value>> curAddrStore = getCloneMap(Address2Idx2Store);
        HashMap<Value, HashMap<Value, Value>> curAddrLoad = getCloneMap(Address2Idx2Load);
        HashMap<GlobalVariable, Value> curGlobalStore = getClone(Global2Store);
        HashMap<GlobalVariable, Value> curGlobalLoad = getClone(Global2Load);
        //todo:如果存在前序被自己支配，则考虑在重复load处插入phi?可能需要arraySSA
        clear();
        for (Instruction instr : block.getInstructions()) {
            if (instr instanceof Instruction.Store) handleStore((Instruction.Store) instr);
            else if (instr instanceof Instruction.Load) handleLoad((Instruction.Load) instr);
            else if (instr instanceof Instruction.Call) handleCall((Instruction.Call) instr);
        }
        for (BasicBlock child : block.getDomTreeChildren()) dfs(child);
        Address2Idx2Store = curAddrStore;
        Address2Idx2Load = curAddrLoad;
        Global2Store = curGlobalStore;
        Global2Load = curGlobalLoad;
    }

    private static HashMap<Value, HashMap<Value, Value>> getCloneMap(HashMap<Value, HashMap<Value, Value>> map) {
        HashMap<Value, HashMap<Value, Value>> res = new HashMap<>();
        for (Value key : map.keySet()) res.put(key, new HashMap<>(map.get(key)));
        return res;
    }

    private static HashMap<GlobalVariable, Value> getClone(HashMap<GlobalVariable, Value> map) {
        HashMap<GlobalVariable, Value> res = new HashMap<>();
        for (GlobalVariable key : map.keySet()) res.put(key, map.get(key));
        return res;
    }


    private static void handleStore(Instruction.Store store) {
        Value baseAddr = store.getAddr();
        if (baseAddr instanceof Instruction.GetElementPtr) {
            Instruction.GetElementPtr gep = (Instruction.GetElementPtr) baseAddr;
            baseAddr = gep.getBase();
            Value idx = gep.getIdx();
            if (idx instanceof Constant.ConstantInt) {
                Address2Idx2Store.putIfAbsent(baseAddr, new HashMap<>());
                Address2Idx2Store.get(baseAddr).put(idx, store.getValue());
                Address2Idx2Load.putIfAbsent(baseAddr, new HashMap<>());
                Address2Idx2Load.get(baseAddr).remove(idx);
            }
            else {
                Address2Idx2Store.putIfAbsent(baseAddr, new HashMap<>());
                Address2Idx2Store.get(baseAddr).clear();
                Address2Idx2Store.get(baseAddr).put(idx, store.getValue());
                Address2Idx2Load.put(baseAddr, new HashMap<>());
            }
        }
        else {
            if (baseAddr instanceof Instruction.Alloc) {
                System.out.println("Alloc");
            }
            Global2Store.put((GlobalVariable) baseAddr, store.getValue());
            Global2Load.remove(baseAddr);
        }
    }

    private static void handleLoad(Instruction.Load load) {
        Value addr = load.getAddr();
        if (addr instanceof Instruction.GetElementPtr) {
            Instruction.GetElementPtr gep = (Instruction.GetElementPtr) addr;
            addr = gep.getBase();
            HashMap<Value, Value> arrayStore = Address2Idx2Store.getOrDefault(addr, new HashMap<>());
            HashMap<Value, Value> arrayLoad = Address2Idx2Load.getOrDefault(addr, new HashMap<>());
            Value idx = gep.getIdx();
            if (arrayStore.containsKey(idx)) {
                Value storeValue = arrayStore.get(idx);
                load.replaceAllUsesWith(storeValue);
                delList.add(load);
            }
            else if (arrayLoad.containsKey(idx)) {
                Value loadValue = arrayLoad.get(idx);
                load.replaceAllUsesWith(loadValue);
                delList.add(load);
            }
            else {
                arrayLoad.put(idx, load);
                Address2Idx2Load.put(addr, arrayLoad);
            }
        }
        else {
            if (Global2Store.containsKey(addr)) {
                Value storeValue = Global2Store.get(addr);
                load.replaceAllUsesWith(storeValue);
                delList.add(load);
            }
            else if (Global2Load.containsKey(addr)) {
                Value loadValue = Global2Load.get(addr);
                load.replaceAllUsesWith(loadValue);
                delList.add(load);
            }
            else {
                Global2Load.put((GlobalVariable) addr, load);
            }
        }
    }

    private static void handleCall(Instruction.Call call) {
        Function callee = call.getDestFunction();
        //传入数组写
        FuncInfo calleeInfo = AnalysisManager.getFuncInfo(callee);
        if (calleeInfo.hasSideEffect) {
            for (Value arg : call.getParams()) {
                if (arg.getType().isPointerTy()) {
                    Value baseAddr = getBaseAddr(arg);
                    Address2Idx2Store.remove(baseAddr);
                    Address2Idx2Load.remove(baseAddr);
                }
            }
        }
        //全局变量写
        //todo:之后考虑重构到具体的全局变量
        if (calleeInfo.hasMemoryWrite) {
            Global2Store.clear();
            Global2Load.clear();
            for (Value key : Address2Idx2Store.keySet()) {
                if (key instanceof GlobalVariable) Address2Idx2Store.get(key).clear();
            }
            for (Value key : Address2Idx2Load.keySet()) {
                if (key instanceof GlobalVariable) Address2Idx2Load.get(key).clear();
            }
        }
    }

    private static Value getBaseAddr(Value inst) {
        Value ret = inst;
        while (ret instanceof Instruction.GetElementPtr || ret instanceof Instruction.BitCast) {
            if (ret instanceof Instruction.GetElementPtr) {
                Instruction.GetElementPtr gep = (Instruction.GetElementPtr) ret;
                ret = gep.getBase();
            }
            if (ret instanceof Instruction.BitCast) {
                Instruction.BitCast bitCast = (Instruction.BitCast) inst;
                ret = bitCast.getSrc();
            }
        }
        return ret;
    }
}
