package midend.Transform.DCE;

import midend.Analysis.AnalysisManager;
import midend.Transform.Loop.LoopInfo;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;


import java.util.HashSet;

public class DeadLoopEliminate {
    private static boolean modified;

    public static boolean run(Module module) {
        boolean modified = false;
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
//            if (function.isParallelLoopBody) continue;
            LoopInfo.runOnFunc(function);
            runOnFunc(function);
        }
        return modified;
    }

    public static boolean runOnFunc(Function function) {
        modified = false;
        AnalysisManager.refreshCFG(function);
        if (function.loopInfo == null) return false;
        HashSet<Loop> removes = new HashSet<>();
        for (Loop loop : function.loopInfo.TopLevelLoops) {
            if (runOnLoop(loop)) removes.add(loop);
        }
        removes.forEach(function.loopInfo.TopLevelLoops::remove);
        RemoveBlocks.runOnFunc(function);
        return modified;
    }

    private static boolean runOnLoop(Loop loop) {
        HashSet<Loop> removes = new HashSet<>();
        for (Loop child : loop.children) {
            if (runOnLoop(child)) removes.add(child);
        }
        removes.forEach(loop.children::remove);
        if (loopCanRemove(loop)) {
            modified = true;
            BasicBlock exit = loop.exits.iterator().next();
            BasicBlock preHead = loop.preHeader;
            BasicBlock head = loop.header;
            if (preHead == null) {
                for (BasicBlock entering : loop.enterings) {
                    Instruction.Terminator term = entering.getTerminator();
                    term.replaceTarget(head, exit);
                }
            } else {
                Instruction.Terminator term = preHead.getTerminator();
                term.replaceTarget(head, exit);
            }
            return true;
        }
        return false;
    }

    private static boolean loopCanRemove(Loop loop) {
        if (!loop.children.isEmpty()) return false;
        if (loop.exits.size() != 1) return false;
        BasicBlock exit = loop.exits.iterator().next();
        if (!exit.getInstructions().isEmpty() && exit.getFirstInst() instanceof Instruction.Phi) return false;
        if (loop.exits.size() != exit.getPreBlocks().size()) return false;
        for (BasicBlock block : loop.nowLevelBB) {
            for (Instruction instr : block.getInstructions()) {
                if (hasStrongEffect(instr)) return false;
                for (Instruction user : instr.getUsers()) {
                    if (user.getParentBlock().loop != loop)
                        return false;
                }
            }
        }
        return true;
    }

    private static boolean hasStrongEffect(Instruction instr) {
        if (instr instanceof Instruction.Store) return true;
        if (instr instanceof Instruction.AtomicAdd) return true;
        if (instr instanceof Instruction.Load) return true;
        if (instr instanceof Instruction.Return) return true;
        if (instr instanceof Instruction.Call call) {
            Function callee = call.getDestFunction();
            if (callee.getName().equals("NELParallelFor")) {
                callee = (Function) call.getParams().get(2);
            }
            FuncInfo calleeInfo = AnalysisManager.getFuncInfo(callee);
            return calleeInfo.hasSideEffect || calleeInfo.hasMemoryWrite || calleeInfo.hasPutOut || calleeInfo.hasReadIn;
        }
        return false;
    }
}
