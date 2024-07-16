package mir;

import midend.Analysis.AnalysisManager;
import midend.Util.CloneInfo;
import utils.NelLinkedList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

public class BasicBlock extends Value {
    private final Function parentFunction; // 父函数
    private final String label;
    private final NelLinkedList<Instruction> instructions;
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
        this.instructions = new NelLinkedList<>();
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

    public NelLinkedList<Instruction> getInstructions() {
        return instructions;
    }

    /**
     * 返回一个指令列表的快照 <br>
     * 主要用于边遍历边修改 防止并发修改异常<br>
     */
    public ArrayList<Instruction> getInstructionsSnap() {
        ArrayList<Instruction> snap = new ArrayList<>();
        instructions.forEach(snap::add);
        return snap;
    }

    public boolean hasCall() {
        for (Instruction inst : instructions) {
            if (inst instanceof Instruction.Call) {
                return true;
            }
        }
        return false;
    }

    // 为维护与旧版本兼容性而保留
    @Deprecated(forRemoval = false)
    public ArrayList<BasicBlock> getPreBlocks() {
        return AnalysisManager.getCFGPredecessors(this);
    }

    // 为维护与旧版本兼容性而保留
    @Deprecated(forRemoval = false)
    public ArrayList<BasicBlock> getSucBlocks() {
        return AnalysisManager.getCFGSuccessors(this);
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
        getTerminator().replaceSucc(oldBlock, newBlock);
    }


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
        BasicBlock ret = new BasicBlock(getLabel() + "_cp" + cpcnt++, function);
        cloneInfo.addValueReflect(this, ret);
        for (Instruction inst : getInstructions()) {
            inst.cloneToBBAndAddInfo(cloneInfo, ret);
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

    @Override
    public void delete() {
        super.delete();
        if (instructions.isEmpty()) return;
        ArrayList<Instruction> delList = new ArrayList<>();
        for (Instruction instr : instructions) {
            delList.add(instr);
        }
        delList.forEach(Instruction::delete);
    }

}
