package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.Module;
import mir.*;

import java.util.*;

public class Scheduler {
    //全局的，维护每个Block出口活跃的指令
    private static final HashMap<BasicBlock, HashSet<Instruction>> outLiveMap = new HashMap<>();
    //维护每个Block的活跃指令 Use - User
    private static final HashMap<Instruction, HashSet<Instruction>> LiveMap = new HashMap<>();
    //维护每个指令的分数，分数越小越先出队，即放在后面
    private static final HashMap<Instruction, Integer> ScoreMap = new HashMap<>();
    //在当前队列中的指令，便于查询
    private static final HashSet<Instruction> InQueueInstrs = new HashSet<>();
    //User - Use
    private static final HashMap<Instruction, HashSet<Instruction>> useMap = new HashMap<>();
    //Use  - User
    private static final HashMap<Instruction, HashSet<Instruction>> userMap = new HashMap<>();

    //按照分数和时间戳排序的指令队列
    private static final PriorityQueue<SchedulerInstr> afterScheduler = new PriorityQueue<>((o1, o2) -> {
        if (o1.score == o2.score) return o2.timestamp - o1.timestamp;
        return o1.score - o2.score;
    });

    private static class SchedulerInstr {
        public final Instruction instr;
        public final int score;
        public final int timestamp;
        private static int total_timestamp = 0;

        public SchedulerInstr(Instruction instr, int score)
        {
            this.instr = instr;
            this.score = score;
            this.timestamp = total_timestamp++;
            InQueueInstrs.add(instr);
        }
    }


    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    private static void runOnFunc(Function func) {
        outLiveMap.clear();
        for (BasicBlock block : func.getBlocks()) outLiveMap.put(block, new HashSet<>());
        for (BasicBlock block : func.getBlocks()) {
            for (Instruction.Phi phi : block.getPhiInstructions()) {
                for (BasicBlock pre : block.getPreBlocks()) {
                    if (phi.getOptionalValue(pre) instanceof Instruction out)
                        outLiveMap.get(pre).add(out);
                }
            }
        }
        dfsScheduleOnBlock(func.getEntry());
    }

    private static void clear() {
        LiveMap.clear();
        ScoreMap.clear();
        useMap.clear();
        userMap.clear();
        InQueueInstrs.clear();
        afterScheduler.clear();
        SchedulerInstr.total_timestamp = 0;
    }

    //dfs
    private static void dfsScheduleOnBlock(BasicBlock block) {
        HashSet<Instruction> outLiveSet = outLiveMap.get(block);
        for (Value operand : block.getTerminator().getOperands()) {
            if (operand instanceof Instruction instr) {
                outLiveSet.add(instr);
            }
        }
        for (BasicBlock child : AnalysisManager.getDomTreeChildren(block)) {
            dfsScheduleOnBlock(child);
            outLiveSet.addAll(outLiveMap.get(child));
        }
        //开始重排
        clear();
        SchedulerOnBlock(block);
    }

    private static void SchedulerOnBlock(BasicBlock block) {
        //构建当前块的指令依赖图
        InitUseUserMap(block);
        InitLiveMap(block);
        for (Instruction instr : block.getMainInstructions()) {
            instr.remove();
            ScoreMap.put(instr, 0);
            if (userMap.get(instr).isEmpty()) afterScheduler.add(new SchedulerInstr(instr, 0));
            if (instr.getType() != Type.VoidType.VOID_TYPE) updateScore(instr, -1);
        }
        LiveMap.forEach((instruction, users) -> updateScore(users.iterator().next(), 1));
        outLiveMap.get(block).addAll(LiveMap.keySet());
        block.getInstructionsSnap().forEach(outLiveMap.get(block)::remove);
        Instruction pos = block.getTerminator();
        while (true) {
            Instruction instr = getNextFromQueue();
            if (instr == null) break;
            block.insertInstBefore(instr, pos);
            pos = instr;
            updateLiveMap(instr);
            updateUseUserMap(instr);
        }
    }

