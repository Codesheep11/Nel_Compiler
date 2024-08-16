package backend.riscv.RiscvInstruction;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.Ir2RiscV.CodeGen;

import java.util.HashSet;

public class J extends RiscvInstruction {


    public JType type;

    public enum JType {
        j, ret, call;

        @Override
        public String toString() {
            return switch (this) {
                case j -> "j";
                case ret -> "ret";
                case call -> "call";
            };
        }
    }

    public RiscvBlock targetBlock;

    public String funcName;

    public J(RiscvBlock block, JType jType) {
        super(block);
        this.type = jType;
        //use.add(Reg.getPreColoredReg(Reg.PhyReg.ra, 64));
    }

    public J(RiscvBlock block, JType jType, RiscvBlock targetBlock) {
        super(block);
        this.type = jType;
        this.targetBlock = targetBlock;
        block.succBlock.add(targetBlock);
        targetBlock.preBlock.add(block);
    }

    public J(RiscvBlock block, JType jType, String funcName) {
        super(block);
        if (jType != JType.call) {
            throw new RuntimeException("not call but use funcName");
        }
        this.type = jType;
        this.funcName = funcName;
    }

    @Override
    public String toString() {
        if (type == JType.ret) {
            return "\t" + type;
        } else if (type == JType.call) {
            return "\t" + type + "\t" + RiscvFunction.funcNameWrap(funcName);
        } else {
            return "\t" + type + "\t\t" + targetBlock.name;
        }
    }

    @Override
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        super.replaceUseReg(oldReg, newReg);
        super.updateUseDef();
        throw new RuntimeException("J instruction should not be replaced");

    }

    public boolean isExternel() {
        if (type != JType.call) throw new RuntimeException("wrong type");
        return switch (funcName) {
            case "memset", "getint", "putint", "getch", "getfloat", "putch", "putfloat", "_sysy_starttime", "getfarray", "_sysy_stoptime", "getarray", "putarray", "putfarray", "putf", "main" ->
                    true;
            default -> false;
        };
    }

    @Override
    public HashSet<Reg> getUse() {
        HashSet<Reg> use = new HashSet<>();
        if (type == JType.call) {
            RiscvFunction rf = CodeGen.ansRis.getFunction(funcName);
            use.addAll(rf.defs);
        }
        if (type == JType.ret) {
            RiscvFunction rf = block.function;
            if (rf.retTypeCode == 1) use.add(Reg.getPreColoredReg(Reg.PhyReg.a0, 32));
            if (rf.retTypeCode == -1) use.add(Reg.getPreColoredReg(Reg.PhyReg.fa0, 32));
            use.add(Reg.getPreColoredReg(Reg.PhyReg.ra, 64));
        }
        return use;
    }

    @Override
    public HashSet<Reg> getDef() {
        HashSet<Reg> def = new HashSet<>();
        if (type == JType.call) {
            RiscvFunction rf = CodeGen.ansRis.getFunction(funcName);
            if (rf.retTypeCode == 1) def.add(Reg.getPreColoredReg(Reg.PhyReg.a0, 32));
            if (rf.retTypeCode == -1) def.add(Reg.getPreColoredReg(Reg.PhyReg.fa0, 32));
            def.addAll(rf.defs);
        }
        return def;
    }

    @Override
    public int getInstFlag() {
        switch (type) {
            case j, call -> {
                return InstFlag.Call.value | InstFlag.None.value;
            }
            case ret -> {
                return InstFlag.None.value |
                        InstFlag.Return.value |
                        InstFlag.NoFallthrough.value |
                        InstFlag.Terminator.value;
            }
            default -> throw new RuntimeException("wrong type");
        }
    }

    @Override
    public int getOperandNum() {
        return type == JType.call ? 1 : 0;
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
        return Reg.getPreColoredReg(Reg.PhyReg.ra, 32);
    }

    @Override
    public RiscvInstruction myCopy(RiscvBlock newBlock) {
        switch (type) {
            case ret -> {
                return new J(newBlock, type);
            }
            case j -> {
                return new J(newBlock, type, targetBlock);
            }
            case call -> {
                return new J(newBlock, type, funcName);
            }
            default -> throw new RuntimeException("wrong type");
        }
    }
}
