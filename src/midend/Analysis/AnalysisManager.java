package midend.Analysis;

import midend.Util.ControlFlowGraph;
import midend.Util.DominanceGraphLT;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;
import midend.Analysis.result.CFGinfo;
import midend.Analysis.result.DGinfo;
import midend.Analysis.result.SCEVinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 分析管理器
 */
public final class AnalysisManager {

    private static final HashMap<Function, CFGinfo> cfgMap = new HashMap<>();
    private static final HashMap<Function, DGinfo> dgMap = new HashMap<>();
    private static final HashMap<Function, SCEVinfo> scevMap = new HashMap<>();
    private static final HashMap<Function, I32RangeAnalysis> rangeMap = new HashMap<>();
    private static final HashMap<Function, FuncInfo> funcInfoMap = new HashMap<>();

    private static AlignmentAnalysis.AlignMap alignMap;

    private static final HashMap<Function, Boolean> dirtyCFG = new HashMap<>();

    private static final HashMap<Function, Boolean> dirtyDG = new HashMap<>();

    private static final HashMap<Function, Boolean> dirtySCEV = new HashMap<>();


    public static void dirtyAll(Function function) {
        dirtyCFG(function);
        dirtyDG(function);
        dirtySCEV(function);
    }
    // region CFG

    public static void buildCFG(Module module) {
        for (Function function : module.getFuncSet()) {
            if (!function.isExternal()) {
                cfgMap.put(function, ControlFlowGraph.run(function));
                dirtyCFG.put(function, false);
            }
        }
    }

    /**
     * 手动强制刷新 CFG
     */
    public static void refreshCFG(Function function) {
        cfgMap.put(function, ControlFlowGraph.run(function));
        dirtyCFG.put(function, false);
    }

    public static CFGinfo getCFG(Function function) {
        if (!cfgMap.containsKey(function) || dirtyCFG.getOrDefault(function, true)) {
            cfgMap.put(function, ControlFlowGraph.run(function));
            dirtyCFG.put(function, false);
        }
        return cfgMap.get(function);
    }

    public static ArrayList<BasicBlock> getCFGPredecessors(BasicBlock block) {
        checkCFG(block.getParentFunction());
        return cfgMap.get(block.getParentFunction()).getPredBlocks(block);
    }

    public static ArrayList<BasicBlock> getCFGSuccessors(BasicBlock block) {
        checkCFG(block.getParentFunction());
        return cfgMap.get(block.getParentFunction()).getSuccBlocks(block);
    }

    public static void dirtyCFG(Function function) {
        dirtyCFG.put(function, true);
    }

    private static void checkCFG(Function function) {
        if (dirtyCFG.getOrDefault(function, true)) {
            refreshCFG(function);
        }
    }
    // endregion

    // region DG
    public static void buildDG(Module module) {
        for (Function function : module.getFuncSet()) {
            if (!function.isExternal()) {
                dgMap.put(function, DominanceGraphLT.runOnFunc(function));
                dirtyDG.put(function, false);
            }
        }
    }

    public static void refreshDG(Function function) {
        dgMap.put(function, DominanceGraphLT.runOnFunc(function));
        dirtyDG.put(function, false);
    }

    public static DGinfo getDG(Function function) {
        if (!dgMap.containsKey(function) || dirtyDG.getOrDefault(function, true)) {
            dgMap.put(function, DominanceGraphLT.runOnFunc(function));
            dirtyDG.put(function, false);
        }
        return dgMap.get(function);
    }

    public static boolean dominate(BasicBlock a, BasicBlock b) {
        checkDG(a.getParentFunction());
        return dgMap.get(a.getParentFunction()).dominate(a, b);
    }

    public static boolean dominate(Instruction a, Instruction b) {
        BasicBlock block_a = a.getParentBlock();
        BasicBlock block_b = b.getParentBlock();
        checkDG(block_a.getParentFunction());
        if (block_a.equals(block_b)) {
            return a.getIndex() < b.getIndex();
        } else return dominate(block_a, block_b);
    }

    public static boolean strictlyDominate(BasicBlock a, BasicBlock b) {
        checkDG(a.getParentFunction());
        return dgMap.get(a.getParentFunction()).strictlyDominate(a, b);
    }

    public static BasicBlock getIDom(BasicBlock block) {
        checkDG(block.getParentFunction());
        return dgMap.get(block.getParentFunction()).getIDom(block);
    }

    public static HashSet<BasicBlock> getDominators(BasicBlock block) {
        checkDG(block.getParentFunction());
        return dgMap.get(block.getParentFunction()).getDominators(block);
    }

    public static HashSet<BasicBlock> getDomFrontiers(BasicBlock block) {
        checkDG(block.getParentFunction());
        return dgMap.get(block.getParentFunction()).getDomFrontiers(block);
    }

    public static ArrayList<BasicBlock> getDomTreeChildren(BasicBlock block) {
        checkDG(block.getParentFunction());
        return dgMap.get(block.getParentFunction()).getDomTreeChildren(block);
    }

    public static int getDomDepth(BasicBlock block) {
        checkDG(block.getParentFunction());
        return dgMap.get(block.getParentFunction()).getDomDepth(block);
    }

    public static BasicBlock getLCA(BasicBlock a, BasicBlock b) {
        checkDG(a.getParentFunction());
        return dgMap.get(a.getParentFunction()).getLCA(a, b);
    }

    public static void dirtyDG(Function function) {
        dirtyDG.put(function, true);
    }

    private static void checkDG(Function function) {
        if (dirtyDG.getOrDefault(function, true)) {
            refreshDG(function);
        }
    }
    // endregion

    // region SCEV
    public static void refreshSCEV(Function function) {
        scevMap.put(function, ScalarEvolution.runOnFunc(function));
    }

    public static SCEVinfo getSCEV(Function function) {
        if (!scevMap.containsKey(function)) {
            scevMap.put(function, ScalarEvolution.runOnFunc(function));
        }
        return scevMap.get(function);
    }

    public static void dirtySCEV(Function function) {
        dirtySCEV.put(function, true);
    }

    private static void checkSCEV(Function function) {
        if (dirtySCEV.getOrDefault(function, true)) {
            refreshSCEV(function);
        }
    }
    // endregion

    // region Range
    public static void runI32Range(Module module) {
        for (Function function : module.getFuncSet()) {
            if (!function.isExternal()) {
                rangeMap.put(function, new I32RangeAnalysis(function));
            }
        }
    }

    public static void refreshI32Range(Function function) {
        rangeMap.put(function, new I32RangeAnalysis(function));
    }

    public static I32RangeAnalysis getI32Range(Function function) {
        return rangeMap.get(function);
    }

    public static I32RangeAnalysis.I32Range getValueRange(Value value, BasicBlock block) {
        if (value.getType().isInt64Ty()) return I32RangeAnalysis.I32Range.Any();
        return rangeMap.get(block.getParentFunction()).getValueRange(value, block);
    }
    // endregion

    // region FuncInfo
    public static FuncInfo getFuncInfo(Function function) {
        if (!funcInfoMap.containsKey(function)) {
            throw new RuntimeException("Function not found");
        }
        return funcInfoMap.get(function);
    }

    public static void setFuncInfo(Function function) {
        funcInfoMap.put(function, new FuncInfo(function));
    }
    // endregion

    // region AlignMap
    public static AlignmentAnalysis.AlignMap getAlignMap() {
        return alignMap;
    }

    public static void setAlignMap(AlignmentAnalysis.AlignMap map) {
        alignMap = map;
    }
    // endregion
}