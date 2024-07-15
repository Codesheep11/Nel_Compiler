package midend.Util;

import mir.BasicBlock;
import mir.Function;
import mir.Instruction;
import mir.Value;

import java.util.*;

public class ControlFlowGraph {

    private static Function parentFunction;


    public static void buildCFG(Function function) {
        parentFunction = function;
        clearGraph();
        buildGraph();
    }

    // 顺序枚举blocks 下的 Terminator 由 分支命令 维护相应block 的前驱后继
    private static void buildGraph() {
        for (BasicBlock block : parentFunction.getBlocks()) {
            // 加入全集
            if (block.getInstructions().isEmpty()) {
                throw new RuntimeException("empty block");
            }
            // 找到第一条终结指令
            Iterator<Instruction> iterator = block.getInstructions().iterator();
            while (iterator.hasNext()) {
                Instruction findFirstTerminator = iterator.next();
                if (findFirstTerminator instanceof Instruction.Terminator) {
                    break;
                }
            }
            // 如果后续指令存在，删除后续指令
            while (iterator.hasNext()) {
                Instruction del = iterator.next();
                del.release();
                iterator.remove();
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

    //清理前驱后继
    private static void clearGraph() {
        for (BasicBlock block : parentFunction.getBlocks()) {
            block.getSucBlocks().clear();
            block.getPreBlocks().clear();
        }
    }


    public void printGraph() {
        // print suc and pre blocks for each block
        for (BasicBlock block : parentFunction.getBlocks()) {
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


}
