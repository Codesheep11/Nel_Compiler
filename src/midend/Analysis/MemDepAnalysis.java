package midend.Analysis;

import midend.Util.FuncInfo;
import mir.BasicBlock;
import mir.Function;
import mir.Instruction;
import mir.Value;

import java.util.HashSet;

public class MemDepAnalysis {
    // 做分析可以得到PathSet集合
    // 分析出每个块对A,B,A到B可能经过的块集合
    //

    private static final int max_block = 500;


    public static boolean assureNotWritten(Function function, BasicBlock A, BasicBlock B, Value pointer) {
        if (!pointer.getType().isPointerTy()) throw new RuntimeException("wrong type");
        Value base = PointerBaseAnalysis.getBaseOrNull(pointer);
        if (base == null) return false;
        if (function.getBlocks().size() > max_block) return false;
        HashSet<BasicBlock> visited = new HashSet<>();
        HashSet<BasicBlock> pass = new HashSet<>();
        visited.add(A);
        if (!dfs(A, B, visited, pass)) return false;
        pass.add(A);
        pass.add(B);
        for (BasicBlock block : pass) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Instruction.Store store) {
                    Value storeBase = PointerBaseAnalysis.getBaseOrNull(store.getAddr());
                    if (storeBase == null || storeBase.equals(base)) {
                        return false;
                    }
                } else if (instruction instanceof Instruction.Call call) {
                    FuncInfo funcInfo = AnalysisManager.getFuncInfo(call.getDestFunction());
                    if (funcInfo.hasMemoryWrite || funcInfo.hasSideEffect) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
    private static boolean dfs(BasicBlock start, BasicBlock target, HashSet<BasicBlock> visited, HashSet<BasicBlock> pass) {
        boolean found = false;
        if (start == target) {
            pass.addAll(visited);
            found = true;
        }
        if (visited.contains(start)) return found;
        for (BasicBlock block : start.getSucBlocks()) {
            visited.add(block);
            found |= dfs(block, target, visited, pass);
            visited.remove(block);
        }
        return found;
    }
}

