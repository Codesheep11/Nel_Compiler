package backend.riscv.RiscvInstruction;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;

public class J extends RiscvInstruction {

    public JType type;

    public enum JType {
        JAL, JALR, j, ret, call;

        @Override
        public String toString() {
            switch (this) {
                case JAL:
                    return "jal";
                case JALR:
                    return "jalr";
                case j:
                    return "j";
                case ret:
                    return "ret";
                case call:
                    return "call";
                default:
                    throw new AssertionError();
            }
        }
    }

    public RiscvBlock targetBlock;

    public String funcName;

    public J(RiscvBlock block, JType jType) {
        super(block);
        this.type = jType;
        //use.add(Reg.getPreColoredReg(Reg.PhyReg.ra, 64));
    }

    public J(RiscvBlock block, JType jType, RiscvBlock targetBlock) {
        super(block);
        this.type = jType;
        this.targetBlock = targetBlock;
        block.succBlock.add(targetBlock);
        targetBlock.preBlock.add(block);
    }

    public J(RiscvBlock block, JType jType, String funcName) {
        super(block);
        if (jType != JType.call) {
            throw new RuntimeException("not call but use funcName");
        }
        this.type = jType;
        this.funcName = funcName;
    }

    @Override
    public String toString() {
        if (type == JType.ret) {
            return "\t" + type.toString();
        }
        else if (type == JType.call) {
            return "\t" + type + "\t" + RiscvFunction.funcNameWrap(funcName);
        }
        else {
            return "\t" + type + "\t\t" + targetBlock.name;
        }
    }

    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        throw new RuntimeException("J instruction should not be replaced");
    }
}
