package midend.Analysis;

import midend.Util.ControlFlowGraph;
import midend.Util.DominanceGraph;
import mir.BasicBlock;
import mir.Function;
import mir.Instruction;
import mir.Module;
import mir.result.CFGinfo;
import mir.result.DGinfo;
import mir.result.SCEVinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 分析信息管理器
 *
 * @author Srchycz
 * TODO: 增加标记机制 实现懒更新以提升性能
 */
public final class AnalysisManager {


    private static final HashMap<Function, CFGinfo> cfgMap = new HashMap<>();
    private static final HashMap<Function, DGinfo> dgMap = new HashMap<>();
    private static final HashMap<Function, SCEVinfo> scevMap = new HashMap<>();


    // region CFG

    public static void buildCFG(Module module) {
        for (Function function : module.getFuncSet()) {
            if (!function.isExternal()) cfgMap.put(function, ControlFlowGraph.run(function));
        }
    }

    public static void refreshCFG(Function function) {
        cfgMap.put(function, ControlFlowGraph.run(function));
    }

    public static CFGinfo getCFG(Function function) {
        if (!cfgMap.containsKey(function)) {
            cfgMap.put(function, ControlFlowGraph.run(function));
        }
        return cfgMap.get(function);
    }

    public static ArrayList<BasicBlock> getCFGPredecessors(BasicBlock block) {
        return cfgMap.get(block.getParentFunction()).getPredBlocks(block);
    }

    public static ArrayList<BasicBlock> getCFGSuccessors(BasicBlock block) {
        return cfgMap.get(block.getParentFunction()).getSuccBlocks(block);
    }
    // endregion

    // region DG
    public static void refreshDG(Function function) {
        dgMap.put(function, DominanceGraph.runOnFunc(function));
    }

    public static DGinfo getDG(Function function) {
        if (!dgMap.containsKey(function)) {
            dgMap.put(function, DominanceGraph.runOnFunc(function));
        }
        return dgMap.get(function);
    }

    public static boolean dominate(BasicBlock a, BasicBlock b) {
        return dgMap.get(a.getParentFunction()).dominate(a, b);
    }

    public static boolean dominate(Instruction a, Instruction b) {
        BasicBlock block_a = a.getParentBlock();
        BasicBlock block_b = b.getParentBlock();
        if (block_a.equals(block_b)) {
            return a.getIndex() < b.getIndex();
        }
        else return dominate(block_a, block_b);
    }

    public static boolean strictlyDominate(BasicBlock a, BasicBlock b) {
        return dgMap.get(a.getParentFunction()).strictlyDominate(a, b);
    }

    public static BasicBlock getIDom(BasicBlock block) {
        return dgMap.get(block.getParentFunction()).getIDom(block);
    }

    public static HashSet<BasicBlock> getDominators(BasicBlock block) {
        return dgMap.get(block.getParentFunction()).getDominators(block);
    }

    public static HashSet<BasicBlock> getDomFrontiers(BasicBlock block) {
        return dgMap.get(block.getParentFunction()).getDomFrontiers(block);
    }

    public static ArrayList<BasicBlock> getDomTreeChildren(BasicBlock block) {
        return dgMap.get(block.getParentFunction()).getDomTreeChildren(block);
    }

    public static int getDomDepth(BasicBlock block) {
        return dgMap.get(block.getParentFunction()).getDomDepth(block);
    }

    // endregion

    private AnalysisManager() {
    }
}