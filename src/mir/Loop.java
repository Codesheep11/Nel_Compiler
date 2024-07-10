package mir;

import midend.Transform.Loop.LoopCloneInfo;

import java.util.HashSet;

//import static midend.Util.CloneInfo.bbMap;

public class Loop {
    private static int loopCounter = 0;
    private int hash;
    public Loop parent = null;
    public HashSet<Loop> children = new HashSet<>();
    public HashSet<BasicBlock> nowLevelBB = new HashSet<>();
    public BasicBlock header = null;
    public BasicBlock preHeader = null;
    public HashSet<BasicBlock> enterings = new HashSet<>(); //enterings -> preheader
    public HashSet<BasicBlock> exitings = new HashSet<>();
    public HashSet<BasicBlock> exits = new HashSet<>();
    public HashSet<BasicBlock> latchs = new HashSet<>();// 1 latch
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
        int ret = 1;
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

    public BasicBlock getLatch() {
        if (latchs.size() != 1) {
            throw new RuntimeException("getLatch: latchs.size() != 1\n");
        }
        return latchs.iterator().next();
    }

    public LoopCloneInfo cloneAndInfo() {
        LoopCloneInfo info = new LoopCloneInfo();
        info.src = this;
        info.cpy = new Loop();

        nowLevelBB.forEach(bb -> info.cpy.addNowLevelBB(bb.cloneToFunc(info, bb.getParentFunction())));
        info.cpy.nowLevelBB.forEach(bb -> bb.fixClone(info));

        info.cpy.header = (BasicBlock) info.getReflectedValue(header);
        info.cpy.enterings = new HashSet<>(enterings);
        info.cpy.exits = new HashSet<>(exits);
        latchs.forEach(bb -> info.cpy.latchs.add((BasicBlock) info.getReflectedValue(bb)));
        exitings.forEach(bb -> info.cpy.exitings.add((BasicBlock) info.getReflectedValue(bb)));

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