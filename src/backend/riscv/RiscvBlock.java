package backend.riscv;

import backend.operand.Reg;
import backend.riscv.RiscvInstruction.B;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import mir.BasicBlock;
import utils.SyncLinkedList;

import java.util.HashMap;
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
            sb.append(ri + "\n");
        }
        return sb.toString();
    }


    /**
    注意，该跳转表不考虑
     **/

    public HashMap<RiscvBlock, Double> jumpTable() {
        boolean hasBranch = false;
        HashMap<RiscvBlock, Double> result = new HashMap<>();
        double prob = 1.0;
        for (RiscvInstruction instruction : riscvInstructions) {
            if (instruction instanceof B) {
                hasBranch = true;
                result.put(((B) instruction).targetBlock, ((B) instruction).getYesProb()*prob);
                prob*=(1-((B) instruction).getYesProb());
            }
        }
        if (!hasBranch) {
            return null;
        }
        return result;
    }
}
