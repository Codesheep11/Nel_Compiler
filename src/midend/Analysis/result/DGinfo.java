package midend.Analysis.result;

import mir.BasicBlock;
import mir.Function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public final class DGinfo {

    private final HashMap<BasicBlock, _DG_Block_Info> map;

    private boolean domFrontierBuilt;

    public DGinfo(Function function) {
        map = new HashMap<>();
        for (BasicBlock block : function.getBlocks()) {
            map.put(block, new _DG_Block_Info(block));
        }
        this.domFrontierBuilt = false;
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    public final class _DG_Block_Info {
        public BasicBlock idom; // 支配图-直接支配块 (支配树父亲)
        public final HashSet<BasicBlock> dominators; // 支配图-支配块集合 (指的是支配该块的所有块, 即支配树上的父节点)
        public final HashSet<BasicBlock> domFrontiers; // 支配图-支配边界
        public final ArrayList<BasicBlock> domTreeChildren; // 支配图-支配树孩子(直接支配)
        public int domDepth; // 支配图-深度

        public _DG_Block_Info(BasicBlock block) {
            this.block = block;
            this.idom = null;
            this.dominators = new HashSet<>();
            this.domFrontiers = new HashSet<>();
            this.domTreeChildren = new ArrayList<>();
            this.domDepth = -1;
        }

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final BasicBlock block;
    }

    /**
     * a 是否支配 b
     */
    public boolean dominate(BasicBlock a, BasicBlock b) {
        return map.get(b).dominators.contains(a);
    }

    public boolean strictlyDominate(BasicBlock a, BasicBlock b) {
        return dominate(a, b) && a != b;
    }

    public void setIDom(BasicBlock block, BasicBlock idom) {
        map.get(block).idom = idom;
    }

    public void setDominator(HashMap<BasicBlock, HashSet<BasicBlock>> map) {
        for (BasicBlock block : map.keySet()) {
            this.map.get(block).dominators.addAll(map.get(block));
        }
    }

    public void setDepth(BasicBlock block, int depth) {
        map.get(block).domDepth = depth;
    }

    public BasicBlock getIDom(BasicBlock block) {
        return map.get(block).idom;
    }

    public HashSet<BasicBlock> getDominators(BasicBlock block) {
        return map.get(block).dominators;
    }

    public HashSet<BasicBlock> getDomFrontiers(BasicBlock block) {
        if (!domFrontierBuilt) {
            domFrontierBuilt = true;
            buildDominanceFrontier();
        }
        return map.get(block).domFrontiers;
    }

    public ArrayList<BasicBlock> getDomTreeChildren(BasicBlock block) {
        return map.get(block).domTreeChildren;
    }

    public int getDomDepth(BasicBlock block) {
        return map.get(block).domDepth;
    }

    public BasicBlock getLCA(BasicBlock a, BasicBlock b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        while (map.get(a).domDepth > map.get(b).domDepth) {
            a = getIDom(a);
        }
        while (map.get(a).domDepth < map.get(b).domDepth) {
            b = getIDom(b);
        }
        while (a != b) {
            a = getIDom(a);
            b = getIDom(b);
        }
        return a;
    }

    /**
     * 仅限构造DG类使用以降低时间开销
     */
    public _DG_Block_Info getInfo(BasicBlock block) {
        return map.get(block);
    }

    private void buildDominanceFrontier() {
        // 枚举控制图的边
        for (BasicBlock a : map.keySet()) {
            for (BasicBlock b : a.getSucBlocks()) {
                BasicBlock x = a;
                while (x != null && !strictlyDominate(x, b)) {
                    map.get(x).domFrontiers.add(b);
                    x = getIDom(x);
                }
            }
        }
        domFrontierBuilt = true;
    }

    public void printDominators() {
        for (BasicBlock block : map.keySet()) {
            System.out.print(block.getLabel() + " : ");
            for (BasicBlock dom : getDominators(block)) {
                System.out.print(dom.getLabel() + " ");
            }
            System.out.println();
        }
    }

    public void printDomTree() {
        System.out.println("domTree:");
        for (BasicBlock block : map.keySet()) {
            System.out.print(block.getLabel() + " : ");
            for (BasicBlock domTreeChild : getDomTreeChildren(block)) {
                System.out.print(domTreeChild.getLabel() + " ");
            }
            System.out.println();
        }
    }

    public void printIdom() {
        System.out.println("idom:");
        for (BasicBlock block : map.keySet()) {
            if (getIDom(block) != null) {
                System.out.println(block.getLabel() + " : " + getIDom(block).getLabel());
            }
            else {
                System.out.println(block.getLabel() + " : null");
            }
        }
    }

    public void printDominanceFrontier() {
        System.out.println("Dominance Frontier:");
        for (BasicBlock block : map.keySet()) {
            System.out.print(block.getLabel() + " : ");
            for (BasicBlock domFrontier : getDomFrontiers(block)) {
                System.out.print(domFrontier.getLabel() + " ");
            }
            System.out.println();
        }
    }

}
