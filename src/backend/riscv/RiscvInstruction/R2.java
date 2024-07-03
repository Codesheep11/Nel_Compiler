package backend.riscv.RiscvInstruction;

import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;

public class R2 extends RiscvInstruction {
    public R2Type type;

    public enum R2Type {
        mv, fmv, fabs, fneg, fmvsx, fmvxs, fcvtws, fcvtsw, sgtz, seqz, snez;

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
                    return "fabs";
                }
                // fabs 浮点数的绝对值,现在看来不需要
                case fneg -> {
                    return "fneg";
                }
                // f negative 浮点数取反
                case fmvsx -> {
                    return "fmv.s.x";
                }
                // f 单精度移动到整数(单纯的bits移动)
                case fmvxs -> {
                    return "fmv.x.s";
                }
                // 整数移动到单精度(单纯的bits移动)
                case fcvtws -> {
                    return "fcvt.w.s";
                }
                // 将整数转化为单精度,这个的意义是从数值上转化，例如5->5.0
                case fcvtsw -> {
                    return "fcvt.s.w";
                }
                // 将单精度转化为整数,这个的意义是从数值上转化
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
                default -> {
                    throw new AssertionError();
                }
            }
        }
    }

    public Operand rd, rs;

    public R2(RiscvBlock block, Operand rd, Operand rs, R2Type type) {
        super(block);
        this.rd = rd;
        this.rs = rs;
        this.type = type;
        if (rd instanceof backend.operand.Reg)
            def.add((Reg) rd);
        if (rs instanceof backend.operand.Reg)
            use.add((Reg) rs);
        rd.use.add(this);
        rs.use.add(this);
    }

    @Override
    public String toString() {
        if (type.toString().length() > 3) {
            return "\t" + type + "\t" + rd + ", " + rs;
        }
        return "\t" + type + "\t\t" + rd + ", " + rs;
    }


    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (use.contains(oldReg)) {
            if (rs == oldReg) {
                rs = newReg;
            }
            use.remove(oldReg);
            use.add(newReg);
        }
        if (def.contains(oldReg)) {
            if (rd == oldReg) {
                rd = newReg;
            }
            def.remove(oldReg);
            def.add(newReg);
        }
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
}
