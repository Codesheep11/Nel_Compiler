package backend.Opt.Scheduler;

import backend.operand.Reg;
import backend.riscv.RiscvInstruction.*;

public abstract class ScheduleClass {


    public static int RISCVIDivPipeline = 1;
    public static int RISCVFPDivPipeline = 2;

    // 这里是俩流水线，A和B

    public enum RISCVIssueMask {
        RISCVPipelineA(1),
        RISCVPipelineB(1 << 1),
        RISCVPipelineAB(RISCVPipelineA.mask | RISCVPipelineB.mask);

        public final int mask;

        RISCVIssueMask(int mask) {
            this.mask = mask;
        }
    }

    public abstract boolean schedule(ScheduleState state, RiscvInstruction instruction);

    public static class RISCVScheduleClassIntegerArithmeticGeneric extends ScheduleClass {

        public RISCVScheduleClassIntegerArithmeticGeneric(RISCVIssueMask validPipeline, boolean early, boolean late) {
            // 检查参数
            if (validPipeline.mask == 0 || !(early || late)) {
                throw new IllegalArgumentException("Invalid template parameters");
            }
            this.validPipeline = validPipeline;
            this.early = early;
            this.late = late;
        }

        private final RISCVIssueMask validPipeline;
        private final boolean early;
        private final boolean late;

        @Override
        public boolean schedule(ScheduleState state, RiscvInstruction inst) {
            if (!state.isAvailable(validPipeline.mask)) {
                return false;
            }
            boolean availableInAG = true;
            for (int idx = 0; idx < inst.getOperandNum(); idx++) {
                if (inst.isUse(idx)) {
                    int latency = state.queryRegisterLatency(inst, idx);
                    if (latency <= 2) {
                        availableInAG &= (latency == 0);
                    } else {
                        return false;
                    }
                }
            }

            if (Boolean.TRUE.equals(early)) {
                if (availableInAG) {
                    if (validPipeline == RISCVIssueMask.RISCVPipelineAB) {
                        state.setIssued(state.isAvailable(RISCVIssueMask.RISCVPipelineA.mask)
                                ? RISCVIssueMask.RISCVPipelineA.mask : RISCVIssueMask.RISCVPipelineB.mask);
                    } else {
                        state.setIssued(validPipeline.mask);
                    }
                    state.makeRegisterReady(inst, 0, 1);
                    return true;
                }
            }
            if (Boolean.TRUE.equals(late)) {
                if (validPipeline == RISCVIssueMask.RISCVPipelineAB) {
                    state.setIssued(state.isAvailable(RISCVIssueMask.RISCVPipelineA.mask)
                            ? RISCVIssueMask.RISCVPipelineA.mask : RISCVIssueMask.RISCVPipelineB.mask);
                } else {
                    state.setIssued(validPipeline.mask);
                }
                state.makeRegisterReady(inst, 0, 3);
                return true;
            }
            return false;
        }
    }


    public static class RISCVScheduleClassIntegerArithmetic extends RISCVScheduleClassIntegerArithmeticGeneric {
        public RISCVScheduleClassIntegerArithmetic() {
            super(RISCVIssueMask.RISCVPipelineAB, true, true);
        }
    }

    public static class RISCVScheduleClassIntegerArithmeticEarlyLateB extends RISCVScheduleClassIntegerArithmeticGeneric {
        public RISCVScheduleClassIntegerArithmeticEarlyLateB() {
            super(RISCVIssueMask.RISCVPipelineB, true, true);
        }
    }

    static class RISCVScheduleClassBranch extends ScheduleClass {
        public boolean schedule(ScheduleState state, RiscvInstruction inst) {
            if (!state.isAvailable(RISCVIssueMask.RISCVPipelineB.mask)) {
                return false;
            }
            for (int idx = 0; idx < inst.getOperandNum(); ++idx) {
                if (inst.isUse(idx)) {
                    if (state.queryRegisterLatency(inst, idx) > 2) {
                        return false;
                    }
                }
            }
            state.setIssued(RISCVIssueMask.RISCVPipelineB.mask);
            return true;
        }
    }

