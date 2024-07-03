package backend.Opt.Scheduler;

import java.util.HashMap;

import backend.operand.Reg;
import backend.riscv.RiscvInstruction.RiscvInstruction;

public class ScheduleState {
    public class PipeLine{

    }
    private int cycleCount;
    private final HashMap<RiscvInstruction, HashMap<Integer, Reg>> regRenameHashMap;
    private int issuedFlag;
    private final HashMap<Integer, Integer> nextPipelineAvailable;
    private final HashMap<Reg, Integer> registerAvailableTime;

    public ScheduleState(HashMap<RiscvInstruction, HashMap<Integer, Reg>> regRenameHashMap) {
        this.cycleCount = 0;
        this.regRenameHashMap = regRenameHashMap;
        this.issuedFlag = 0;
        this.nextPipelineAvailable = new HashMap<>();
        this.registerAvailableTime = new HashMap<>();
    }

    public int queryRegisterLatency(RiscvInstruction inst, int idx) {
        Reg reg = regRenameHashMap.get(inst).get(idx);
        return registerAvailableTime.getOrDefault(reg, cycleCount) > cycleCount
                ? registerAvailableTime.get(reg) - cycleCount
                : 0;
    }

    public boolean isPipelineReady(int pipelineId) {
        return nextPipelineAvailable.getOrDefault(pipelineId, cycleCount) <= cycleCount;
    }

    public boolean isAvailable(int mask) {
        return (issuedFlag & mask) != mask;
    }

    public void setIssued(int mask) {
        issuedFlag |= mask;
    }

    public void resetPipeline(int pipelineId, int duration) {
        nextPipelineAvailable.put(pipelineId, cycleCount + duration);
    }

    public void makeRegisterReady(RiscvInstruction inst, int idx, int latency) {
        Reg reg = regRenameHashMap.get(inst).get(idx);
        registerAvailableTime.put(reg, cycleCount + latency);
    }

    public int nextCycle() {
        cycleCount++;
        issuedFlag = 0;
        return cycleCount;
    }
}