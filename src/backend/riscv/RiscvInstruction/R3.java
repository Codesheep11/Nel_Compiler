package backend.riscv.RiscvInstruction;

import backend.operand.Address;
import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;

import java.util.HashSet;

public class R3 extends RiscvInstruction {
    public R3Type type;

    public enum R3Type {
        add, sub, addi, addw, addiw, subw, divw, mulw, remw, and, andi, or, ori, xorw, xoriw, sllw, slliw, sraw, sraiw, srlw, srliw, slt, slti,
        fadd, fsub, fmul, fdiv, min, max, feq, fle, flt, mul, rem, srai, srli, div, slli, xori,fmin,fmax,
        sh1add, sh2add, sh3add, adduw;// 这三个的最后一个参数才是需要位移的

        @Override
        public String toString() {
            switch (this) {
                case add -> {
                    return "add";
                }
                case sub -> {
                    return "sub";
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
                case div -> {
                    return "div";
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
                case and -> {
                    return "and";
                }
                case xori -> {
                    return "xori";
                }
                // 字与
                case andi -> {
                    return "andi";
                }
                // 与立即数,注意范围限制
                case or -> {
                    return "or";
                }
                // 或字
                case ori -> {
                    return "ori";
                }
                // 或立即数
                case xorw -> {
                    return "xor";
                }
                // 异或,现在看来不需要
                case xoriw -> {
                    return "xori";
                }
                // 异或,现在看来不需要
                case sllw -> {
                    return "sllw";
                }
                // 字左移
                case slliw -> {
                    return "slliw";
                }
                case slli -> {
                    return "slli";
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
                case min -> {
                    return "min";
                }
                case max -> {
                    return "max";
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
                case sh1add -> {
                    return "sh1add";
                }
                case sh2add -> {
                    return "sh2add";
                }
                case sh3add -> {
                    return "sh3add";
                }
                case mul -> {
                    return "mul";
                }
                case srai -> {
                    return "srai";
                }
                case srli -> {
                    return "srli";
                }
                case rem -> {
                    return "rem";
                }
                case adduw -> {
                    return "add.uw";
                }
                case fmax -> {
                    return "fmax.s";
                }
                case fmin -> {
                    return "fmin.s";
                }
                default -> throw new AssertionError();
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
    }

    @Override
    public String toString() {
        if (type.toString().length() > 3) {
            return "\t" + type + "\t" + rd + ", " + rs1 + ", " + rs2;
        }
        return "\t" + type + "\t\t" + rd + ", " + rs1 + ", " + rs2;
    }

    @Override
    public HashSet<Reg> getDef() {
        HashSet<Reg> def = new HashSet<>();
        if (rd instanceof Reg) def.add((Reg) rd);
        return def;
    }

    @Override
    public HashSet<Reg> getUse() {
        HashSet<Reg> use = new HashSet<>();
        if (rs1 instanceof Reg) use.add((Reg) rs1);
        if (rs2 instanceof Reg) use.add((Reg) rs2);
        return use;
    }

    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (rs1 == oldReg) rs1 = newReg;
        if (rs2 == oldReg) rs2 = newReg;
        if (rd == oldReg) rd = newReg;

        super.updateUseDef();
    }

    @Override
    public int getOperandNum() {
        return rs2 instanceof Reg ? 3 : 2;
    }

    @Override
    public boolean isDef(int idx) {
        return idx == 0;
    }

    @Override
    public boolean isUse(int idx) {
        return idx != 0;
    }

    @Override
    public Reg getRegByIdx(int idx) {
        return idx == 0 ? (Reg) rd : (idx == 1 ? (Reg) rs1 : (Reg) rs2);
    }

    @Override
    public int getInstFlag() {
        return InstFlag.None.value;
    }

    @Override
    public RiscvInstruction myCopy(RiscvBlock newBlock) {
        return new R3(newBlock, rd, rs1, rs2, type);
    }

    public void replaceMe() {
        if (type != R3Type.addi && type != R3Type.addiw) return;
        if (rs2 instanceof Address add) {
            if (add.getOffset() >= 2048 || add.getOffset() <= -2048) {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
                Li li = new Li(block, tmp, add);
                block.insertInstBefore(li, this);
                this.rs2 = tmp;
                this.type = type == R3Type.addi ? R3Type.add : R3Type.addw;
            }
        }
    }
}