    static class RISCVScheduleClassLoadStore extends ScheduleClass {
        public boolean schedule(ScheduleState state, RiscvInstruction inst) {
            if (!state.isAvailable(RISCVIssueMask.RISCVPipelineA.mask)) {
                return false;
            }
            for (int idx = 0; idx < inst.getOperandNum(); ++idx) {
                if (inst.isUse(idx)) {
                    if (state.queryRegisterLatency(inst, idx) > 0) {
                        return false;
                    }
                }
            }

            if (inst.isDef(0)) {
                state.makeRegisterReady(inst, 0, 3);
            }

            state.setIssued(RISCVIssueMask.RISCVPipelineA.mask);
            return true;
        }
    }

    static class RISCVScheduleClassMulti extends ScheduleClass {
        public boolean schedule(ScheduleState state, RiscvInstruction inst) {
            if (!state.isAvailable(RISCVIssueMask.RISCVPipelineB.mask)) {
                return false;
            }
            for (int idx = 0; idx < inst.getOperandNum(); ++idx) {
                if (inst.isUse(idx)) {
                    if (state.queryRegisterLatency(inst, idx) > 0) {
                        return false;
                    }
                }
            }

            state.makeRegisterReady(inst, 0, 3);
            state.setIssued(RISCVIssueMask.RISCVPipelineB.mask);
            return true;
        }
    }

    static class RISCVScheduleClassDivRem extends ScheduleClass {
        public boolean schedule(ScheduleState state, RiscvInstruction inst) {
            if (!state.isAvailable(RISCVIssueMask.RISCVPipelineB.mask)) {
                return false;
            }
            if (!state.isPipelineReady(RISCVIDivPipeline)) {
                return false;
            }

            for (int idx = 0; idx < inst.getOperandNum(); ++idx) {
                if (inst.isUse(idx)) {
                    if (state.queryRegisterLatency(inst, idx) > 0) {
                        return false;
                    }
                }
            }

            state.resetPipeline(RISCVIDivPipeline, 65);
            state.makeRegisterReady(inst, 0, 68);
            state.setIssued(RISCVIssueMask.RISCVPipelineB.mask);
            return true;
        }
    }

    static class RISCVScheduleClassFP extends ScheduleClass {

        public int latency;

        public RISCVScheduleClassFP(int latency) {
            this.latency = latency;
        }

        public boolean schedule(ScheduleState state, RiscvInstruction inst) {
            if (!state.isAvailable(RISCVIssueMask.RISCVPipelineB.mask)) {
                return false;
            }
            for (int idx = 0; idx < inst.getOperandNum(); ++idx) {
                if (inst.isUse(idx)) {
                    if (state.queryRegisterLatency(inst, idx) > 0) {
                        return false;
                    }
                }
            }
            state.makeRegisterReady(inst, 0, latency);
            state.setIssued(RISCVIssueMask.RISCVPipelineB.mask);
            return true;
        }
    }

    static class RISCVScheduleClassFPCycle1 extends RISCVScheduleClassFP {
        public RISCVScheduleClassFPCycle1() {
            super(1);
        }
    }

    static class RISCVScheduleClassFPCycle2 extends RISCVScheduleClassFP {
        public RISCVScheduleClassFPCycle2() {
            super(2);
        }
    }

    static class RISCVScheduleClassFPCycle4 extends RISCVScheduleClassFP {
        public RISCVScheduleClassFPCycle4() {
            super(4);
        }
    }

    static class RISCVScheduleClassFPCycle5 extends RISCVScheduleClassFP {
        public RISCVScheduleClassFPCycle5() {
            super(5);
        }
    }

    static class RISCVScheduleClassFPDiv extends ScheduleClass {
        public boolean schedule(ScheduleState state, RiscvInstruction inst) {
            if (!state.isAvailable(RISCVIssueMask.RISCVPipelineB.mask)) {
                return false;
            }
            if (!state.isPipelineReady(RISCVFPDivPipeline)) {
                return false;
            }

            for (int idx = 0; idx < inst.getOperandNum(); ++idx) {
                if (inst.isUse(idx)) {
                    if (state.queryRegisterLatency(inst, idx) > 0) {
                        return false;
                    }
                }
            }

            state.resetPipeline(RISCVFPDivPipeline, 33);
            state.makeRegisterReady(inst, 0, 36);
            state.setIssued(RISCVIssueMask.RISCVPipelineB.mask);
            return true;
        }
    }

