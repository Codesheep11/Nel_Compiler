package midend.Transform.Loop;

import mir.Module;
import mir.*;

public class BrPredction {
    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function func) {
        for (Loop loop : func.loopInfo.TopLevelLoops) {
            brPredction(loop);
        }
    }

    private static void brPredction(Loop loop) {
        for (Loop child : loop.children) {
            brPredction(child);
        }
        double pro = 0.0;
        for (BasicBlock exiting : loop.exitings) {
            Instruction.Terminator terminator = exiting.getTerminator();
            if (!(terminator instanceof Instruction.Branch branch)) continue;
            if (loop.exits.contains(branch.getThenBlock())) branch.setProbability(pro);
            else branch.setProbability(1 - pro);
//            System.out.println(exiting.getLabel() + " " + branch + " " + branch.getProbability());
        }
        for (BasicBlock entering : loop.enterings) {
            Instruction.Terminator terminator = entering.getTerminator();
            if (!(terminator instanceof Instruction.Branch branch)) continue;
            if (branch.getThenBlock().equals(loop.header)) branch.setProbability(1 - pro);
            else branch.setProbability(pro);
//            System.out.println(entering.getLabel() + " " + branch + " " + branch.getProbability());
        }
    }
}
