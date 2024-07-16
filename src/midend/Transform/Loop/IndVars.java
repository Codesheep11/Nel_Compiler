package midend.Transform.Loop;

import mir.*;
import mir.Module;
import mir.result.SCEVinfo;

/**
 * 邮电部诗人
 */
public class IndVars {
    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            run(function);
        }
    }

    public static void run(Function func) {
        func.buildSCEVInfo();
        for (Loop loop : func.loopInfo.TopLevelLoops) {
            run(loop, func.scevInfo);
        }
    }

    private static int tick(SCEVExpr scevExpr, Instruction.Icmp.CondCode cmp, int n) {
        int max = 100000;
        int i = -1;
        while (i < max) {
            i++;
            switch (cmp) {
                case EQ -> {
                    if (SCEVExpr.calc(scevExpr, i) == n) continue;
                }
                case NE -> {
                    if (SCEVExpr.calc(scevExpr, i) != n) continue;
                }
                case SLE -> {
                    if (SCEVExpr.calc(scevExpr, i) <= n) continue;
                }
                case SLT -> {
                    if (SCEVExpr.calc(scevExpr, i) < n) continue;
                }
                case SGE -> {
                    if (SCEVExpr.calc(scevExpr, i) >= n) continue;
                }
                case SGT -> {
                    if (SCEVExpr.calc(scevExpr, i) > n) continue;
                }
            }
            return i;

        }
        return -1;
    }

    private static boolean getTripCount(Loop loop, SCEVinfo scevInfo) {
        if (loop.tripCount != -1) return true;
        Instruction.Terminator terminator = loop.header.getTerminator();
        if (!(terminator instanceof Instruction.Branch branch)) return false;
        if (!(branch.getCond() instanceof Instruction.Icmp icmp)) return false;

        Value op1 = icmp.getSrc1();
        Value op2 = icmp.getSrc2();
        if (op1 instanceof Instruction.Phi phi && op2 instanceof Constant.ConstantInt constant) {
            if (!scevInfo.contains(phi)) return false;
            if (loop.exits.size() > 1) return false;

            int n = constant.getIntValue();
            loop.tripCount = tick(scevInfo.query(phi), icmp.getCondCode(), n);
            return loop.tripCount != -1;
        }
        if (op2 instanceof Instruction.Phi phi && op1 instanceof Constant.ConstantInt constant) {
            if (!scevInfo.contains(phi)) return false;
            if (loop.exits.size() > 1) return false;

            int n = constant.getIntValue();
            loop.tripCount = tick(scevInfo.query(phi), icmp.getCondCode().inverse(), n);
            return loop.tripCount != -1;
        }
        return false;
    }

    private static void run(Loop loop, SCEVinfo scevInfo) {
        if (getTripCount(loop, scevInfo)) {
            BasicBlock exit = loop.getExit();
            for (var inst : exit.getPhiInstructions()) {
                if (inst.isLCSSA && inst.getIncomingValueSize() == 1) {
                Value val = inst.getIncomingValues().get(0);
                    if (scevInfo.contains(val)) {
                        int res = SCEVExpr.calc(scevInfo.query(val), loop.tripCount);
                        inst.replaceAllUsesWith(new Constant.ConstantInt(res));
                    }
                }
            }
        }
    }

//    private static boolean dfsDefUse(Value now, HashSet<Value> visited, ArrayList<Value> path) {
//        if (visited.contains(now)) return true;
//        visited.add(now);
//        path.add(now);
//        if (now instanceof Instruction inst) {
//            for (var val : inst.getOperands()) {
//                dfsDefUse(val, visited, path);
//            }
//        }
//        visited.remove(now);
//        path.remove(now);
//        return false;
//    }

    private static Value getInitial(Instruction.Phi phiInst, Loop loop) {
        return phiInst.getOptionalValue(loop.getPreHeader());
    }

    private static Value getNext(Instruction.Phi phiInst, Loop loop) {
        for (BasicBlock block : phiInst.getPreBlocks())
            if (block != loop.getPreHeader())
                return phiInst.getOptionalValue(block);
        throw new RuntimeException("getNext: no next value");
    }
}
