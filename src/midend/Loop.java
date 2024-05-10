package midend;

import mir.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

public class Loop {
    public BasicBlock header;
    /**
     * 循环的前置节点，用于不变量外提
     */
    public BasicBlock preHeader = null;

    /**
     * 循环的后边界,对于自然循环，backEdges只有一个元素
     */
    public LinkedList<BasicBlock> backEdges = new LinkedList<>();

    /**
     * 循环的基本块，不包含子循环的基本块
     */

    public LinkedList<BasicBlock> blocks = new LinkedList<>();//对于自然循环，blocks以支配树自顶向下的顺序排列
    public Loop parent = null;
    public HashSet<Loop> children = new HashSet<>();

    public boolean isNatural = true;

    /**
     * 循环中通向循环外部的节点集合
     */
    public HashSet<BasicBlock> exitings = new HashSet<>();
    /**
     * 循环的出口节点集合
     */
    public HashSet<BasicBlock> exits = new HashSet<>();

    public Loop(BasicBlock header, BasicBlock backEdge) {
        this.header = header;
        this.blocks.add(header);
        backEdges.add(backEdge);
    }


    public void printLoopInfo() {
        String name = isNatural ? "Natural Loop:" : "Irreducible Loop:\n";
        name += "header:" + header.getLabel() + "\n";
        name += "latch:" + backEdges.get(0).getLabel() + "\n";
        for (BasicBlock block : blocks) {
            name += block.getLabel() + " ";
        }
        System.out.println(name);
        System.out.println("");
        for (Loop child : children) {
            System.out.println(header.getLabel() + " child:");
            child.printLoopInfo();
        }
    }

    /**
     * 得到循环的深度
     */
    public int getDepth() {
        if (parent == null) return 1;
        return parent.getDepth() + 1;
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
        if (LoopContains(((Instruction) value).getParentBlock())) return true;
        return false;
    }

    /**
     * 递归判断Block是否在循环中
     *
     * @param block
     * @return
     */

    public boolean LoopContains(BasicBlock block) {
        if (children.size() == 0) return blocks.contains(block);
        boolean flag = false;
        flag |= blocks.contains(block);
        for (Loop child : children) {
            flag |= child.LoopContains(block);
        }
        return flag;
    }

    public boolean LoopContainsAll(Collection<BasicBlock> blocks) {
        for (BasicBlock block : blocks) {
            if (!LoopContains(block)) return false;
        }
        return true;
    }

    //判断是否为循环不变量
    public boolean isInvariant(Instruction instr, LinkedList<Instruction> invariants) {
        if (instr instanceof Instruction.Call) return false;
        for (Value use : instr.getOperands()) {
            //如果use是常数或者use的定义点在循环之外或者use的定义点是循环不变量，那么use可以视作是不变量
            if (use instanceof Function.Argument) continue;
            if (!(use instanceof Constant || !this.defValue(use) || invariants.contains(use))) return false;
            //todo:这里认为对指针类型操作如果不是内存访问，就可以视作不变量，但是这里可能有优化空间
            if (use.getType() instanceof Type.PointerType && instr instanceof Instruction.Load || instr instanceof Instruction.Store)
                return false;
        }
        return true;
    }

    /**
     * 合并两个循环
     *
     * @param loop
     */
    public void mergeLoop(Loop loop) {
        if (!this.header.equals(loop.header)) {
            throw new RuntimeException("mergeLoop: header not equal\n");
        }
        for (BasicBlock block : loop.blocks) {
            if (this.blocks.contains(block) || loop.LoopContains(block)) {
                continue;
            }
            this.blocks.add(block);
        }
        for (Loop child : loop.children) {
            this.children.add(child);
            child.parent = this;
        }

        this.exitings.clear();
        this.exits.clear();
        for (BasicBlock bb : this.blocks) {
            //这里仍然认为通向内层循环不算exiting
            if (LoopContainsAll(bb.getSucBlocks())) continue;
            loop.exitings.add(bb);
            HashSet<BasicBlock> outs = new HashSet<>(bb.getSucBlocks());
            outs.removeAll(loop.blocks);
            loop.exits.addAll(outs);
        }
        //header相同的循环不可能存在不同的parent todo
        this.parent = loop.parent;
        this.isNatural = false;
    }
}