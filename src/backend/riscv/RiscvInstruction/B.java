package backend.riscv.RiscvInstruction;

import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;

public class B extends RiscvInstruction {

    public BType type;

    // 默认概率对半开
    private double yesProb = 0.5;

    public double getYesProb() {
        return yesProb;
    }

    /**
     * 注意，这里只能整数和整数比较
     * 函数调用的参数传递，维护数据流关系
     */

    public enum BType {
        beq, bge, bgt, ble, bleu, blt, bne;

        @Override
        public String toString() {
            switch (this) {
                case beq:
                    return "beq";
                case bge:
                    return "bge";
                case bgt:
                    return "bgt";
                case ble:
                    return "ble";
                case bleu:
                    return "bleu";
                case blt:
                    return "blt";
                case bne:
                    return "bne";
                default:
                    throw new AssertionError();
            }
        }
    }

    public Operand rs1, rs2;

    public RiscvBlock targetBlock;

    public B(RiscvBlock block, BType type, Operand rs1, Operand rs2, RiscvBlock targetBlock) {
        super(block);
        this.type = type;
        this.rs1 = rs1;
        this.rs2 = rs2;
        this.targetBlock = targetBlock;
        block.succBlock.add(targetBlock);
        targetBlock.preBlock.add(block);
        if (rs1 instanceof Reg)
            use.add((Reg) rs1);
        if (rs2 instanceof Reg)
            use.add((Reg) rs2);
        rs1.use.add(this);
        rs2.use.add(this);
    }

    @Override
    public String toString() {
        return "\t" + type + "\t\t" + rs1 + ", " + rs2 + ", " + targetBlock.name;
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
    }
}
