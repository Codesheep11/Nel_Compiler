package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import midend.Util.CloneInfo;
import mir.*;
import mir.Module;
import midend.Analysis.result.SCEVinfo;
import utils.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

public class LoopParallel {

    private static Instruction.Icmp indvar_cmp;

    private static int init;
    private static int step;
    private static SCEVinfo scevInfo;
    private static Instruction.Phi indvar;
    private static Instruction.Phi recIndvar;


    private static Module module;

    private static Function LoopParallelFor = null;

    private static Function ReduceAddI32 = null;

    private static Function ReduceAddF32 = null;

    private static Function getLoopParallelFuncLib() {
        if (LoopParallelFor != null) return LoopParallelFor;
        LoopParallelFor = new Function(Type.VoidType.VOID_TYPE,
                "NELParallelFor",
                Type.BasicType.I32_TYPE,
                Type.BasicType.I32_TYPE,
                Type.FunctionType.FUNC_TYPE);
        return LoopParallelFor;
    }

    private static Function getReduceAddI32FuncLib() {
        if (ReduceAddI32 != null) return ReduceAddI32;
        ReduceAddI32 = new Function(Type.VoidType.VOID_TYPE,
                "NELReduceAddI32",
                new Type.PointerType(Type.BasicType.I32_TYPE),
                Type.BasicType.I32_TYPE);
        return ReduceAddI32;
    }

    private static Function getReduceAddF32FuncLib() {
        if (ReduceAddF32 != null) return ReduceAddF32;
        ReduceAddF32 = new Function(Type.VoidType.VOID_TYPE,
                "NELReduceAddF32",
                new Type.PointerType(Type.BasicType.F32_TYPE),
                Type.BasicType.F32_TYPE);
        return ReduceAddF32;
    }

    private static int id = 0;

    private static String getParallelBodyName() {
        return "NEL_parallel_body_" + id++;
    }