    static class RISCVScheduleClassFPLoadStore extends ScheduleClass {
        public boolean schedule(ScheduleState state, RiscvInstruction inst) {
            if (!state.isAvailable(RISCVIssueMask.RISCVPipelineA.mask)) {
                return false;
            }
            for (int idx = 0; idx < inst.getOperandNum(); ++idx) {
                if (inst.isUse(idx)) {
                    if (state.queryRegisterLatency(inst, idx) > 0) {
                        return false;
                    }
                }
            }

            if (inst.isDef(0)) {
                state.makeRegisterReady(inst, 0, 2);
            }

            state.setIssued(RISCVIssueMask.RISCVPipelineA.mask);
            return true;
        }
    }

    static class RISCVScheduleClassSlowLoadImm extends ScheduleClass {
        @Override
        public boolean schedule(ScheduleState state, RiscvInstruction inst) {
            assert inst instanceof Li;
            Integer imm = ((Li) inst).imm;
            if (imm < 2048 && imm > -2048) {
                state.makeRegisterReady(inst, 0, 1);
            } else {
                // LUI + ADDI
                state.makeRegisterReady(inst, 0, 3);
            }
            return true;
        }
    }

    static class RISCVScheduleClassLa extends ScheduleClass {
        @Override
        public boolean schedule(ScheduleState state, RiscvInstruction inst) {
            assert inst instanceof La;
            state.makeRegisterReady(inst, 0, 3);
            return true;
        }
    }

    static class RISCVScheduleClassGeneralLoad extends ScheduleClass {
        private final RISCVScheduleClassLoadStore load = new RISCVScheduleClassLoadStore();
        private final RISCVScheduleClassFPLoadStore fpLoad = new RISCVScheduleClassFPLoadStore();

        public boolean schedule(ScheduleState state, RiscvInstruction inst) {
            if (isOperandGPR(inst.getRegByIdx(0))) {
                return load.schedule(state, inst);
            }
            return fpLoad.schedule(state, inst);
        }

        private boolean isOperandGPR(Reg reg) {
            return reg.regType == Reg.RegType.GPR;
        }
    }

    public static ScheduleClass getInstScheduleClass(RiscvInstruction instr) {
        if (instr instanceof B || instr instanceof J) {
            return new RISCVScheduleClassBranch();
        } else if (instr instanceof Li) {
            return new RISCVScheduleClassSlowLoadImm();
        } else if (instr instanceof La) {
            return new RISCVScheduleClassLa();
        } else if (instr instanceof LS) {
            return new RISCVScheduleClassGeneralLoad();
        } else if (instr instanceof R2 r2) {
            switch (r2.type) {
                case mv, sgtz, seqz, snez -> {
                    return new RISCVScheduleClassIntegerArithmetic();
                }
                case fmvxs -> {
                    return new RISCVScheduleClassFPCycle1();
                }
                case fmv, fabs, fneg, fmvsx, fcvtws, fcvtsw -> {
                    return new RISCVScheduleClassFPCycle2();
                }
                default -> throw new RuntimeException("wrong type");
            }
        } else if (instr instanceof R3 r3) {
            switch (r3.type) {
                case add, addi, addw, addiw, subw, andw, andiw, orw, oriw, xorw,
                        xoriw, sllw, slliw, sraw, sraiw, srlw, srliw, slt, slti, min, max -> {
                    return new RISCVScheduleClassIntegerArithmetic();
                }
                case sh1add, sh2add, sh3add -> {
                    return new RISCVScheduleClassIntegerArithmeticEarlyLateB();
                }
                case mulw -> {
                    return new RISCVScheduleClassMulti();
                }
                case divw, remw -> {
                    return new RISCVScheduleClassDivRem();
                }
                case fdiv -> {
                    return new RISCVScheduleClassFPDiv();
                }
                case feq, fle, flt -> {
                    return new RISCVScheduleClassFPCycle4();
                }
                case fadd, fsub, fmul -> {
                    return new RISCVScheduleClassFPCycle5();
                }
                default -> throw new RuntimeException("wrong type");
            }
        } else {
            System.out.println(instr);
            throw new RuntimeException("未知指令!" + instr);
        }
    }
}
