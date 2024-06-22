package mir;

import backend.operand.Address;
import midend.*;
import utils.SyncLinkedList;

import java.util.*;

import static midend.CloneInfo.bbMap;

public class Function extends Value {

    //Note that Function is a GlobalValue and therefore also a Constant.
    // The value of the function
    // is its address (after linking) which is guaranteed to be constant.
    //basic inf

    public static class Argument extends Value {
        private Function parentFunction;

        public int idx;

        public Argument(Type type) {
            super(type);
            this.parentFunction = null;
        }

        public Argument(Type type, Function parentFunction) {
            super(type);
            this.parentFunction = parentFunction;
        }

        public void setParentFunction(Function function) {
            parentFunction = function;
        }

        @Override
        public String getDescriptor() {
            return "%arg_" + idx;
        }

    }

    private final Type retType; // 返回值类型
    private final ArrayList<Argument> myArguments; // 参数表
    private ArrayList<Argument> funcRArguments = new ArrayList<>(); //
    private final SyncLinkedList<BasicBlock> blocks; // 内含基本块链表
    private BasicBlock entry; // 入口基本块
    public Loop rootLoop = null; // 循环信息
    //GVN
    public boolean isLeaf = true;
    /**
     * 对内存进行了读写，这里的内存只包括全局变量
     */
    public boolean hasMemoryRead = false;
    public boolean hasMemoryWrite = false;
    public boolean hasMemoryAlloc = false;
    /**
     * IO操作
     */
    public boolean hasReadIn = false;
    public boolean hasPutOut = false;
    public boolean hasReturn = false;
    /**
     * 表示该函数有副作用，对传入的数组参数进行了写操作
     */
    public boolean hasSideEffect = false;
    /**
     * 表示该函数是无状态的,不使用/修改全局变量，传入的数组
     */
    public boolean isStateless = false;
    public boolean isRecurse = false;

    private final ControlFlowGraph CG = new ControlFlowGraph(this);
    private final DominanceGraph DG = new DominanceGraph(this);

    private int countOfBB = 0;

    public Function(Type type, String name, Type... argumentTypes) {
        super(Type.FunctionType.FUNC_TYPE);
        entry = null;
        setName(name);
        retType = type;
        blocks = new SyncLinkedList<>();

        ArrayList<Argument> arguments = new ArrayList<>();

        for (int i = 0; i < argumentTypes.length; i++) {
            Argument arg = new Argument(argumentTypes[i], this);
            arguments.add(arg);
            arg.idx = i;
        }

        myArguments = arguments;
    }

    public Function(Type type, String name, ArrayList<Type> argumentTypes, Loop rootLoop) {
        super(Type.FunctionType.FUNC_TYPE);
        setName(name);
        entry = null;
        retType = type;
        blocks = new SyncLinkedList<>();
        this.rootLoop = rootLoop;
        ArrayList<Argument> arguments = new ArrayList<>();


        for (int i = 0; i < argumentTypes.size(); i++) {
            Argument arg = new Argument(argumentTypes.get(i), this);
            arguments.add(arg);
            arg.idx = i;
        }

        myArguments = arguments;
    }

    public boolean isExternal() {
        return blocks.isEmpty();
    }

    public BasicBlock getEntry() {
        return getBlocks().getFirst();
    }

    public BasicBlock getFirstBlock() {
        return blocks.getFirst();
    }

    public BasicBlock getLastBlock() {
        return blocks.getLast();
    }

    public SyncLinkedList<BasicBlock> getBlocks() {
        return blocks;
    }

    public void appendBlock(BasicBlock block) {
        if (blocks.isEmpty()) {
            entry = block;
        }
        blocks.addLast(block);
    }

    //region outputLLVMIR
    public String FArgsToString() {
        StringBuilder str = new StringBuilder();
        Iterator<Type> iter = getArgumentsTP().iterator();
        while (iter.hasNext()) {
            str.append(iter.next().toString());
            if (iter.hasNext()) {
                str.append(',');
            }
        }
        return str.toString();
    }

    public String RArgsToString() {
        StringBuilder str = new StringBuilder();
        Iterator<Type> iter = getArgumentsTP().iterator();
        int idx = 0;
        while (iter.hasNext()) {
            str.append(iter.next().toString());
            str.append(String.format(" %%arg_%d", idx++));
            if (iter.hasNext()) {
                str.append(',');
            }
        }
        return str.toString();
    }

    public ArrayList<String> output() {
        ArrayList<String> outputList = new ArrayList<>();
        outputList.add(String.format("define %s @%s(%s) {", getRetType().toString(), name, RArgsToString()));
        for (BasicBlock block :
                blocks) {
            outputList.addAll(block.output());
            outputList.add("\n");
        }
        outputList.add("}\n");
        return outputList;
    }

    //endregion


    public Type getRetType() {
        return retType;
    }


    public ArrayList<Type> getArgumentsTP() {
        ArrayList<Type> types = new ArrayList<>();
        for (Argument arg :
                myArguments) {
            types.add(arg.getType());
        }
        return types;
    }

    public void setFuncRArguments(ArrayList<Argument> arguments) {
        this.funcRArguments = arguments;
    }

    public ArrayList<Argument> getFuncRArguments() {
        return funcRArguments;
    }

    public ArrayList<Argument> getMyArguments() {
        return myArguments;
    }

    public void buildControlFlowGraph() {
        CG.build();
    }

    public void buildDominanceGraph() {
        DG.build();
    }

    public void checkCFG() {
        CG.printGraph();
    }

