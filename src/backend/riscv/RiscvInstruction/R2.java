package backend.riscv.RiscvInstruction;

import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;

import java.util.HashSet;

public class R2 extends RiscvInstruction {
    public final R2Type type;

    public enum R2Type {
        mv, fmv, fabs, fneg, fmvwx, fmvxw, fcvtws, fcvtsw, sgtz, seqz, snez, sextw;


        @Override
        public String toString() {
            switch (this) {
                case mv -> {
                    return "mv";
                }
                // move 一个整数移动到另一个整数

                case fmv -> {
                    return "fmv.s";
                }
                // fmove 一个浮点数移动到另一个浮点数

                case fabs -> {
                    return "fabs.s";
                }
                // fabs 浮点数的绝对值,现在看来不需要

                case fneg -> {
                    return "fneg.s";
                }
                // f negative 浮点数取反

                case fmvxw -> {
                    return "fmv.x.w";
                }
                // f 单精度移动到整数(单纯的bits移动)

                case fmvwx -> {
                    return "fmv.w.x";
                }
                // 整数移动到单精度(单纯的bits移动)

                case fcvtws -> {
                    return "fcvt.w.s";
                }
                // 将单精度转化为整数,这个的意义是从数值上转化,需要加上rtz才是像0舍入(和c语言一样)

                case fcvtsw -> {
                    return "fcvt.s.w";
                }
                // 将整数转化为单精度,这个的意义是从数值上转化，例如5->5.0

                case sgtz -> {
                    return "sgtz";
                }
                // 大于0则置位,要求:必须是整数
                case seqz -> {
                    return "seqz";
                }
                case snez -> {
                    return "snez";
                }
                case sextw -> {
                    return "sext.w";
                }
                default -> throw new AssertionError();
            }
        }
    }

    public Operand rd, rs;

    public R2(RiscvBlock block, Operand rd, Operand rs, R2Type type) {
        super(block);
        this.rd = rd;
        this.rs = rs;
        this.type = type;
    }

    @Override
    public HashSet<Reg> getUse() {
        HashSet<Reg> use = new HashSet<>();
        if (rs instanceof Reg) use.add((Reg) rs);
        return use;
    }

    @Override
    public HashSet<Reg> getDef() {
        HashSet<Reg> def = new HashSet<>();
        if (rd instanceof Reg) def.add((Reg) rd);
        return def;
    }

    @Override
    public String toString() {
//        if (type == R2Type.mv) {
//            return "\tori\t" + rd + ",\t" + rs + ",\t0";
//        }
        String app = type == R2Type.fcvtws ? ", rtz " : "";
        if (type.toString().length() > 3) {
            return "\t" + type + "\t" + rd + ", " + rs + app;
        }
        return "\t" + type + "\t\t" + rd + ", " + rs + app;
    }


    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (rs == oldReg) rs = newReg;
        if (rd == oldReg) rd = newReg;

        super.updateUseDef();
    }

    @Override
    public int getOperandNum() {
        return 2;
    }


    @Override
    public boolean isUse(int idx) {
        return idx == 1;
    }

    @Override
    public boolean isDef(int idx) {
        return idx == 0;
    }

    @Override
    public Reg getRegByIdx(int idx) {
        return idx == 0 ? (Reg) rd : (Reg) rs;
    }

    @Override
    public int getInstFlag() {
        if (type == R2Type.mv || type == R2Type.fmv) {
            return InstFlag.None.value | InstFlag.RegCopy.value;
        }
        return InstFlag.None.value;
    }

    @Override
    public RiscvInstruction myCopy(RiscvBlock newBlock) {
        return new R2(newBlock, rd, rs, type);
    }
}
