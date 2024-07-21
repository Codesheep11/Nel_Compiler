package backend.riscv;

import backend.riscv.RiscvInstruction.RiscvInstruction;
import mir.BasicBlock;
import utils.NelLinkedList;

import java.util.HashSet;

public class RiscvBlock {
    public String name;

    public RiscvFunction function;

    public NelLinkedList<RiscvInstruction> riscvInstructions = new NelLinkedList<>();//便于插入指令

    //riscvBlock的前驱和后继,在codegen时维护

    public HashSet<RiscvBlock> preBlock = new HashSet<>();
    public HashSet<RiscvBlock> succBlock = new HashSet<>();

    public RiscvBlock(RiscvFunction rf, BasicBlock irBlock) {
        this.function = rf;
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
        StringBuilder sb = new StringBuilder(".p2align 2\n" + name + ":\n");
        for (RiscvInstruction ri : riscvInstructions) {
            sb.append(ri + "\n");
        }
        return sb.toString();
    }


}
