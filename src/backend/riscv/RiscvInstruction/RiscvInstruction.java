package backend.riscv.RiscvInstruction;

import backend.allocator.LivenessAnalyze;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import utils.NelLinkedList;

import java.util.HashSet;

import static backend.allocator.LivenessAnalyze.Def;
import static backend.allocator.LivenessAnalyze.Use;

public class RiscvInstruction extends NelLinkedList.NelLinkNode {

    public RiscvBlock block;
    public static int cnt = 0;
    public final int id = cnt++;

    public int getInstFlag() {
        return 0;
    }

    public RiscvInstruction(RiscvBlock block) {
        this.block = block;
    }

    /**
     * 当前指令中替换使用的寄存器,并维护LivenessAnalyze
     *
     * @param oldReg 被替换的寄存器
     * @param newReg 替换的寄存器
     */
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        LivenessAnalyze.RegUse.putIfAbsent(oldReg, new HashSet<>());
        LivenessAnalyze.RegUse.get(oldReg).remove(this);
        LivenessAnalyze.RegUse.putIfAbsent(newReg, new HashSet<>());
        LivenessAnalyze.RegUse.get(newReg).add(this);
    }

    public void updateUseDef() {
        Def.put(this, getDef());
        Use.put(this, getUse());
    }

    @Override
    public int hashCode() {
        return id;
    }

    public int getOperandNum() {
        return 0;
    }


    public boolean isUse(int idx) {
        return false;//
    }

    public boolean isDef(int idx) {
        return false;
    }

    public Reg getRegByIdx(int idx) {
        return null;
    }

    public boolean hasFlag(int flag) {
        return (getInstFlag() & flag) != 0;
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    public enum InstFlag {
        None(0),
        Load(1 << 0),
        Store(1 << 1),
        Terminator(1 << 2),
        Branch(1 << 3),
        Call(1 << 4),
        NoFallthrough(1 << 5),
        Push(1 << 6),
        LoadConstant(1 << 7),
        RegDef(1 << 8),  // def ISA register
        Return(1 << 10),
        WithDelaySlot(1 << 12),
        RegCopy(1 << 13),
        PCRel(1 << 15),
        Padding(1 << 18),
        IndirectJump(1 << 19),
        SideEffect(Load.value | Store.value | Terminator.value | Branch.value |
                Call.value | Push.value | RegDef.value | Return.value |
                WithDelaySlot.value | Padding.value | IndirectJump.value);

        public final int value;

        InstFlag(int value) {
            this.value = value;
        }
    }

    public HashSet<Reg> getUse() {
        return new HashSet<>();
    }

    public HashSet<Reg> getDef() {
        return new HashSet<>();
    }

    public HashSet<Reg> getReg() {
        HashSet<Reg> regs = new HashSet<>();
        regs.addAll(getUse());
        regs.addAll(getDef());
        return regs;
    }

    public RiscvInstruction myCopy(RiscvBlock newBlock) {
        return new RiscvInstruction(newBlock);
    }
}
