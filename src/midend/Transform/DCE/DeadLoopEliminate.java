package midend.Transform.DCE;

import midend.Analysis.AnalysisManager;
import midend.Transform.Loop.LoopInfo;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashSet;

public class DeadLoopEliminate {
    public static void run(Module module) {
        for (var function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            function.loopInfo = new LoopInfo(function);
            runOnFunc(function);
        }
    }

    private static void runOnFunc(Function function) {
        AnalysisManager.refreshCFG(function);
        if (function.loopInfo == null) return;
        HashSet<Loop> removes = new HashSet<>();
        for (Loop loop : function.loopInfo.TopLevelLoops) {
            if (runOnLoop(loop)) removes.add(loop);
        }
        removes.forEach(function.loopInfo.TopLevelLoops::remove);
        RemoveBlocks.runOnFunc(function);
    }

    private static boolean runOnLoop(Loop loop) {
        HashSet<Loop> removes = new HashSet<>();
        for (Loop child : loop.children) {
            if (runOnLoop(child)) removes.add(child);
        }
        removes.forEach(loop.children::remove);

        if (loopCanRemove(loop)) {
            BasicBlock exit = loop.exits.iterator().next();
            if (loop.preHeader == null) {
                for (BasicBlock entering : loop.enterings) {
                    BasicBlock head = loop.header;
                    Instruction.Terminator term = entering.getTerminator();
                    term.replaceSucc(head, exit);
                }
            } else {
                BasicBlock preHead = loop.preHeader;
                BasicBlock head = loop.header;
                Instruction.Terminator term = preHead.getTerminator();
                term.replaceSucc(head, exit);
            }
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
                for (Use use : instr.getUses()) {
                    Instruction user = (Instruction) use.getUser();
                    Loop userLoop = user.getParentBlock().loop;
                    if (!loop.equals(userLoop) || hasStrongEffect(user)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasStrongEffect(Instruction instr) {
        if (instr instanceof Instruction.Store) return true;
        if (instr instanceof Instruction.Load) return true;
        if (instr instanceof Instruction.Return) return true;
        if (instr instanceof Instruction.Call call) {
            Function callee = call.getDestFunction();
            if (FuncInfo.hasSideEffect.get(callee) || !FuncInfo.isStateless.get(callee) || FuncInfo.hasReadIn.get(callee) || FuncInfo.hasPutOut.get(callee))
                return true;
        }
        return false;
    }
}
