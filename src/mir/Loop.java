package mir;

import midend.Transform.Loop.LoopCloneInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;

public final class Loop {
    private static int loopCounter = 0;
    private final int hash;
    public Loop parent = null;
    public final HashSet<Loop> children = new HashSet<>();
    public final LinkedHashSet<BasicBlock> nowLevelBB = new LinkedHashSet<>();
    public BasicBlock header = null;
    public BasicBlock preHeader = null;
    public LinkedHashSet<BasicBlock> enterings = new LinkedHashSet<>(); //enterings -> preheader
    public final LinkedHashSet<BasicBlock> exitings = new LinkedHashSet<>();
    public LinkedHashSet<BasicBlock> exits = new LinkedHashSet<>();
    public final LinkedHashSet<BasicBlock> latchs = new LinkedHashSet<>();// 1 latch

    public int tripCount = -1;
    public boolean isRoot = false;


    public Loop(BasicBlock header) {
        this.header = header;
        this.hash = loopCounter++;
    }

    public Loop() {
        this.hash = loopCounter++;
    }

    public ArrayList<Loop> getChildrenSnap() {
        return new ArrayList<>(children);
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
     */
    public boolean defValue(Value value) {
        if (value instanceof Constant) return false;
//        if (value instanceof Constant.ConstantInt) return false;
//        if (value instanceof Constant.ConstantBool) return false;
//        if (value instanceof Constant.ConstantFloat) return false;
        if (value instanceof Function.Argument) return false;
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
            throw new RuntimeException("getExit: exits.size() != 1 size:" + exits.size() + "\n");
        }
        return exits.iterator().next();
    }

    public BasicBlock getBodyEntry() {
        for (var block : header.getSucBlocks()) {
            if (exits.contains(block)) continue;
            return block;
        }
        throw new RuntimeException("getBodyEntry: no body entry\n");
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

    /**
     * 获得该循环的大小 (指令数)
     */
    public int getSize() {
        int sum = 0;
        for (var block : nowLevelBB) {
            sum += block.getInstructions().size();
        }
        for (var loop : children) {
            sum += loop.getSize();
        }
        return sum;
    }

    public LoopCloneInfo cloneAndInfo() {
        LoopCloneInfo info = new LoopCloneInfo();
        info.src = this;
        info.cpy = new Loop();

        for (Loop child : children) {
            LoopCloneInfo childInfo = child.cloneAndInfo();
            info.cpy.addChildLoop(childInfo.cpy);
            info.merge(childInfo);
        }

        nowLevelBB.forEach(bb -> info.cpy.addNowLevelBB(bb.cloneToFunc(info, bb.getParentFunction())));
        info.cpy.nowLevelBB.forEach(bb -> bb.fixClone(info));
        for (var block : info.cpy.getAllBlocks()) {
            block.fixClone(info);
        }

        info.cpy.header = (BasicBlock) info.getReflectedValue(header);
        info.cpy.enterings = new LinkedHashSet<>(enterings);
        info.cpy.exits = new LinkedHashSet<>(exits);
        latchs.forEach(bb -> info.cpy.latchs.add((BasicBlock) info.getReflectedValue(bb)));
        exitings.forEach(bb -> info.cpy.exitings.add((BasicBlock) info.getReflectedValue(bb)));

        Loop cpLoop = info.cpy;
        cpLoop.tripCount = tripCount;
        cpLoop.parent = parent;
        cpLoop.isRoot = isRoot;

        return info;
    }

    public ArrayList<Value> getInComingValues() {
        ArrayList<Value> ret = new ArrayList<>();
        for (var block : getAllBlocks()) {
            for (var inst : block.getInstructions()) {
                for (var op : inst.getOperands()) {
                    if (op instanceof Constant) continue;
                    if (op instanceof Instruction) {
                        if (!LoopContains(((Instruction) op).getParentBlock())) {
                            ret.add(op);
                        }
                        continue;
                    }
                    if (op instanceof Function) continue;
                    if (op instanceof BasicBlock) continue;
                    if (op instanceof Function.Argument) continue;
                    throw new RuntimeException("getInComingValues: op is not a value\n");
                }
            }
        }
        return ret;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isNoSideEffect() {
        for (var block : nowLevelBB) {
            for (var inst : block.getInstructions()) {
                if (inst instanceof Instruction.Terminator) continue;
                if (!inst.isNoSideEffect()) {
                    return false;
                }
            }
        }
        for (var loop : children) {
            if (!loop.isNoSideEffect()) {
                return false;
            }
        }
        return true;
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