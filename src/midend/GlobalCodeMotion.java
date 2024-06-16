package midend;

import mir.*;

import java.util.HashMap;
import java.util.HashSet;

/**
 * 全局代码移动
 * @author Srchycz
 */
public class GlobalCodeMotion {

    private BasicBlock entry;
    private HashSet<Instruction> scheduledSet;

    public GlobalCodeMotion() {
        this.scheduledSet = new HashSet<>();
    }

    public static void run(Function function) {
        GlobalCodeMotion gcm = new GlobalCodeMotion();
        gcm.entry = function.getEntry();
        function.buildDominanceGraph();
    }

    private void scheduleEarly(Instruction instr) {
        if (isPinned(instr)) {
            instr.earliest = instr.getParentBlock();
            return;
        }
        if (scheduledSet.contains(instr)) {
            return;
        }
        scheduledSet.add(instr);
        instr.earliest = entry;
        for (Use use : instr.getUses()) {
            User user = use.getUser();
            if (user instanceof Instruction instrUser) {
                scheduleEarly(instrUser);
                if (instrUser.earliest.getDomDepth() > instr.earliest.getDomDepth()) {
                    instr.earliest = instrUser.earliest;
                }
            }
            else {
                System.out.println("Warning: GCM 向前调度遇到了非指令User!");
            }
        }
    }

    private void scheduleLate(Instruction instr) {
        if (isPinned(instr)) {
            instr.latest = instr.getParentBlock();
            return;
        }
        if (scheduledSet.contains(instr)) {
            return;
        }
        instr.latest = null;
        for (Use use : instr.getUses()) {
            User user = use.getUser();
            if (user instanceof Instruction instrUser) {
                scheduleLate(instrUser);
                instr.latest = getDomLCA(instr.latest, instrUser.latest);
            }
            else {
                System.out.println("Warning: GCM 向后调度遇到了非指令User!");
            }
        }
    }

    /**
     * 获取两个基本块在支配树上的最近公共祖先
     * @param a BasicBlock
     * @param b BasicBlock
     * @return 最近公共祖先 LCA(a, b)
     */
    private BasicBlock getDomLCA(BasicBlock a, BasicBlock b) {
        if (a == null) {
            return b;
        }
        while (a.getDomDepth() > b.getDomDepth()) {
            a = a.getIdom();
        }
        while (a.getDomDepth() < b.getDomDepth()) {
            b = b.getIdom();
        }
        while (a != b) {
            a = a.getIdom();
            b = b.getIdom();
        }
        return a;
    }

    private boolean isPinned(Instruction inst) {
        return switch(inst.getInstType()){
            case PHI, PHICOPY, JUMP, BRANCH, RETURN, STORE, LOAD, CALL -> true;
            default -> false;
        };
    }
}