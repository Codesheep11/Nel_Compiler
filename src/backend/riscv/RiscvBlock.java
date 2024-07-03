package backend.riscv;

import backend.operand.Reg;
import backend.riscv.RiscvInstruction.Explain;
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

    //数据流分析中使用
    public HashSet<Reg> use = new HashSet<>();
    public HashSet<Reg> def = new HashSet<>();
    public HashSet<Reg> in = new HashSet<>();
    public HashSet<Reg> out = new HashSet<>();

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

    public void clean() {
        use.clear();
        def.clear();
        in.clear();
        out.clear();
        for (RiscvInstruction ins : riscvInstructions) {
            ins.clean();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name + ":\n");
        for (RiscvInstruction ri : riscvInstructions) {
            if(ri instanceof Explain)continue;
            sb.append(ri + "\n");
        }
        return sb.toString();
    }


}
