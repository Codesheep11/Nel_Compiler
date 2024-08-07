package midend.Transform.Loop;

import mir.*;
import mir.Module;
import mir.result.SCEVinfo;

import java.util.ArrayList;

public class LoopParallel {

    private static Instruction.Icmp indvar_cmp;

    private static int init;
    private static int step;
    private static Instruction indvar;

    private static SCEVinfo scevInfo;


    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        for (Loop loop : function.loopInfo.TopLevelLoops) {
            tryParallelLoop(loop);
        }
    }

    private static void tryParallelLoop(Loop loop) {
        if (!canTransform(loop)) return;
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
        indvar = (Instruction) icmp.getSrc1();
        init = scevInfo.query(icmp.getSrc1()).getInit();
        step = scevInfo.query(icmp.getSrc1()).getStep();
        if (step != 1) return false;
        return true;
    }
}
