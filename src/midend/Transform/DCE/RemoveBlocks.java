package midend.Transform.DCE;

import midend.Analysis.AnalysisManager;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

public class RemoveBlocks {
    private static final HashSet<BasicBlock> vis = new HashSet<>();

    public static boolean run(Module module) {
        boolean modified = false;
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            modified |= runOnFunc(function);
        }
        return modified;
    }

    public static boolean runOnFunc(Function function) {
        boolean modified = false;
        modified |= removeEmptyBlocks(function);
        AnalysisManager.refreshCFG(function);
        modified |= removeDeadBlocks(function);
        AnalysisManager.refreshCFG(function);
        updatePhi(function);
        return modified;
    }

    public static boolean removeEmptyBlocks(Function function) {
        ArrayList<BasicBlock> removeList = new ArrayList<>();
        for (BasicBlock block : function.getBlocks()) {
            if (block.getInstructions().isEmpty()) removeList.add(block);
        }
        for (BasicBlock block : removeList) block.delete();
        return !removeList.isEmpty();
    }

    public static boolean removeDeadBlocks(Function function) {
        vis.clear();
        ArrayList<BasicBlock> removeList = new ArrayList<>();
        depthFirstSearch(function.getEntry());
        for (BasicBlock block : function.getBlocks()) {
            if (!vis.contains(block)) removeList.add(block);
        }
        for (BasicBlock block : removeList) block.delete();
        return !removeList.isEmpty();
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
                    phi.delete();
                } else {
                    Value v = phi.getConstantPhiValue();
                    if (v != null) {
                        phi.replaceAllUsesWith(v);
                        phi.delete();
                    }
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
