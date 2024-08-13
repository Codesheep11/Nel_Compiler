package midend.Analysis;

import midend.Util.FuncInfo;
import mir.BasicBlock;
import mir.Function;
import mir.Instruction;
import mir.Value;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

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
        HashSet<BasicBlock> set = bfs(A, B);
        if (set == null) return false;
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


    private static HashSet<BasicBlock> bfs(BasicBlock start, BasicBlock target) {
        HashSet<BasicBlock> passingBlocks = new HashSet<>();
        Queue<BasicBlock> queue = new LinkedList<>();
        HashSet<BasicBlock> visitedBlocks = new HashSet<>();
        queue.add(start);
        visitedBlocks.add(start);
        boolean found = false;
        while (!queue.isEmpty()) {
            BasicBlock current = queue.poll();
            passingBlocks.add(current);
            if (current.equals(target)) {
                // 如果找到了目标块，可以选择立即返回，也可以继续搜索所有路径
                found = true;
                continue;  // 在这里继续搜索以确保找到所有可能路径
            }
            for (BasicBlock next : current.getSucBlocks()) {
                if (!visitedBlocks.contains(next)) {
                    queue.add(next);
                    visitedBlocks.add(next);
                }
            }
        }
        return found ? passingBlocks : null;
    }

}

