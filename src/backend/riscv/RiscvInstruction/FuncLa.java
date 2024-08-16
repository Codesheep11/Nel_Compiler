package backend.riscv.RiscvInstruction;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;

import java.util.HashSet;

public class FuncLa extends RiscvInstruction {

    // 所取函数的地址
    public final String content;

    public Reg reg;

    public FuncLa(RiscvBlock block, Reg reg, String function) {
        super(block);
        this.content = function;
        this.reg = reg;
    }

    @Override
    public String toString() {
        return "\tlla\t\t" + reg + ", " + content;
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
        return new FuncLa(newBlock, reg, content);
    }
}
