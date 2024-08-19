package backend.operand;

import backend.allocator.LivenessAnalyze;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import manager.Manager;

import java.util.HashMap;
import java.util.HashSet;

public class Reg extends Operand {
    public static int Cnt = 0;

    public final int regCnt;

    public final int bits;

    public boolean temp = false;//是否是来自lla或者li或者flw的寄存器Constant

    public enum PhyReg {
        zero, ra, sp, gp, tp, t0, t1, t2, s0, s1, a0, a1, a2, a3, a4, a5, a6, a7, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, t3, t4, t5, t6,
        ft0, ft1, ft2, ft3, ft4, ft5, ft6, ft7, fs0, fs1, fa0, fa1, fa2, fa3, fa4, fa5, fa6, fa7, fs2, fs3, fs4, fs5, fs6, fs7, fs8, fs9, fs10, fs11, ft8, ft9, ft10, ft11;

        @Override
        public String toString() {
            if (this.ordinal() == 0) {
                return "zero";
            }
            else if (this.ordinal() == 1) {
                return "ra";
            }
            else if (this.ordinal() == 2) {
                return "sp";
            }
            else if (this.ordinal() == 3) {
                return "gp";
            }
            else if (this.ordinal() == 4) {
                return "tp";
            }
            else if (this.ordinal() >= 5 && this.ordinal() <= 7) {
                return "t" + (this.ordinal() - 5);
            }
            else if (this.ordinal() >= 8 && this.ordinal() <= 9) {
                return "s" + (this.ordinal() - 8);
            }
            else if (this.ordinal() >= 10 && this.ordinal() <= 17) {
                return "a" + (this.ordinal() - 10);
            }
            else if (this.ordinal() >= 18 && this.ordinal() <= 27) {
                return "s" + (this.ordinal() - 16);
            }
            else if (this.ordinal() >= 28 && this.ordinal() <= 31) {
                return "t" + (this.ordinal() - 28 + 3);
            }
            else if (this.ordinal() >= 32 && this.ordinal() <= 39) {
                return "ft" + (this.ordinal() - 32);
            }
            else if (this.ordinal() >= 40 && this.ordinal() <= 41) {
                return "fs" + (this.ordinal() - 40);
            }
            else if (this.ordinal() >= 42 && this.ordinal() <= 49) {
                return "fa" + (this.ordinal() - 42);
            }
            else if (this.ordinal() >= 50 && this.ordinal() <= 59) {
                return "fs" + (this.ordinal() - 48);
            }
            else if (this.ordinal() >= 60 && this.ordinal() <= 63) {
                return "ft" + (this.ordinal() - 60 + 8);
            }
            else {
                return "error";
            }
        }


        public static int getOrder(PhyReg reg) {
            return reg.ordinal();
        }

        public static PhyReg getPhyRegByOrder(int order) {
            return PhyReg.values()[order];
        }

    }

    public enum RegType {
        GPR, FPR
    }

    public PhyReg phyReg = null;

    public final RegType regType;

    public boolean preColored = false;

    public Reg(RegType regType, int bits) {
        regCnt = Cnt++;
        this.regType = regType;
        this.bits = bits;
    }

    private Reg(PhyReg phyReg, int bits) {
        regCnt = Cnt++;
        this.phyReg = phyReg;
        this.preColored = true;
        this.bits = bits;
        if (phyReg.ordinal() >= 0 && phyReg.ordinal() <= 31) {
            this.regType = RegType.GPR;
        }
        else {
            this.regType = RegType.FPR;
        }
    }

    private static final HashMap<String, Reg> preColoredRegs = new HashMap<>();

    public static void initPreColoredRegs() {
        for (int i = 0; i < 64; i++) {
            preColoredRegs.put(PhyReg.getPhyRegByOrder(i).toString() + 32, new Reg(PhyReg.getPhyRegByOrder(i), 32));
        }
        for (int i = 0; i < 32; i++) {
            preColoredRegs.put(PhyReg.getPhyRegByOrder(i).toString() + 64, new Reg(PhyReg.getPhyRegByOrder(i), 64));
        }
    }

    /**
     * 申请预着色寄存器
     */
    public static Reg getPreColoredReg(PhyReg phyReg, int bits) {
        return preColoredRegs.get(phyReg.toString() + bits);
    }

    /**
     * 申请虚拟寄存器
     */
    public static Reg getVirtualReg(RegType regType, int bits) {
        return new Reg(regType, bits);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Reg) {
            return obj.hashCode() == hashCode();
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (preColored) return phyReg.ordinal();
        if (Manager.afterRegAssign) {
            assert phyReg != null;
            return phyReg.ordinal();
        }
        return regCnt + 64;
    }

    @Override
    public String toString() {
        if (phyReg != null) {
            //已经映射了对应的物理寄存器
            return phyReg.toString();
        }
        else if (regType == RegType.GPR) {
            return "gvr" + regCnt;
        }
        else {
            return "fvr" + regCnt;
        }
    }

    public void mergeReg(Reg reg) {
        //对于所有使用reg的riscv指令，将其换成this
//        System.out.println(this+" "+ reg);
        HashSet<RiscvInstruction> tmp = new HashSet<>(LivenessAnalyze.RegUse.get(reg));
        for (RiscvInstruction ins : tmp) {
            ins.replaceUseReg(reg, this);
        }
        LivenessAnalyze.RegUse.remove(reg);
    }
}
