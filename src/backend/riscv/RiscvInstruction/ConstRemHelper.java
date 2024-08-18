package backend.riscv.RiscvInstruction;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;

import java.util.HashSet;

public class ConstRemHelper extends RiscvInstruction {
    public Reg src;
    public Reg reg;
    public int imm;

    @Override
    public int getInstFlag() {
        return 0;
    }

    public ConstRemHelper(RiscvBlock block, Reg src, Reg reg, int imm) {
        super(block);
        this.src = src;
        this.reg = reg;
        this.imm = imm;
    }

    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (src.equals(oldReg)) src = newReg;
        if (reg.equals(oldReg)) reg = newReg;
        super.updateUseDef();
    }

    @Override
    public int getOperandNum() {
        return 2;
    }

    @Override
    public boolean isUse(int idx) {
        return idx == 1;
    }

    @Override
    public Reg getRegByIdx(int idx) {
        return idx == 0 ? reg : src;
    }

    @Override
    public boolean isDef(int idx) {
        return idx == 0;
    }

    @Override
    public HashSet<Reg> getDef() {
        HashSet<Reg> tmp = new HashSet<>();
        tmp.add(reg);
        return tmp;
    }

    @Override
    public HashSet<Reg> getUse() {
        HashSet<Reg> tmp = new HashSet<>();
        tmp.add(src);
        return tmp;
    }

    @Override
    public HashSet<Reg> getReg() {
        HashSet<Reg> tmp = new HashSet<>();
        tmp.add(src);
        tmp.add(reg);
        return tmp;
    }

    @Override
    public RiscvInstruction myCopy(RiscvBlock newBlock) {
        return new ConstRemHelper(block, src, reg, imm);
    }

    @Override
    public String toString() {
        return "\tcrh\t"  + " " + src + " " + reg + " " + imm;
    }
}
