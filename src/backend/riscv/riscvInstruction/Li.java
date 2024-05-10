package backend.riscv.riscvInstruction;

import backend.operand.Address;
import backend.operand.Reg;
import backend.riscv.riscvBlock;

public class Li extends riscvInstruction {

    public Integer imm;

    public Address addr;

    public Reg reg;

    public Li(riscvBlock block, Reg reg, Integer value) {
        super(block);
        this.imm = value;
        this.reg = reg;
        def.add(reg);
        reg.use.add(this);
    }

    public Li(riscvBlock block, Reg reg, Address addr) {
        super(block);
        this.addr = addr;
        this.reg = reg;
        def.add(reg);
        reg.use.add(this);

    }

    @Override
    public String toString() {
        if (addr != null)
            return "\tli" + "\t\t" + reg + ", " + addr;
        else
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
}
