package mir;

import backend.operand.Address;
import midend.Analysis.Manager.ModuleAnalysisManager;
import midend.Analysis.ScalarEvolution;
import midend.Transform.Loop.LoopInfo;
import midend.Util.CloneInfo;
import mir.result.SCEVinfo;
import utils.NelLinkedList;

import java.util.*;


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
    private ArrayList<Argument> funcRArguments = new ArrayList<>(); //
    private final NelLinkedList<BasicBlock> blocks; // 内含基本块链表
    private BasicBlock entry; // 入口基本块
    public LoopInfo loopInfo = null; // 循环信息
    private int countOfBB = 0;

    public SCEVinfo scevInfo = new SCEVinfo();

    public Function(Type type, String name, Type... argumentTypes) {
        super(Type.FunctionType.FUNC_TYPE);
        entry = null;
        setName(name);
        retType = type;
        blocks = new NelLinkedList<>();
        ArrayList<Argument> arguments = new ArrayList<>();
        for (int i = 0; i < argumentTypes.length; i++) {
            Argument arg = new Argument(argumentTypes[i], this);
            arguments.add(arg);
            arg.idx = i;
        }
        funcRArguments = arguments;
    }

    public Function(Type type, String name, ArrayList<Type> argumentTypes) {
        super(Type.FunctionType.FUNC_TYPE);
        setName(name);
        entry = null;
        retType = type;
        blocks = new NelLinkedList<>();
        ArrayList<Argument> arguments = new ArrayList<>();


        for (int i = 0; i < argumentTypes.size(); i++) {
            Argument arg = new Argument(argumentTypes.get(i), this);
            arguments.add(arg);
            arg.idx = i;
        }

        funcRArguments = arguments;
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

    public NelLinkedList<BasicBlock> getBlocks() {
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
        Iterator<Argument> iter = getFuncRArguments().iterator();
        while (iter.hasNext()) {
            Argument arg = iter.next();
            str.append(arg.getType().toString() + " ");
            str.append(arg.getDescriptor());
            if (iter.hasNext()) {
                str.append(", ");
            }
        }
        return str.toString();
    }

    public ArrayList<String> output() {
        ArrayList<String> outputList = new ArrayList<>();
        outputList.add(String.format("define %s @%s(%s) {", getRetType().toString(), name, RArgsToString()));
        for (BasicBlock block : blocks) {
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
        for (Argument arg : funcRArguments) {
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


    /**
     * 构建函数的控制流图 <br>
     * 建议使用AnalysisManager.refreshCFG(Function)来刷新CFG
     * @deprecated
     */
    @Deprecated
    public void buildControlFlowGraph() {
        ModuleAnalysisManager.refreshCFG(this);
    }

    /**
     * 构建函数的支配图 <br>
     * 建议使用AnalysisManager.refreshDG(Function)来刷新DG
     * @deprecated
     */
    @Deprecated
    public void buildDominanceGraph() {
        ModuleAnalysisManager.refreshDG(this);
    }

    public void buildSCEVInfo() {
        scevInfo = ScalarEvolution.run(this);
    }


    public Value inlineToFunc(CloneInfo cloneInfo, Function tagFunc, BasicBlock retBB, Instruction.Call call, int idx) {
        //Instruction.Phi retPhi = null;

        //维护phi函数
//        CloneInfo.fixLoopReflect();
        HashMap<BasicBlock, BasicBlock> bbMap = new HashMap<>();
        for (BasicBlock block : getBlocks()) {
            bbMap.put(block, block.cloneToFunc(cloneInfo, tagFunc));
        }
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
//        ArrayList<Argument> funcParams = getMyArguments();

        for (int i = 0; i < callParams.size(); i++) {
            cloneInfo.addValueReflect(funcRArguments.get(i), callParams.get(i));
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
            BasicBlock needFixBB = (BasicBlock) cloneInfo.getReflectedValue(block);

            for (Instruction inst : needFixBB.getInstructions()) {
                inst.fix(cloneInfo);
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
        return getName() + "_BB" + countOfBB++;
    }


    /**
     * 获取函数的dom树层序遍历
     *
     * @return
     */
    public ArrayList<BasicBlock> getDomTreeLayerSort() {
        if (entry == null) return new ArrayList<>();
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

    /**
     * 获取支配树后序遍历
     *
     * @return 返回支配树后序遍历顺序的基本块列表
     */
    public ArrayList<BasicBlock> getDomTreePostOrder() {
        if (entry == null) return new ArrayList<>();
        ArrayList<BasicBlock> postOrder = new ArrayList<>();
        Stack<BasicBlock> stack = new Stack<>();
        stack.push(entry);
        Set<BasicBlock> visited = new HashSet<>();
        visited.add(entry);

        while (!stack.isEmpty()) {
            BasicBlock cur = stack.peek();
            boolean allChildrenVisited = true;

            for (BasicBlock child : cur.getDomTreeChildren()) {
                if (!visited.contains(child)) {
                    stack.push(child);
                    visited.add(child);
                    allChildrenVisited = false;
                }
            }

            if (allChildrenVisited) {
                // 如果当前节点的所有子节点都已经访问过，则将当前节点从栈中弹出并加入后序遍历结果列表中
                stack.pop();
                postOrder.add(cur);
            }
        }

        // 返回支配树的后序遍历顺序的列表
        return postOrder;
    }


    /**
     * 获取函数的反向后序遍历，即dfs的反序
     *
     * @return
     */
    public ArrayList<BasicBlock> buildReversePostOrderTraversal() {
        ArrayList<BasicBlock> rpot = new ArrayList<>();
        HashSet<BasicBlock> visited = new HashSet<>();
        Stack<BasicBlock> stack = new Stack<>();
        BasicBlock entry = this.getEntry();
        stack.push(entry);
        while (!stack.isEmpty()) {
            BasicBlock current = stack.peek();
            if (visited.contains(current)) {
                stack.pop();
                if (!rpot.contains(current)) {
                    rpot.add(current);
                }
            }
            else {
                visited.add(current);
                for (BasicBlock succ : current.getSucBlocks()) {
                    if (!visited.contains(succ)) {
                        stack.push(succ);
                    }
                }
            }
        }
        Collections.reverse(rpot);
        return rpot;
    }

    @Override
    public void delete() {
        super.release();
        ArrayList<BasicBlock> delList = new ArrayList<>();
        blocks.forEach(delList::add);
        delList.forEach(Value::delete);
    }
}
