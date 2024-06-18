package midend;

import mir.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

public class Loop {
    private static int loopCounter = 0;
    private int hash;
    public Instruction.Loop parent = null;
    public HashSet<Instruction.Loop> children = new HashSet<>();
    public HashSet<BasicBlock> nowLevelBB = new HashSet<>();
    public BasicBlock header = null;
    public HashSet<BasicBlock> enterings = new HashSet<>();
    public HashSet<BasicBlock> exitings = new HashSet<>();
    public HashSet<BasicBlock> exits = new HashSet<>();
    public HashSet<BasicBlock> latchs = new HashSet<>();
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

    public Loop(BasicBlock header) {
        this.header = header;
        this.hash = loopCounter++;
    }

    /**
     * 得到循环的深度
     */
    public int getDepth() {
        if (parent == null) return 0;
        return parent.getDepth() + 1;
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
        if (children.size() == 0) return nowLevelBB.contains(block);
        boolean flag = false;
        flag |= nowLevelBB.contains(block);
        for (Instruction.Loop child : children) {
            flag |= child.LoopContains(block);
        }
        return flag;
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
//    public boolean isInvariant(Instruction instr, LinkedList<Instruction> invariants) {
//        if (instr instanceof Instruction.Call) return false;
//        for (Value use : instr.getOperands()) {
//            //如果use是常数或者use的定义点在循环之外或者use的定义点是循环不变量，那么use可以视作是不变量
//            if (use instanceof Function.Argument) continue;
//            if (!(use instanceof Constant || !this.defValue(use) || invariants.contains(use))) return false;
//            //todo:这里认为对指针类型操作如果不是内存访问，就可以视作不变量，但是这里可能有优化空间
//            if (use.getType() instanceof Type.PointerType && instr instanceof Instruction.Load || instr instanceof Instruction.Store)
//                return false;
//        }
//        return true;
//    }
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
}