package midend.Util;

import mir.BasicBlock;
import mir.Function;
import midend.Analysis.result.DGinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Lengauer-Tarjan 算法求支配树
 * <p>
 *
 * @see <a href="https://dl.acm.org/doi/pdf/10.1145/357062.357071">Lengauer-Tarjan论文原文</a>
 * @see <a href="https://zerol.me/2018/10/22/dominator-tree">Lengauer-Tarjan算法博客</a>
 * @see <a href="">李煜东：图连通性若干拓展问题探讨</a>
 */
public class DominanceGraphLT {

    private static Function parentFunction;
    private static BasicBlock entry;
    private static DGinfo dginfo;
    private static final HashMap<BasicBlock, Node> nodeMap = new HashMap<>();
    private static int timer = 0;
    private static Node[] id;

    public static DGinfo runOnFunc(Function function) {
        dginfo = new DGinfo(function);
        parentFunction = function;
        entry = parentFunction.getEntry();
        build();
        return dginfo;
    }

    private static void build() {
//        RemoveBlocks.runOnFunc(parentFunction);
        clear();
        for (var block : parentFunction.getBlocks()) {
            nodeMap.put(block, new Node(block));
        }
        dfs(nodeMap.get(entry));
        LengauerTarjan();
        dfs4fill(nodeMap.get(entry), new HashSet<>(), 0);
    }

    private static void clear() {
        nodeMap.clear();
        timer = 0;
        id = new Node[parentFunction.getBlocks().size() + 10];
    }

    private static void dfs(Node node) {
        ++timer;
        node.dfn = timer;
        id[node.dfn] = node;
        for (var child : node.block.getSucBlocks()) {
            Node childNode = nodeMap.get(child);
            if (childNode.dfn == -1) {
                childNode.parent = node;
                dfs(childNode);
            }
        }
    }

    private static void LengauerTarjan() {
        for (int i = timer; i >= 1; --i) {
            Node now = id[i];
            for (var v : now.bucket) {
                WeightedDisjointSetUnion.get(v);
                if (v.dsuNode.weight.semi == now) {
                    v.idom = now;
                } else {
                    v.idom = v.dsuNode.weight;
                }
            }
            now.bucket.clear();
            if (i == 1) continue;
            for (var pre : now.block.getPreBlocks()) {
                Node u = nodeMap.get(pre);
                if (u.dfn == -1) continue; // 存在不可达点
                if (u.dfn < now.dfn) {
                    if (u.dfn < now.semi.dfn)
                        now.semi = u;
                } else if (u.dfn > now.dfn) {
                    WeightedDisjointSetUnion.get(u);
                    if (u.dsuNode.weight.semi.dfn < now.semi.dfn) {
                        now.semi = u.dsuNode.weight.semi;
                    }
                }
            }
            now.semi.bucket.add(now);
            // 维护并查集
            now.dsuNode.father = now.parent.dsuNode;
        }
        for (int i = 2; i <= timer; ++i) {
            Node now = id[i];
            if (now.semi != now.idom) {
                now.idom = now.idom.idom;
            }
            now.idom.bucket.add(now);
        }
        id[1].idom = null; // entry
    }

    private static void dfs4fill(Node now, HashSet<BasicBlock> parents, int depth) {
        now.hasDumped = true;
        BasicBlock nowBlock = now.block;
        DGinfo._DG_Block_Info nowInfo = dginfo.getInfo(nowBlock);

        nowInfo.domDepth = depth;
        if (nowBlock != entry)
            nowInfo.idom = now.idom.block;
        parents.add(nowBlock);
        nowInfo.dominators.addAll(parents);
        for (var node : now.bucket) {
            BasicBlock nodeBlock = node.block;
            nowInfo.domTreeChildren.add(nodeBlock);
            if (!node.hasDumped) {
                dfs4fill(node, parents, depth + 1);
            }
        }
        parents.remove(nowBlock);
    }

    private static class Node {
        Node parent; // dfs
        Node semi;
        Node idom;
        final BasicBlock block;
        int dfn;
        final ArrayList<Node> bucket = new ArrayList<>(); // 以当前节点为半支配点的节点
        final WeightedDisjointSetUnion.DsuNode dsuNode;
        boolean hasDumped;

        Node(BasicBlock block) {
            this.block = block;
            this.dfn = -1;
            this.semi = this;
            this.dsuNode = new WeightedDisjointSetUnion.DsuNode(this);
            this.hasDumped = false;
        }
    }

    private static class WeightedDisjointSetUnion {

        private static class DsuNode {
            DsuNode father;
            Node weight; // 权重的含义: 该点到当前祖先中拥有最小的semi节点
            final Node represent;

            public DsuNode(Node node) {
                this.father = this;
                this.weight = node;
                this.represent = node;
            }
        }

        public static Node get(Node x) {
            DsuNode dsuNode = x.dsuNode;
            return find(dsuNode).represent;
        }

        public static DsuNode find(DsuNode x) {
            if (x.father == x) return x;
            DsuNode y = find(x.father);
            // 路径压缩 同时维护权重
            if (x.father.weight.semi.dfn < x.weight.semi.dfn) x.weight = x.father.weight;
            x.father = y;
            return y;
        }
    }
}