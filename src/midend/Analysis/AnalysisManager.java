package midend.Analysis;

import midend.Util.ControlFlowGraph;
import mir.BasicBlock;
import mir.Function;
import mir.Module;
import mir.result.CFGinfo;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 分析信息管理器
 * @author Srchycz
 * TODO: 增加标记机制 实现懒更新以提升性能
 */
public final class AnalysisManager {


    private static final HashMap<Function, CFGinfo> cfgMap = new HashMap<>();


    // region CFG
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

    private AnalysisManager() { }
}