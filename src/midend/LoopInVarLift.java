package midend;

import mir.*;
import mir.Module;

import java.util.LinkedHashMap;
import java.util.LinkedList;

public class LoopInVarLift {

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.getBlocks().getSize() == 0) {
                continue;
            }
            for (Loop loop : function.loops) {
                runLoop(loop);
            }
        }
    }

    public static void runLoop(Loop loop) {
        //先处理子循环
        for (Loop child : loop.children) {
            runLoop(child);
        }
        //再处理当前循环
        LinkedList<BasicBlock> domBlocks = new LinkedList<>();
        if (!loop.isNatural) {
            for (BasicBlock block : loop.blocks) {
                if (block.getDomSet().containsAll(loop.exits)) {
                    domBlocks.add(block);
                }
            }
        }
        else domBlocks = new LinkedList<>(loop.blocks);
        boolean changed = true;
        LinkedList<Instruction> invariants = new LinkedList<>();
        while (changed) {
            //通过迭代找到所有循环不变量
            changed = false;
            for (BasicBlock bb : domBlocks) {
                for (Instruction instr : bb.getInstructions()) {
                    if (invariants.contains(instr)) continue;
                    if (instr instanceof Instruction.Terminator || instr instanceof Instruction.Call || instr instanceof Instruction.Phi)
                        continue;
                    if (loop.isInvariant(instr, invariants)) {
                        invariants.add(instr);
                        changed = true;
                    }
                }
            }
        }
        if (!invariants.isEmpty()) {
//            System.out.println("Invariant detected");
            BasicBlock Header = loop.header;
            Function func = Header.getParentFunction();
            BasicBlock preHeader = new BasicBlock(func.getBBName(), func);
            //循环中不变量复制到preHeader
            for (Instruction instruction : invariants) {
                instruction.remove();
//                    Instruction newInstr = new Instruction(instruction);
                preHeader.addInstLast(instruction);
                instruction.setParentBlock(preHeader);
            }
            //数据流改写
            //得到header的所有循环外前驱块
            LinkedList<BasicBlock> preBlocksOutOfLoop = new LinkedList<>(Header.getPreBlocks());
            preBlocksOutOfLoop.removeAll(loop.blocks);
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
            loop.preHeader = preHeader;
            if (loop.parent != null) {
                loop.parent.blocks.add(preHeader);
            }
        }
    }
}