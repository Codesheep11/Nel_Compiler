package mir;

import midend.CloneInfo;
import utils.SyncLinkedList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

public class BasicBlock extends Value {
    private final Function parentFunction; // 父函数
    private final String label;
    private final SyncLinkedList<Instruction> instructions;
    // 控制图属性
    private final ArrayList<BasicBlock> preBlocks; // 控制图-前驱块
    private final ArrayList<BasicBlock> sucBlocks; // 控制图-后继块
    // 支配图属性
    private BasicBlock idom; // 支配图-直接支配块
    private HashSet<BasicBlock> domSet = new HashSet<>(); // 支配图-支配块集合 (指的是支配该块的所有块, 即支配树上的父节点)
    private final HashSet<BasicBlock> domFrontiers = new HashSet<>(); // 支配图-支配边界
    private final ArrayList<BasicBlock> domTreeChildren = new ArrayList<>(); // 支配图-支配树孩子(直接支配)

    private int domDepth = -1; // 支配图-深度

    public Loop loop = null;// 循环信息
    public boolean isDeleted = false;

    private int cpcnt = 0;

    public BasicBlock(String label, Function parentFunction) {
        super(Type.LabelType.LABEL_TYPE);
        this.parentFunction = parentFunction;
        parentFunction.appendBlock(this);
        this.label = label;
        this.sucBlocks = new ArrayList<>();
        this.preBlocks = new ArrayList<>();
        this.instructions = new SyncLinkedList<>();
    }

    /**
     * 供循环优化使用
     * 得到一个关系干净的基本块
     */
    public static BasicBlock getNewCleanBlock(String label, Function parentFunction, Loop loop) {
        BasicBlock newBlock = new BasicBlock(label, parentFunction);
        newBlock.remove();
        newBlock.loop = loop;
        loop.nowLevelBB.add(newBlock);
        return newBlock;
    }

    /**
     * 获得该基本块的phi指令列表 <br>
     * 事实上 phi 指令被认为发生在前驱块到后继块的边上 <br>
     * 也许应该独立出来存储 <br>
     * <p>
     * Warning: 不要将返回值改为 SyncLinkedList, 会破坏原有的链表关系！
     */
    public ArrayList<Instruction.Phi> getPhiInstructions() {
        ArrayList<Instruction.Phi> phiInstructions = new ArrayList<>();
        for (Instruction inst : instructions) {
            if (inst instanceof Instruction.Phi phi) {
                phiInstructions.add(phi);
            }
            else {
                break;
            }
        }
        return phiInstructions;
    }

    public Function getParentFunction() {
        return parentFunction;
    }

    public String getLabel() {
        return label;
    }

    public Instruction getFirstInst() {
        return instructions.getFirst();
    }

    public Instruction getLastInst() {
        return instructions.getLast();
    }

    public Instruction.Terminator getTerminator() {
        return (Instruction.Terminator) getLastInst();
    }

    public void addInstFirst(Instruction inst) {
        instructions.addFirst(inst);
    }

    public void addInstLast(Instruction inst) {
        instructions.addLast(inst);
    }

    public SyncLinkedList<Instruction> getInstructions() {
        return instructions;
    }

    /**
     * 返回一个指令列表的快照 <br>
     * 主要用于边遍历边修改 防止并发修改异常<br>
     * TODO: SYNCLINKEDLIST 应该增强安全性 抛出相应异常
     */
    public ArrayList<Instruction> getInstructionsSnap() {
        ArrayList<Instruction> snap = new ArrayList<>();
        instructions.forEach(snap::add);
        return snap;
    }

    public void addPreBlock(BasicBlock preBlock) {
        if (!preBlocks.contains(preBlock)) {
            preBlocks.add(preBlock);
        }
    }

    public void addSucBlock(BasicBlock sucBlock) {
        if (!sucBlocks.contains(sucBlock)) {
            sucBlocks.add(sucBlock);
        }
    }

    public ArrayList<BasicBlock> getPreBlocks() {
        return preBlocks;
    }

    public ArrayList<BasicBlock> getSucBlocks() {
        return sucBlocks;
    }

    public void setIdom(BasicBlock idom) {
        this.idom = idom;
    }

    public BasicBlock getIdom() {
        return idom;
    }

    public HashSet<BasicBlock> getDomFrontiers() {
        return domFrontiers;
    }

    public ArrayList<BasicBlock> getDomTreeChildren() {
        return domTreeChildren;
    }

    public HashSet<BasicBlock> getDomSet() {
        return domSet;
    }

    public void setDomSet(HashSet<BasicBlock> domSet) {
        this.domSet = domSet;
    }

    public void setDomDepth(int dep) {
        this.domDepth = dep;
    }

    public int getDomDepth() {
        return this.domDepth;
    }

    public int getLoopDepth() {
        if (loop == null) {
            return 0;
        }
        return loop.getDepth();
    }

    public boolean isTerminated() {
        if (instructions.isEmpty()) {
            return false;
        }
        return getLastInst() instanceof Instruction.Terminator;
    }

    /**
     * 替换后继块
     *
     * @param oldBlock 要替换掉的后继块
     * @param newBlock 新的后继块
     */
    public void replaceSucc(BasicBlock oldBlock, BasicBlock newBlock) {
//        for (int i = 0; i < sucBlocks.size(); i++) {
//            if (sucBlocks.get(i).equals(oldBlock)) {
//                sucBlocks.set(i, newBlock);
//            }
//        }
        getTerminator().replaceSucc(oldBlock, newBlock);
    }

//    public void replacePred(BasicBlock oldBlock, BasicBlock newBlock) {
//        for (int i = 0; i < preBlocks.size(); i++) {
//            if (preBlocks.get(i).equals(oldBlock)) {
//                preBlocks.set(i, newBlock);
//            }
//        }
//        for (Instruction instruction : instructions) {
//            if (instruction instanceof Instruction.Phi phi)
//                phi.changePreBlock(oldBlock, newBlock);
//            else break;
//        }
//    }


    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        BasicBlock that = (BasicBlock) object;
        return label.equals(that.label) && Objects.equals(parentFunction, that.parentFunction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label);
    }

    @Override
    public String getDescriptor() {
        return label;
    }

    //region outputLLVMIR
    public ArrayList<String> output() {
        if (instructions.isEmpty()) {
            return new ArrayList<>();
        }
        ArrayList<String> outputList = new ArrayList<>();
        outputList.add(label + ":");

        for (Instruction instruction :
                instructions) {
            outputList.add("\t" + instruction.toString());
        }
        return outputList;
    }
    //endregion


    //函数内联的时候,维护循环信息,方便GCM
    public BasicBlock cloneToFunc(CloneInfo cloneInfo, Function function) {
        BasicBlock ret = new BasicBlock(function.getName() + "_" + getLabel() + "_" + cpcnt++, function);
        cloneInfo.addValueReflect(this, ret);
        for (Instruction inst : getInstructions()) {
            Instruction tmp = inst.cloneToBBAndAddInfo(cloneInfo, ret);
        }
        return ret;
    }

    public void fixClone(CloneInfo cloneInfo) {
        instructions.forEach(inst -> inst.fix(cloneInfo));
    }

//    public BasicBlock defaultClone() {
//        BasicBlock ret = new BasicBlock(getDescriptor() + "_cp", parentFunction);
//        for (Instruction inst : getInstructions()) {
//            Instruction tmp = inst.cloneToBB(ret);
//        }
//        return ret;
//    }

    public boolean dominates(BasicBlock block) {
        return block.domSet.contains(this);
    }

}
