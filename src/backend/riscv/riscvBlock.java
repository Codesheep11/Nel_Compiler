package backend.riscv;

import backend.operand.Reg;
import backend.riscv.riscvInstruction.riscvInstruction;
import mir.BasicBlock;
import utils.SyncLinkedList;

import java.util.ArrayList;
import java.util.HashSet;

public class riscvBlock {
    public String name;

    public SyncLinkedList<riscvInstruction> riscvInstructions = new SyncLinkedList<>();//便于插入指令

    //riscvBlock的前驱和后继,在codegen时维护

    public HashSet<riscvBlock> preBlock = new HashSet<>();
    public HashSet<riscvBlock> succBlock = new HashSet<>();

    //数据流分析中使用
    public HashSet<Reg> use = new HashSet<>();
    public HashSet<Reg> def = new HashSet<>();
    public HashSet<Reg> in = new HashSet<>();
    public HashSet<Reg> out = new HashSet<>();

    public riscvBlock(BasicBlock irBlock) {
        this.name = irBlock.getLabel();
    }

    public void addInstrucion(riscvInstruction ri) {
        riscvInstructions.addLast(ri);
    }

    public riscvInstruction getFirst() {
        return riscvInstructions.getFirst();
    }

    public riscvInstruction getLast() {
        return riscvInstructions.getLast();
    }

    public void clean() {
        use.clear();
        def.clear();
        in.clear();
        out.clear();
        for (riscvInstruction ins : riscvInstructions) {
            ins.clean();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name + ":\n");
        for (riscvInstruction ri : riscvInstructions) {
            sb.append(ri + "\n");
        }
        return sb.toString();
    }
}
