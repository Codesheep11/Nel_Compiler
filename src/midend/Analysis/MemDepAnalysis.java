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
        private int offset;
        private int size;

        public MemObject(Value baseAddr, int offset, int size) {
            this.baseAddr = baseAddr;
            this.offset = offset;
            this.size = size;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MemObject memObject)) return false;
            return this.baseAddr.equals(memObject.baseAddr) &&
                    this.offset == memObject.offset &&
                    this.size == memObject.size;
        }

        public boolean contains(MemObject memObject) {
            if (!baseAddr.equals(memObject.baseAddr)) return false;
            if (this.offset <= memObject.offset && this.offset + this.size >= memObject.offset + memObject.size)
                return true;
            return false;
        }
    }

    //内存对象与Value的快速表
    private static HashMap<Value, MemObject> memObjectMap = new HashMap<>();

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
            if (idx instanceof Constant.ConstantInt c) {
                int sizeCnt = gep.getType().queryBytesSizeOfType();
                memObject = new MemObject(baseAddr, c.getIntValue() * sizeCnt, sizeCnt);
            }
            else {
                int sizeCnt = ((Type.PointerType) baseAddr.getType()).getInnerType().queryBytesSizeOfType();
                memObject = new MemObject(baseAddr, 0, sizeCnt);
            }
        }
        else {
            int sizeCnt = ((Type.PointerType) addr.getType()).getInnerType().queryBytesSizeOfType();
            memObject = new MemObject(baseAddr, 0, sizeCnt);
        }
        memObjectMap.put(addr, memObject);
        return memObject;
    }

    //记录内存对象与上次定值语句
    //baseAddr - MemObject - Store/Call
    private static HashMap<Value, HashMap<MemObject, Instruction>> storeMap = new HashMap<>();

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
        HashMap<Value, HashMap<MemObject, Instruction>> curStoreMap = cloneMap(storeMap);
        if (block.getPreBlocks().size() > 1) {
            storeMap.clear();
        }
//        for (Instruction inst : block.getInstructions()) {
//            if (inst instanceof Instruction.Store) {
//                handleStore((Instruction.Store) inst);
//            }
//            else if (inst instanceof Instruction.Load) {
//                handleLoad((Instruction.Load) inst);
//            }
//            else if (inst instanceof Instruction.Call) {
//                handleCall((Instruction.Call) inst);
//            }
//        }
        for (BasicBlock child : dgInfo.getDomTreeChildren(block)) {
            dfs(child);
        }
        storeMap = curStoreMap;
    }


    private static HashMap<Value, HashMap<MemObject, Instruction>> cloneMap(HashMap<Value, HashMap<MemObject, Instruction>> map) {
        HashMap<Value, HashMap<MemObject, Instruction>> res = new HashMap<>();
        for (Value key : map.keySet()) {
            res.put(key, new HashMap<>(map.get(key)));
        }
        return res;
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
