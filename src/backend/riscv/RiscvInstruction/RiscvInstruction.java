package backend.riscv.RiscvInstruction;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import utils.SyncLinkedList;

import java.util.HashSet;

public class RiscvInstruction extends SyncLinkedList.SyncLinkNode {

    public RiscvBlock block;
    public static int cnt = 0;
    public int id = cnt++;


    //使用，定义寄存器:构造时维护
    public HashSet<Reg> use = new HashSet<>();
    public HashSet<Reg> def = new HashSet<>();

    //出入寄存器：活跃变量分析时维护
    public HashSet<Reg> in = new HashSet<>();

    public HashSet<Reg> out = new HashSet<>();

    public void clean() {
        in = new HashSet<>();
        out = new HashSet<>();
    }

    public RiscvInstruction(RiscvBlock block) {
        this.block = block;
//        block.addInstrucion(this);
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
}
