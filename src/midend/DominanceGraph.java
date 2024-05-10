package midend;

import mir.BasicBlock;
import mir.Function;

import java.util.ArrayList;
import java.util.HashSet;

public class DominanceGraph {
    private final Function parentFunction;
    private BasicBlock entry;
    private final HashSet<BasicBlock> vis = new HashSet<>();
    private final ArrayList<BasicBlock> blocks = new ArrayList<>();

    public DominanceGraph(Function parentFunction) {
        this.parentFunction = parentFunction;
    }

    public void build() {
        clear();
        this.entry = parentFunction.getEntry();
        buildDominatorSet();
        buildImmDominateTree();
        buildDominanceFrontier();
        //printDomInfo();
    }

    private void clear() {
        vis.clear();
        blocks.clear();
        for (BasicBlock block : parentFunction.getBlocks()) {
            block.getDomSet().clear();
            block.getDomFrontiers().clear();
            block.getDomTreeChildren().clear();
            block.setIdom(null);
        }
    }

    private void printDomInfo() {
        printDominators();
        printIdom();
        printDomTree();
        printDominanceFrontier();
    }

    private void printDomTree() {
        System.out.println("domTree:");
        for (BasicBlock block : blocks) {
            System.out.print(block.getLabel() + " : ");
            for (BasicBlock domTreeChild : block.getDomTreeChildren()) {
                System.out.print(domTreeChild.getLabel() + " ");
            }
            System.out.println();
        }
    }

    private void printIdom() {
        System.out.println("idom:");
        for (BasicBlock block : blocks) {
            if (block.getIdom() != null) {
                System.out.println(block.getLabel() + " : " + block.getIdom().getLabel());
            }
            else {
                System.out.println(block.getLabel() + " : null");
            }
        }
    }

    private void printDominanceFrontier() {
        System.out.println("Dominance Frontier:");
        for (BasicBlock block : blocks) {
            System.out.print(block.getLabel() + " : ");
            for (BasicBlock domFrontier : block.getDomFrontiers()) {
                System.out.print(domFrontier.getLabel() + " ");
            }
            System.out.println();
        }
    }

    private void printReversePostorderTraversal() {
        System.out.print("reverse postorder: ");
        for (BasicBlock block : blocks) {
            System.out.print(block.getLabel() + " ");
        }
        System.out.println();
    }

    private void search(BasicBlock cur) {
        vis.add(cur);
        for (BasicBlock preBlock : cur.getPreBlocks()) {
            if (!vis.contains(preBlock)) {
                search(preBlock);
            }
        }
        // ensure that each block's preBlocks have been inserted
        blocks.add(cur);
    }

    private void makeReversePostorderTraversal() {
        vis.clear();
        for (BasicBlock block : parentFunction.getBlocks()) {
            if (!vis.contains(block)) {
                search(block);
            }
        }
    }

    private void printDominators() {
        for (BasicBlock block : blocks) {
            System.out.print(block.getLabel() + " : ");
            for (BasicBlock dom : block.getDomSet()) {
                System.out.print(dom.getLabel() + " ");
            }
            System.out.println();
        }
    }

    private void buildDominatorSet() {
        // 计算逆后序
        makeReversePostorderTraversal();
        // printReversePostorderTraversal();
        // 初始化节点的支配集合
        for (BasicBlock block : blocks) {
            // System.out.println("init " + block.getLabel());
            block.getDomSet().clear();
            if (block.equals(entry)) {
                block.getDomSet().add(entry);
            }
            else {
                block.getDomSet().addAll(blocks);
            }
        }
        // 边界检测
        boolean changed;
        do {
            changed = false;
            // 逆后序遍历
            for (BasicBlock block : blocks) {
                // 跳过入口
                if (block.equals(entry)) {
                    continue;
                }
                HashSet<BasicBlock> dom = block.getDomSet();
                HashSet<BasicBlock> new_dom = new HashSet<>(blocks);
                // 取支配交集
                for (BasicBlock preBlock : block.getPreBlocks()) {
                    HashSet<BasicBlock> preDom = preBlock.getDomSet();
                    new_dom.retainAll(preDom);
                }
                // 加入自身
                new_dom.add(block);
                // 更新并 标记修改
                if (!dom.equals(new_dom)) {
                    block.setDomSet(new_dom);
                    changed = true;
                }
            }
        } while (changed);
    }


    private boolean dominates(BasicBlock x, BasicBlock y) {
        return y.getDomSet().contains(x);
    }

    // check whether x strictly dominates y
    private boolean strictlyDominates(BasicBlock x, BasicBlock y) {
        return dominates(x, y) && !x.equals(y);
    }

    // 直接支配
    private boolean immDominates(BasicBlock x, BasicBlock y) {
        // 严格支配 y
        if (!strictlyDominates(x, y)) {
            return false;
        }

        for (BasicBlock strictDom : y.getDomSet()) {
            // 跳过自身
            if (strictDom.equals(y)) {
                continue;
            }
            // 不允许严格支配其他支配块
            if (strictlyDominates(x, strictDom)) {
                return false;
            }
        }
        // 唯一的距离最近的支配块
        return true;
    }

    private void buildImmDominateTree() {
        for (BasicBlock block : blocks) {
            if (block.equals(entry)) {
                continue;
            }
            for (BasicBlock dom : block.getDomSet()) {
                if (immDominates(dom, block)) {
                    block.setIdom(dom);
                    break;
                }
            }
        }
        for (BasicBlock block : blocks) {
            BasicBlock idom = block.getIdom();
            if (idom != null) {
                idom.getDomTreeChildren().add(block);
            }
        }
    }

    private void buildDominanceFrontier() {
        // 枚举控制图的边
        for (BasicBlock a : blocks) {
            for (BasicBlock b : a.getSucBlocks()) {
                BasicBlock x = a;
                while (x != null && !strictlyDominates(x, b)) {
                    x.getDomFrontiers().add(b);

                    x = x.getIdom();
                }
            }
        }
    }

}
