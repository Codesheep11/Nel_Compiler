package midend.Transform.DCE;

import midend.Util.Print;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

public class RemoveDeadBlock {
    private static Function curFunction;
    private static HashSet<BasicBlock> vis = new HashSet<>();

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        curFunction = function;
        curFunction.buildControlFlowGraph();
        removeDeadBlocks();
        curFunction.buildControlFlowGraph();
        updatePhi();
    }

    private static void removeDeadBlocks() {
        vis.clear();
        depthFirstSearch(curFunction.getEntry());
        ArrayList<BasicBlock> removeList = new ArrayList<>();
        for (BasicBlock block : curFunction.getBlocks()) {
            if (block.getInstructions().isEmpty()) removeList.add(block);
            if (!vis.contains(block)) removeList.add(block);
        }
        for (BasicBlock block : removeList) {
            block.delete();
        }
    }

    private static void updatePhi() {
        //phi指令更新 这里考虑phi得到的信息会更少，同时默认LCSSA不会被删除
        for (BasicBlock block : curFunction.getBlocks()) {
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
                } else break;
            }
        }
    }

    private static void depthFirstSearch(BasicBlock cur) {
        vis.add(cur);
        for (BasicBlock sucBlock : cur.getSucBlocks()) {
            if (!vis.contains(sucBlock)) {
                depthFirstSearch(sucBlock);
            }
        }
    }
}
