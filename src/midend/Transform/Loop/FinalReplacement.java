package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import mir.*;
import mir.Module;
import midend.Analysis.result.SCEVinfo;

import java.util.ArrayList;

public class FinalReplacement {

    private static SCEVinfo scevInfo;
    private static int init;
    @SuppressWarnings({"FieldCanBeLocal"})
    private static int step;
    private static Instruction.Icmp indvar_cmp;

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        IndVars.runOnFunc(function);
        scevInfo = AnalysisManager.getSCEV(function);
        for (Loop loop : function.loopInfo.TopLevelLoops) {
            runLoop(loop);
        }
    }

    private static boolean canTransform(Loop loop) {
        if (!loop.children.isEmpty()) return false;
        if (loop.getAllBlocks().size() > 2) return false;
        if (loop.tripCount > 0) return false;
        if (loop.exits.size() != 1) return false;
        // 退出条件复杂
        ArrayList<BasicBlock> _pre = loop.getExit().getPreBlocks();
        if (_pre.size() > 1 || _pre.get(0) != loop.header) return false;
        Instruction inst = loop.header.getTerminator();
        if (!(inst instanceof Instruction.Branch br)) return false;
        Value cond = br.getCond();
        if (!(cond instanceof Instruction.Icmp icmp)) return false;
        if (!(icmp.getSrc2() instanceof Constant.ConstantInt) && scevInfo.contains(icmp.getSrc2()))
            icmp.swap();
        switch (icmp.getCondCode()) {
            case EQ, NE, SGE, SGT -> {
                return false;
            }
            default -> {
            }
        }
        if (scevInfo.contains(icmp.getSrc2()))
            return false;
        if (!scevInfo.contains(icmp.getSrc1()))
            return false;
        if (!scevInfo.query(icmp.getSrc1()).isNotNegative())
            return false;
        if (!(icmp.getSrc1() instanceof Instruction))
            return false;
        indvar_cmp = icmp;
        init = scevInfo.query(icmp.getSrc1()).getInit();
        step = scevInfo.query(icmp.getSrc1()).getStep();
        if (step != 1) return false;
        return true;
    }

    private static void runLoop(Loop loop) {
        for (Loop child : loop.children) {
            runLoop(child);
        }
        if (!canTransform(loop)) return;
        BasicBlock exit = loop.getExit();
        // calc
        // slt: tripCount = (limit_i - initial_i) sle: tripCount = (limit_i - initial_i + 1)
        int _tmp = init;
        if (indvar_cmp.getCondCode() == Instruction.Icmp.CondCode.SLE)
            _tmp--;
        Instruction.Sub tripCount = new Instruction.Sub(exit, indvar_cmp.getSrc2().getType(), indvar_cmp.getSrc2(), Constant.ConstantInt.get(_tmp));
        tripCount.remove();
        exit.addInstAfterPhi(tripCount);
        Instruction.Max max = new Instruction.Max(exit, tripCount.getType(), tripCount, Constant.ConstantInt.get(0));
        max.remove();
        tripCount.addNext(max);
        for (var inst : exit.getPhiInstructions()) {
            if (inst.isLCSSA && inst.getIncomingValueSize() == 1) {
                Value val = inst.getIncomingValues().get(0);
                if (scevInfo.contains(val)) {
                    int initVal = scevInfo.query(val).getInit();
                    int stepVal = scevInfo.query(val).getStep();
                    Instruction.Mul mul = new Instruction.Mul(exit, tripCount.getType(), max, Constant.ConstantInt.get(stepVal));
                    mul.remove();
                    max.addNext(mul);
                    Instruction.Add add = new Instruction.Add(exit, mul.getType(), Constant.ConstantInt.get(initVal), mul);
                    add.remove();
                    mul.addNext(add);
                    inst.replaceAllUsesWith(add);
                }
            }
        }
    }
}
