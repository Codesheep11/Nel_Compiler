//package midend.Transform.Loop;
//
//import midend.Analysis.AnalysisManager;
//import midend.Analysis.result.DGinfo;
//import midend.Util.FuncInfo;
//import mir.*;
//import mir.Module;
//
//import java.util.*;
//
//import static midend.Transform.Loop.LCSSA.isDomable;
//
//public class LoopInCodeMotion {
//
//    public static HashSet<Value> varInLoop = new HashSet<>();
//
//    public static DGinfo dgInfo;
//
//    public static void run(Module module) {
//        for (Function function : module.getFuncSet()) {
//            if (function.isExternal()) continue;
//            dgInfo = AnalysisManager.getDG(function);
//            for (Loop loop : function.loopInfo.TopLevelLoops)
//                runLoop(loop);
//        }
//    }
//
//    public static void runLoop(Loop loop) {
//        //先处理子循环
//        for (Loop child : loop.children) {
//            runLoop(child);
//        }
//        //再处理当前循环
//        if (loop.exits.size() != 1) return;
//        ArrayList<BasicBlock> _pre = loop.getExit().getPreBlocks();
//        if (_pre.size() > 1 || _pre.get(0) != loop.header) return;
//        if (detectInvariant(loop)) {
//            liftInvariant(loop);
//        }
//    }
//
//    public static void liftInvariant(Loop loop) {
//        BasicBlock Header = loop.header;
//        Function func = Header.getParentFunction();
//        BasicBlock preHeader = new BasicBlock(func.getBBName(), func);
//        //循环中不变量复制到preHeader
//        for (Instruction instruction : invariants) {
//            instruction.remove();
//            preHeader.addInstLast(instruction);
//            instruction.setParentBlock(preHeader);
//        }
//        //数据流改写
//        //得到header的所有循环外前驱块
//        LinkedList<BasicBlock> preBlocksOutOfLoop = new LinkedList<>(Header.getPreBlocks());
//        preBlocksOutOfLoop.removeAll(loop.nowLevelBB);
//        new Instruction.Jump(preHeader, loop.header);
//        for (BasicBlock pre : preBlocksOutOfLoop) {
//            pre.getLastInst().replaceUseOfWith(loop.header, preHeader);
//        }
//        //Phi指令重写
//        if (preBlocksOutOfLoop.size() == 1) {
//            //如果只有一个循环外前驱块，那么将Header中所有phi指令的该块改成preHeader
//            for (Instruction instr : Header.getInstructions()) {
//                if (instr instanceof Instruction.Phi) {
//                    Instruction.Phi phi = (Instruction.Phi) instr;
//                    phi.changePreBlock(preBlocksOutOfLoop.getFirst(), preHeader);
//                }
//                else break;
//            }
//        }
//        else {
//            //如果有多个循环外前驱块，那么需要在preBlock插入phi指令，并修改header的phi指令
//            for (Instruction instr : Header.getInstructions()) {
//                if (instr instanceof Instruction.Phi) {
//                    Instruction.Phi phi = (Instruction.Phi) instr;
//                    //在preHeader插入phi指令
//                    LinkedHashMap<BasicBlock, Value> optionalValues = new LinkedHashMap<>();
//                    for (BasicBlock pre : preBlocksOutOfLoop) {
//                        optionalValues.put(pre, phi.getOptionalValue(pre));
//                    }
//                    Instruction.Phi newPhi = new Instruction.Phi(preHeader, phi.getType(), optionalValues);
//                    newPhi.remove();
//                    preHeader.addInstFirst(newPhi);
//                    //修改header的phi指令
//                    LinkedList<BasicBlock> preBlocks = phi.getPreBlocks();
//                    for (BasicBlock pre : preBlocks) {
//                        if (preBlocksOutOfLoop.contains(pre)) {
//                            phi.removeOptionalValue(pre);
//                        }
//                    }
//                    phi.addOptionalValue(preHeader, newPhi);
//                }
//                else break;
//            }
//        }
//        func.buildControlFlowGraph();
//        loop.parent.nowLevelBB.add(preHeader);
//    }
//
//    /**
//     * 检测循环中的循环不变量
//     *
//     * @param loop
//     * @return
//     */
//    private static boolean detectInvariant(Loop loop) {
//        ArrayList<BasicBlock> domSort = new ArrayList<>(loop.nowLevelBB);
//        domSort.sort(Comparator.comparingInt(a -> AnalysisManager.getDomDepth(a)));
//        InitVarInLoop(loop);
//        boolean changed = true;
//        while (changed) {
//            //通过迭代找到所有循环不变量
//            changed = false;
//            for (BasicBlock bb : domSort) {
//                for (Instruction instr : bb.getInstructions()) {
//                    if (!varInLoop.contains(instr)) continue;
//                    if (isInvariant(instr, loop, invariants)) {
//                        invariants.add(instr);
//                        changed = true;
//                    }
//                }
//            }
//        }
//        return !invariants.isEmpty();
//    }
//
//    private static void InitVarInLoop(Loop loop) {
//        varInLoop.clear();
//        for (BasicBlock bb : loop.nowLevelBB) {
//            for (Instruction instr : bb.getInstructions()) {
//                switch (instr.getInstType()) {
//                    case STORE -> {
//                        Instruction.Store store = (Instruction.Store) instr;
//                        varInLoop.add(store.getAddr());
//                    }
//                    case ATOMICADD -> {
//                        Instruction.AtomicAdd atomicAdd = (Instruction.AtomicAdd) instr;
//                        varInLoop.add(atomicAdd.getPtr());
//                    }
//                    case CALL -> {
//                        Instruction.Call call = (Instruction.Call) instr;
//                        Function callee = call.getDestFunction();
//                        FuncInfo calleeInfo = AnalysisManager.getFuncInfo(callee);
//                        if (callee.isExternal()) continue;
//                        if (calleeInfo.hasSideEffect || !calleeInfo.isStateless || calleeInfo.hasPutOut || calleeInfo.hasReadIn)
//                            continue;
//                        for (Value arg : call.getOperands()) {
//                            if (arg instanceof Function) continue;
//                            if (arg instanceof Function.Argument) continue;
//                            if (arg instanceof Constant) continue;
//                            if (!loop.defValue(arg)) continue;
//                            varInLoop.add(arg);
//                        }
//                    }
//                }
//                varInLoop.add(instr);
//            }
//        }
//    }
//
//    /**
//     * 判断指令是否是循环不变量
//     * todo 有副作用的函数调用或内存写入 call
//     *
//     * @param instr
//     * @param loop
//     * @param invariants
//     * @return
//     */
//    public static boolean isInvariant(Instruction instr, Loop loop, LinkedList<Instruction> invariants) {
//        if (instr instanceof Instruction.Terminator || instr instanceof Instruction.Phi) {
//            return false;
//        }
//        else if (instr instanceof Instruction.Call) {
//            Function callee = ((Instruction.Call) instr).getDestFunction();
//            FuncInfo calleeInfo = AnalysisManager.getFuncInfo(callee);
//            if (callee.isExternal()) return false;
//            if (calleeInfo.hasSideEffect || !calleeInfo.isStateless || calleeInfo.hasPutOut || calleeInfo.hasReadIn)
//                return false;
//        }
//        else if (instr instanceof Instruction.Store) {
//            return false;
//        }
//        else if (instr instanceof Instruction.Load) {
//            return false;
//        }
//        for (Value use : instr.getOperands()) {
//            //如果use均满足以下：
//            //1.常数
//            //2.use的定义点在循环之外
//            //3.use是循环不变量
//            //4.函数的参数
//            //那么use可以视作是不变量
//            if (use instanceof Function) continue;
//            if (use instanceof Function.Argument) continue;
//            if (use instanceof Constant) continue;
//            if (!loop.defValue(use)) continue;
//            if (invariants.contains(use))
//                continue;
//            return false;
//        }
//        return true;
//    }
//}