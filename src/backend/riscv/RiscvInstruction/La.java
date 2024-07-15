package backend.riscv.RiscvInstruction;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvGlobalVar;

import java.util.HashSet;

public class La extends RiscvInstruction {

    // 所取的全局变量的地址
    public RiscvGlobalVar content;

    public Reg reg;

    public La(RiscvBlock block, Reg reg, RiscvGlobalVar rb) {
        super(block);
        this.content = rb;
        this.reg = reg;
    }

    @Override
    public String toString() {
        return "\tlla\t\t" + reg + ", " + content.name;
    }

    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (reg == oldReg) reg = newReg;

        super.updateUseDef();

    }

    @Override
    public HashSet<Reg> getUse() {
        return new HashSet<>();
    }

    @Override
    public HashSet<Reg> getDef() {
        HashSet def = new HashSet<>();
        def.add(reg);
        return def;
    }

    @Override
    public int getOperandNum() {
        return 1;
    }

    @Override
    public boolean isDef(int idx) {
        return true;
    }

    @Override
    public boolean isUse(int idx) {
        return false;
    }

    @Override
    public Reg getRegByIdx(int idx) {
        return reg;
    }

    @Override
    public int getInstFlag() {
        return InstFlag.None.value | InstFlag.LoadConstant.value | InstFlag.PCRel.value;
    }
}
