package backend.riscv.riscvInstruction;

import backend.operand.Reg;
import backend.riscv.*;

public class La extends riscvInstruction {

    // 所取的全局变量的地址
    public riscvGlobalVar content;

    public Reg reg;

    public La(riscvBlock block, Reg reg, riscvGlobalVar rb) {
        super(block);
        this.content = rb;
        this.reg = reg;
        def.add(reg);
        content.use.add(this);
        reg.use.add(this);
    }

    @Override
    public String toString() {
        return "\tla\t\t" + reg + ", " + content.name;
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
