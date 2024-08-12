package midend.Analysis;

import midend.Util.FuncInfo;
import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class MemDepAnalysis {
    // 做分析可以得到PathSet集合
    // 分析出每个块对A,B,A到B可能经过的块集合
    //

    private static class PathSet {
        private final ArrayList<ArrayList<HashSet<Integer>>> set = new ArrayList<>();

        private final HashMap<BasicBlock, Integer> blockIndex = new HashMap<>();

        private Integer getId(BasicBlock block) {
            return blockIndex.get(block);
        }
    }

    private static final HashMap<Function, PathSet> fps = new HashMap<>();


    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static boolean assureNotWritten(Function function, BasicBlock A, BasicBlock B, Value pointer) {
        if (!pointer.getType().isPointerTy()) throw new RuntimeException("wrong type");
        PathSet ps = fps.get(function);
        Integer i = ps.getId(A);
        Integer j = ps.getId(B);
        Value base = PointerBaseAnalysis.getBaseOrNull(pointer);
        if (base == null) throw new RuntimeException("can't find base");
        for (Integer k : ps.set.get(i).get(j)) {
            BasicBlock block = function.getBlocks().get(k);
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Instruction.Store store) {
                    if (PointerBaseAnalysis.getBaseOrNull(store.getAddr()).equals(base)) {
                        return false;
                    }
                }
                else if (instruction instanceof Instruction.Call call) {
                    FuncInfo funcInfo = AnalysisManager.getFuncInfo(call.getDestFunction());
                    if (funcInfo.hasMemoryWrite || funcInfo.hasSideEffect) {
                        return false;
                    }
                }
            }
        }
        for (Instruction instruction : A.getInstructions()) {
            if (instruction instanceof Instruction.Store store) {
                if (PointerBaseAnalysis.getBaseOrNull(store.getAddr()).equals(base)) {
                    return false;
                }
            }
            else if (instruction instanceof Instruction.Call call) {
                FuncInfo funcInfo = AnalysisManager.getFuncInfo(call.getDestFunction());
                if (funcInfo.hasMemoryWrite || funcInfo.hasSideEffect) {
                    return false;
                }
            }
        }
        for (Instruction instruction : B.getInstructions()) {
            if (instruction instanceof Instruction.Store store) {
                if (PointerBaseAnalysis.getBaseOrNull(store.getAddr()).equals(base)) {
                    return false;
                }
            }
            else if (instruction instanceof Instruction.Call call) {
                FuncInfo funcInfo = AnalysisManager.getFuncInfo(call.getDestFunction());
                if (funcInfo.hasMemoryWrite || funcInfo.hasSideEffect) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void runOnFunc(Function function) {
        PathSet ps = new PathSet();
        int n = function.getBlocks().size();
        fps.put(function, ps);
        for (int i = 0; i < n; i++) {
            ps.blockIndex.put(function.getBlocks().get(i), i);
        }

        // Initialize reachability and intermediate nodes
        boolean[][] reach = new boolean[n][n];

        for (int i = 0; i < n; i++) {
            ps.set.add(new ArrayList<>());
            for (int j = 0; j < n; j++) {
                ps.set.get(i).add(new HashSet<>());
            }
        }

        // Setup initial reachability based on direct edges
        for (BasicBlock block : function.getBlocks()) {
            int u = ps.blockIndex.get(block);
            for (BasicBlock succ : block.getSucBlocks()) {
                int v = ps.blockIndex.get(succ);
                reach[u][v] = true;
            }
        }

        // Compute the transitive closure and track intermediate nodes
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (reach[i][k] && reach[k][j]) {
                        if (!reach[i][j]) {
                            reach[i][j] = true;
                        }
                        ps.set.get(i).get(j).add(k);
                        ps.set.get(i).get(j).addAll(ps.set.get(i).get(k));
                        ps.set.get(i).get(j).addAll(ps.set.get(k).get(j));
                    }
                }
            }
        }
    }
}

