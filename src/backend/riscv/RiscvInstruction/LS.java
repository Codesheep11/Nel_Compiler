package backend.riscv.RiscvInstruction;

import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import midend.Analysis.AlignmentAnalysis;

import java.util.HashSet;

public class LS extends RiscvInstruction {
    public final LSType type;

    public AlignmentAnalysis.AlignType align;
    // 该属性代表了基指针的对齐程度,是4还是8

    public enum LSType {
        lw, ld, sw, sd, flw, fsw;

        @Override
        public String toString() {
            switch (this) {
                case lw -> {
                    return "lw";
                }
                case ld -> {
                    return "ld";
                }
                case sw -> {
                    return "sw";
                }
                case sd -> {
                    return "sd";
                }
                case flw -> {
                    return "flw";
                }
                case fsw -> {
                    return "fsw";
                }
                default -> throw new AssertionError();
            }
        }
    }

    public Reg val, base;
    public Operand addr;


    //标记是否是因为寄存器分配阶段由于寄存器溢出而产生的访存指令
    public boolean isSpilled = false;

    public LS(RiscvBlock block, Reg val, Reg base, Operand addr, LSType type, AlignmentAnalysis.AlignType align) {
        super(block);
        this.val = val;
        this.base = base;
        this.addr = addr;
        this.type = type;
        this.align = align;
    }

    public LS(RiscvBlock block, Reg val, Reg base, Operand addr, LSType type, boolean isSpilled, AlignmentAnalysis.AlignType align) {
        super(block);
        this.val = val;
        this.base = base;
        this.addr = addr;
        this.type = type;
        this.isSpilled = isSpilled;
        this.align = align;
    }

    @Override
    public HashSet<Reg> getUse() {
        HashSet<Reg> use = new HashSet<>();
        if (base != null) use.add(base);
        if (type == LSType.sw || type == LSType.sd || type == LSType.fsw) {
            if (val != null) use.add(val);
        }
        return use;
    }

    @Override
    public HashSet<Reg> getDef() {
        HashSet<Reg> def = new HashSet<>();
        if (type == LSType.lw || type == LSType.ld || type == LSType.flw) {
            if (val != null) def.add(val);
        }
        return def;
    }

    @Override
    public String toString() {
        return "\t" + type + "\t\t" + val + ", " + addr + "(" + base + ")" + (isSpilled ? " #spilled" : "") ;
    }

    public void replaceMe() {
        if (addr instanceof Address address) {
            if (address.getOffset() >= 2048 || address.getOffset() <= -2048) {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Li li = new Li(block, tmp, addr);
                block.insertInstBefore(li, this);
                R3 add = new R3(block, tmp, base, tmp, R3.R3Type.add);
                block.insertInstBefore(add, this);
                this.addr = new Imm(0);
                this.base = tmp;
                if (align == AlignmentAnalysis.AlignType.ALIGN_BYTE_8) {
                    if (address.getOffset() % 8 != 0) {
                        align = AlignmentAnalysis.AlignType.ALIGN_BYTE_4;
                    }
                } else if (align == AlignmentAnalysis.AlignType.ALIGN_BYTE_4) {
                    if (address.getOffset() % 8 != 0) {
                        align = AlignmentAnalysis.AlignType.ALIGN_BYTE_8;
                    }
                }
            }
        }
    }


    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (base == oldReg) base = newReg;
        if (val == oldReg) val = newReg;

        super.updateUseDef();
    }


    @Override
    public int getOperandNum() {
        return 2;
    }

    @Override
    public boolean isDef(int idx) {
        if (type == LSType.sd || type == LSType.sw || type == LSType.fsw) {
            return false;
        }
        return idx == 0;
    }

    @Override
    public boolean isUse(int idx) {
        if (type == LSType.sd || type == LSType.sw || type == LSType.fsw) {
            return true;
        }
        return idx == 1;
    }

    @Override
    public Reg getRegByIdx(int idx) {
        return idx == 0 ? val : base;
    }

    @Override
    public int getInstFlag() {
        switch (type) {
            case ld, lw, flw -> {
                return InstFlag.None.value |
                        InstFlag.Load.value;
            }
            case sd, sw, fsw -> {
                return InstFlag.None.value |
                        InstFlag.Store.value;
            }
            default -> throw new RuntimeException("wrong type");
        }
    }

    @Override
    public RiscvInstruction myCopy(RiscvBlock newBlock) {
        return new LS(newBlock, val, base, addr, type, isSpilled, align);
    }
}
