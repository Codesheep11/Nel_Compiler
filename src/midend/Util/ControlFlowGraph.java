package midend.Util;

import mir.BasicBlock;
import mir.Function;
import mir.Instruction;
import midend.Analysis.result.CFGinfo;

import java.util.*;

public class ControlFlowGraph {

    private static Function parentFunction;

    public static CFGinfo run(Function function) {
        parentFunction = function;
        CFGinfo cfgInfo = new CFGinfo(function);
        buildGraph(cfgInfo);
        return cfgInfo;
    }

    @Deprecated(forRemoval = true)
    public static void buildCFG(Function function) {
        parentFunction = function;
    }

    // 顺序枚举blocks 下的 Terminator 由 分支命令 维护相应block 的前驱后继
    private static void buildGraph(CFGinfo info) {
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
                info.addSuccBlock(block, thenBlock);
                info.addPredBlock(thenBlock, block);
                // else edge
                info.addSuccBlock(block, elseBlock);
                info.addPredBlock(elseBlock, block);
            }
            if (instr instanceof Instruction.Jump) {
                // System.out.println(block.getLabel() + " :" + inst.getDescriptor());
                BasicBlock targetBlock = ((Instruction.Jump) instr).getTargetBlock();
                // jump edge
                info.addSuccBlock(block, targetBlock);
                info.addPredBlock(targetBlock, block);
            }
        }
    }
}
