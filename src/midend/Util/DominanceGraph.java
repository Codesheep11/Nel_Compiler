package midend.Util;

import midend.Transform.DCE.RemoveBlocks;
import mir.BasicBlock;
import mir.Function;
import midend.Analysis.result.DGinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 数据迭代求DG <br>
 * 由于时空开销较大 已弃用 <br>
 * 保留以防万一 <br>
 *
 * @see <a href="https://oi-wiki.org/graph/dominator-tree/#%E6%95%B0%E6%8D%AE%E6%B5%81%E8%BF%AD%E4%BB%A3%E6%B3%95">数据流迭代法</a>
 */
@Deprecated
public class DominanceGraph {
    private static Function parentFunction;
    private static BasicBlock entry;
    private static final HashSet<BasicBlock> vis = new HashSet<>();
    private static final ArrayList<BasicBlock> blocks = new ArrayList<>();

    private static DGinfo dginfo;

    public static DGinfo runOnFunc(Function function) {
        dginfo = new DGinfo(function);
        parentFunction = function;
        build();
        return dginfo;
    }

    private static void build() {
        clear();
        entry = parentFunction.getEntry();
        RemoveBlocks.runOnFunc(parentFunction);
        buildDominatorSet();
        buildImmDominateTree();
        buildDomDepth();
        buildDominanceFrontier();
        //printDomInfo();
    }

    private static void clear() {
        vis.clear();
        blocks.clear();
    }

    private void printDomInfo() {
        dginfo.printDominators();
        dginfo.printIdom();
        dginfo.printDomTree();
        dginfo.printDominanceFrontier();
    }

    private void printReversePostorderTraversal() {
        System.out.print("reverse postorder: ");
        for (BasicBlock block : blocks) {
            System.out.print(block.getLabel() + " ");
        }
        System.out.println();
    }

    private static void search(BasicBlock cur) {
        vis.add(cur);
        for (BasicBlock preBlock : cur.getPreBlocks()) {
            if (!vis.contains(preBlock)) {
                search(preBlock);
            }
        }
        // ensure that each block's preBlocks have been inserted
        blocks.add(cur);
    }

    private static void makeReversePostorderTraversal() {
        vis.clear();
        for (BasicBlock block : parentFunction.getBlocks()) {
            if (!vis.contains(block)) {
                search(block);
            }
        }
    }

    private static void buildDominatorSet() {
        // 计算逆后序
        makeReversePostorderTraversal();
        HashMap<BasicBlock, HashSet<BasicBlock>> _domSet = new HashMap<>();
        for (BasicBlock block : blocks) {
            _domSet.put(block, new HashSet<>());
        }
        // printReversePostorderTraversal();
        // 初始化节点的支配集合
        for (BasicBlock block : blocks) {
            // System.out.println("init " + block.getLabel());
            if (block.equals(entry)) {
                _domSet.get(block).add(entry);
            }
            else {
                _domSet.get(block).addAll(blocks);
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
                HashSet<BasicBlock> dom = _domSet.get(block);
//                HashSet<BasicBlock> new_dom = new HashSet<>(blocks);
                HashSet<BasicBlock> new_dom = null;
                // 取支配交集
                for (BasicBlock preBlock : block.getPreBlocks()) {
                    if (new_dom == null) {
                        new_dom = new HashSet<>(_domSet.get(preBlock));
                    }
                    else {
                        new_dom.retainAll(_domSet.get(preBlock));
                    }
//                    HashSet<BasicBlock> preDom = _domSet.get(preBlock);
//                    new_dom.retainAll(preDom);
                }
                // 加入自身
                if (new_dom == null) {
                    new_dom = new HashSet<>(blocks);
                }
                new_dom.add(block);
                // 更新并 标记修改
                if (!dom.equals(new_dom)) {
                    _domSet.put(block, new_dom);
                    changed = true;
                }
            }
        } while (changed);
        dginfo.setDominator(_domSet);
    }

    // 直接支配
    private static boolean immDominates(BasicBlock x, BasicBlock y) {
        // 严格支配 y
        if (!dginfo.strictlyDominate(x, y)) {
            return false;
        }

        for (BasicBlock strictDom : dginfo.getDominators(y)) {
            // 跳过自身
            if (strictDom.equals(y)) {
                continue;
            }
            // 不允许严格支配其他支配块
            if (dginfo.strictlyDominate(x, strictDom)) {
                return false;
            }
        }
        // 唯一的距离最近的支配块
        return true;
    }

    private static void buildImmDominateTree() {
        for (BasicBlock block : blocks) {
            if (block.equals(entry)) {
                continue;
            }
            for (BasicBlock dom : dginfo.getDominators(block)) {
                if (immDominates(dom, block)) {
                    dginfo.setIDom(block, dom);
                    break;
                }
            }
        }
        for (BasicBlock block : blocks) {
            BasicBlock idom = dginfo.getIDom(block);
            if (idom != null) {
                dginfo.getDomTreeChildren(idom).add(block);
            }
        }
    }

    private static void buildDomDepth() {
        dfsDomTree(entry, 0);
    }

    private static void dfsDomTree(BasicBlock cur, int dep) {
        dginfo.setDepth(cur, dep);
        for (BasicBlock domTreeChild : dginfo.getDomTreeChildren(cur)) {
            dfsDomTree(domTreeChild, dep + 1);
        }
    }

    private static void buildDominanceFrontier() {
        // 枚举控制图的边
        for (BasicBlock a : blocks) {
            for (BasicBlock b : a.getSucBlocks()) {
                BasicBlock x = a;
                while (x != null && !dginfo.strictlyDominate(x, b)) {
                    dginfo.getDomFrontiers(x).add(b);
                    x = dginfo.getIDom(x);
                }
            }
        }
    }

}
