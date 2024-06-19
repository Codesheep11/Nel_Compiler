package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instruction;
import mir.Value;

import java.util.HashSet;
import java.util.LinkedList;

public class ControlFlowGraph {

    private final Function parentFunction;
    private final HashSet<BasicBlock> vis = new HashSet<>();
    private final HashSet<BasicBlock> blocks = new HashSet<>();

    public ControlFlowGraph(Function parentFunction) {
        this.parentFunction = parentFunction;
    }

    public void build() {
        clearGraph();
        buildGraph();
        removeDeadBlocks();
        clearGraph();
        buildGraph();
        updatePhi();
        //checkGraph();
    }

    // 顺序枚举blocks 下的 inst 由 分支命令 维护相应block 的前驱后继
    private void buildGraph() {
        // reset
        blocks.clear();
        for (BasicBlock block : parentFunction.getBlocks()) {
            // 加入全集
            if (block.getInstructions().isEmpty())
                continue;
            blocks.add(block);

            // 删除多余的终结指令
//            Instruction lastInst = block.getLastInst();
//            while (lastInst.getPrev() instanceof Instruction.Terminator) {
//                lastInst.remove();
//                lastInst = block.getLastInst();
//            }

            // 找到第一条终结指令
            Instruction findFirstTerminator = block.getFirstInst();
            while (!(findFirstTerminator instanceof Instruction.Terminator)) {
//                System.out.println(findFirstTerminator.getDescriptor());
                findFirstTerminator = (Instruction) findFirstTerminator.getNext();
            }
            // 如果后续指令存在，删除后续指令
            while (findFirstTerminator.hasNext()) {
                findFirstTerminator.getNext().remove();
            }
            // 枚举指令
            Instruction instr = block.getLastInst();
            if (instr instanceof Instruction.Branch) {
                // System.out.println(block.getLabel() + " :" + inst.getDescriptor());
                BasicBlock thenBlock = ((Instruction.Branch) instr).getThenBlock();
                BasicBlock elseBlock = ((Instruction.Branch) instr).getElseBlock();
                // then edge
                block.addSucBlock(thenBlock);
                thenBlock.addPreBlock(block);
                // else edge
                block.addSucBlock(elseBlock);
                elseBlock.addPreBlock(block);
            }
            if (instr instanceof Instruction.Jump) {
                // System.out.println(block.getLabel() + " :" + inst.getDescriptor());
                BasicBlock targetBlock = ((Instruction.Jump) instr).getTargetBlock();
                // jump edge
                block.addSucBlock(targetBlock);
                targetBlock.addPreBlock(block);
            }
        }
    }

    private void clearGraph() {
        for (BasicBlock block : vis) {
            block.getSucBlocks().clear();
            block.getPreBlocks().clear();
        }
    }

    private void removeDeadBlocks() {
        // reset
        vis.clear();
        if (parentFunction.getBlocks().getSize() == 0) {
            return;
        }
        depthFirstSearch(parentFunction.getEntry());
        for (BasicBlock block : blocks) {
            if (vis.contains(block)) {
                continue;
            }
            block.remove();
        }
    }

    private void depthFirstSearch(BasicBlock cur) {
        vis.add(cur);
        for (BasicBlock sucBlock : cur.getSucBlocks()) {
            if (!vis.contains(sucBlock)) {
                depthFirstSearch(sucBlock);
            }
        }
    }

    public void printGraph() {
        // print suc and pre blocks for each block
        for (BasicBlock block : blocks) {
            System.out.println(" block: " + block.getLabel());
            System.out.println("pre blocks: ");
            for (BasicBlock preBlock : block.getPreBlocks()) {
                System.out.println(preBlock.getLabel());
            }
            System.out.println("suc blocks: ");
            for (BasicBlock sucBlock : block.getSucBlocks()) {
                System.out.println(sucBlock.getLabel());
            }
        }
    }

    private void updatePhi() {
        //phi指令更新
        for (BasicBlock block : parentFunction.getBlocks()) {
            for (Instruction instr : block.getInstructions()) {
                if (instr instanceof Instruction.Phi) {
                    Instruction.Phi phi = (Instruction.Phi) instr;
                    LinkedList<BasicBlock> rms = new LinkedList<>();
                    for (BasicBlock preBlock : phi.getPreBlocks()) {
                        if (!block.getPreBlocks().contains(preBlock)) {
                            rms.add(preBlock);
                        }
                    }
                    for (BasicBlock rm : rms) {
                        phi.removeOptionalValue(rm);
                    }
                    if (phi.getPreBlocks().size() != block.getPreBlocks().size()) {
                        throw new RuntimeException("phi error");
                    }
                    if (phi.canBeReplaced()) {
                        Value value = phi.getOptionalValue(phi.getPreBlocks().get(0));
                        phi.replaceAllUsesWith(value);
                        phi.remove();
                    }
                }
                else break;
            }
        }
    }

}
