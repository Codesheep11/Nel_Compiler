package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Transform.DCE.SimplifyCFGPass;
import mir.*;
import mir.Module;

import java.util.ArrayList;

public class Branch2FMinMax {
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
            if (cond instanceof Instruction.Fcmp fcmp) {
                Instruction.Fcmp.CondCode condCode = fcmp.getCondCode();
                if (condCode == Instruction.Fcmp.CondCode.EQ || condCode == Instruction.Fcmp.CondCode.NE) return;
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
                        br2MinMax(endBlock, thenBlock, fcmp, branch);
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
                    br2MinMax(endBlock, thenBlock, fcmp, branch);
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
        if (cmp instanceof Instruction.Fcmp fcmp) {
            Value LHS = fcmp.getSrc1();
            Value RHS = fcmp.getSrc2();
            Instruction.Fcmp.CondCode condCode = fcmp.getCondCode();
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
//                System.out.println("MinMaxPhi run!");
                delPhiList.add(phi);
                Value thenValue = phi.getOptionalValue(thenBlock);
                Instruction.BinaryOperation inst = null;
                switch (condCode) {
                    case OGE, OGT -> {
                        if (thenValue == LHS) {
                            inst = new Instruction.FMax(endBlock, LHS.getType(), LHS, RHS);
                        }
                        else {
                            inst = new Instruction.FMin(endBlock, LHS.getType(), LHS, RHS);
                        }
                    }
                    case OLE, OLT -> {
                        if (thenValue == LHS) {
                            inst = new Instruction.FMin(endBlock, LHS.getType(), LHS, RHS);
                        }
                        else {
                            inst = new Instruction.FMax(endBlock, LHS.getType(), LHS, RHS);
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
