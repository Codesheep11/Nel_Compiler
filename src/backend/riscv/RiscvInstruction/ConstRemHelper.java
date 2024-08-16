package backend.riscv.RiscvInstruction;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;

import java.util.HashSet;

public class ConstRemHelper extends RiscvInstruction {
    public String name1;
    public String name2;
    public Reg src;
    public Reg reg;

    @Override
    public int getInstFlag() {
        return 0;
    }

    public ConstRemHelper(RiscvBlock block, String name1, String name2, Reg src, Reg reg) {
        super(block);
        this.name1 = name1;
        this.name2 = name2;
        this.src = src;
        this.reg = reg;
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
    public RiscvInstruction myCopy(RiscvBlock newBlock) {
        return new ConstRemHelper(block, name1, name2, src, reg);
    }

    @Override
    public String toString() {
        return "\tcrh\t" + name1 + " " + name2 + " " + src + " " + reg;
    }
}
