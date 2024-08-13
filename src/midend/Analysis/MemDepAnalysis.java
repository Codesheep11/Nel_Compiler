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
        HashSet<BasicBlock> set = queryPasser(A, B);
        set.add(A);
        set.add(B);
        for (BasicBlock block : set) {
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

    private static HashSet<BasicBlock> queryPasser(BasicBlock A, BasicBlock B) {
        HashSet<BasicBlock> visitedBlocks = new HashSet<>();
        HashSet<BasicBlock> passingBlocks = new HashSet<>();

        if (dfs(A, B, visitedBlocks, passingBlocks)) {
            passingBlocks.remove(A);  // A不应该在passingBlocks中
        }

        return passingBlocks;
    }

    private static boolean dfs(BasicBlock current, BasicBlock target, HashSet<BasicBlock> visitedBlocks, HashSet<BasicBlock> passingBlocks) {
        if (current.equals(target)) {
            return true;
        }
        visitedBlocks.add(current);
        for (BasicBlock next : current.getSucBlocks()) {
            if (!visitedBlocks.contains(next)) {
                passingBlocks.add(next);

                if (dfs(next, target, visitedBlocks, passingBlocks)) {
                    return true;  // 找到目标块，终止搜索
                } else {
                    passingBlocks.remove(next);  // 移除不通向目标块的路径上的块
                }
            }
        }
        return false;  // 没有找到目标块
    }

}

