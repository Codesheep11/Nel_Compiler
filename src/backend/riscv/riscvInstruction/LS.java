package backend.riscv.riscvInstruction;

import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.riscvBlock;

public class LS extends riscvInstruction {
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
                default -> {
                    throw new AssertionError();
                }
            }
        }
    }

    public Operand rs1, rs2, imm;


    //标记是否是因为寄存器分配阶段由于寄存器溢出而产生的访存指令
    public boolean isSpilled = false;

    public LS(riscvBlock block, Operand rs1, Operand rs2, Operand imm, LSType type) {
        super(block);
        this.rs1 = rs1;
        this.rs2 = rs2;
        this.imm = imm;
        this.type = type;
        if (rs2 instanceof Reg)
            use.add((Reg) rs2);
        if (type == LSType.lw || type == LSType.ld || type == LSType.flw) {
            if (rs1 instanceof Reg)
                def.add((Reg) rs1);
        }
        else {
            if (rs1 instanceof Reg)
                use.add((Reg) rs1);
        }
        rs1.use.add(this);
        rs2.use.add(this);
        imm.use.add(this);
    }

    public LS(riscvBlock block, Operand rs1, Operand rs2, Operand imm, LSType type, boolean isSpilled) {
        super(block);
        this.rs1 = rs1;
        this.rs2 = rs2;
        this.imm = imm;
        this.type = type;
        if (rs2 instanceof Reg)
            use.add((Reg) rs2);
        if (type == LSType.lw || type == LSType.ld || type == LSType.flw) {
            if (rs1 instanceof Reg)
                def.add((Reg) rs1);
        }
        else {
            if (rs1 instanceof Reg)
                use.add((Reg) rs1);
        }
        rs1.use.add(this);
        rs2.use.add(this);
        imm.use.add(this);
        this.isSpilled = isSpilled;
    }

    @Override
    public String toString() {
        if (imm instanceof Address) {
            if (((Address) imm).getOffset() >= 2048 || ((Address) imm).getOffset() <= -2048) {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                return "\tli" + "\t\t" + tmp + ", " + -1 * ((Address) imm).getOffset() + "\n" +
                        "\tadd\t\t" + tmp + ", " + tmp + ", " + rs2 + "\n" +
                        "\t" + type + "\t\t" + rs1 + ", 0(" + tmp + ")";
            }
        }
        return "\t" + type + "\t\t" + rs1 + ", " + imm + "(" + rs2 + ")";
    }


    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (this.type == LSType.lw || this.type == LSType.ld || this.type == LSType.flw) {
            if (use.contains(oldReg)) {
                if (rs2 == oldReg) {
                    rs2 = newReg;
                }
                use.remove(oldReg);
                use.add(newReg);
            }
            if (def.contains(oldReg)) {
                if (rs1 == oldReg) {
                    rs1 = newReg;
                }
                def.remove(oldReg);
                def.add(newReg);
            }
        }
        else {
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
        }
    }
}
