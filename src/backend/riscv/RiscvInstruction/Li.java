package backend.riscv.RiscvInstruction;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;

public class Li extends RiscvInstruction {

    public Integer imm;


    public Reg reg;

    public Li(RiscvBlock block, Reg reg, Integer value) {
        super(block);
        this.imm = value;
        this.reg = reg;
        def.add(reg);
        reg.use.add(this);
    }

    @Override
    public String toString() {
            return "\tli" + "\t\t" + reg + ", " + imm;
    }

    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (reg == oldReg) {
            reg = newReg;
        }
        def.remove(oldReg);
        def.add(newReg);
    }

    @Override
    public int getOperandNum() {
        return 1;
    }

    @Override
    public boolean isUse(int idx) {
        return false;
    }

    @Override
    public boolean isDef(int idx) {
        return true;
    }

    @Override
    public Reg getRegByIdx(int idx) {
        return reg;
    }

    @Override
    public int getInstFlag() {
        return InstFlag.None.value|InstFlag.LoadConstant.value;
    }
}
