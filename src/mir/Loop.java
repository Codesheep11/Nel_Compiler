package mir;

import midend.CloneInfo;

import java.util.HashSet;
import java.util.LinkedList;

import static midend.CloneInfo.bbMap;

public class Loop {
    private static int loopCounter = 0;
    private int hash;
    public Loop parent = null;
    public HashSet<Loop> children = new HashSet<>();
    public HashSet<BasicBlock> nowLevelBB = new HashSet<>();
    public BasicBlock header = null;
    public HashSet<BasicBlock> enterings = new HashSet<>();
    public HashSet<BasicBlock> exitings = new HashSet<>();
    public HashSet<BasicBlock> exits = new HashSet<>();
    public HashSet<BasicBlock> latchs = new HashSet<>();
    public Value cond;
    LinkedList<Instruction> conds;
    public boolean canAggressiveParallel = false;
    public boolean isRoot = false;
    public Boolean idcSet = false;
    public Value idcAlu = null;
    public Value idcPhi = null;
    public Value idcCmp = null;
    public Value idcInit = null;
    public Value idcEnd = null;
    public Value idcStep = null;
    public boolean idcTimeSet = false;
    public int idcTime = -1;

    public Loop() {
        isRoot = true;
        this.hash = loopCounter++;
    }

    public Loop(Loop parent) {
        this.hash = loopCounter++;
        this.parent = parent;
        parent.children.add(this);
    }

    /**
     * 得到循环的深度
     */
    public int getDepth() {
        int ret = 0;
        Loop par = parent;
        while (par != null) {
            par = par.parent;
            ret++;
        }
        return ret;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    /**
     * 判断value是否在循环中被定义
     *
     * @param value
     * @return
     */
    public boolean defValue(Value value) {
        if (!(value instanceof Instruction))
            throw new RuntimeException("defValue:" + value + "value is not an instruction\n");
        return LoopContains(((Instruction) value).getParentBlock());
    }

    /**
     * 递归判断Block是否在循环中
     */

    public boolean LoopContains(BasicBlock block) {
        if (children.isEmpty()) return nowLevelBB.contains(block);
        boolean flag = false;
        flag |= nowLevelBB.contains(block);
        for (Loop child : children) {
            flag |= child.LoopContains(block);
        }
        return flag;
    }

    /**
     * todo
     * 当block从cfg图中删除时调用该方法
     *
     * @param block
     */
    public void remove(BasicBlock block) {
        nowLevelBB.remove(block);
        //如果循环头被删除，那么整个循环被删除
        if (!isRoot && header.equals(block)) {
            delete();
        }
        //如果循环回边被删除，判断是否还存在回边，如果没有，则整个循环被删除
        if (latchs.contains(block)) {
            latchs.remove(block);
            if (latchs.isEmpty()) {
                delete();
            }
        }
    }

    public void delete() {
        if (isRoot) {
            throw new RuntimeException("delete: root loop can not be deleted\n");
        }
        //被删除的循环头的子循环全部挂到父循环上
        for (Loop child : children) {
            parent.children.add(child);
            child.parent = parent;
        }
        //剩下基本块挂到父循环上
        for (BasicBlock bb : nowLevelBB) {
            parent.nowLevelBB.add(bb);
            bb.loop = parent;
        }
        parent.children.remove(this);
    }
    //
//    public boolean LoopContainsAll(Collection<BasicBlock> blocks) {
//        for (BasicBlock block : blocks) {
//            if (!LoopContains(block)) return false;
//        }
//        return true;
//    }
//
//    //判断是否为循环不变量

    //
//    /**
//     * 合并两个循环
//     *
//     * @param loop
//     */
//    public void mergeLoop(Loop loop) {
//        if (!this.header.equals(loop.header)) {
//            throw new RuntimeException("mergeLoop: header not equal\n");
//        }
//        for (BasicBlock block : loop.blocks) {
//            if (this.blocks.contains(block) || loop.LoopContains(block)) {
//                continue;
//            }
//            this.blocks.add(block);
//        }
//        for (Loop child : loop.children) {
//            this.children.add(child);
//            child.parent = this;
//        }
//
//        this.exitings.clear();
//        this.exits.clear();
//        for (BasicBlock bb : this.blocks) {
//            //这里仍然认为通向内层循环不算exiting
//            if (LoopContainsAll(bb.getSucBlocks())) continue;
//            loop.exitings.add(bb);
//            HashSet<BasicBlock> outs = new HashSet<>(bb.getSucBlocks());
//            outs.removeAll(loop.blocks);
//            loop.exits.addAll(outs);
//        }
//        //header相同的循环不可能存在不同的parent todo
//        this.parent = loop.parent;
//        this.isNatural = false;
//    }
    public void cloneToFunc(Function tagFunc, Loop curLoop, int idx) {
        for (BasicBlock block : nowLevelBB) {
            bbMap.put(block, block.cloneToFunc(tagFunc, curLoop, idx));
        }
        for (Loop next : children) {
            Loop newChild = new Loop(curLoop);
            next.cloneToFunc(tagFunc, newChild, idx);
        }
        CloneInfo.addLoopReflect(curLoop, this);
    }

    public void cloneFix(Loop srcLoop) {
        for (BasicBlock block : srcLoop.nowLevelBB) {
            this.nowLevelBB.add(bbMap.get(block));
        }
        for (BasicBlock block : srcLoop.enterings) {
            this.enterings.add(bbMap.get(block));
        }
        for (BasicBlock block : srcLoop.exitings) {
            this.exitings.add(bbMap.get(block));
        }
        for (BasicBlock block : srcLoop.exits) {
            this.exits.add(bbMap.get(block));
        }
        for (BasicBlock block : srcLoop.latchs) {
            this.latchs.add(bbMap.get(block));
        }
        if (srcLoop.isRoot) return;
        this.header = bbMap.get(srcLoop.header);
    }


    public void LoopInfoPrint() {
        String LoopName = "Loop_" + hash;
        if (isRoot) System.out.println("Root " + LoopName + " begin:");
        else {
            System.out.println(LoopName + " begin:");
            System.out.println("header:" + header.getLabel());
            System.out.println("enterings:");
            for (BasicBlock bb : enterings) {
                System.out.println(bb.getLabel());
            }
            System.out.println("exitings:");
            for (BasicBlock bb : exitings) {
                System.out.println(bb.getLabel());
            }
            System.out.println("exits:");
            for (BasicBlock bb : exits) {
                System.out.println(bb.getLabel());
            }
            System.out.println("latchs:");
            for (BasicBlock bb : latchs) {
                System.out.println(bb.getLabel());
            }
        }
        System.out.println("nowLevelBB:");
        for (BasicBlock bb : nowLevelBB) {
            System.out.println(bb.getLabel());
        }
        System.out.println("children:");
        for (Loop child : children) {
            System.out.println("Loop_" + child.hash);
        }
        System.out.println((isRoot ? "Root" : LoopName) + " End!");
        for (Loop child : children) {
            child.LoopInfoPrint();
        }
    }
}