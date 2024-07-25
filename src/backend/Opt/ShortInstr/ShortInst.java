package backend.Opt.ShortInstr;

import backend.riscv.RiscvBlock;
import backend.riscv.RiscvInstruction.RiscvInstruction;

public class ShortInst extends RiscvInstruction {
    public ShortInst(RiscvBlock block) {
        super(block);
    }

    public class ShortAdd extends ShortInst
    {

        public ShortAdd(RiscvBlock block) {
            super(block);
        }
    }

    public class ShortMove extends ShortInst {
        public ShortMove(RiscvBlock block) {
            super(block);
        }
    }
}
