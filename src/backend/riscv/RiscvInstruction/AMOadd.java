package backend.riscv.RiscvInstruction;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;

import java.util.HashSet;

public class AMOadd extends RiscvInstruction {
    public Reg val;
    public Reg base;
    public Reg rd;

    public AMOadd(RiscvBlock block, Reg rd, Reg val, Reg base) {
        super(block);
        this.val = val;
        this.base = base;
        this.rd = rd;
    }

    @Override
    public String toString() {
        return "\tamoadd.w.aqrl\t" + rd + ", " + val + ", (" + base + ")";
    }

    @Override
    public HashSet<Reg> getDef() {
        HashSet<Reg> def = new HashSet<>();
        def.add(rd);
        return def;
    }

    @Override
    public HashSet<Reg> getUse() {
        HashSet<Reg> use = new HashSet<>();
        use.add(val);
        use.add(base);
        return use;
    }

    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (val == oldReg) val = newReg;
        if (base == oldReg) base = newReg;
        if (rd == oldReg) rd = newReg;

        super.updateUseDef();
    }

    @Override
    public int getOperandNum() {
        return 3;
    }

    @Override
    public boolean isDef(int idx) {
        return idx == 0;
    }

    @Override
    public boolean isUse(int idx) {
        return idx != 0;
    }

    @Override
    public Reg getRegByIdx(int idx) {
        return idx == 0 ? rd : (idx == 1 ? base : val);
    }

    @Override
    public int getInstFlag() {
        return InstFlag.None.value;
    }

    @Override
    public RiscvInstruction myCopy(RiscvBlock newBlock) {
        return new AMOadd(newBlock, rd, val, base);
    }

}
