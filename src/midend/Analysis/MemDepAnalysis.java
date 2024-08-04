package midend.Analysis;

import midend.Analysis.result.DGinfo;
import midend.Analysis.result.MemDepInfo;
import mir.*;
import mir.Module;

import java.util.HashMap;

public class MemDepAnalysis {
    private static DGinfo dgInfo;

    private static MemDepInfo memDepInfo;

    public static class MemObject {
        private Value baseAddr; //global alloca argument
        private Value idx;
        private int offset;
        private Value size;

        public MemObject(Value baseAddr, Value idx, int offset, Value size) {
            this.baseAddr = baseAddr;
            this.idx = idx;
            this.offset = offset;
            this.size = size;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MemObject memObject)) return false;
            return this.baseAddr.equals(memObject.baseAddr) &&
                    this.idx.equals(memObject.idx) &&
                    this.size.equals(memObject.size) &&
                    this.offset == memObject.offset;
        }

        public boolean isClearMemLocation() {
            return idx instanceof Constant.ConstantInt;
        }
    }

    //记录内存对象与上次定值语句
    private static HashMap<MemObject, Instruction> storeMap = new HashMap<>();

    //内存对象与Value的快速表
    private static HashMap<Value, MemObject> memObjectMap = new HashMap<>();

    private static void run(Module module) {
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            runOnFunc(func);
        }
    }

    private static void runOnFunc(Function func) {
        dgInfo = AnalysisManager.getDG(func);
        MemDepInfo memDepInfo = new MemDepInfo(func);
        storeMap.clear();
        memObjectMap.clear();
        dfs(func.getEntry());
        //AnalysisManager.setMemDepInfo(func, memDepInfo);
    }

    private static void dfs(BasicBlock block) {
        HashMap<MemObject, Instruction> curStoreMap = cloneMap(storeMap);
        for (Instruction inst : block.getInstructions()) {
            if (inst instanceof Instruction.Store) {
                handleStore((Instruction.Store) inst);
            }
//            else if (inst instanceof Instruction.Load) {
//                handleLoad((Instruction.Load) inst);
//            }
//            else if (inst instanceof Instruction.Call) {
//                handleCall((Instruction.Call) inst);
//            }
        }
        for (BasicBlock child : dgInfo.getDomTreeChildren(block)) {
            dfs(child);
        }
        storeMap = curStoreMap;
    }

    private static void handleStore(Instruction.Store store) {
        MemObject memObject = getMemObject(store.getAddr());
        if (memObject.isClearMemLocation()) {
            storeMap.put(memObject, store);
            return;
        }
        //所有相关基地址都可以消除
        for (MemObject key : storeMap.keySet()) {
            if (key.baseAddr.equals(memObject.baseAddr)) {
                storeMap.remove(key);
            }
        }
        storeMap.put(memObject, store);
    }


    private static HashMap<MemObject, Instruction> cloneMap(HashMap<MemObject, Instruction> map) {
        HashMap<MemObject, Instruction> ret = new HashMap<>();
        for (MemObject key : map.keySet()) {
            ret.put(key, map.get(key));
        }
        return ret;
    }

    private static MemObject getMemObject(Value addr) {
        if (!addr.getType().isPointerTy()) throw new RuntimeException("not a pointer");
        if (memObjectMap.containsKey(addr)) return memObjectMap.get(addr);
        Value baseAddr = getBaseAddr(addr);
        MemObject memObject;
        if (addr instanceof Instruction.BitCast bitcast) {
            addr = bitcast.getSrc();
        }
        if (addr instanceof Instruction.GetElementPtr gep) {
            Value idx = gep.getIdx();
            int sizeCnt = gep.getType().queryBytesSizeOfType();
            Value size = Constant.ConstantInt.get(sizeCnt);
            memObject = new MemObject(baseAddr, idx, sizeCnt, size);
        }
        else {
            int sizeCnt = ((Type.PointerType) addr.getType()).getInnerType().queryBytesSizeOfType();
            Value size = Constant.ConstantInt.get(sizeCnt);
            memObject = new MemObject(baseAddr, Constant.ConstantInt.get(0), sizeCnt, size);
        }
        memObjectMap.put(addr, memObject);
        return memObject;
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
