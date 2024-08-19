package backend.riscv;

import backend.operand.Reg;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import mir.BasicBlock;
import utils.NelLinkedList;

import java.util.HashSet;

@SuppressWarnings("unused")
public class RiscvBlock {
    public final String name;

    public final RiscvFunction function;

    public final NelLinkedList<RiscvInstruction> riscvInstructions = new NelLinkedList<>();//便于插入指令

    public int loopDepth = 0; //循环深度

    //riscvBlock的前驱和后继,在codegen时维护

    public final HashSet<RiscvBlock> preBlock = new HashSet<>();
    public final HashSet<RiscvBlock> succBlock = new HashSet<>();

    public RiscvBlock(RiscvFunction rf, BasicBlock irBlock) {
        this.function = rf;
        this.name = irBlock.getLabel();
    }

    public RiscvBlock(RiscvFunction rf, String name) {
        this.function = rf;
        this.name = name;
    }

    public void addInstFirst(RiscvInstruction inst) {
        new RiscvBlockAsNelListFriend().addFirst(inst);
        inst.block = this;
    }

    public void addInstLast(RiscvInstruction inst) {
        new RiscvBlockAsNelListFriend().addLast(inst);
        inst.block = this;
    }

    public void insertInstBefore(RiscvInstruction inst, RiscvInstruction pos) {
        new RiscvBlockAsNelListFriend().insertBefore(inst, pos);
        inst.block = this;
    }

    public void insertInstAfter(RiscvInstruction inst, RiscvInstruction pos) {
        new RiscvBlockAsNelListFriend().insertAfter(inst, pos);
        inst.block = this;
    }

    public RiscvInstruction getFirst() {
        return riscvInstructions.getFirst();
    }

    public RiscvInstruction getLast() {
        return riscvInstructions.getLast();
    }

    private final class RiscvBlockAsNelListFriend extends NelLinkedList.NelList_Friend {
        private void insertBefore(RiscvInstruction newNode, RiscvInstruction node) {
            super.insertBefore(riscvInstructions, newNode, node);
        }

        private void insertAfter(RiscvInstruction newNode, RiscvInstruction node) {
            super.insertAfter(riscvInstructions, newNode, node);
        }

        private void addFirst(RiscvInstruction newNode) {
            super.addFirst(riscvInstructions, newNode);
        }

        private void addLast(RiscvInstruction newNode) {
            super.addLast(riscvInstructions, newNode);
        }

    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(".p2align 2\n" + name + ":\n");
        for (RiscvInstruction ri : riscvInstructions) {
            sb.append(ri).append("\n");
//            sb.append(ri).append("\t#");
//            for (Reg reg: ri.getReg()) {
//                sb.append(reg.regCnt).append(" ");
//            }
//            sb.append("\n");
        }
        return sb.toString();
    }


}
