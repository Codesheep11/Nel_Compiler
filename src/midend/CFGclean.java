package midend;

import mir.*;
import mir.Module;

public class CFGclean {
    public static void run(Module module) {
        cfgClean(module);

        cleanEmptyBlocks(module);
        for (Function function : module.getFuncSet()) {
            function.buildControlFlowGraph();
        }
    }

    private static void cleanEmptyBlocks(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) {
                continue;
            }
            if (function.getBlocks().isEmpty()) {
                module.removeFunction(function);
                continue;
            }
            for (BasicBlock block : function.getBlocks()) {
                if (block.getInstructions().isEmpty()) {
                    block.remove();
                }
            }
        }
    }

    private static void cfgClean(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) {
                continue;
            }
            while (cfgCleanFunc(function)) ;
        }
    }

    private static boolean cfgCleanFunc(Function function) {
        boolean ret = false;
//        System.out.println("br2Jump");
        for (BasicBlock block : function.getBlocks()) {
            if (block.getInstructions().isEmpty()) {
                continue;
            }
            Instruction inst = block.getLastInst();
            if (inst instanceof Instruction.Return) {
                continue;
            }
            if (inst instanceof Instruction.Branch) {
                if (brToJump((Instruction.Branch) inst))
                    ret = true;
            }
        }
        function.buildControlFlowGraph();

//        System.out.println("changeTarget");
        boolean flag = true;
        while (flag) {
            flag = false;
            for (BasicBlock block : function.getBlocks()) {
                if (block.getInstructions().isEmpty()) {
                    continue;
                }
                Instruction inst = block.getLastInst();
                if (inst instanceof Instruction.Return) {
                    continue;
                }
                if (changeTarget(inst)) {
                    ret = true;
                    flag = true;
                    break;
                }
            }
            function.buildControlFlowGraph();
        }

//        System.out.println("mergeBlock");
        flag = true;
        while (flag) {
            flag = false;
            for (BasicBlock block : function.getBlocks()) {
                if (block.getInstructions().getSize() == 0 || block.isDeleted) {
                    continue;
                }
                if (mergeBlock(block)) {
                    ret = true;
                    flag = true;
                    break;
                }
            }
            function.buildControlFlowGraph();
        }
        for (BasicBlock block : function.getBlocks()) {
            if (block.getInstructions().isEmpty()) {
                continue;
            }
            Instruction inst = block.getLastInst();
            if (inst instanceof Instruction.Jump) {
                if (replaceJump((Instruction.Jump) inst))
                    ret = true;
            }
        }
        function.buildControlFlowGraph();
//            buildControlFlowGraph(function);
        return ret;
    }

    private static boolean brToJump(Instruction.Branch br) {
//        System.out.println("cond " + br.getCond() + " else " + br.getElseBlock().getLabel() + " then " + br.getThenBlock().getLabel());
        if (br.getElseBlock().equals(br.getThenBlock())) {
            // 输出 else then block label
            BasicBlock block = br.getParentBlock();
            new Instruction.Jump(block, br.getElseBlock());
            br.remove();
            return true;
        }

        if (br.getCond() instanceof Constant.ConstantBool) {
            if (((Constant.ConstantBool) br.getCond()).isZero()) {
                BasicBlock block = br.getParentBlock();
                new Instruction.Jump(block, br.getElseBlock());
                br.remove();
            }
            else {
                BasicBlock block = br.getParentBlock();
                new Instruction.Jump(block, br.getThenBlock());
                br.remove();

            }
            return true;
        }
        return false;
    }

    private static boolean changeTarget(Instruction inst) {
        assert inst instanceof Instruction.Jump || inst instanceof Instruction.Branch;
        if (inst instanceof Instruction.Jump) {
            Instruction nxtFirst = ((Instruction.Jump) inst).getTargetBlock().getFirstInst();
            if (IsEdgeSimplfy(nxtFirst, inst)) {
                boolean rm = true;
                do {
                    if (nxtFirst.getParentBlock().getPreBlocks().size() != 1) {
                        rm = false;
                    }
                    Instruction next = ((Instruction.Jump) nxtFirst).getTargetBlock().getFirstInst();
                    if (IsEdgeSimplfy(next, inst)) {
                        nxtFirst = next;
                    }
                    else {
                        break;
                    }
                } while (true);
                BasicBlock newTarget = ((Instruction.Jump) nxtFirst).getTargetBlock();
                inst.replaceUseOfWith(((Instruction.Jump) inst).getTargetBlock(), newTarget);
                //维护phi指令
                if (rm)
                    for (Instruction instr : newTarget.getInstructions()) {
                        if (instr instanceof Instruction.Phi) {
                            Instruction.Phi phi = (Instruction.Phi) instr;
                            phi.changePreBlock(nxtFirst.getParentBlock(), inst.getParentBlock());
                        }
                        else break;
                    }
                else {
                    for (Instruction instr : newTarget.getInstructions()) {
                        if (instr instanceof Instruction.Phi) {
                            Instruction.Phi phi = (Instruction.Phi) instr;
                            phi.addOptionalValue(inst.getParentBlock(), phi.getOptionalValue(nxtFirst.getParentBlock()));
                        }
                        else break;
                    }
                }
                return true;
            }
            else if (nxtFirst instanceof Instruction.Return) {
                Instruction.Return that = (Instruction.Return) ((Instruction.Jump) inst).getTargetBlock().getFirstInst();
                that.cloneToBB(inst.getParentBlock());
                inst.remove();
                return true;
            }
            return false;
        }
        else {
            boolean ret = false;
            Instruction thenFirst = ((Instruction.Branch) inst).getThenBlock().getFirstInst();
            Instruction elseFirst = ((Instruction.Branch) inst).getElseBlock().getFirstInst();
            if (IsEdgeSimplfy(thenFirst, inst)) {
                boolean rm = true;
                do {
                    if (thenFirst.getParentBlock().getPreBlocks().size() != 1) {
                        rm = false;
                    }
                    Instruction next = ((Instruction.Jump) thenFirst).getTargetBlock().getFirstInst();
                    if (IsEdgeSimplfy(next, inst)) {
                        thenFirst = next;
                    }
                    else {
                        break;
                    }
                } while (true);
                BasicBlock newTarget = ((Instruction.Jump) thenFirst).getTargetBlock();
                inst.replaceUseOfWith(((Instruction.Branch) inst).getThenBlock(), newTarget);
                //维护phi指令
                if (rm) {
                    for (Instruction instr : newTarget.getInstructions()) {
                        if (instr instanceof Instruction.Phi) {
                            Instruction.Phi phi = (Instruction.Phi) instr;
                            if (phi.getPreBlocks().contains(inst.getParentBlock())) break;
                            phi.changePreBlock(thenFirst.getParentBlock(), inst.getParentBlock());
                        }
                        else break;
                    }
                }
                else {
                    for (Instruction instr : newTarget.getInstructions()) {
                        if (instr instanceof Instruction.Phi) {
                            Instruction.Phi phi = (Instruction.Phi) instr;
                            if (phi.getPreBlocks().contains(inst.getParentBlock())) break;
                            phi.addOptionalValue(inst.getParentBlock(), phi.getOptionalValue(thenFirst.getParentBlock()));
                        }
                        else break;
                    }
                }
                ret = true;
            }
            if (IsEdgeSimplfy(elseFirst, inst)) {
                boolean rm = true;
                do {
                    if (elseFirst.getParentBlock().getPreBlocks().size() != 1) {
                        rm = false;
                    }
                    Instruction next = ((Instruction.Jump) elseFirst).getTargetBlock().getFirstInst();
                    if (IsEdgeSimplfy(next, inst)) {
                        elseFirst = next;
                    }
                    else {
                        break;
                    }
                } while (true);
                BasicBlock newTarget = ((Instruction.Jump) elseFirst).getTargetBlock();
                inst.replaceUseOfWith(((Instruction.Branch) inst).getElseBlock(), newTarget);
                //维护phi指令
                if (rm) {
                    for (Instruction instr : newTarget.getInstructions()) {
                        if (instr instanceof Instruction.Phi) {
                            Instruction.Phi phi = (Instruction.Phi) instr;
                            if (phi.getPreBlocks().contains(inst.getParentBlock())) break;
                            phi.changePreBlock(elseFirst.getParentBlock(), inst.getParentBlock());
                        }
                        else break;
                    }
                }
                else {
                    for (Instruction instr : newTarget.getInstructions()) {
                        if (instr instanceof Instruction.Phi) {
                            Instruction.Phi phi = (Instruction.Phi) instr;
                            if (phi.getPreBlocks().contains(inst.getParentBlock())) break;
                            phi.addOptionalValue(inst.getParentBlock(), phi.getOptionalValue(elseFirst.getParentBlock()));
                        }
                        else break;
                    }
                }
                ret = true;
            }
            return ret;
        }
    }

    private static boolean IsEdgeSimplfy(Instruction jump, Instruction terminate) {
        if (!(jump instanceof Instruction.Jump)) return false;
        BasicBlock target = ((Instruction.Jump) jump).getTargetBlock();
        //如果terminate已有边连接jump，可能存在phi函数冲突
        Instruction targetFirst = target.getFirstInst();
        if (targetFirst instanceof Instruction.Phi && ((Instruction.Phi) targetFirst).getPreBlocks().contains(terminate.getParentBlock())) {
            for (Instruction instr : target.getInstructions()) {
                if (instr instanceof Instruction.Phi) {
                    Instruction.Phi phi = (Instruction.Phi) instr;
                    if (phi.getPreBlocks().contains(terminate.getParentBlock())) {
                        if (phi.getOptionalValue(terminate.getParentBlock()) != phi.getOptionalValue(jump.getParentBlock())) {
                            return false;
                        }
                    }
                }
                else break;
            }
        }
        return true;
    }

    /**
     * 尝试对block的后继块合并
     *
     * @param block
     */
    private static boolean mergeBlock(BasicBlock block) {
        boolean ret = false;
        BasicBlock curBlock = block;
        Instruction inst = curBlock.getLastInst();
        if (inst instanceof Instruction.Jump) {
            BasicBlock that = ((Instruction.Jump) inst).getTargetBlock();
            if (that.getPreBlocks().size() == 1 && !(that.getInstructions().getFirst() instanceof Instruction.Phi)) {
//                System.out.println("Merge Block: " + curBlock.getLabel() + " " + that.getLabel());
                inst.remove();
                for (Instruction instruction : that.getInstructions()) {
//                    if (instruction instanceof Instruction.Phi)
//                        throw new RuntimeException("Phi should not in the block to be merged");
                    instruction.setParentBlock(curBlock);
                }
                curBlock.getInstructions().concat(that.getInstructions());
                that.isDeleted = true;
//                    that.remove();
                that.getInstructions().setEmpty();
                //重写phi指令
                for (BasicBlock suc : that.getSucBlocks()) {
                    for (Instruction instr : suc.getInstructions()) {
                        if (instr instanceof Instruction.Phi) {
                            Instruction.Phi phi = (Instruction.Phi) instr;
                            phi.changePreBlock(that, curBlock);
                        }
                        else break;
                    }
                }
                ret = true;
            }
        }
        return ret;
    }


    private static boolean replaceJump(Instruction.Jump jump) {
        BasicBlock that = jump.getTargetBlock();
        if (that.getInstructions().isEmpty()) {
            throw new RuntimeException("Jump to empty block");
        }
        if (that.getFirstInst() instanceof Instruction.Branch) {
            Instruction.Branch br = (Instruction.Branch) that.getFirstInst();
            br.cloneToBB(jump.getParentBlock());
            jump.remove();
            //维护phi指令
            for (Instruction instr : br.getThenBlock().getInstructions()) {
                if (instr instanceof Instruction.Phi) {
                    Instruction.Phi phi = (Instruction.Phi) instr;
                    if (phi.getPreBlocks().contains(jump.getParentBlock())) break;
                    phi.changePreBlock(that, jump.getParentBlock());
                }
                else break;
            }
            for (Instruction instr : br.getElseBlock().getInstructions()) {
                if (instr instanceof Instruction.Phi) {
                    Instruction.Phi phi = (Instruction.Phi) instr;
                    if (phi.getPreBlocks().contains(jump.getParentBlock())) break;
                    phi.changePreBlock(that, jump.getParentBlock());
                }
                else break;
            }
            return true;
        }
        return false;
    }


}
