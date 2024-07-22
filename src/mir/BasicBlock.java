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

    public ArrayList<Instruction> getMainInstructions() {
        ArrayList<Instruction> mainInstructions = new ArrayList<>();
        for (Instruction inst : instructions) {
            if (inst instanceof Instruction.Phi) continue;
            if (inst instanceof Instruction.Terminator) break;
            mainInstructions.add(inst);
        }
        return mainInstructions;
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

    public void addInstAfterPhi(Instruction inst) {
        Instruction pos = instructions.getFirst();
        while (pos instanceof Instruction.Phi) {
            pos = (Instruction) pos.getNext();
        }
        instructions.insertBefore(inst, pos);
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

    public ArrayList<BasicBlock> getPreBlocks() {
        return AnalysisManager.getCFGPredecessors(this);
    }

    public ArrayList<BasicBlock> getSucBlocks() {
        return AnalysisManager.getCFGSuccessors(this);
    }

    public HashSet<BasicBlock> getDomFrontiers() {
        return AnalysisManager.getDomFrontiers(this);
    }

    public ArrayList<BasicBlock> getDomTreeChildren() {
        return AnalysisManager.getDomTreeChildren(this);
    }

    public HashSet<BasicBlock> getDomSet() {
        return AnalysisManager.getDominators(this);
    }

    public int getDomDepth() {
        return AnalysisManager.getDomDepth(this);
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
