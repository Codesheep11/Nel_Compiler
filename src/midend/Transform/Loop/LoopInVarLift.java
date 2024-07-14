package midend.Transform.Loop;

import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import static midend.Transform.Loop.LCSSA.isDomable;

public class LoopInVarLift {
    public static LinkedList<Instruction> invariants = new LinkedList<>();

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            for (Loop loop : function.loopInfo.TopLevelLoops)
                runLoop(loop);
        }
    }

    public static void runLoop(Loop loop) {
        //先处理子循环
        for (Loop child : loop.children) {
            runLoop(child);
        }
        //再处理当前循环
        if (loop.isRoot) return;
        invariants.clear();
        if (detectInvariant(loop)) {
            liftInvariant(loop);
        }
    }

    public static void liftInvariant(Loop loop) {
        BasicBlock Header = loop.header;
        Function func = Header.getParentFunction();
        BasicBlock preHeader = new BasicBlock(func.getBBName(), func);
        //循环中不变量复制到preHeader
        for (Instruction instruction : invariants) {
            instruction.remove();
            preHeader.addInstLast(instruction);
            instruction.setParentBlock(preHeader);
        }
        //数据流改写
        //得到header的所有循环外前驱块
        LinkedList<BasicBlock> preBlocksOutOfLoop = new LinkedList<>(Header.getPreBlocks());
        preBlocksOutOfLoop.removeAll(loop.nowLevelBB);
        new Instruction.Jump(preHeader, loop.header);
        for (BasicBlock pre : preBlocksOutOfLoop) {
            pre.getLastInst().replaceUseOfWith(loop.header, preHeader);
        }
        //Phi指令重写
        if (preBlocksOutOfLoop.size() == 1) {
            //如果只有一个循环外前驱块，那么将Header中所有phi指令的该块改成preHeader
            for (Instruction instr : Header.getInstructions()) {
                if (instr instanceof Instruction.Phi) {
                    Instruction.Phi phi = (Instruction.Phi) instr;
                    phi.changePreBlock(preBlocksOutOfLoop.getFirst(), preHeader);
                }
                else break;
            }
        }
        else {
            //如果有多个循环外前驱块，那么需要在preBlock插入phi指令，并修改header的phi指令
            for (Instruction instr : Header.getInstructions()) {
                if (instr instanceof Instruction.Phi) {
                    Instruction.Phi phi = (Instruction.Phi) instr;
                    //在preHeader插入phi指令
                    LinkedHashMap<BasicBlock, Value> optionalValues = new LinkedHashMap<>();
                    for (BasicBlock pre : preBlocksOutOfLoop) {
                        optionalValues.put(pre, phi.getOptionalValue(pre));
                    }
                    Instruction.Phi newPhi = new Instruction.Phi(preHeader, phi.getType(), optionalValues);
                    newPhi.remove();
                    preHeader.addInstFirst(newPhi);
                    //修改header的phi指令
                    LinkedList<BasicBlock> preBlocks = phi.getPreBlocks();
                    for (BasicBlock pre : preBlocks) {
                        if (preBlocksOutOfLoop.contains(pre)) {
                            phi.removeOptionalValue(pre);
                        }
                    }
                    phi.addOptionalValue(preHeader, newPhi);
                }
                else break;
            }
        }
        func.buildControlFlowGraph();
        loop.parent.nowLevelBB.add(preHeader);
    }

    /**
     * 检测循环中的循环不变量
     *
     * @param loop
     * @return
     */
    public static boolean detectInvariant(Loop loop) {
        LinkedList<BasicBlock> domBlocks = getDomSort(loop.nowLevelBB);

        for (BasicBlock block : loop.nowLevelBB) {
            if (block.getDomSet().containsAll(loop.exits)) {
                domBlocks.add(block);
            }
        }
        boolean changed = true;
        while (changed) {
            //通过迭代找到所有循环不变量
            changed = false;
            for (BasicBlock bb : domBlocks) {
                for (Instruction instr : bb.getInstructions()) {
                    if (invariants.contains(instr)) continue;
                    if (isInvariant(instr, loop, invariants)) {
                        invariants.add(instr);
                        changed = true;
                    }
                }
            }
        }
        return !invariants.isEmpty();
    }

    public static LinkedList<BasicBlock> getDomSort(HashSet<BasicBlock> blocks) {
        LinkedList<BasicBlock> domBlocks = new LinkedList<>();
        LinkedList<BasicBlock> domSort = new LinkedList<>();
        domBlocks.addAll(blocks);
        //对于domSort中的Blocks，按照支配关系排序
        while (!domBlocks.isEmpty()) {
            BasicBlock domBlock = domBlocks.getFirst();
            domBlocks.remove(domBlock);
            boolean flag = false;
            for (BasicBlock block : domSort) {
                if (isDomable(block, domBlock, new HashSet<>())) {
                    domSort.add(domSort.indexOf(block), domBlock);
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                domSort.add(domBlock);
            }
        }
        return domSort;
    }

    /**
     * 判断指令是否是循环不变量
     * todo 有副作用的函数调用或内存写入 call
     *
     * @param instr
     * @param loop
     * @param invariants
     * @return
     */
    public static boolean isInvariant(Instruction instr, Loop loop, LinkedList<Instruction> invariants) {
        if (instr instanceof Instruction.Terminator || instr instanceof Instruction.Phi) {
            return false;
        }
        else if (instr instanceof Instruction.Call) {
            Function callee = ((Instruction.Call) instr).getDestFunction();
            if (callee.isExternal()) return false;
            if (FuncInfo.hasSideEffect.get(callee) || FuncInfo.isStateless.get(callee)
                    || FuncInfo.hasReadIn.get(callee) || FuncInfo.hasPutOut.get(callee)) return false;
        }
        else if (instr instanceof Instruction.Store) {
            return false;
        }
        else if (instr instanceof Instruction.Load) {
            return false;
        }
        for (Value use : instr.getOperands()) {
            //如果use均满足以下：
            //1.常数
            //2.use的定义点在循环之外
            //3.use是循环不变量
            //4.函数的参数
            //那么use可以视作是不变量
            if (use instanceof Function) continue;
            if (use instanceof Function.Argument) continue;
            if (use instanceof Constant) continue;
            if (!loop.defValue(use)) continue;
            if (invariants.contains(use))
                continue;
            return false;
        }
        return true;
    }
}