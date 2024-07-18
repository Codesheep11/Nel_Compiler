package midend.Transform.DCE;

import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

public class RemoveBlocks {
    private static HashSet<BasicBlock> vis = new HashSet<>();

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        removeEmptyBlocks(function);
//        Print.outputLLVM(function, "debug.txt");
        function.buildControlFlowGraph();
        removeDeadBlocks(function);
        function.buildControlFlowGraph();
        updatePhi(function);
    }

    public static void removeEmptyBlocks(Function function) {
        ArrayList<BasicBlock> removeList = new ArrayList<>();
        for (BasicBlock block : function.getBlocks()) {
            if (block.getInstructions().isEmpty()) removeList.add(block);
        }
        for (BasicBlock block : removeList) block.delete();
    }

    public static void removeDeadBlocks(Function function) {
        vis.clear();
        ArrayList<BasicBlock> removeList = new ArrayList<>();
        depthFirstSearch(function.getEntry());
        for (BasicBlock block : function.getBlocks()) {
            if (!vis.contains(block)) removeList.add(block);
        }
        for (BasicBlock block : removeList) block.delete();
    }

    private static void updatePhi(Function function) {
        //phi指令更新 这里考虑phi得到的信息会更少，同时默认LCSSA不会被删除
        for (BasicBlock block : function.getBlocks()) {
            for (var phi : block.getPhiInstructions()) {
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
                    System.out.println(phi.getParentBlock().getDescriptor());
                    System.out.println(phi);
                    throw new RuntimeException("phi error");
                }
                if (phi.canBeReplaced() && !phi.isLCSSA) {
                    Value value = phi.getOptionalValue(phi.getPreBlocks().get(0));
                    phi.replaceAllUsesWith(value);
                    phi.remove();
                }
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
