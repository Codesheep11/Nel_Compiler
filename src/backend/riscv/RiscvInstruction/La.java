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
        this.reg.temp = true;
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
        return super.getUse();
    }

    @Override
    public HashSet<Reg> getDef() {
        HashSet<Reg> def = new HashSet<>();
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
        return super.isUse(idx);
    }

    @Override
    public Reg getRegByIdx(int idx) {
        return reg;
    }

    @Override
    public int getInstFlag() {
        return InstFlag.None.value | InstFlag.LoadConstant.value | InstFlag.PCRel.value;
    }

    @Override
    public RiscvInstruction myCopy(RiscvBlock newBlock) {
        return new La(newBlock, reg, content);
    }
}
