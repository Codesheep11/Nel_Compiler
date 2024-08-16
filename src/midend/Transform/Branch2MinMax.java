package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Transform.DCE.SimplifyCFGPass;
import mir.Module;
import mir.*;

import java.util.ArrayList;

public class Branch2MinMax {

    private static final ArrayList<BasicBlock> visited = new ArrayList<>();

    public static void run(Module module) {
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            SimplifyCFGPass.runOnFunc(func);
            runOnFunction(func);
            AnalysisManager.refreshCFG(func);
        }
    }

    private static void runOnFunction(Function func) {
        visited.clear();
        for (BasicBlock block : func.getBlocks()) {
            if (visited.contains(block)) continue;
            runOnBlock(block);
        }
    }

    private static void runOnBlock(BasicBlock block) {
//        System.out.println("runOnBlock "+block.getLabel());
        Instruction.Terminator term = block.getTerminator();
        if (term instanceof Instruction.Branch branch) {
            Value cond = branch.getCond();
            if (cond instanceof Instruction.Icmp icmp) {
                Instruction.Icmp.CondCode condCode = icmp.getCondCode();
                if (condCode == Instruction.Icmp.CondCode.EQ || condCode == Instruction.Icmp.CondCode.NE) return;
                BasicBlock thenBlock = branch.getThenBlock();
                BasicBlock elseBlock = branch.getElseBlock();
                if (thenBlock.getPreBlocks().size() == 1 && elseBlock.getPreBlocks().size() == 1) {
                    if (thenBlock.getTerminator() instanceof Instruction.Jump thenJump &&
                            elseBlock.getTerminator() instanceof Instruction.Jump elseJump)
                    {
                        if (thenJump.getTargetBlock() != elseJump.getTargetBlock()) return;
                        visited.add(thenBlock);
                        visited.add(elseBlock);
                        //开始转换
                        BasicBlock endBlock = thenJump.getTargetBlock();
                        if (endBlock.getPreBlocks().size() > 2) return;
                        br2MinMax(endBlock, thenBlock, icmp, branch);
                        if (endBlock.getPhiInstructions().isEmpty()) {
                            if (thenBlock.getInstructions().size() == 1 && elseBlock.getInstructions().size() == 1) {
                                block.getLastInst().delete();
                                new Instruction.Jump(block, endBlock);
                            }
                        }
                    }
                }
                else if (thenBlock.getPreBlocks().size() == 2 || elseBlock.getPreBlocks().size() == 2) {
                    BasicBlock endBlock = thenBlock.getPreBlocks().size() == 2 ? thenBlock : elseBlock;
                    BasicBlock passBlock = thenBlock.getPreBlocks().size() == 2 ? elseBlock : thenBlock;
                    if (!passBlock.getSucBlocks().contains(endBlock)) return;
                    if (!(passBlock.getTerminator() instanceof Instruction.Jump)) return;
                    visited.add(passBlock);
                    thenBlock = thenBlock.equals(endBlock) ? block : thenBlock;
                    br2MinMax(endBlock, thenBlock, icmp, branch);
                    if (endBlock.getPhiInstructions().isEmpty()) {
                        if (passBlock.getInstructions().size() == 1) {
                            block.getLastInst().delete();
                            new Instruction.Jump(block, endBlock);
                        }
                    }
                }
            }
        }
    }

    private static void br2MinMax(BasicBlock endBlock, BasicBlock thenBlock, Instruction cmp, Instruction.Branch br) {
        if (cmp instanceof Instruction.Icmp icmp) {
            Value LHS = icmp.getSrc1();
            Value RHS = icmp.getSrc2();
            Instruction.Icmp.CondCode condCode = icmp.getCondCode();
            ArrayList<Instruction.Phi> delPhiList = new ArrayList<>();
            for (Instruction.Phi phi : endBlock.getPhiInstructions()) {
                boolean isMinMaxPhi = true;
                for (Value v : phi.getIncomingValues()) {
                    if (!v.equals(LHS) && !v.equals(RHS)) {
                        isMinMaxPhi = false;
                        break;
                    }
                }
                if (!isMinMaxPhi) continue;
                delPhiList.add(phi);
                Value thenValue = phi.getOptionalValue(thenBlock);
                Instruction.BinaryOperation inst = null;
                switch (condCode) {
                    case SGE, SGT -> {
                        if (thenValue == LHS) {
                            inst = new Instruction.Max(endBlock, LHS.getType(), LHS, RHS);
                        }
                        else {
                            inst = new Instruction.Min(endBlock, LHS.getType(), LHS, RHS);
                        }
                    }
                    case SLE, SLT -> {
                        if (thenValue == LHS) {
                            inst = new Instruction.Min(endBlock, LHS.getType(), LHS, RHS);
                        }
                        else {
                            inst = new Instruction.Max(endBlock, LHS.getType(), LHS, RHS);
                        }
                    }
                    default -> throw new RuntimeException("Unexpected condCode: " + condCode);
                }
                inst.remove();
                phi.replaceAllUsesWith(inst);
                endBlock.addInstAfterPhi(inst);
            }
            delPhiList.forEach(Instruction::delete);
        }
    }
}
