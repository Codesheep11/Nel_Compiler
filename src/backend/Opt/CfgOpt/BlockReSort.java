package backend.Opt.CfgOpt;

import backend.Opt.CfgOpt.BackCFGNode;
import backend.Opt.CfgOpt.GenCFG;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvModule;
import utils.Pair;

import java.util.*;
import java.util.function.Function;

/**
 * 重新排列基本块
 *
 * @see <a href="https://www.cs.cornell.edu/courses/cs6120/2019fa/blog/codestitcher/">CodeStitcher</a>
 */
public class BlockReSort {

    static class BranchEdge {
        public final int source;
        public final int target;
        public final double prob;

        public BranchEdge(int source, int target, double prob) {
            this.source = source;
            this.target = target;
            this.prob = prob;
        }
    }


    private static ArrayList<Integer> solvePettisHansen(List<Integer> weights, List<Double> freq, List<BranchEdge> edges) {
        int blockCount = weights.size();
        // Stage1: chain decomposition
        ArrayList<Integer> fa = new ArrayList<>(blockCount);
        ArrayList<Pair<Integer, ArrayList<Integer>>> chains = new ArrayList<>(blockCount);
        for (int idx = 0; idx < blockCount; ++idx) {
            chains.add(new Pair<>(Integer.MAX_VALUE, new ArrayList<>(Collections.singletonList(idx))));
            fa.add(idx);
        }
        ArrayList<Pair<BranchEdge, Double>> edgeInfo = new ArrayList<>(edges.size());
        for (BranchEdge edge : edges) {
            edgeInfo.add(new Pair<>(edge, freq.get(edge.source) * edge.prob));
        }
        Function<Integer, Integer> findFa = new Function<>() {
            @Override
            public Integer apply(Integer u) {
                if (Objects.equals(fa.get(u), u)) return u;
                fa.set(u, apply(fa.get(u)));
                return fa.get(u);
            }
        };
        edgeInfo.sort((lhs, rhs) -> Double.compare(rhs.getValue(), lhs.getValue()));
        ArrayList<ArrayList<Integer>> graph = new ArrayList<>(Collections.nCopies(blockCount, new ArrayList<>()));
        int p = 0;
        for (Pair<BranchEdge, Double> entry : edgeInfo) {
            BranchEdge e = entry.first;
            int u=e.source;
            int v=e.target;
            if (u==v) continue;
            graph.get(u).add(v);
            Pair<Integer, ArrayList<Integer>> pv_cv = chains.get(v);
            if (findFa.apply(v)!= v) continue;
            int fu = findFa.apply(u);
            if (fu == v) continue;
            Pair<Integer, ArrayList<Integer>> pu_cu = chains.get(fu);
            if (!pu_cu.second.get(pu_cu.second.size()-1).equals(u)) continue;
            pu_cu.first=Math.min(Math.min(pu_cu.first,pv_cv.first),++p);
            pu_cu.second.addAll(pv_cv.second);
            fa.set(v, fu);
        }
        if (findFa.apply(0) != 0) throw new AssertionError("Entry block not found");
        PriorityQueue<Integer> workList = new PriorityQueue<>(
                Comparator.comparingInt(fu -> chains.get(fu).getKey())
        );
        HashSet<Integer> inserted = new HashSet<>();
        HashSet<Integer> insertedWorkList = new HashSet<>();
        workList.add(0);
        insertedWorkList.add(0);
        ArrayList<Integer> seq = new ArrayList<>();
        seq.ensureCapacity(blockCount);
        while (!workList.isEmpty()) {
            int k = workList.poll();
            for (int u : chains.get(k).second) {
                if (inserted.add(u)) seq.add(u);
            }
            for (int u : chains.get(k).second) {
                for (int v : graph.get(u)) {
                    if (inserted.contains(v)) continue;
                    int head = findFa.apply(v);
                    if (insertedWorkList.add(head)) workList.add(head);
                }
            }
        }
        return seq;
    }public static void blockSort(RiscvModule module) {
        for (RiscvFunction function : module.funcList) {
            if (function.isExternal) continue;
            optimizeBlockLayout(function);
        }
    }

    private static void optimizeBlockLayout(RiscvFunction func) {
        if (func.blocks.size() <= 2) return;
        HashMap<RiscvBlock, BackCFGNode> cfg = GenCFG.calcCFG(func);
        List<Integer> weights = new ArrayList<>(func.blocks.size());
        List<BranchEdge> edges = new ArrayList<>();
        HashMap<RiscvBlock, Integer> idxMap = new HashMap<>();
        List<Double> freq = new ArrayList<>();
        HashMap<RiscvBlock, Double> blockFreq = GenCFG.callFreq(func, cfg);
        int idx = 0;
        for (RiscvBlock block : func.blocks) {
            idxMap.put(block, idx++);
            weights.add(block.riscvInstructions.size());
        }
        idx = 0;
        for (RiscvBlock block : func.blocks) {
            int blockIdx = idx++;
            freq.add(blockFreq.get(block));
            for (Map.Entry<RiscvBlock, Double> pair : cfg.get(block).suc.entrySet()) {
                edges.add(new BranchEdge(blockIdx, idxMap.get(pair.getKey()), pair.getValue()));
            }
        }
        ArrayList<Integer> seq=solvePettisHansen(weights, freq, edges);
        if (seq.get(0) != 0) throw new AssertionError("Entry block is not at the beginning");
        List<RiscvBlock> newBlocks = new ArrayList<>(func.blocks.size());
        newBlocks.addAll(func.blocks);
        func.blocks.clear();
        // 重新排列
        for (int i : seq) func.blocks.add(newBlocks.get(i));
    }
}
