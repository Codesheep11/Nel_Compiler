package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Transform.DCE.RemoveBlocks;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashSet;

public class Cond2MinMax {

    private static final ArrayList<BasicBlock> queue = new ArrayList<>();

    private static final HashSet<BasicBlock> visited = new HashSet<>();

    public static void run(Module module) {
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            runOnFunction(func);
        }
    }

    public static void runOnFunction(Function func) {
        visited.clear();
        queue.clear();
        queue.addAll(func.getDomTreePostOrder());
        while (!queue.isEmpty()) {
            BasicBlock block = queue.remove(0);
            if (visited.contains(block)) continue;
            visited.add(block);
            runOnBlock(block);
        }
        RemoveBlocks.runOnFunc(func);
        AnalysisManager.refreshCFG(func);
        AnalysisManager.refreshDG(func);
    }

    private static void runOnBlock(BasicBlock block) {
        Instruction.Terminator term = block.getTerminator();
        if (!(term instanceof Instruction.Branch branch)) return;
        Value cond = branch.getCond();
        if (!(cond instanceof Instruction.Icmp icmp)) return;
        Instruction.Icmp.CondCode condCode = icmp.getCondCode();
        if (condCode == Instruction.Icmp.CondCode.EQ || condCode == Instruction.Icmp.CondCode.NE) return;
        BasicBlock thenBlock = branch.getThenBlock();
        BasicBlock elseBlock = branch.getElseBlock();
        Value a = icmp.getSrc1();
        Value b = icmp.getSrc2();
        if (thenBlock.getTerminator() instanceof Instruction.Branch thenBranch && thenBranch.getCond() instanceof Instruction.Icmp thenIcmp) {
            if (!isOnlyJumpBlock(thenBlock)) return;
            if (thenBranch.getThenBlock().equals(elseBlock)) {
                thenIcmp.reverse();
                thenBranch.swap();
            }
            Value c = thenIcmp.getSrc1();
            Value d = thenIcmp.getSrc2();
            BasicBlock thenBlockTrue = thenBranch.getThenBlock();
            BasicBlock thenBlockFalse = thenBranch.getElseBlock();
            if (thenBlockFalse.equals(elseBlock)) {
                if (a.equals(d)) thenIcmp.swap();
                if (b.equals(c)) icmp.swap();
                condCode = icmp.getCondCode();
                if (icmp.getSrc1().equals(thenIcmp.getSrc1())) {
                    if (icmp.getCondCode().equals(thenIcmp.getCondCode())) {
//                        System.out.println("Cond2MinMax");
                        Instruction condInst = condCode.equals(Instruction.Icmp.CondCode.SLE) || condCode.equals(Instruction.Icmp.CondCode.SLT) ?
                                new Instruction.Min(block, a.getType(), icmp.getSrc2(), thenIcmp.getSrc2()) :
                                new Instruction.Max(block, a.getType(), icmp.getSrc2(), thenIcmp.getSrc2());
                        condInst.remove();
                        icmp.replaceUseOfWith(icmp.getSrc2(), condInst);
                        block.insertInstBefore(condInst, icmp);
                        branch.delete();
                        new Instruction.Branch(block, icmp, thenBlockTrue, thenBlockFalse);
                        queue.add(block);
                        visited.remove(block);
                        return;
                    }
                    //todo:拓展情况 如SLT和SLE其实也可以合并
                }
            }
        }
        else if (elseBlock.getTerminator() instanceof Instruction.Branch elseBranch && elseBranch.getCond() instanceof Instruction.Icmp elseIcmp) {
            if (!isOnlyJumpBlock(elseBlock)) return;
            if (elseBranch.getElseBlock().equals(thenBlock)) {
                elseIcmp.reverse();
                elseBranch.swap();
            }
            Value c = elseIcmp.getSrc1();
            Value d = elseIcmp.getSrc2();
            BasicBlock elseBlockTrue = elseBranch.getThenBlock();
            BasicBlock elseBlockFalse = elseBranch.getElseBlock();
            if (elseBlockTrue.equals(thenBlock)) {
                if (a.equals(d)) elseIcmp.swap();
                if (b.equals(c)) icmp.swap();
                condCode = icmp.getCondCode();
                if (icmp.getSrc1().equals(elseIcmp.getSrc1())) {
                    if (icmp.getCondCode().equals(elseIcmp.getCondCode())) {
//                        System.out.println("Cond2MinMax");
                        Instruction condInst = condCode.equals(Instruction.Icmp.CondCode.SLE) || condCode.equals(Instruction.Icmp.CondCode.SLT) ?
                                new Instruction.Max(block, a.getType(), icmp.getSrc2(), elseIcmp.getSrc2()) :
                                new Instruction.Min(block, a.getType(), icmp.getSrc2(), elseIcmp.getSrc2());
                        condInst.remove();
                        icmp.replaceUseOfWith(icmp.getSrc2(), condInst);
                        block.insertInstBefore(condInst, icmp);
                        branch.delete();
                        new Instruction.Branch(block, icmp, elseBlockTrue, elseBlockFalse);
                        queue.add(block);
                        visited.remove(block);
                        return;
                    }
                }
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isOnlyJumpBlock(BasicBlock block) {
        for (Instruction inst : block.getInstructions()) {
            if (!(inst instanceof Instruction.Terminator) &&
                    !(inst instanceof Instruction.Icmp)) return false;
        }
        return true;
    }
}
