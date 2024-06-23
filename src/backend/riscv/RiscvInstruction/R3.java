package backend.riscv.RiscvInstruction;

import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;

public class R3 extends RiscvInstruction {
    public R3Type type;

    public enum R3Type {
        add, addi, addw, addiw, subw, divw, mulw, remw, andw, andiw, orw, oriw, xorw, xoriw, sllw, slliw, sraw, sraiw, srlw, srliw, slt, slti,
        fadd, fsub, fmul, fdiv, fmin, fmax, fsqrt, feq, fle, flt;

        @Override
        public String toString() {
            switch (this) {
                case add -> {
                    return "add";
                }
                // 用于地址
                case addi -> {
                    return "addi";
                }
                //用于地址
                case addw -> {
                    return "addw";
                }
                // 字加
                case addiw -> {
                    return "addiw";
                }
                // 字加立即数：**注意**范围：立即数不多于12位
                case subw -> {
                    return "subw";
                }
                // 字减
                case divw -> {
                    return "divw";
                }
                // 字除
                case mulw -> {
                    return "mulw";
                }
                // 字乘
                case remw -> {
                    return "remw";
                }
                // 字余
                case andw -> {
                    return "andw";
                }
                // 字与
                case andiw -> {
                    return "andiw";
                }
                // 与立即数,注意范围限制
                case orw -> {
                    return "orw";
                }
                // 或字
                case oriw -> {
                    return "oriw";
                }
                // 或立即数
                case xorw -> {
                    return "xorw";
                }
                // 异或,现在看来不需要
                case xoriw -> {
                    return "xoriw";
                }
                // 异或,现在看来不需要
                case sllw -> {
                    return "sllw";
                }
                // 字左移
                case slliw -> {
                    return "slliw";
                }
                // 左移立即数，注意范围
                case sraw -> {
                    return "sraw";
                }
                // 右移，注意是有符号扩展
                case sraiw -> {
                    return "sraiw";
                }
                //
                case srlw -> {
                    return "srlw";
                }
                case srliw -> {
                    return "srliw";
                }
                case slt -> {
                    return "slt";
                }
                case slti -> {
                    return "slti";
                }
                case fadd -> {
                    return "fadd.s";
                }
                case fsub -> {
                    return "fsub.s";
                }
                case fmul -> {
                    return "fmul.s";
                }
                case fdiv -> {
                    return "fdiv.s";
                }
                case fmin -> {
                    return "fmin.s";
                }
                case fmax -> {
                    return "fmax.s";
                }
                case fsqrt -> {
                    return "fsqrt.s";
                }
                case feq -> {
                    return "feq.s";
                }
                case fle -> {
                    return "fle.s";
                }
                case flt -> {
                    return "flt.s";
                }
                default -> {
                    throw new AssertionError();
                }
            }
        }
    }

    public Operand rd, rs1, rs2;

    public R3(RiscvBlock block, Operand rd, Operand rs1, Operand rs2, R3Type type) {
        super(block);
        this.rd = rd;
        this.rs1 = rs1;
        this.rs2 = rs2;
        this.type = type;
        if (rd instanceof Reg)
            def.add((Reg) rd);
        if (rs1 instanceof Reg)
            use.add((Reg) rs1);
        if (rs2 instanceof Reg)
            use.add((Reg) rs2);
        rd.use.add(this);
        rs1.use.add(this);
        rs2.use.add(this);
    }

    @Override
    public String toString() {
        if (type.toString().length() > 3) {
            return "\t" + type + "\t" + rd + ", " + rs1 + ", " + rs2;
        }
        return "\t" + type + "\t\t" + rd + ", " + rs1 + ", " + rs2;
    }


    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (use.contains(oldReg)) {
            if (rs1 == oldReg) {
                rs1 = newReg;
            }
            if (rs2 == oldReg) {
                rs2 = newReg;
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
}
