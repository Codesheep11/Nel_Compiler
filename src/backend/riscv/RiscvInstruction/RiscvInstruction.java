package backend.riscv.RiscvInstruction;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import utils.SyncLinkedList;

import java.util.HashSet;

public class RiscvInstruction extends SyncLinkedList.SyncLinkNode {

    public RiscvBlock block;
    public static int cnt = 0;
    public int id = cnt++;

    public int getInstFlag() {
        return 0;
    }


    //使用，定义寄存器:构造时维护
    public HashSet<Reg> use = new HashSet<>();
    public HashSet<Reg> def = new HashSet<>();

    public RiscvInstruction(RiscvBlock block) {
        this.block = block;
    }

    public void addUse(Reg reg) {
        use.add(reg);
    }

    public void addDef(Reg reg) {
        def.add(reg);
    }

    /**
     * 当前指令中替换使用的寄存器
     *
     * @param oldReg 被替换的寄存器
     * @param newReg 替换的寄存器
     */
    public void replaceUseReg(Reg oldReg, Reg newReg) {
        if (!(use.contains(oldReg) || def.contains(oldReg))) {
            throw new RuntimeException("replace error");
        }
        oldReg.use.remove(this);
        newReg.use.add(this);
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
}
