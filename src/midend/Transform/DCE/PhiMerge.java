package midend.Transform.DCE;

import mir.Module;
import mir.*;

import java.util.ArrayList;

public class PhiMerge {
    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        for (BasicBlock block : function.getBlocks()) {
            runOnBlock(block);
        }
    }

    private static boolean runOnBlock(BasicBlock block) {
        ArrayList<Instruction.Phi> phiInstructions = block.getPhiInstructions();
        if (phiInstructions.isEmpty()) return false;
        if (block.getTerminator() instanceof Instruction.Jump jump) {
            if (!(jump.getPrev() instanceof Instruction.Phi)) return false;
            BasicBlock target = jump.getTargetBlock();
            ArrayList<Instruction.Phi> targetPhiInstructions = target.getPhiInstructions();
            if (targetPhiInstructions.isEmpty()) return false;
            //没有相同的前驱
            ArrayList<BasicBlock> preBlocks = new ArrayList<>(phiInstructions.get(0).getPreBlocks());
            ArrayList<BasicBlock> targetPreBlocks = new ArrayList<>(targetPhiInstructions.get(0).getPreBlocks());
            preBlocks.retainAll(targetPreBlocks);
            if (!preBlocks.isEmpty()) return false;
            //所有phi指令的用户都在target的Phi中
            for (Instruction.Phi phi : phiInstructions) {
                ArrayList<Instruction> users = phi.getUsers();
                ArrayList<Instruction.Phi> targetUsers = new ArrayList<>();
                for (Instruction user : users) {
                    if (user instanceof Instruction.Phi && user.getParentBlock().equals(target)) {
                        targetUsers.add((Instruction.Phi) user);
                    }
                }
                if (users.size() != targetUsers.size()) return false;
            }
            // merge
            ArrayList<BasicBlock> curPreBBs = new ArrayList<>(phiInstructions.get(0).getPreBlocks());
            ArrayList<Instruction.Phi> fixPhis = new ArrayList<>();
            for (Instruction.Phi phi : phiInstructions) {
                ArrayList<Instruction> users = phi.getUsers();
                for (Instruction inst : users) {
                    Instruction.Phi targetPhi = (Instruction.Phi) inst;
                    targetPhi.removeOptionalValue(block);
                    for (BasicBlock pre : phi.getPreBlocks()) {
                        pre.getTerminator().replaceTarget(block, target);
                        targetPhi.addOptionalValue(pre, phi.getOptionalValue(pre));
                    }
                    fixPhis.add(targetPhi);
                }
                phi.delete();
            }
            // fix
            for (Instruction.Phi targetPhi : targetPhiInstructions) {
                if (fixPhis.contains(targetPhi)) continue;
                for (BasicBlock pre : curPreBBs) {
                    targetPhi.addOptionalValue(pre, targetPhi.getOptionalValue(block));
                }
                targetPhi.removeOptionalValue(block);
            }
        }
        return true;
    }
}
