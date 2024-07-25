package backend.Opt.ShortInstr;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvInstruction.RiscvInstruction;

public class ShortInst extends RiscvInstruction {
    // li,addi,sub,add,lwsp,swsp,lw,sw
    public ShortInst(RiscvBlock block) {
        super(block);
    }

    public static class ShortAdd extends ShortInst {
        private final Reg baseAddr;
        private final Reg addr;

        public ShortAdd(RiscvBlock block, Reg baseAddr, Reg addr) {
            super(block);
            this.baseAddr = baseAddr;
            this.addr = addr;
        }

        @Override
        public String toString() {
            return "\tc.add\t" + baseAddr + ",\t" + addr;
        }
    }

    public static class ShortMove extends ShortInst {
        Reg dst;
        Reg src;

        public ShortMove(RiscvBlock block, Reg dst, Reg src) {
            super(block);
            this.dst = dst;
            this.src = src;
        }

        @Override
        public String toString() {
            return "\tc.mv\t" + dst + ",\t" + src;
        }
    }



    public static class LwSp extends ShortInst {
        private final int offest;
        private final Reg rd;

        public LwSp(RiscvBlock block, Reg rd, int offest) {
            super(block);
            this.rd = rd;
            this.offest = offest;
        }

        @Override
        public String toString() {
            return "\tc.lwsp\t" + rd + ",\t" + offest  + "(sp)";
        }
    }

    public static class SwSp extends ShortInst {
        private final int offest;
        private final Reg rd;

        public SwSp(RiscvBlock block, Reg rd, int offest) {
            super(block);
            this.rd = rd;
            this.offest = offest;
        }

        @Override
        public String toString() {
            return "\tc.swsp\t" + rd + ",\t" + offest  + "(sp)";
        }

    }

    public static class ShortLi extends ShortInst {

        private final Reg reg;

        private final int imm;

        public ShortLi(RiscvBlock block, Reg reg, int imm) {
            super(block);
            this.reg = reg;
            this.imm = imm;
        }

        @Override
        public String toString() {
            return "\tc.li\t" + reg + ",\t" + imm;
        }
    }

    public static class ShortLw extends ShortInst {
        private final int offset;
        private final Reg base;
        private final Reg rd;

        public ShortLw(RiscvBlock block, Reg base, Reg reg, int offset) {
            super(block);
            this.base = base;
            this.rd = reg;
            this.offset = offset;
        }

        @Override
        public String toString() {
            return "\tc.lw\t" + rd + ",\t" + offset  + "(" + base + ")";
        }
    }

    public static class ShortSw extends ShortInst {
        private final int offset;
        private final Reg base;
        private final Reg rd;

        public ShortSw(RiscvBlock block, Reg base, Reg reg, int offset) {
            super(block);
            this.base = base;
            this.rd = reg;
            this.offset = offset;
        }

        @Override
        public String toString() {
            return "\tc.sw\t" + rd + ",\t" + offset  + "(" + base + ")";
        }
    }

    public static class ShortAddi extends ShortInst {
        private final Reg baseAddr;
        private final int imm;

        public ShortAddi(RiscvBlock block, Reg baseAddr, int imm) {
            super(block);
            this.baseAddr = baseAddr;
            this.imm = imm;
        }

        @Override
        public String toString() {
            return "\tc.addi\t" + baseAddr + ",\t" + imm;
        }
    }

    public static class ShortAddi16Sp extends ShortInst {
        private final int imm;

        public ShortAddi16Sp(RiscvBlock block, int imm) {
            super(block);
            this.imm = imm;
        }

        @Override
        public String toString() {
            return "\tc.addi16sp\tsp," + imm;
        }
    }

}