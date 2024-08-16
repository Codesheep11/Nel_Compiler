package backend.riscv.RiscvInstruction;

import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;

import java.util.HashSet;

public class R4 extends RiscvInstruction {
    public final R4Type type;

    public enum R4Type {
        fmadd, fmsub, fnmadd, fnmsub;

        @Override
        public String toString() {
            switch (this) {
                case fmadd -> {
                    return "fmadd.s";
                }
                case fmsub -> {
                    return "fmsub.s";
                }
                case fnmadd -> {
                    return "fnmadd.s";
                }
                case fnmsub -> {
                    return "fnmsub.s";
                }
                default -> throw new AssertionError();
            }
        }
    }

    public Operand rd, rs1, rs2, rs3;

    public R4(RiscvBlock block, Operand rd, Operand rs1, Operand rs2, Operand rs3, R4Type type) {
        super(block);
        this.rd = rd;
        this.rs1 = rs1;
        this.rs2 = rs2;
        this.rs3 = rs3;
        this.type = type;
    }

    @Override
    public String toString() {
        return "\t" + type + "\t" + rd + ", " + rs1 + ", " + rs2 + ", " + rs3;
    }

    @Override
    public HashSet<Reg> getDef() {
        HashSet<Reg> def = new HashSet<>();
        if (rd instanceof Reg) def.add((Reg) rd);
        return def;
    }

    @Override
    public HashSet<Reg> getUse() {
        HashSet<Reg> use = new HashSet<>();
        if (rs1 instanceof Reg) use.add((Reg) rs1);
        if (rs2 instanceof Reg) use.add((Reg) rs2);
        if (rs3 instanceof Reg) use.add((Reg) rs3);
        return use;
    }

    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        if (rs1 == oldReg) rs1 = newReg;
        if (rs2 == oldReg) rs2 = newReg;
        if (rs3 == oldReg) rs3 = newReg;
        if (rd == oldReg) rd = newReg;

        super.updateUseDef();
    }

    @Override
    public int getOperandNum() {
        return 4;
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
        return idx == 0 ? (Reg) rd : (idx == 1 ? (Reg) rs1 : (idx == 2 ? (Reg) rs2 : (Reg) rs3));
    }

    @Override
    public int getInstFlag() {
        return InstFlag.None.value;
    }

    @Override
    public RiscvInstruction myCopy(RiscvBlock newBlock) {
        return new R4(newBlock, rd, rs1, rs2, rs3, type);
    }

}
