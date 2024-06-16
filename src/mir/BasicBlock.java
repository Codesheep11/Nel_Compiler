package mir;

import midend.CloneInfo;
import midend.Loop;
import utils.SyncLinkedList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;

public class BasicBlock extends Value {
    private Function parentFunction; // 父函数
    private final String label;
    private final SyncLinkedList<Instruction> instructions;
    // 控制图属性
    private LinkedList<BasicBlock> preBlocks; // 控制图-前驱块
    private LinkedList<BasicBlock> sucBlocks; // 控制图-后继块
    // 支配图属性
    private BasicBlock idom; // 支配图-直接支配块
    private HashSet<BasicBlock> domSet = new HashSet<>(); // 支配图-支配块集合 (指的是支配该块的所有块, 即支配树上的父节点)
    private final HashSet<BasicBlock> domFrontiers = new HashSet<>(); // 支配图-支配边界
    private final LinkedList<BasicBlock> domTreeChildren = new LinkedList<>(); // 支配图-支配树孩子

    private int domDepth = -1; // 支配图-深度

    public Loop loop = null;// 循环信息
    public boolean isDeleted = false;

    public BasicBlock(String label, Function parentFunction) {
        super(Type.LabelType.LABEL_TYPE);
        this.parentFunction = parentFunction;
        parentFunction.appendBlock(this);
        this.label = label;
        this.sucBlocks = new LinkedList<>();
        this.preBlocks = new LinkedList<>();
        this.instructions = new SyncLinkedList<>();
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

    public void addInstFirst(Instruction inst) {
        instructions.addFirst(inst);
    }

    public void addInstLast(Instruction inst) {
        instructions.addLast(inst);
    }

    public SyncLinkedList<Instruction> getInstructions() {
        return instructions;
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

    public LinkedList<BasicBlock> getPreBlocks() {
        return preBlocks;
    }

    public LinkedList<BasicBlock> getSucBlocks() {
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

    public LinkedList<BasicBlock> getDomTreeChildren() {
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

    public void setLoop(Loop loop) {
        this.loop = loop;
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
//            if (instruction instanceof Instruction.Terminator) {
//                break;
//            }
        }
        return outputList;
    }
    //endregion


    //函数内联的时候,维护循环信息,方便GCM
    public BasicBlock cloneToFunc(Function function, int idx) {

        BasicBlock ret = new BasicBlock(function.getName() + "_" + getLabel() + "_" + idx, function);
        CloneInfo.addValueReflect(this, ret);

        for (Instruction inst : getInstructions()) {
            Instruction tmp = inst.cloneToBBAndAddInfo(ret);
        }

        return ret;
    }


}