    private static void updateLiveMap(Instruction instr) {
        for (Value operand : instr.getOperands()) {
            if (operand instanceof Instruction opInstr) {
                if (LiveMap.containsKey(opInstr)) {
                    var users = LiveMap.get(opInstr);
                    users.remove(instr);
                    for (Instruction user : users) {
                        updateScore(user, -1);
                    }
                }
            }
        }
    }

    private static void updateUseUserMap(Instruction instr) {
        for (Instruction use : useMap.get(instr)) {
            var users = userMap.get(use);
            users.remove(instr);
            if (users.isEmpty()) {
                afterScheduler.add(new SchedulerInstr(use, ScoreMap.get(use)));
                InQueueInstrs.add(use);
            }
        }
    }

    private static Instruction getNextFromQueue() {
        while (!afterScheduler.isEmpty()) {
            SchedulerInstr si = afterScheduler.remove();
            if (si.score != ScoreMap.get(si.instr)) continue;
            if (!InQueueInstrs.contains(si.instr)) continue;
            InQueueInstrs.remove(si.instr);
            return si.instr;
        }
        return null;
    }

    private static void InitUseUserMap(BasicBlock block) {
        for (Instruction instr : block.getMainInstructions()) {
            useMap.put(instr, new HashSet<>());
            userMap.put(instr, new HashSet<>());
        }
        ArrayList<Instruction> passList = new ArrayList<>();
        Instruction lastPinedInstr = null;
        for (Instruction instr : block.getMainInstructions()) {
            for (Value operand : instr.getOperands()) {
                if (operand instanceof Instruction opInstr && passList.contains(opInstr)) {
                    useMap.get(instr).add(opInstr);
                    userMap.get(opInstr).add(instr);
                }
            }
            if (instr instanceof Instruction.Call call) {
                if (call.getDestFunction().getName().equals("starttime") || call.getDestFunction().getName().equals("stoptime")) {
                    //时间戳不可调度
                    for (Instruction passInstr : passList) {
                        useMap.get(call).add(passInstr);
                        userMap.get(passInstr).add(call);
                    }
                    while (call.getNext() instanceof Instruction nextInstr) {
                        if (nextInstr instanceof Instruction.Terminator) {
                            break;
                        }
                        else {
                            userMap.get(nextInstr).add(call);
                            useMap.get(call).add(nextInstr);
                        }
                    }
                }
            }
            //维护严格次序的指令
            if (isPinedInstr(instr)) {
                if (lastPinedInstr != null) {
                    useMap.get(instr).add(lastPinedInstr);
                    userMap.get(lastPinedInstr).add(instr);
                }
                lastPinedInstr = instr;
            }
            passList.add(instr);
        }
    }

    /**
     * block所有operand的非出口活跃指令
     *
     */
    private static void InitLiveMap(BasicBlock block) {
        HashSet<Instruction> exitLive = outLiveMap.get(block);
        for (Instruction instr : block.getMainInstructions()) {
            for (Value operand : instr.getOperands()) {
                if (operand instanceof Instruction opInstr) {
                    if (!exitLive.contains(opInstr)) {
                        LiveMap.putIfAbsent(opInstr, new HashSet<>());
                        LiveMap.get(opInstr).add(instr);
                    }
                }
            }
        }
    }

    /**
     * 更新分数
     *
     */
    private static void updateScore(Instruction instr, int score) {
        ScoreMap.put(instr, ScoreMap.get(instr) + score);
        if (InQueueInstrs.contains(instr)) {
            int newScore = ScoreMap.get(instr);
            afterScheduler.add(new SchedulerInstr(instr, newScore));
        }
    }

    //严格次序的指令
    private static boolean isPinedInstr(Instruction instruction) {
        if (instruction instanceof Instruction.Load ||
                instruction instanceof Instruction.Store ||
                instruction instanceof Instruction.AtomicAdd)
            return true;
        if (instruction instanceof Instruction.Call call) {
            Function callee = call.getDestFunction();
            if (callee.getName().equals("NELParallelFor") || callee.getName().equals("NELCacheLookup"))
                return true;
            FuncInfo calleeInfo = AnalysisManager.getFuncInfo(callee);
            if (!calleeInfo.isStateless || calleeInfo.hasPutOut || calleeInfo.hasReadIn)
                return true;
        }
        return false;
    }
}
