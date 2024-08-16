package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import mir.*;
import mir.Module;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;

/**
 * 对于循环中定义的值，如果在循环外部被使用，则在循环出口添加phi
 */
public class LCSSA {
    //循环出口构建LCSSA


    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        AnalysisManager.refreshCFG(function);
        AnalysisManager.refreshDG(function);
        for (Loop loop : function.loopInfo.TopLevelLoops) {
            runOnLoop(loop);
        }
    }

    public static void runOnLoop(Loop loop) {
        //先对子循环进行处理
        for (Loop child : loop.children) {
            runOnLoop(child);
        }
        //再对当前循环进行处理
        for (BasicBlock block : loop.nowLevelBB) {
            for (Instruction instr : block.getInstructions()) {
                //如果指令在循环外部被使用，则在循环出口添加phi
                if (usedOutLoop(instr, loop)) {
                    for (BasicBlock exit : loop.exits) {
                        addPhiAtExitBB(instr, exit, loop);
                    }
                }
            }
        }
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    private static void addPhiAtExitBB(Instruction instr, BasicBlock exit, Loop loop) {
//        System.out.println("addPhiAtExitBB: " + instr + " " + exit.getLabel());
        LinkedHashMap<BasicBlock, Value> phiMap = new LinkedHashMap<>();
        for (BasicBlock pre : exit.getPreBlocks()) {
            phiMap.put(pre, instr);
        }
        Instruction.Phi phi = new Instruction.Phi(exit, instr.getType(), phiMap, true);
        phi.remove();
        exit.addInstFirst(phi);
        LinkedList<Instruction> users = new LinkedList<>();
        for (Instruction user : instr.getUsers()) {
            //循环内部块
            if (loop.LoopContains(user.getParentBlock())) continue;
            //本身
            if (user.equals(phi)) continue;
            //退出块的Phi
            if ((user instanceof Instruction.Phi p) && loop.exits.contains(p.getParentBlock())) continue;
            //不被其支配的普通指令
            switch (user.getInstType()) {
                case PHI -> {
                    Instruction.Phi phiUser = (Instruction.Phi) user;
                    BasicBlock incomingBlock = phiUser.getIncomingBlock(instr);
                    if (!AnalysisManager.dominate(exit, incomingBlock)) {
                        continue;
                    }
                }
                default -> {
                    if (!AnalysisManager.dominate(exit, user.getParentBlock())) {
                        continue;
                    }
                }
            }
            users.add(user);
        }
        for (Instruction user : users) {
            user.replaceUseOfWith(instr, phi);
        }
    }

    /**
     * 判断指令是否在循环外部被使用
     */
    public static boolean usedOutLoop(Instruction instr, Loop loop) {
        for (Use use : instr.getUses()) {
            if (!loop.LoopContains(((Instruction) use.getUser()).getParentBlock())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 支配树中判断A是否支配B
     */
    public static boolean isDomable(BasicBlock A, BasicBlock B, HashSet<BasicBlock> visited) {
        if (A.equals(B)) return true;
        for (BasicBlock suc : A.getDomTreeChildren()) {
            if (visited.contains(suc)) continue;
            visited.add(suc);
            if (isDomable(suc, B, visited)) return true;
        }
        return false;
    }

    public static void remove(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            removeOnFunc(function);
        }
    }

    public static void removeOnFunc(Function function) {
        for (BasicBlock block : function.getBlocks()) {
            for (Instruction.Phi phi : block.getPhiInstructions()) {
                if (phi.isLCSSA) {
                    if (phi.getPreBlocks().size() != 1) {
                        if (!phi.canBeReplaced()) {
                            phi.isLCSSA = false;
                            continue;
                        }
                    }
                    Value v = phi.getOptionalValue(phi.getPreBlocks().get(0));
                    phi.replaceAllUsesWith(v);
                    phi.delete();
                }
            }
        }
    }
}

