package backend.riscv;

import backend.riscv.RiscvInstruction.RiscvInstruction;
import mir.BasicBlock;
import utils.SyncLinkedList;

import java.util.HashSet;

public class RiscvBlock {
    public String name;

    public SyncLinkedList<RiscvInstruction> riscvInstructions = new SyncLinkedList<>();//便于插入指令

    //riscvBlock的前驱和后继,在codegen时维护

    public HashSet<RiscvBlock> preBlock = new HashSet<>();
    public HashSet<RiscvBlock> succBlock = new HashSet<>();

    public RiscvBlock(BasicBlock irBlock) {
        this.name = irBlock.getLabel();
    }

    public void addInstrucion(RiscvInstruction ri) {
        riscvInstructions.addLast(ri);
    }

    public RiscvInstruction getFirst() {
        return riscvInstructions.getFirst();
    }

    public RiscvInstruction getLast() {
        return riscvInstructions.getLast();
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name + ":\n");
        for (RiscvInstruction ri : riscvInstructions) {
            sb.append(ri + "\n");
        }
        return sb.toString();
    }


}
