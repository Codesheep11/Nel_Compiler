package mir;

import midend.CloneInfo;
import midend.LoopCloneInfo;
import midend.LoopInfo;

import java.util.HashSet;
import java.util.LinkedList;

//import static midend.CloneInfo.bbMap;

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
    //todo: cond
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


    public Loop(BasicBlock header) {
        this.header = header;
        this.hash = loopCounter++;
    }

    public Loop() {
        this.hash = loopCounter++;
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
     * 得到循环中的所有基本块
     *
     * @return
     */
    public HashSet<BasicBlock> getAllBlocks() {
        HashSet<BasicBlock> ret = new HashSet<>(nowLevelBB);
        for (Loop child : children) {
            ret.addAll(child.getAllBlocks());
        }
        return ret;
    }

    public void addChildLoop(Loop loop) {
        children.add(loop);
        loop.parent = this;
    }

    public void addNowLevelBB(BasicBlock bb) {
        nowLevelBB.add(bb);
        bb.loop = this;
    }

    public BasicBlock getExit() {
        if (exits.size() != 1) {
            throw new RuntimeException("getExit: exits.size() != 1\n");
        }
        return exits.iterator().next();
    }

    public BasicBlock getPreHeader() {
        if (enterings.size() != 1) {
            throw new RuntimeException("getPreHeader: enterings.size() != 1\n");
        }
        return enterings.iterator().next();
    }

    public LoopCloneInfo cloneAndInfo() {
        LoopCloneInfo info = new LoopCloneInfo();
        info.src = this;
        info.cpy = new Loop();

        nowLevelBB.forEach(bb -> info.cpy.addNowLevelBB(bb.cloneToFunc(info, bb.getParentFunction())));
        info.cpy.nowLevelBB.forEach(bb -> bb.fixClone(info));

        info.cpy.header = (BasicBlock) info.getReflectedValue(header);
        info.cpy.enterings = new HashSet<>(enterings);
        info.cpy.exitings = new HashSet<>(exitings);
        latchs.forEach(bb -> info.cpy.latchs.add((BasicBlock) info.getReflectedValue(bb)));
        exits.forEach(bb -> info.cpy.exits.add((BasicBlock) info.getReflectedValue(bb)));

        Loop cpLoop = info.cpy;
        cpLoop.parent = parent;
        cpLoop.isRoot = isRoot;
        cpLoop.idcSet = idcSet;
        cpLoop.idcAlu = idcAlu;
        cpLoop.idcPhi = idcPhi;
        cpLoop.idcCmp = idcCmp;
        cpLoop.idcInit = idcInit;
        cpLoop.idcEnd = idcEnd;
        cpLoop.idcStep = idcStep;
        cpLoop.idcTimeSet = idcTimeSet;
        cpLoop.idcTime = idcTime;

        return info;
    }

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