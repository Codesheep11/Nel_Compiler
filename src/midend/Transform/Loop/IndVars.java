package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import mir.Module;
import mir.*;
import midend.Analysis.result.SCEVinfo;

/**
 * 邮电部诗人
 */
public class IndVars {

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function func) {
        AnalysisManager.refreshSCEV(func);
        SCEVinfo scevInfo = AnalysisManager.getSCEV(func);
        for (Loop loop : func.loopInfo.TopLevelLoops) {
            run(loop, scevInfo);
        }
    }

    // TODO: 判断tripCount的方式太朴素了 考虑优化
    private static int tick(SCEVExpr scevExpr, Instruction.Icmp.CondCode cmp, int n) {
        int max = 100000;
        int i = -1;
        if (scevExpr.isNotNegative() && cmp != Instruction.Icmp.CondCode.EQ && cmp != Instruction.Icmp.CondCode.NE) {
            return calcTick(scevExpr, cmp, n);
        }
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

    private static int calcTick(SCEVExpr scevExpr, Instruction.Icmp.CondCode cmp, int n) {
        int init = scevExpr.getInit();
        int step = scevExpr.getStep();
        return switch(cmp) {
            case SLE -> {
                if (init > n) yield -1;
                yield Math.floorDiv(n - init , step) + 1;
            }
            case SLT -> {
                if (init >= n) yield -1;
                yield (int) Math.ceil((double) (n - init) / (double) step);
            }
            case SGE -> {
                if (init < n) yield -1;
                yield Math.floorDiv(n - init , step) + 1;
            }
            case SGT -> {
                if (init <= n) yield -1;
                yield (int) Math.ceil((double) (n - init) / (double) step);
            }
            default -> -1;
        };
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
        for (Loop child : loop.children) {
            run(child, scevInfo);
        }
        if (getTripCount(loop, scevInfo)) {
            BasicBlock exit = loop.getExit();
            for (var inst : exit.getPhiInstructions()) {
                if (inst.isLCSSA && inst.getIncomingValueSize() == 1) {
                    Value val = inst.getIncomingValues().get(0);
                    if (scevInfo.contains(val)) {
                        int res = SCEVExpr.calc(scevInfo.query(val), loop.tripCount);
                        inst.replaceAllUsesWith(Constant.ConstantInt.get(res));
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
