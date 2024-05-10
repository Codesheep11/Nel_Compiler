package midend;

import manager.Manager;
import mir.*;
import mir.Module;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;

public class LCSSA {
    //循环出口构建LCSSA
    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            addPhiForFunc(function);
        }
    }

    private static void addPhiForFunc(Function function) {
        for (Loop loop : function.loops) {
            addPhiForLoop(loop);
        }
    }

    private static void addPhiForLoop(Loop loop) {
        //先对子循环进行处理
        for (Loop child : loop.children) {
            addPhiForLoop(child);
        }
        //再对当前循环进行处理
        for (BasicBlock block : loop.blocks) {
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
        for (Use use : instr.getUses()) {
            if (!(use.getUser() instanceof Instruction)) {
                throw new RuntimeException("addPhiAtExitBB: " + instr + " use is not an instruction\n");
            }
            if ((use.getUser() instanceof Instruction.Phi) && (((Instruction.Phi) use.getUser()).getParentBlock().equals(exit)))
                continue;
            BasicBlock parentBlock = ((Instruction) use.getUser()).getParentBlock();
            HashSet<BasicBlock> visited = new HashSet<>();
            visited.add(exit);
            if (!isDomable(exit, parentBlock, visited)) continue;
            Instruction user = (Instruction) use.getUser();
            if (user.equals(phi)) continue;
            if (loop.LoopContains(user.getParentBlock())) continue;
            users.add(user);
        }
        for (Instruction user : users) {
            user.replaceUseOfWith(instr, phi);
        }
        if (users.isEmpty()) {
            phi.remove();
        }
    }

    /**
     * 判断指令是否在循环外部被使用
     *
     * @param instr
     * @param loop
     * @return
     */
    public static boolean usedOutLoop(Instruction instr, Loop loop) {
        for (Use use : instr.getUses()) {
            if (!(use.getUser() instanceof Instruction)) {
                throw new RuntimeException("usedOutLoop: " + instr + " use is not an instruction\n");
            }
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
            for (BasicBlock block : function.getBlocks()) {
                for (Instruction instr : block.getInstructions()) {
                    if (instr instanceof Instruction.Phi) {
                        Instruction.Phi phi = (Instruction.Phi) instr;
                        if (phi.isLCSSA) {
                            if (phi.getSize() != 1) {
                                throw new RuntimeException("LCSSA phi size != 1\n");
                            }
                            Value v = phi.getOptionalValue(phi.getPreBlocks().get(0));
                            for (Use use : phi.getUses()) {
                                use.getUser().replaceUseOfWith(phi, v);
                            }
                            instr.remove();
                        }
                    }
                    else break;
                }
            }
        }
    }
}

