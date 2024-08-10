package midend.Transform.Loop;

import backend.Opt.BackLoop.LoopConstLift;
import midend.Analysis.AnalysisManager;
import midend.Util.CloneInfo;
import midend.Util.Print;
import mir.*;
import mir.Module;
import midend.Analysis.result.SCEVinfo;
import utils.Pair;

import java.util.*;

public class LoopParallel {

    private static Instruction.Icmp indvar_cmp;

    private static int init;
    private static int step;
    private static SCEVinfo scevInfo;
    private static Instruction indvar;
    private static LinkedHashMap<Value, Integer> payLoad = new LinkedHashMap<>();
    private static HashSet<Value> InPayLoad = new HashSet<>();
    private static HashSet<Value> OutPayLoad = new HashSet<>();
    private static LinkedHashMap<Instruction.Phi, ArrayList<Pair<Instruction, Value>>> recMap = new LinkedHashMap<>();
    private static int totalSize = 0;

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
        module.addFunction(LoopParallelFor);
        return LoopParallelFor;
    }

    private static Function getReduceAddI32FuncLib() {
        if (ReduceAddI32 != null) return ReduceAddI32;
        ReduceAddI32 = new Function(Type.VoidType.VOID_TYPE,
                "NELReduceAddI32",
                new Type.PointerType(Type.BasicType.I32_TYPE),
                Type.BasicType.I32_TYPE);
        module.addFunction(ReduceAddI32);
        return ReduceAddI32;
    }

    private static Function getReduceAddF32FuncLib() {
        if (ReduceAddF32 != null) return ReduceAddF32;
        ReduceAddF32 = new Function(Type.VoidType.VOID_TYPE,
                "NELReduceAddF32",
                new Type.PointerType(Type.BasicType.F32_TYPE),
                Type.BasicType.F32_TYPE);
        module.addFunction(ReduceAddF32);
        return ReduceAddF32;
    }

    private static int id = -1;

    private static String getParallelBodyName() {
        return "NEL_parallel_body_" + id;
    }

    private static String getParallelPayloadName() {
        return "NEL_parallel_body_payload_" + id;
    }


    public static void run(Module mod) {
        module = mod;
        for (Function function : mod.getFuncSet()) {
            if (!function.getName().equals("main")) continue;
            runOnFunc(function);
            break;
        }
    }

    public static void runOnFunc(Function function) {
        AnalysisManager.refreshSCEV(function);
        scevInfo = AnalysisManager.getSCEV(function);
        ArrayList<Loop> loops = new ArrayList<>(function.loopInfo.TopLevelLoops);
        loops.sort(Comparator.comparingInt(a -> AnalysisManager.getDomDepth(a.header)));
        for (Loop loop : loops) {
            tryParallelLoop(loop);
        }
    }

    private static void tryParallelLoop(Loop loop) {
        if (!canTransform(loop)) return;
//        System.out.println("Parallel Loop run");
        id++;
        //新建loopFunc
        Function loopFunc = new Function(Type.VoidType.VOID_TYPE, getParallelBodyName());
        module.addFunction(loopFunc);
        loopFunc.isParallelLoopBody = true;
        //新建payload
        payLoad.clear();
        InPayLoad.clear();
        OutPayLoad.clear();
        totalSize = 0;
        ArrayList<BasicBlock> blocks = new ArrayList<>(loop.getAllBlocks());
        //将循环中使用的不在循环中定义的变量加入payload
        for (BasicBlock bb : blocks) {
            for (Instruction inst : bb.getInstructions()) {
                for (Value operand : inst.getOperands()) {
                    if (operand instanceof Instruction instr && !blocks.contains(instr.getParentBlock()))
                        insertPayLoad(operand, InPayLoad);
                }
            }
        }
        //将外部使用的归纳变量加入payload
        for (Value v : recList) {
            insertPayLoad(v, OutPayLoad);
        }
        GlobalVariable payloadVar = new GlobalVariable(new Constant.ConstantArray
                (new Type.ArrayType(totalSize / 4, Type.BasicType.I32_TYPE)),
                getParallelPayloadName());
        module.addGlobalValue(payloadVar);
        ArrayList<Function.Argument> arguments = new ArrayList<>();
        Function.Argument beg = new Function.Argument(Type.BasicType.I32_TYPE, loopFunc);
        beg.idx = 0;
        arguments.add(beg);
        Function.Argument end = new Function.Argument(Type.BasicType.I32_TYPE, loopFunc);
        arguments.add(end);
        end.idx = 1;
        loopFunc.setFuncRArguments(arguments);
        BasicBlock funcEntry = new BasicBlock(loopFunc.getBBName() + "_entry", loopFunc);
        BasicBlock funcRet = new BasicBlock(loopFunc.getBBName() + "_ret", loopFunc);

        LoopCloneInfo info = loop.cloneAndInfo();
        Loop cloneLoop = info.cpy;

        // 将clone得到的块放入新函数
        for (BasicBlock block : cloneLoop.getAllBlocks()) {
            block.remove();
            block.setParentFunction(loopFunc);
            loopFunc.appendBlock(block);
        }

        BasicBlock preHeader = loop.getPreHeader();
        for (Instruction.Phi phi : cloneLoop.header.getPhiInstructions()) {
            phi.changePreBlock(preHeader, funcEntry);
        }
        Instruction.Phi reflectIndvar = (Instruction.Phi) info.getReflectedValue(indvar);
        reflectIndvar.replaceOptionalValueAtWith(funcEntry, beg);

        Instruction.Icmp icmp = (Instruction.Icmp) info.getReflectedValue(indvar_cmp);
        icmp.replaceUseOfWith(icmp.getSrc2(), end);

        Instruction.Branch br = (Instruction.Branch) cloneLoop.header.getTerminator();
        br.replaceUseOfWith(loop.getExit(), funcRet);

        for (Value rec : recList) {
            Instruction reflectedValue = (Instruction) info.getReflectedValue(rec);
            Instruction.Add add = (Instruction.Add) ((Instruction.Phi) reflectedValue).getOptionalValue(cloneLoop.getLatch());
            BasicBlock block = add.getParentBlock();
            Value inc = add.getOperand_1().equals(reflectedValue) ? add.getOperand_2() : add.getOperand_1();
            ArrayList<Value> offsets = new ArrayList<>();
            offsets.add(Constant.ConstantInt.get(0));
            offsets.add(Constant.ConstantInt.get(payLoad.get(rec) / 4));
            Instruction ptr = new Instruction.GetElementPtr(funcRet, payloadVar, Type.BasicType.I32_TYPE, offsets);
            ptr.remove();
            block.getInstructions().insertBefore(ptr, add);
            if (rec.getType().isFloatTy()) {
                ptr = new Instruction.BitCast(funcRet, ptr, Type.BasicType.F32_TYPE);
                ptr.remove();
                block.getInstructions().insertBefore(ptr, add);
            }
            Instruction.AtomicAdd atomicAdd = new Instruction.AtomicAdd(add.getParentBlock(), reflectedValue.getType(), ptr, inc);
            atomicAdd.remove();
            block.getInstructions().insertBefore(atomicAdd, add);
        }
        // 修改原循环为函数调用
        BasicBlock callBlock = new BasicBlock(loop.header.getParentFunction().getBBName()
                + "_exec_parallel", loop.header.getParentFunction());
        preHeader.getTerminator().delete();
        new Instruction.Jump(preHeader, callBlock);
        //内部使用的value换成payLoad
        for (Value v : InPayLoad) {
            //在原函数存
            ArrayList<Value> offsets = new ArrayList<>();
            offsets.add(Constant.ConstantInt.get(0));
            offsets.add(Constant.ConstantInt.get(payLoad.get(v) / 4));
            Value ptr = new Instruction.GetElementPtr(callBlock, payloadVar, Type.BasicType.I32_TYPE, offsets);
            if (v.getType().isFloatTy()) {
                ptr = new Instruction.BitCast(callBlock, ptr, Type.BasicType.F32_TYPE);
            }
            else if (v.getType().isPointerTy()) {
                ptr = new Instruction.BitCast(callBlock, ptr, Type.BasicType.I64_TYPE);
            }
            new Instruction.Store(callBlock, v, ptr);
            //在新函数取
            Value ptr1 = new Instruction.GetElementPtr(funcEntry, payloadVar, Type.BasicType.I32_TYPE, new ArrayList<>(offsets));
            if (v.getType().isFloatTy()) {
                ptr1 = new Instruction.BitCast(funcEntry, ptr, Type.BasicType.F32_TYPE);
            }
            else if (v.getType().isPointerTy()) {
                ptr1 = new Instruction.BitCast(funcEntry, ptr, Type.BasicType.I64_TYPE);
            }
            Instruction.Load load = new Instruction.Load(funcEntry, ptr1);
            ArrayList<Instruction> replaceUsers = new ArrayList<>();
            for (Instruction user : v.getUsers()) {
                if (user.getParentBlock().getParentFunction().equals(loopFunc)) {
                    replaceUsers.add(user);
                }
            }
            for (Instruction user : replaceUsers) {
                user.replaceUseOfWith(v, load);
            }
        }
        new Instruction.Jump(funcEntry, cloneLoop.header);
        //添加Call指令
        ArrayList<Value> args = new ArrayList<>();
        args.add(Constant.ConstantInt.get(init));
        args.add(indvar_cmp.getSrc2());
        args.add(loopFunc);
        new Instruction.Call(callBlock, getLoopParallelFuncLib(), args);
//        new Instruction.Call(callBlock, loopFunc, args);
        //循环的出口换成payLoad
        for (Value v : OutPayLoad) {
            Value reflectedValue = info.getReflectedValue(v);
            ArrayList<Value> offsets = new ArrayList<>();
//            offsets.add(Constant.ConstantInt.get(0));
//            offsets.add(Constant.ConstantInt.get(payLoad.get(v) / 4));
//            Value ptr = new Instruction.GetElementPtr(funcRet, payloadVar, Type.BasicType.I32_TYPE, offsets);
//            if (v.getType().isFloatTy()) {
//                ptr = new Instruction.BitCast(funcRet, ptr, Type.BasicType.F32_TYPE);
//            }
//            else if (v.getType().isPointerTy()) {
//                ptr = new Instruction.BitCast(funcRet, ptr, Type.BasicType.I64_TYPE);
//            }
//            new Instruction.Store(funcRet, reflectedValue, ptr);
            Value ptr1 = new Instruction.GetElementPtr(callBlock, payloadVar, Type.BasicType.I32_TYPE, new ArrayList<>(offsets));
            if (v.getType().isFloatTy()) {
                ptr1 = new Instruction.BitCast(callBlock, ptr1, Type.BasicType.F32_TYPE);
            }
            else if (v.getType().isPointerTy()) {
                ptr1 = new Instruction.BitCast(callBlock, ptr1, Type.BasicType.I64_TYPE);
            }
            Instruction.Load load = new Instruction.Load(callBlock, ptr1);
            //在退出块替换
            ArrayList<Instruction> replaceUsers = new ArrayList<>();
            for (Instruction user : v.getUsers()) {
                if (user.getParentBlock().equals(loop.getExit())) {
                    replaceUsers.add(user);
                }
            }
            for (Instruction user : replaceUsers) {
                user.replaceUseOfWith(v, load);
            }
        }
        new Instruction.Jump(callBlock, loop.getExit());
        new Instruction.Return(funcRet);
    }

    private static void insertPayLoad(Value value, HashSet<Value> payLoadSet) {
        if (value instanceof Constant) return;
        if (value.equals(indvar)) return;
        //todo:目前只支持main
//        if (value instanceof Function.Argument) return;
        if (payLoadSet.contains(value)) return;
        int size = value.getType().queryBytesSizeOfType();
        if (size == 8 && totalSize % 8 != 0) {
            totalSize += 4;
        }
        payLoad.put(value, totalSize);
        totalSize += size;
        payLoadSet.add(value);
    }

    private static boolean canTransform(Loop loop) {
        if (loop.tripCount > 0) return false;
        if (loop.exits.size() != 1) return false;
        ArrayList<BasicBlock> _pre = loop.getExit().getPreBlocks();
        if (_pre.size() > 1 || _pre.get(0) != loop.header) return false;
        Instruction terminator = loop.header.getTerminator();
        if (!(terminator instanceof Instruction.Branch br)) return false;
        Value cond = br.getCond();
        if (!(cond instanceof Instruction.Icmp icmp)) return false;
        if (scevInfo.contains(icmp.getSrc2()))
            icmp.swap();
        if (scevInfo.contains(icmp.getSrc2()))
            return false;
        if (!scevInfo.contains(icmp.getSrc1()))
            return false;
        if (!(icmp.getSrc1() instanceof Instruction))
            return false;
        indvar_cmp = icmp;
        if (!(icmp.getSrc1() instanceof Instruction.Phi phi)) return false;
        indvar = phi;
        SCEVExpr scev = scevInfo.query(icmp.getSrc1());
        if (!scev.isInSameLoop())
            return false;
        init = scev.getInit();
        step = scev.getStep();
        if (step != 1) return false;
        //识别出循环中的累加量
        recMap.clear();
        for (Instruction.Phi phi1 : loop.getExit().getPhiInstructions()) {
            if (!phi1.isLCSSA) continue;
            //todo: 找到所有可以找到的rec 存储在recMap中<LCSSA_out, <inst,inc> >
            Value val = phi1.getOptionalValue(loop.header);
            if (!(val instanceof Instruction.Phi rec)) return false;
            //这里的val是循环头中向外传递出去的val
            if (!getRecPhi(rec, loop)) {
                return false;
            }
        }
        //循环独立性检查
        //收集循环内所有的基地址
        HashMap<Value, Integer> loadStoreMap = new HashMap<>();
        for (BasicBlock block : loop.getAllBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof Instruction.Terminator) continue;
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
//                    System.err.println(inst);
                    return false;
                }
            }
        }
        //检查是否有可以原子化的读写操作以及危险操作
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

    private static boolean getRecPhi(Instruction.Phi rec, Loop curLoop) {
        BasicBlock latch = curLoop.getLatch();
        if (rec.getOptionalValue(latch) instanceof Instruction.Phi phi) {
            if (phi.isLCSSA) {
                phi.getOptionalValue();
                return getRecPhi(phi, curLoop);
            }
        }
        else if (rec.getOptionalValue(latch) instanceof Instruction.Add add) {
//            recMap
            return true;
        }
        else if (rec.getOptionalValue(latch) instanceof Instruction.FAdd fadd) {
//            recList.add(fadd);
            return true;
        }
        return false;

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
