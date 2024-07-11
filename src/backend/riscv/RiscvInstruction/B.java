package backend.riscv.RiscvInstruction;

import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;

public class B extends RiscvInstruction {

    public BType type;

    // 默认概率对半开
    private double yesProb = 0.8;

    public double getYesProb() {
        return yesProb;
    }

    /**
     * 注意，这里只能整数和整数比较
     * 函数调用的参数传递，维护数据流关系
     */

    public enum BType {
        beq, bge, bgt, ble, blt, bne;

        @Override
        public String toString() {
            return switch (this) {
                case beq -> "beq";
                case bge -> "bge";
                case bgt -> "bgt";
                case ble -> "ble";
                case blt -> "blt";
                case bne -> "bne";
                default -> throw new AssertionError();
            };
        }
    }

    public void inverse() {
        type = switch (type) {
            case beq -> BType.bne;
            case bne -> BType.beq;
            case bge -> BType.blt;
            case blt -> BType.bge;
            case ble -> BType.bgt;
            case bgt -> BType.ble;
            default -> throw new RuntimeException("wrong type");
        };
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

    @Override
    public int getOperandNum() {
        return 2;
    }

    @Override
    public boolean isDef(int idx) {
        return false;
    }

    @Override
    public boolean isUse(int idx) {
        return true;
    }

    @Override
    public Reg getRegByIdx(int idx) {
        return idx == 0 ? (Reg) rs1 : (Reg) rs2;
    }

    @Override
    public int getInstFlag() {
        return InstFlag.None.value |
                InstFlag.Terminator.value |
                InstFlag.Branch.value;
    }
}