    public void runMem2Reg() {
        Mem2Reg.run(this);
    }


    public Value inlineToFunc(Function tagFunc, BasicBlock retBB, Instruction.Call call, int idx) {
        //Instruction.Phi retPhi = null;

        //维护phi函数
        rootLoop.cloneToFunc(tagFunc, retBB.loop, idx);
//        CloneInfo.fixLoopReflect();
        for (BasicBlock block : bbMap.values()) {
            for (Instruction instr : block.getInstructions()) {
                if (instr instanceof Instruction.Phi) {
                    Instruction.Phi phi = (Instruction.Phi) instr;
                    phi.changePreBlocks(bbMap);
                }
                else break;
            }
        }

        ArrayList<Value> callParams = call.getParams();
        ArrayList<Argument> funcParams = getMyArguments();

        for (int i = 0; i < callParams.size(); i++) {
            CloneInfo.addValueReflect(funcRArguments.get(i), callParams.get(i));
//            for (Use use:
//                 funcRArguments.get(i).getUses()) {
//                use.getUser().replaceUseOfWith(use.get(), callParams.get(i));
//            }
        }

//        Instruction.Load load = null;
        Value retValue = null;
        LinkedHashMap<BasicBlock, Value> phiOptional = new LinkedHashMap<>();
        for (BasicBlock block : getBlocks()) {
            //((BasicBlock) CloneInfoMap.getReflectedValue(bb)).fix();
            BasicBlock needFixBB = (BasicBlock) CloneInfo.getReflectedValue(block);

            for (Instruction inst : needFixBB.getInstructions()) {
                inst.fix();
                if (inst instanceof Instruction.Return && ((Instruction.Return) inst).hasValue()) {
                    Instruction jumpToRetBB = new Instruction.Jump(needFixBB, retBB);
                    jumpToRetBB.remove();
                    needFixBB.getInstructions().insertBefore(jumpToRetBB, inst);
                    //instr.insertBefore(jumpToRetBB);
//                    retBB.getPreBlocks().add(needFixBB);
//                    needFixBB.setSucBlocks(retSucc);
//                    assert alloc != null;
                    //retPhi.addOptionalValue(((Instr.Return) instr).getRetValue());
//                    Instruction.Store store = new Instruction.Store(needFixBB, ((Instruction.Return) inst).getRetValue(), alloc);
                    phiOptional.put(needFixBB, ((Instruction.Return) inst).getRetValue());
//                    store.remove();
//                    needFixBB.getInstructions().insertBefore(store, jumpToRetBB);
                    //load = new Instruction.Load(retBB, alloc);
                    //load.remove();
                    //retBB.getInstructions().insertAfter(load, store);
                    inst.remove();
                    //维护前驱后继
                }
                else if (inst instanceof Instruction.Return) {
                    Instruction jumpToRetBB = new Instruction.Jump(needFixBB, retBB);
                    jumpToRetBB.remove();
                    needFixBB.getInstructions().insertBefore(jumpToRetBB, inst);
                    //instr.insertBefore(jumpToRetBB);
//                    retBB.getPreBlocks().add(needFixBB);
//                    needFixBB.setSucBlocks(retSucc);
                    inst.remove();
                }
            }
        }

        if (!(retType instanceof Type.VoidType)) {
            if (phiOptional.size() > 1)
                retValue = new Instruction.Phi(retBB, retType, phiOptional);
            else {
                retValue = phiOptional.values().iterator().next();
            }
//            load = new Instruction.Load(retBB, alloc);
            //CloneInfo.addValueReflect(call, load);
        }
        return retValue;

    }

    // 是不是存到地址里的arg
    public boolean isAddressArg(Value value) {
        if (!(value instanceof Argument)) {
            return false;
        }
        Type argType = value.getType();
        boolean isInt = !argType.isFloatTy();
        int count = 0;
        for (Argument arg : funcRArguments) {
            if (isInt != arg.getType().isFloatTy()) {
                count++;
            }
            if (arg == value) {
                return count > 8;
            }
        }
        return false;
    }

    public Address getArgAddress(Argument argument) {
        int sp_move = 0;
        int int_count = 0;
        int float_count = 0;
        for (Argument arg : funcRArguments) {
            if (arg == argument) {
                return new Address(sp_move);
            }
            if (arg.getType().isPointerTy() || arg.getType().isInt64Ty()) {
                int_count++;
                if (int_count > 8) {
                    sp_move -= 8;
                }
            }
            else if (arg.getType().isInt32Ty()) {
                int_count++;
                if (int_count > 8) {
                    sp_move -= 4;
                }
            }
            else if (arg.getType().isFloatTy()) {
                float_count++;
                if (float_count > 8) {
                    sp_move -= 4;
                }
            }
        }
        throw new RuntimeException("not arg in this func");
    }

    public String getBBName() {
        String name = getName() + "_BB" + countOfBB;
        countOfBB++;
        return name;
    }

    /**
     * 获取函数的dom树层序遍历
     *
     * @return
     */
    public ArrayList<BasicBlock> getDomTreeLayerSort() {
        BasicBlock entry = getEntry();
        ArrayList<BasicBlock> layerSort = new ArrayList<>();
        Queue<BasicBlock> queue = new LinkedList<>();
        queue.offer(entry);
        while (!queue.isEmpty()) {
            BasicBlock cur = queue.poll();
            layerSort.add(cur);
            for (BasicBlock child : cur.getDomTreeChildren()) {
                queue.offer(child);
            }
        }
        return layerSort;
    }
}
