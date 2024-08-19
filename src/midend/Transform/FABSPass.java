package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Transform.DCE.SimplifyCFGPass;
import mir.*;
import mir.Module;

import java.util.ArrayList;

public class FABSPass {
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
                // 交换操作数，保证src1是非0的操作数
                if (fcmp.getSrc1().equals(new Constant.ConstantFloat(0))) {
                    fcmp.swap();
                }
                if (!fcmp.getSrc2().equals(new Constant.ConstantFloat(0))) {
                    return;
                }
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
                        br2FAbs(endBlock, thenBlock, fcmp, branch);
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
                    br2FAbs(endBlock, thenBlock, fcmp, branch);
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

    private static void br2FAbs(BasicBlock endBlock, BasicBlock thenBlock, Instruction.Fcmp fcmp, Instruction.Branch br) {
        Value LHS = fcmp.getSrc1();
        Instruction.Fcmp.CondCode condCode = fcmp.getCondCode();
        ArrayList<Instruction.Phi> delPhiList = new ArrayList<>();
        for (Instruction.Phi phi : endBlock.getPhiInstructions()) {
            boolean isABSPhi = true;
            for (Value v : phi.getIncomingValues()) {
                if (!(v.equals(LHS) || (v instanceof Instruction.FSub fSub &&
                        fSub.getOperand_1().equals(new Constant.ConstantFloat(0)) && fSub.getOperand_2().equals(LHS))))
                {
                    isABSPhi = false;
                    break;
                }
            }
            if (!isABSPhi) continue;
//            System.out.println("ABSPhi run!");
            delPhiList.add(phi);
            Instruction.FAbs fAbs = new Instruction.FAbs(endBlock, LHS.getType(), LHS);
            fAbs.remove();
            endBlock.addInstAfterPhi(fAbs);
            Value thenValue = phi.getOptionalValue(thenBlock);
            switch (condCode) {
                case OGE, OGT -> {
                    if (thenValue == LHS) {
                        phi.replaceAllUsesWith(fAbs);
                    }
                    else {
                        Instruction.FSub fSub = new Instruction.FSub(endBlock, LHS.getType(), new Constant.ConstantFloat(0), fAbs);
                        fSub.remove();
                        endBlock.insertInstAfter(fSub, fAbs);
                        phi.replaceAllUsesWith(fSub);
                    }
                }
                case OLE, OLT -> {
                    if (thenValue == LHS) {
                        Instruction.FSub fSub = new Instruction.FSub(endBlock, LHS.getType(), new Constant.ConstantFloat(0), fAbs);
                        fSub.remove();
                        endBlock.insertInstAfter(fSub, fAbs);
                        phi.replaceAllUsesWith(fSub);
                    }
                    else {
                        phi.replaceAllUsesWith(fAbs);
                    }
                }
            }
        }
        delPhiList.forEach(Instruction::delete);
    }

}
