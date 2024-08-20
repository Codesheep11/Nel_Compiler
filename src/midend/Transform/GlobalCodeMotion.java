package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import midend.Util.Print;
import mir.*;
import manager.CentralControl;
import mir.Module;
import midend.Analysis.result.DGinfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

import static manager.Manager.module;

/**
 * 全局代码移动
 *
 * @see <a href="https://dl.acm.org/doi/pdf/10.1145/207110.207154">GVN/GCM</a>
 */
public class GlobalCodeMotion {

    private Function currentFunc;
    private BasicBlock entry;
    private final HashSet<Instruction> scheduledSet;
    private DGinfo dginfo;

    private GlobalCodeMotion() {
        this.scheduledSet = new HashSet<>();
    }

    public static void run(Module module) {
        if (!CentralControl._GCM_OPEN) return;
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            runOnFunc(func);
        }
    }

    public static void runOnFunc(Function function) {
        if (!CentralControl._GCM_OPEN) return;
        GlobalCodeMotion gcm = new GlobalCodeMotion();
        gcm.entry = function.getEntry();
        gcm.currentFunc = function;
        AnalysisManager.refreshDG(function);
        gcm.dginfo = AnalysisManager.getDG(function);
        gcm.GCMEarly4Block(function.getEntry());
        gcm.scheduledSet.clear();
        gcm.GCMLate4Block();
    }

    private void GCMEarly4Block(BasicBlock block) {
        for (Instruction instr : block.getInstructionsSnap()) {
            scheduleEarly(instr);
        }
        for (BasicBlock child : block.getDomTreeChildren()) {
            GCMEarly4Block(child);
        }
    }

    private void GCMLate4Block() {
        ArrayList<BasicBlock> visitList = currentFunc.getDomTreePostOrder();
        Collections.reverse(visitList);
        for (BasicBlock basicblock : currentFunc.getDomTreePostOrder()) {
            ArrayList<Instruction> instructions = basicblock.getInstructionsSnap();
            Collections.reverse(instructions);
            for (Instruction instr : instructions) {
                scheduleLateAndBest(instr);
            }
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
                if (instrValue.earliest == null) {
//                    Print.output(module, "debug.txt");
                    System.out.println(instrValue);
                }
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
        scheduledSet.add(instr);
        instr.latest = null;
        for (Use use : instr.getUses()) {
            User user = use.getUser();
            if (user instanceof Instruction instrUser) {
                scheduleLateAndBest(instrUser);
                BasicBlock userBlock = instrUser.latest;
                if (instrUser instanceof Instruction.Phi phi) {
                    userBlock = null;
                    for(Map.Entry<BasicBlock, Value> entry : phi.getOptionalValues().entrySet()) {
                        if (entry.getValue() == instr) {
                            userBlock = getDomLCA(userBlock, entry.getKey());
                        }
                    }
                }
                if (userBlock == null || instr.earliest.getDomDepth() > userBlock.getDomDepth())
                    continue;
                instr.latest = getDomLCA(instr.latest, userBlock);
            }
        }
        if (instr.latest == null) {
            // Dead Instruction
            // instr.earliest = instr.latest = instr.getParentBlock();
            return;
        }
        // instr.latest 现在是最后可被调度到的块
        if (instr.earliest == null) {
            System.out.println(instr);
        }
        if (instr.latest.getDomDepth() < instr.earliest.getDomDepth()) {
            instr.earliest = instr.latest = instr.getParentBlock();
            return;
        }
        BasicBlock best = instr.latest;
        int bestDepth = instr.latest.getDomDepth();
        int bestLoopDepth = instr.latest.getLoopDepth();
        while (instr.latest != instr.earliest) {
            instr.latest = dginfo.getIDom(instr.latest);
            if (instr.latest == null) {
                Print.output(module, "debug.txt");
                System.out.println("err");
            }
            if (instr.latest.getLoopDepth() < bestLoopDepth) {
                best = instr.latest;
                bestDepth = instr.latest.getDomDepth();
                bestLoopDepth = instr.latest.getLoopDepth();
            } else if(instr.latest.getLoopDepth() == bestLoopDepth && instr.latest.getDomDepth() > bestDepth) {
                best = instr.latest;
                bestDepth = instr.latest.getDomDepth();
            }
        }
        instr.latest = best;
        // 开始调度
        if (!instr.latest.equals(instr.getParentBlock())) {
            // ConcurrentModification!
            instr.remove();
            instr.latest.insertInstBefore(instr, findPos(instr, instr.latest));
            if (instr.getNext() == null)
                System.out.println(instr);
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
                // region test
//                if (users.contains(inst)) {
                        // just for test
//                    System.out.println("Error: GCM 尝试调度在PHI指令之前!");
//                }
                // endregion
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
        if (b == null) {
            return a;
        }
        while (a.getDomDepth() > b.getDomDepth())
            a = dginfo.getIDom(a);
        while (a.getDomDepth() < b.getDomDepth())
            b = dginfo.getIDom(b);
        while (a != b) {
            a = dginfo.getIDom(a);
            b = dginfo.getIDom(b);
        }
        return a;
    }

    private boolean isPinned(Instruction inst) {
        return switch (inst.getInstType()) {
            case PHI, PHICOPY, JUMP, BRANCH, RETURN, STORE, LOAD, ATOMICADD -> true;
            case CALL -> {
                Function func = ((Instruction.Call) inst).getDestFunction();
                FuncInfo funcInfo = AnalysisManager.getFuncInfo(func);
                yield func.isExternal() || !funcInfo.isStateless || funcInfo.hasReadIn || funcInfo.hasPutOut;
            }
            default -> false;
        };
    }
}