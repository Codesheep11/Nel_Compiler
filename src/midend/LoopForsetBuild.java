package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Module;

import java.util.*;

public class LoopForsetBuild {
    public static void build(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.getBlocks().getSize() == 0) {
                continue;
            }
            function.buildDominanceGraph();
            buildLoopNestTree(function.getEntry());
//            function.printLoops();
        }
    }

    /**
     * 以entry为入口，构建循环嵌套树
     *
     * @param entry
     */
    public static void buildLoopNestTree(BasicBlock entry) {
        Function func = entry.getParentFunction();
        LinkedList<BasicBlock> visited = new LinkedList<>();
        visited.add(entry);
        for (BasicBlock block : entry.getDomTreeChildren()) {
            dfs(block, visited);
        }
        //合并所有header相同的循环
        HashMap<BasicBlock, ArrayList<Loop>> headerLoops = new HashMap<>();
        for (Loop loop : func.loops) {
            if (!headerLoops.containsKey(loop.header)) {
                headerLoops.put(loop.header, new ArrayList<>());
            }
            headerLoops.get(loop.header).add(loop);
        }
        for (BasicBlock header : headerLoops.keySet()) {
            ArrayList<Loop> loops = headerLoops.get(header);
            Loop base = loops.get(0);
            for (int i = 1; i < loops.size(); i++) {
                Loop loop = loops.get(i);
                base.mergeLoop(loop);
                func.loops.remove(loop);
            }
        }
        headerLoops.clear();
        //构建循环嵌套树
        HashSet<Loop> forest = new HashSet<>();
        for (Loop loop : func.loops) {
            boolean newTree = true;
            for (Loop root : forest) {
                if (root.LoopContains(loop.header)) {
                    insertLoopToTree(root, loop);
                    newTree = false;
                    break;
                }
                if (loop.LoopContains(root.header)) {
                    insertLoopToTree(loop, root);
                    forest.remove(root);
                    forest.add(loop);
                    newTree = false;
                    break;
                }
            }
            if (newTree) forest.add(loop);
        }
        func.loops = forest;
        //深度优先搜索访问循环森林，更新blocks
        for (Loop root : forest) {
            dfsLoopTree(root);
        }
    }

    /**
     * 对循环树进行深度优先搜索遍历，更新循环的blocks
     *
     * @param root
     */
    private static void dfsLoopTree(Loop root) {
        for (Loop child : root.children) {
            dfsLoopTree(child);
            root.blocks.removeAll(child.blocks);
        }
    }

    /**
     * 将循环插入循环森林
     *
     * @param root
     * @param loop
     */
    private static void insertLoopToTree(Loop root, Loop loop) {
        boolean newTree = true;
        for (Loop child : root.children) {
            if (child.LoopContains(loop.header)) {
                insertLoopToTree(child, loop);
                newTree = false;
                break;
            }
            if (loop.LoopContains(child.header)) {
                root.children.remove(child);
                root.children.add(loop);
                loop.parent = root;
                loop.children.add(child);
                child.parent = loop;
                root.blocks.removeAll(loop.blocks);
                loop.blocks.removeAll(child.blocks);
                newTree = false;
                break;
            }
        }
        if (newTree) {
            root.children.add(loop);
            loop.parent = root;
        }
    }

    /**
     * 对支配树进行深度优先搜索遍历，通过回边找到所有循环
     *
     * @param block
     * @param visited
     */
    public static void dfs(BasicBlock block, LinkedList<BasicBlock> visited) {
        if (visited.contains(block)) {
            return;
        }
//        String out = "path:";
//        for (BasicBlock vis : visited) {
//            out = out + vis.getLabel() + " ";
//        }
//        out = out + block.getLabel();
//        System.out.println(out);
//        for (Loop loop : block.getParentFunction().loops) {
//            loop.printLoopInfo();
//        }
        Function func = block.getParentFunction();
        //循环判定
        for (BasicBlock vis : visited) {
            if (block.getSucBlocks().contains(vis)) {
                //存在回边
                Loop loop = new Loop(vis, block);
//                loop.blocks.addAll(visited);
//                for (BasicBlock rm : visited) {
//                    if (rm.equals(vis)) break;
//                    loop.blocks.remove(rm);
//                }
                findLoopBlocks(loop, loop.header);
                for (BasicBlock bb : loop.blocks) {
                    //这里认为通向内层循环不算exits todo
                    if (loop.blocks.containsAll(bb.getSucBlocks())) continue;
                    loop.exitings.add(bb);
                    HashSet<BasicBlock> outs = new HashSet<>(bb.getSucBlocks());
                    outs.removeAll(loop.blocks);
                    loop.exits.addAll(outs);
                }
                func.loops.add(loop);
            }
        }
        visited.add(block);
        for (BasicBlock suc : block.getDomTreeChildren()) {
            dfs(suc, visited);
        }
        visited.remove(block);
    }

    /**
     * 从循环头开始，dfs寻找循环中的基本块
     *
     * @param loop
     * @param cur
     */
    private static void findLoopBlocks(Loop loop, BasicBlock cur) {
//        System.out.println("find loopBlocks: " + header.getLabel());
        BasicBlock header = loop.header;
        for (BasicBlock child : cur.getDomTreeChildren()) {
            HashSet<BasicBlock> visited = new HashSet<>();
            visited.add(header);
            if (isReachable(child, loop.backEdges.get(0), visited)) {
                loop.blocks.add(child);
                findLoopBlocks(loop, child);
            }
        }
    }

    /**
     * cfg图的dfs判断基本块A是否可达基本块B
     *
     * @param A
     * @param B
     * @param visited
     * @return isReachable
     */
    public static boolean isReachable(BasicBlock A, BasicBlock B, HashSet<BasicBlock> visited) {
//        System.out.println("isReachable: " + A.getLabel() + " " + B.getLabel());
        if (A.equals(B)) return true;
        for (BasicBlock suc : A.getSucBlocks()) {
            if (visited.contains(suc)) continue;
            visited.add(suc);
            if (isReachable(suc, B, visited)) return true;
        }
        return false;
    }
}
