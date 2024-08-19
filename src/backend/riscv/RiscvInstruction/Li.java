package backend.riscv.RiscvInstruction;

import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;

import java.util.HashSet;

public class Li extends RiscvInstruction {

    public final Operand imm;


    public Reg reg;

    public Li(RiscvBlock block, Reg reg, Operand value) {
        super(block);
        this.imm = value;
        this.reg = reg;
        this.reg.temp = true;
    }

    @Override
    public String toString() {
        return "\tli" + "\t\t" + reg + ", " + imm;
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
    public boolean isUse(int idx) {
        return super.isUse(idx);
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
        return InstFlag.None.value | InstFlag.LoadConstant.value;
    }

    @Override
    public RiscvInstruction myCopy(RiscvBlock newBlock) {
        return new Li(newBlock, reg, imm);
    }

    public long getVal() {
        if (imm instanceof Address) {
            return -((Address) imm).getOffset();
        }
        else if (imm instanceof Imm) {
            return ((Imm) imm).getVal();
        }
        else throw new RuntimeException("wrong type");
    }
}
