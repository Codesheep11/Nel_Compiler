package backend.allocater;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.RiscvInstruction;

import java.util.ArrayList;
import java.util.HashSet;

public class LivenessAnalyze {
    public RiscvFunction function;

    public LivenessAnalyze(RiscvFunction function) {
        this.function = function;
    }

    public void genInOutSet() {
        for (RiscvBlock block : function.blocks) {
            block.clean();
            for (RiscvInstruction ins : block.riscvInstructions) {
                HashSet<Reg> tmp = new HashSet<>(ins.use);
                tmp.removeAll(block.def);
                block.use.addAll(tmp);
                block.def.addAll(ins.def);
            }
        }
        ArrayList<RiscvBlock> topoSort = function.getTopoSort();
        int time = 0;
        boolean changed = true;
        while (changed) {
//            System.out.println("cycle times: " + time++);
            changed = false;
            for (RiscvBlock block : topoSort) {
                HashSet<Reg> inSet = new HashSet();
                HashSet<Reg> outSet = new HashSet();
                // out = U(succ)in
                for (RiscvBlock succ : block.succBlock) {
                    outSet.addAll(succ.in);
                }
                if (!outSet.equals(block.out)) {
                    changed = true;
                    block.out = outSet;
                }
                // in = use ∪ (out - def)
                inSet.addAll(block.out);
                inSet.removeAll(block.def);
                inSet.addAll(block.use);
                if (!inSet.equals(block.in)) {
                    changed = true;
                    block.in = inSet;
                }
            }
        }
        for (RiscvBlock block : topoSort) {
            if (block.riscvInstructions.getSize() == 0) continue;
            block.getFirst().in = block.in;
            block.getLast().out = block.out;
            int size = block.riscvInstructions.getSize();
            for (int i = size - 1; i >= 0; i--) {
//                System.out.println("ins num: " + i + " size: " + size);
                RiscvInstruction ins = block.riscvInstructions.get(i);
                if (i != size - 1) {
                    //out[i] = in[i+1]
                    ins.out = block.riscvInstructions.get(i + 1).in;
                }
                if (i != 0) {
                    //in[i] = use[i] U (out[i] - def[i])
                    HashSet<Reg> inSet = new HashSet(ins.out);
                    inSet.removeAll(ins.def);
                    inSet.addAll(ins.use);
                    ins.in = inSet;
                }
            }
        }
    }
}