    private static String getParallelPayloadName() {
        return "NEL_parallel_body_payload_" + id++;
    }


    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (!function.getName().equals("main")) continue;
            runOnFunc(function);
            break;
        }
    }

    public static void runOnFunc(Function function) {
        scevInfo = AnalysisManager.getSCEV(function);
        ArrayList<Loop> loops = new ArrayList<>(function.loopInfo.TopLevelLoops);
        loops.sort(Comparator.comparingInt(a -> AnalysisManager.getDomDepth(a.header)));
        for (Loop loop : loops) {
            tryParallelLoop(loop);
        }
    }

    private static void tryParallelLoop(Loop loop) {
        if (!canTransform(loop)) return;

        //新建bodyFunc
        Function bodyFunc = new Function(Type.VoidType.VOID_TYPE, getParallelBodyName());
        module.addFunction(bodyFunc);
        ArrayList<Pair<Value, Integer>> payload = new ArrayList<>();
        int totalSize = 0;
        GlobalVariable payloadVar = new GlobalVariable(new Constant.ConstantArray
                (new Type.ArrayType(totalSize / 4, Type.BasicType.I32_TYPE)),
                getParallelPayloadName());
        module.addGlobalValue(payloadVar);
        ArrayList<Function.Argument> arguments = new ArrayList<>();
        Function.Argument beg = new Function.Argument(Type.BasicType.I32_TYPE, bodyFunc);
        beg.idx = 0;
        arguments.add(beg);
        Function.Argument end = new Function.Argument(Type.BasicType.I32_TYPE, bodyFunc);
        arguments.add(end);
        end.idx = 1;
        bodyFunc.setFuncRArguments(arguments);
        BasicBlock funcEntry = new BasicBlock(bodyFunc.getBBName() + "_entry", bodyFunc);
        BasicBlock funcRet = new BasicBlock(bodyFunc.getBBName() + "_ret", bodyFunc);
        for (BasicBlock block : loop.getAllBlocks()) {
            block.remove();
            block.setParentFunction(bodyFunc);
            bodyFunc.appendBlock(block);
        }
        loop.header.
    }

    private static void addArgument(Value param, HashSet<Value> insertedParam) {
//        if (param == indvar) return;
//        if (param instanceof Constant) return;
//        if (param == indvar_cmp.getSrc1() && !indvar_cmp.getSrc2().getUsers().isEmpty()) return;
//        if (!insertedParam.add(param)) return;
//        int align = param.getType().getAlign();
//        int size = param.getType().getSize();
//        totalSize = (totalSize + align - 1) / align * align;
//        maxAlignment = Math.max(maxAlignment, align);
//        if (param == indvar_cmp.getSrc1())
//            givOffset = new Constant.ConstantInt(Type.BasicType.I32_TYPE, totalSize);
//        else
//            payload.add(new Pair<>(param, totalSize));
//        totalSize += size;
    }

    private static boolean canTransform(Loop loop) {
        if (loop.tripCount > 0) return false;
        if (loop.exits.size() != 1) return false;
        int phiCnt = 0;
        for (Instruction.Phi phi : loop.header.getPhiInstructions()) {
            if (scevInfo.contains(phi)) {
                phiCnt++;
            }
        }
        //保证最多一个归纳变量 一个计算变量
        if (phiCnt > 2) return false;
        //todo:分别设置indvar和recIndvar

        //循环独立性检查
        //收集循环内所有的基地址
        HashMap<Value, Integer> loadStoreMap = new HashMap<>();
        for (BasicBlock block : loop.getAllBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof Instruction.Terminator) return false;
                if (inst instanceof Instruction.Load load) {
                    Value ptr = load.getAddr();
                    Value baseAddr = getBaseAddr(ptr);
                    loadStoreMap.put(baseAddr, loadStoreMap.getOrDefault(baseAddr, 0) | 1);
                }
                else if (inst instanceof Instruction.Store store) {
                    Value ptr = store.getAddr();
                    Value baseAddr = getBaseAddr(ptr);
                    loadStoreMap.put(baseAddr, loadStoreMap.getOrDefault(baseAddr, 0) | 2);
                }
                else if (!inst.isNoSideEffect()) {
                    return false;
                }
            }
        }

        ArrayList<Pair<Instruction.Load, Instruction.Store>> workList = new ArrayList<>();
        for (Value key : loadStoreMap.keySet()) {
            if (loadStoreMap.get(key) == 3) {
                Instruction.Load load = null;
                for (BasicBlock block : loop.getAllBlocks()) {
                    for (Instruction inst : block.getInstructions()) {
                        if (inst instanceof Instruction.Load) {
                            if (getBaseAddr(((Instruction.Load) inst).getAddr()) == key) {
                                if (load != null) return false;
                                load = (Instruction.Load) inst;
                            }
                        }
                        else if (inst instanceof Instruction.Store store) {
                            if (getBaseAddr(((Instruction.Store) inst).getAddr()) == key) {
                                if (load == null) return false;
                                if (load.getUsers().size() == 1 && load.getAddr() == store.getAddr()
                                        && load.getParentBlock() == store.getParentBlock())
                                {
                                    Value storeVal = store.getValue();
                                    if (!(storeVal instanceof Instruction.Add add)) return false;
                                    if (add.getOperands().contains(load) && add.getUsers().size() == 1) {
                                        workList.add(new Pair<>(load, store));
                                        load = null;
                                    }
                                    else {
                                        return false;
                                    }
                                }
                                else {
                                    return false;
                                }
                            }
                        }
                    }
                }
                if (load != null) return false;
            }
        }
        //将存储操作转化成原子指令
        for (Pair<Instruction.Load, Instruction.Store> pair : workList) {
            Instruction.Load load = pair.getKey();
            Instruction.Store store = pair.getValue();
            BasicBlock block = store.getParentBlock();
            Value ptr = store.getAddr();
            Instruction.Add add = (Instruction.Add) store.getValue();
            Value inc = add.getOperand_1().equals(load) ? add.getOperand_2() : add.getOperand_1();
            Instruction.AtomicAdd atomicAdd = new Instruction.AtomicAdd(block, load.getType(), ptr, inc);
            atomicAdd.remove();
            block.getInstructions().insertBefore(atomicAdd, store);
            store.delete();
            add.delete();
            load.delete();
        }
        return true;
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
