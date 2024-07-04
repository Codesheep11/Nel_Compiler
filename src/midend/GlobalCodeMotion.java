package midend;

import mir.*;
import manager.CentralControl;
import mir.Module;

import java.util.HashSet;

/**
 * 全局代码移动
 *
 * @author Srchycz
 */
public class GlobalCodeMotion {

    private Function currentFunc;
    private BasicBlock entry;
    private final HashSet<Instruction> scheduledSet;

    private GlobalCodeMotion() {
        this.scheduledSet = new HashSet<>();
    }

    public static void run(Module module) {
        if (!CentralControl._GCM_OPEN) return;
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            run(func);
        }
    }

    public static void run(Function function) {
        if (!CentralControl._GCM_OPEN) return;
        GlobalCodeMotion gcm = new GlobalCodeMotion();
        gcm.entry = function.getEntry();
        gcm.currentFunc = function;
        function.buildDominanceGraph();
        gcm.GCM4Block(function.getEntry(), true);
//        gcm.scheduledSet.clear();
        gcm.GCM4Block(function.getEntry(), false);
    }

    private void GCM4Block(BasicBlock block, boolean isScheduleEarly) {
        for (Instruction instr : block.getInstructions()) {
            if (isScheduleEarly)
                scheduleEarly(instr);
            else
                scheduleLateAndBest(instr);
        }
        for (BasicBlock child : block.getDomTreeChildren()) {
            GCM4Block(child, isScheduleEarly);
        }
    }

    private void scheduleEarly(Instruction instr) {
        if (!instr.getParentBlock().getParentFunction().equals(currentFunc))
            return;
        if (isPinned(instr)) {
            instr.earliest = instr.getParentBlock();
            return;
        }
        if (scheduledSet.contains(instr)) {
            return;
        }
        scheduledSet.add(instr);
        instr.earliest = entry;
        for (Value value : instr.getOperands()) {
            if (value instanceof Instruction instrValue) {
                scheduleEarly(instrValue);
                if (instrValue.earliest.getDomDepth() > instr.earliest.getDomDepth()) {
                    instr.earliest = instrValue.earliest;
                }
            }
        }
    }

    /**
     * 向后调度同时找到最佳位置
     * 最佳: 优先循环深度最浅
     */
    private void scheduleLateAndBest(Instruction instr) {
        if (!instr.getParentBlock().getParentFunction().equals(currentFunc))
            return;
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
                scheduleLateAndBest(instrUser);
                BasicBlock userBlock = instrUser.latest;
                if (instrUser instanceof Instruction.Phi)
                    userBlock = instrUser.latest.getIdom();
                instr.latest = getDomLCA(instr.latest, userBlock);
            }
        }
        // instr.latest 现在是最后可被调度到的块
        if (instr.latest.getDomDepth() < instr.earliest.getDomDepth()) {
            instr.earliest = instr.latest = instr.getParentBlock();
            return;
        }
        BasicBlock best = instr.latest;
//        int bestDepth = instr.latest.getDomDepth();
        int bestLoopDepth = instr.latest.getLoopDepth();
        while (instr.latest != instr.earliest) {
            instr.latest = instr.latest.getIdom();
            if (instr.latest.getLoopDepth() < bestLoopDepth) {
                best = instr.latest;
//                bestDepth = instr.latest.getDomDepth();
                bestLoopDepth = instr.latest.getLoopDepth();
            }
        }
        instr.latest = best;
        // 开始调度
        if (!instr.latest.equals(instr.getParentBlock())) {
            instr.remove();
            instr.latest.getInstructions().insertBefore(instr, findPos(instr, instr.latest));
            instr.setParentBlock(instr.latest);
        }
    }

    private Instruction findPos(Instruction instr, BasicBlock block) {
        HashSet<User> users = new HashSet<>();
        for (Use use : instr.getUses()) {
            users.add(use.getUser());
        }
        for (Instruction inst : block.getInstructions()) {
            if (inst instanceof Instruction.Phi) {
                // just for test
                if (users.contains(inst))
                    System.out.println("Error: GCM 尝试调度在PHI指令之前!");
                continue;
            }
            if (users.contains(inst)) {
                return inst;
            }
        }
        if (block.getInstructions().getLast() instanceof Instruction.Phi) {
            System.out.println("Warning: 出现了冗余块，请先进行DCD优化再进行GCM!");
        }
        return block.getInstructions().getLast();
    }

    /**
     * 获取两个基本块在支配树上的最近公共祖先
     *
     * @param a BasicBlock
     * @param b BasicBlock
     * @return 最近公共祖先 LCA(a, b)
     */
    private BasicBlock getDomLCA(BasicBlock a, BasicBlock b) {
        if (a == null) {
            return b;
        }
        while (a.getDomDepth() > b.getDomDepth())
            a = a.getIdom();
        while (a.getDomDepth() < b.getDomDepth())
            b = b.getIdom();
        while (a != b) {
            a = a.getIdom();
            b = b.getIdom();
        }
        return a;
    }

    private boolean isPinned(Instruction inst) {
        return switch (inst.getInstType()) {
            case PHI, PHICOPY, JUMP, BRANCH, RETURN, STORE, LOAD, CALL -> true;
            default -> false;
        };
    }
}