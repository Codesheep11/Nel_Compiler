package midend.Analysis;

import midend.Util.FuncInfo;
import mir.BasicBlock;
import mir.Function;
import mir.Instruction;
import mir.Value;

import java.util.ArrayList;
import java.util.HashSet;

public class MemDepAnalysis {
    // 分析出每个块对A,B,A到B可能经过的块集合
    // 策略:从B开始倒着查所有可能到B的块，再从A正着查所有A可能到的块，这俩的交集就是可能经过的块的集合


    public static boolean assureNotWritten(Function function, BasicBlock A, BasicBlock B, Value pointer) {
        if (!pointer.getType().isPointerTy()) throw new RuntimeException("wrong type");
        Value base = PointerBaseAnalysis.getBaseOrNull(pointer);
        if (base == null) return false;
        HashSet<BasicBlock> visitedA = new HashSet<>();
        HashSet<BasicBlock> visitedB = new HashSet<>();
        visitedA.add(A);
        visitedB.add(B);
        dfs(A, visitedA, false);
        dfs(B, visitedB, true);
        visitedA.retainAll(visitedB);
        if (visitedA.isEmpty()) return false;
        for (BasicBlock block : visitedA) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Instruction.Store store) {
                    Value storeBase = PointerBaseAnalysis.getBaseOrNull(store.getAddr());
                    if (storeBase == null || storeBase.equals(base)) {
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
        return true;
    }

    private static void dfs(BasicBlock block, HashSet<BasicBlock> visited, boolean reverse) {
        ArrayList<BasicBlock> todo = reverse ? block.getPreBlocks() : block.getSucBlocks();
        for (BasicBlock next : todo) {
            if (visited.contains(next)) continue;
            visited.add(next);
            dfs(next, visited, reverse);
        }
    }
}

