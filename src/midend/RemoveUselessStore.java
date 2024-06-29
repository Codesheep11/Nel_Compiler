//package midend;
//
//import mir.*;
//import mir.Function;
//import mir.Module;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.Iterator;
//
///**
// * 移除冗余的store语句
// * 可能存在的store:
// * 1.局部数组的store
// * 2.全局标量的store
// * 3.全局数组的store
// * 全局数组和全局标量的store依靠call指令和use进行分割
// * 局部数组的store的call必须包含Pointer参数，use一致
// * <p>
// * 局部数组的冗余store策略：一个store的所有的idoms都没有在下一次store前use的时候,才能删除
// * 标量的store策略：一个store的所有的idoms都没有在下一次store前use的时候,才能删除
// */
//public class RemoveUselessStore {
//
//    private static HashMap<Instruction.Alloc, HashSet<Instruction>> allocAddress = new HashMap<>();//局部Alloc的记录
//    private static HashMap<Instruction.GetElementPtr, HashSet<Instruction>> localStoreAddress = new HashMap<>();//局部GEP的记录
//    private static HashMap<GlobalVariable, HashSet<Instruction>> globalAddress = new HashMap<>();//全局变量的记录
//    private static HashMap<Instruction, HashSet<Instruction>> globalStoreAddress = new HashMap<>();//全局变量的store记录
//
//    public static void run(Module module) {
//        for (Function function : module.getFuncSet()) {
//            runOnFunc(function);
//        }
//    }
//
//    /**
//     * fixme:基址与变址
//     * 对于每一条Store指令，如果其地址在下一次Store指令前或者Call指令之前没有被load或者Call，则删除这条Store指令
//     *
//     * @param function
//     */
//    private static void runOnFunc(Function function) {
//        allocAddress.clear();
//        localStoreAddress.clear();
//        globalAddress.clear();
//        globalStoreAddress.clear();
//        ArrayList<BasicBlock> domSet = function.getDomTreeLayerSort();
//        for (BasicBlock block : domSet) {
//            for (Instruction inst : block.getInstructions()) {
//                if (inst instanceof Instruction.Alloc) handleAlloc((Instruction.Alloc) inst);
//                else if (inst instanceof Instruction.Store) handleStore((Instruction.Store) inst);
//                else if (inst instanceof Instruction.Load) handleLoad((Instruction.Load) inst);
//                else if (inst instanceof Instruction.Call) handleCall((Instruction.Call) inst);
//            }
//        }
//        for (Instruction.Alloc alloc : allocStoreAddress.keySet()) {
//            for (Instruction store : allocStoreAddress.get(alloc)) {
//                store.delete();
//            }
//        }
//    }
//
//    /**
//     * 将局部变量的栈空间记录下来
//     *
//     * @param alloc
//     */
//
//    private static void handleAlloc(Instruction.Alloc alloc) {
//        localStoreAddress.put(alloc, new HashSet<>());
//    }
//
//    private static void handleStore(Instruction.Store store) {
//        Value baseAddr = getBaseAddr(store.getAddr());
//        if (baseAddr instanceof GlobalVariable) {
//            if (globalStoreAddress.containsKey(baseAddr)) {
//
//            }
//            else {
//                globalStoreAddress.put((GlobalVariable) baseAddr, new HashSet<>());
//                globalStoreAddress.get(baseAddr).add(store);
//            }
//        }
//        else if (baseAddr instanceof Instruction.Alloc) {
//            Instruction.Alloc alloc = (Instruction.Alloc) baseAddr;
//            localStoreAddress.get(alloc).add(store);
//        }
//    }
//
//    private static void handleLoad(Instruction.Load load) {
//        Value addr = getBaseAddr(load.getAddr());
//        if (addr instanceof GlobalVariable) {
//            GlobalVariable global = (GlobalVariable) addr;
//            globalStoreAddress.remove(global);
//        }
//        else if (addr instanceof Instruction.Alloc) {
//            Instruction.Alloc alloc = (Instruction.Alloc) addr;
//            localStoreAddress.remove(alloc);
//        }
//    }
//
//    private static void handleCall(Instruction.Call call) {
//        for (Value arg : call.getArgs()) {
//            if (arg instanceof GlobalVariable) {
//                GlobalVariable global = (GlobalVariable) arg;
//                globalStoreAddress.remove(global);
//            }
//            else if (arg instanceof Instruction.Alloc) {
//                Instruction.Alloc alloc = (Instruction.Alloc) arg;
//                localStoreAddress.remove(alloc);
//            }
//        }
//    }
//
//    private static Value getBaseAddr(Value inst) {
//        if (inst instanceof Instruction.GetElementPtr) {
//            Instruction.GetElementPtr gep = (Instruction.GetElementPtr) inst;
//            return gep.getBase();
//        }
//        if (inst instanceof Instruction.BitCast) {
//            Instruction.BitCast bitCast = (Instruction.BitCast) inst;
//            return bitCast.getSrc();
//        }
//        return inst;
//    }
//}
