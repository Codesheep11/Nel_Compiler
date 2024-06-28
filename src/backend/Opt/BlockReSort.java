package backend.Opt;

import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.B;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import utils.Pair;

import java.util.*;
import java.util.function.Function;

public class BlockReSort {
    /**
     * 重排序原则：尽量减少j指令，且尽量把块都放一起
     * 比如可以将j的指令重排
     **/
    public static final String blockPlacementAlgo = "Pettis-Hansen";

    static class NodeIndex {
        public int value;

        public NodeIndex(int value) {
            this.value = value;
        }
    }

    static class CostT {
        public double value;

        public CostT(double value) {
            this.value = value;
        }
    }

    static class BranchEdge {
        public int source;
        public int target;
        public double prob;

        public BranchEdge(int source, int target, double prob) {
            this.source = source;
            this.target = target;
            this.prob = prob;
        }
    }

    static class BlockSeq extends ArrayList<Integer> {
    }

    static CostT evalExtTspScore(BlockSeq seq, List<Integer> weights, List<Double> freq, List<BranchEdge> edges) {
        throw new UnsupportedOperationException("Not implemented");
    }

    static BlockSeq solveExtTsp(List<Integer> weights, List<Double> freq, List<BranchEdge> edges) {
        throw new UnsupportedOperationException("Not implemented");
    }

