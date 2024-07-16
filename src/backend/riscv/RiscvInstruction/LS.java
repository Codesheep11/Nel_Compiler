package backend.riscv.RiscvInstruction;

import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;

import java.util.HashSet;

public class LS extends RiscvInstruction {
    public LSType type;

    public enum LSType {
        lw, ld, sw, sd, flw, fsw;

        @Override
        public String toString() {
            switch (this) {
                case lw -> {
                    return "lw";
                }
                case ld -> {
                    return "ld";
                }
                case sw -> {
                    return "sw";
                }
                case sd -> {
                    return "sd";
                }
                case flw -> {
                    return "flw";
                }
                case fsw -> {
                    return "fsw";
                }
                default -> throw new AssertionError();
            }
        }
    }

    public Operand rs1, rs2, imm;


    //标记是否是因为寄存器分配阶段由于寄存器溢出而产生的访存指令
    public boolean isSpilled = false;

    public LS(RiscvBlock block, Operand rs1, Operand rs2, Operand imm, LSType type) {
        super(block);
        this.rs1 = rs1;
        this.rs2 = rs2;
        this.imm = imm;
        this.type = type;
    }

    public LS(RiscvBlock block, Operand rs1, Operand rs2, Operand imm, LSType type, boolean isSpilled) {
        super(block);
        this.rs1 = rs1;
        this.rs2 = rs2;
        this.imm = imm;
        this.type = type;
        this.isSpilled = isSpilled;
    }

    @Override
    public HashSet<Reg> getUse() {
        HashSet<Reg> use = new HashSet<>();
        if (rs2 instanceof Reg) use.add((Reg) rs2);
        if (type == LSType.sw || type == LSType.sd || type == LSType.fsw) {
            if (rs1 instanceof Reg) use.add((Reg) rs1);
        }
        return use;
    }

    @Override
    public HashSet<Reg> getDef() {
        HashSet<Reg> def = new HashSet<>();
        if (type == LSType.lw || type == LSType.ld || type == LSType.flw) {
            if (rs1 instanceof Reg) def.add((Reg) rs1);
        }
        return def;
    }

    @Override
    public String toString() {
        return "\t" + type + "\t\t" + rs1 + ", " + imm + "(" + rs2 + ")" + (isSpilled ? " #spilled" : "");
    }

    public void replaceMe(RiscvBlock nowBlock) {
        if (imm instanceof Address) {
            if (((Address) imm).getOffset() >= 2048 || ((Address) imm).getOffset() <= -2048) {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Li li = new Li(nowBlock, tmp, -1 * ((Address) imm).getOffset());
                nowBlock.riscvInstructions.insertBefore(li, this);
                R3 add = new R3(nowBlock, tmp, tmp, rs2, R3.R3Type.add);
                nowBlock.riscvInstructions.insertBefore(add, this);
                this.imm = new Imm(0);
                this.rs2 = tmp;
            }
        }
    }


    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (rs2 == oldReg) rs2 = newReg;
        if (rs1 == oldReg) rs1 = newReg;

        super.updateUseDef();
    }


    @Override
    public int getOperandNum() {
        return 2;
    }

    @Override
    public boolean isDef(int idx) {
        if (type == LSType.sd || type == LSType.sw || type == LSType.fsw) {
            return false;
        }
        return idx == 0;
    }

    @Override
    public boolean isUse(int idx) {
        if (type == LSType.sd || type == LSType.sw || type == LSType.fsw) {
            return true;
        }
        return idx == 1;
    }

    @Override
    public Reg getRegByIdx(int idx) {
        return idx == 0 ? (Reg) rs1 : (Reg) rs2;
    }

    @Override
    public int getInstFlag() {
        switch (type) {
            case ld, lw, flw -> {
                return InstFlag.None.value |
                        InstFlag.Load.value;
            }
            case sd, sw, fsw -> {
                return InstFlag.None.value |
                        InstFlag.Store.value;
            }
            default -> throw new RuntimeException("wrong type");
        }
    }

    @Override
    public RiscvInstruction myCopy() {
        return new LS(block, rs1, rs2, imm, type, isSpilled);
    }
}