    static BlockSeq solvePettisHansen(List<Integer> weights, List<Double> freq, List<BranchEdge> edges) {
        int blockCount = weights.size();

        // Stage1: chain decomposition
        List<Integer> fa = new ArrayList<>(blockCount);
        List<Map.Entry<Integer, LinkedList<Integer>>> chains = new ArrayList<>(blockCount);
        for (int idx = 0; idx < blockCount; ++idx) {
            chains.add(new AbstractMap.SimpleEntry<>(Integer.MAX_VALUE, new LinkedList<>(Collections.singletonList(idx))));
            fa.add(idx);
        }

        List<Map.Entry<BranchEdge, Double>> edgeInfo = new ArrayList<>(edges.size());
        for (BranchEdge edge : edges) {
            edgeInfo.add(new AbstractMap.SimpleEntry<>(edge, freq.get(edge.source) * edge.prob));
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

        List<List<Integer>> graph = new ArrayList<>(Collections.nCopies(blockCount, new ArrayList<>()));
        int p = 0;

        for (Map.Entry<BranchEdge, Double> entry : edgeInfo) {
            BranchEdge e = entry.getKey();
            graph.get(e.source).add(e.target);

            if (e.source == e.target) continue;

            int fv = findFa.apply(e.target);
            if (fv != e.target) continue;

            int fu = findFa.apply(e.source);
            if (fu == e.target) continue;

            // 获取链表并进行合并
            Map.Entry<Integer, LinkedList<Integer>> chainUEntry = chains.get(fu);
            Map.Entry<Integer, LinkedList<Integer>> chainVEntry = chains.get(fv);

            // 检查链U的最后一个元素是否为源节点
            if (!chainUEntry.getValue().getLast().equals(e.source)) continue;

            // 获取当前的链表和值
            int chainUKey = chainUEntry.getKey();
            int chainVKey = chainVEntry.getKey();
            LinkedList<Integer> chainUList = chainUEntry.getValue();
            LinkedList<Integer> chainVList = chainVEntry.getValue();

            // 设置链U的新键值，并将链V的所有元素添加到链U
            chainUKey = Math.min(Math.min(chainUKey, chainVKey), ++p);
            chainUList.addAll(chainVList);

            // 更新链U的键值对
            chains.set(fu, new AbstractMap.SimpleEntry<>(chainUKey, chainUList));

            // 更新fa数组
            fa.set(fv, fu);
        }


        // Stage2: code layout
        if (findFa.apply(0) != 0) throw new AssertionError("Entry block not found");

        PriorityQueue<Integer> workList = new PriorityQueue<>(
                Comparator.comparingInt(fu -> chains.get(fu).getKey())
        );

        Set<Integer> inserted = new HashSet<>();
        Set<Integer> insertedWorkList = new HashSet<>();
        workList.add(0);
        insertedWorkList.add(0);

        BlockSeq seq = new BlockSeq();
        seq.ensureCapacity(blockCount);

        while (!workList.isEmpty()) {
            int k = workList.poll();
            for (int u : chains.get(k).getValue()) {
                if (inserted.add(u)) seq.add(u);
            }
            for (int u : chains.get(k).getValue()) {
                for (int v : graph.get(u)) {
                    if (inserted.contains(v)) continue;
                    int head = findFa.apply(v);
                    if (insertedWorkList.add(head)) workList.add(head);
                }
            }
        }
        return seq;
    }

    static CostT evalCost(BlockSeq seq, List<Integer> invMap, List<BranchEdge> edges, List<Double> freq, List<Integer> weights, int bufferSize) {
        if (seq.get(0) != 0) throw new AssertionError("Entry block is not at the beginning");
        for (int idx = 0; idx < seq.size(); ++idx) {
            invMap.set(seq.get(idx), idx);
        }

        double cost = 0.0;
        double totalJumpSize = 0.0;

        final int branchPenalty = 5;
        final int bufferFlushPenalty = 100;

        for (BranchEdge edge : edges) {
            int p1 = invMap.get(edge.source);
            int p2 = invMap.get(edge.target);
            double w = edge.prob;

            boolean backward = false;
            if (p1 >= p2) {
                int temp = p1;
                p1 = p2;
                p2 = temp + 1;
                backward = true;
            }

            int jumpSize = 0;
            for (int i = p1 + 1; i < p2; ++i) {
                jumpSize += weights.get(seq.get(i));
            }
            totalJumpSize += jumpSize * w;

            if (jumpSize > bufferSize || backward) {
                cost += bufferFlushPenalty * w;
            } else if (jumpSize != 0) {
                cost += branchPenalty * w;
            }
        }
        cost += 1.0 / (1.0 + Math.exp(-totalJumpSize));
        return new CostT(cost);
    }

    public static void solveBruteForce(BlockSeq seq, List<BranchEdge> edges, List<Double> freq, List<Integer> weights, int bufferSize) {
        List<Integer> invMap = new ArrayList<>(Collections.nCopies(seq.size(), 0));
        BlockSeq best = new BlockSeq();
        best.addAll(seq);  // 正确初始化 best
        CostT bestCost = new CostT(1e10);

        do {
            CostT cost = evalCost(seq, invMap, edges, freq, weights, bufferSize);
            if (cost.value < bestCost.value) {
                bestCost = cost;
                best.clear();
                best.addAll(seq);  // 正确复制 seq 到 best
            }
        } while (nextPermutation(seq));

        seq.clear();
        seq.addAll(best);
    }

    static boolean nextPermutation(List<Integer> list) {
        int n = list.size();
        if (n < 2) return false;

        int k = n - 2;
        while (k >= 0 && list.get(k) >= list.get(k + 1)) k--;
        if (k < 0) return false;

        int l = n - 1;
        while (list.get(l) <= list.get(k)) l--;
        Collections.swap(list, k, l);

        Collections.reverse(list.subList(k + 1, n));
        return true;
    }

    static CostT localSearch(BlockSeq seq, List<Integer> invMap, List<BranchEdge> edges, List<Double> freq, List<Integer> weights, int bufferSize, Random urbg) {
        final int mutateCount = 100;
        CostT bestCost = evalCost(seq, invMap, edges, freq, weights, bufferSize);
        for (int i = 0; i < mutateCount; ++i) {
            int p1 = urbg.nextInt(seq.size() - 1) + 1;
            int p2 = urbg.nextInt(seq.size() - 1) + 1;
            if (p1 == p2) continue;
            Collections.swap(seq, p1, p2);
            CostT cost = evalCost(seq, invMap, edges, freq, weights, bufferSize);
            if (cost.value < bestCost.value) {
                bestCost = cost;
            } else {
                Collections.swap(seq, p1, p2);
            }
        }
        return bestCost;
    }

    public static void solveGA(BlockSeq seq, List<BranchEdge> edges, List<Double> freq, List<Integer> weights, int bufferSize) {
        Random urbg = new Random(998244353L);

        final int popSize = 20;
        final int crossCount = 5;
        final int iteration = 50;

        List<Integer> invMap = new ArrayList<>(Collections.nCopies(seq.size(), 0));
        List<Map.Entry<BlockSeq, CostT>> pop = new ArrayList<>();

        for (int i = 0; i < popSize; ++i) {
            CostT cost = evalCost(seq, invMap, edges, freq, weights, bufferSize);
            BlockSeq seqCopy = new BlockSeq();
            seqCopy.addAll(seq);
            pop.add(new AbstractMap.SimpleEntry<>(seqCopy, cost));
            Collections.shuffle(seq.subList(1, seq.size()), urbg);
        }

        for (int i = 0; i < iteration; ++i) {
            for (int j = 0; j < crossCount; ++j) {
                int p1 = urbg.nextInt(pop.size());
                int p2 = urbg.nextInt(pop.size());
                if (p1 == p2) continue;

                int start = urbg.nextInt(seq.size() - 1) + 1;
                int end = urbg.nextInt(seq.size() - 1) + 1;
                if (start > end) {
                    int temp = start;
                    start = end;
                    end = temp;
                }

                BlockSeq newSeq = new BlockSeq();
                newSeq.addAll(pop.get(p1).getKey());
                boolean[] used = new boolean[seq.size()];

                for (int k = 0; k < start; ++k) used[newSeq.get(k)] = true;
                for (int k = end; k < seq.size(); ++k) used[newSeq.get(k)] = true;

                int freePos = start;
                BlockSeq seq2 = pop.get(p2).getKey();

                for (int k = 0; k < seq.size(); ++k) {
                    if (!used[seq2.get(k)]) newSeq.set(freePos++, seq2.get(k));
                }

                CostT cost = localSearch(newSeq, invMap, edges, freq, weights, bufferSize, urbg);
                pop.add(new AbstractMap.SimpleEntry<>(newSeq, cost));
            }

            pop.sort(Comparator.comparingDouble(e -> e.getValue().value));

            if (pop.size() > popSize) {
                pop = pop.subList(0, popSize);
            }
        }

        seq.clear();
        seq.addAll(pop.get(0).getKey());
    }

    public static void optimizeBlockLayout(RiscvFunction func) {
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
            weights.add(block.riscvInstructions.getSize());
        }
        idx = 0;
        for (RiscvBlock block : func.blocks) {
            int blockIdx = idx++;
            freq.add(blockFreq.get(block));
            for (Pair<RiscvBlock, Double> pair : cfg.get(block).suc) {
                edges.add(new BranchEdge(blockIdx, idxMap.get(pair.first), pair.second));
            }
        }
        BlockSeq seq;
        if (blockPlacementAlgo.equals("GA")) {
            seq = new BlockSeq();
            for (int i = 0; i < func.blocks.size(); ++i) seq.add(i);
            int bufferSize = 128;
            if (seq.size() <= 10) {
                solveBruteForce(seq, edges, freq, weights, bufferSize);
            } else {
                solveGA(seq, edges, freq, weights, bufferSize);
            }
        } else if (blockPlacementAlgo.equals("Pettis-Hansen")) {
            seq = solvePettisHansen(weights, freq, edges);
        } else if (blockPlacementAlgo.equals("ExtTSP")) {
            seq = solveExtTsp(weights, freq, edges);
        } else {
            throw new IllegalArgumentException("Unrecognized block placement method: " + blockPlacementAlgo);
        }

        if (seq.get(0) != 0) throw new AssertionError("Entry block is not at the beginning");

        List<RiscvBlock> newBlocks = new ArrayList<>(func.blocks.size());
        newBlocks.addAll(func.blocks);
        func.blocks.clear();
        // 重新排列
        for (int i : seq) func.blocks.add(newBlocks.get(i));

        // 下面就是在解决跳转问题
        // 解决策略:如果下一个块就是直接跳转的target,那么就去掉跳转指令
        // 如果下一个块不是默认的target,且最后一个不是跳往的指令，那么就应该加上
        for (int i = 0; i < func.blocks.size(); i++) {
            RiscvBlock block = func.blocks.get(i);
            HashSet<RiscvBlock> targets = new HashSet<>();
            for (Pair<RiscvBlock, Double> pair : cfg.get(block).suc) {
                targets.add(pair.first);
                // 将所有目的加入其中
            }
            boolean removeLast = false;// 是否要删除最后一条指令
            for (RiscvInstruction ri : block.riscvInstructions) {
                // 检查B,将所有的可能的块收入其中
                if (ri instanceof B) {
                    targets.remove(((B) ri).targetBlock);
                    if (ri == block.getLast()) {
                        if (func.blocks.get(i + 1) == ((B) ri).targetBlock) {
                            removeLast = true;
                            break;
                        }
                    }
                }
            }
            // 如果是最后一个指令
            RiscvInstruction ri = block.getLast();
            if (ri instanceof J && ((J) block.getLast()).type != J.JType.ret) {
                // 如果最后一个是J,不是而J的目标是下一个block的话,
                targets.remove(((J) ri).targetBlock);
                if (func.blocks.size() > i + 1) {
                    if (func.blocks.get(i + 1) == ((J) ri).targetBlock) {
                        removeLast = true;
                    }
                }
            }
            if (targets.size() == 1) {
                // 如果最后一个指令还有剩余
                block.addInstrucion(new J(targets.iterator().next(), J.JType.j));
            } else if (targets.size() >= 1) {
                throw new RuntimeException("too more target block");
            }
            if (removeLast) {
                block.riscvInstructions.removeLast();
            }
        }
    }
}