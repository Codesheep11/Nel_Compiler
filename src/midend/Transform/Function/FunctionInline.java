package midend.Transform.Function;

import midend.Analysis.AnalysisManager;
import midend.Util.CloneInfo;
import mir.*;
import mir.Module;
import utils.NelLinkedList;

import java.util.*;

import static manager.CentralControl._FUNC_INLINE_OPEN;

public class FunctionInline {
    private static final int INLINE_SIZE_THRESHOLD = 150000;
    private static Collection<Function> functions;
    private static Module module;
    private static final ArrayList<Function> funcCanInline = new ArrayList<>();
    private static final HashMap<Function, Integer> funcSize = new HashMap<>();
    //A调用B则存在B->A
    private static final HashMap<Function, HashSet<Function>> reserveMap = new HashMap<>();
    //记录反图的入度
    private static final HashMap<Function, Integer> inNum = new HashMap<>();
    //A调用B则存在A->B
    private static final HashMap<Function, HashSet<Function>> Map = new HashMap<>();
    private static final Queue<Function> queue = new LinkedList<>();

    /***
     * 函数内联
     * @param module 编译模块的 ir 形式
     *
     */
    public static void run(Module module) {
        if (!_FUNC_INLINE_OPEN) return;
        FunctionInline.module = module;
        functions = new HashSet<>();
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            functions.add(function);
        }
        GetFuncCanInline();
        for (Function function : funcCanInline) {
            inlineFunc(function);
            module.removeFunction(function);
        }
        for (Function function : funcCanInline) function.delete();
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            AnalysisManager.refreshCFG(function);
        }
    }

    /***
     * 获取可以内联的函数
     */
    private static void GetFuncCanInline() {
        makeReserveMap();
        calFuncSize();
        topologySort();
    }

    private static void calFuncSize() {
        for (Function function : functions) {
            int size = 0;
            for (BasicBlock basicBlock : function.getBlocks()) {
                size += basicBlock.getInstructions().size();
            }
            funcSize.put(function, size);
        }
    }

    /***
     * 构建反图
     *
     */
    private static void makeReserveMap() {

        for (Function function : functions) {
//            System.out.println("function: " + function.getName());
            Map.put(function, new HashSet<>());
        }

        for (Function function : functions) {
            reserveMap.put(function, new HashSet<>());
            if (!inNum.containsKey(function)) {
                inNum.put(function, 0);
            }

            for (Use use : function.getUses()) {
                assert use.getUser() instanceof Instruction.Call;
                Function userFunc = ((Instruction.Call) use.getUser()).getParentBlock().getParentFunction();
                Map.get(userFunc).add(function);
                if (!inNum.containsKey(userFunc)) {
                    inNum.put(userFunc, 0);
                }
                if (reserveMap.get(function).add(userFunc)) {
                    inNum.put(userFunc, inNum.get(userFunc) + 1);
                }
            }
        }
    }


    private static void topologySort() {
        for (Function function : inNum.keySet()) {
//            if (inNum.get(function) == 0 && !function.getName().equals("main") && !function.isExternal() && funcSize.get(function) <= INLINE_SIZE_THRESHOLD)
            if (inNum.get(function) == 0 && !function.getName().equals("main") && !function.isExternal()) {
                queue.add(function);
            }
        }
        while (!queue.isEmpty()) {
            Function pos = queue.peek();
            funcCanInline.add(pos);
            for (Function next : reserveMap.get(pos)) {
                inNum.put(next, inNum.get(next) - 1);
                if (inNum.get(next) == 0 && !next.getName().equals("main") && !next.isExternal()) {
                    queue.add(next);
                }
            }
            queue.poll();
        }
    }

    /***
     * 内联一个具体的函数
     * targets : 调用该函数的所有指令所在的基本块
     * callers : 调用该函数的所有指令
     * 一一对应的
     * @param function 要内联的函数
     */
    private static void inlineFunc(Function function) {
        ArrayList<Instruction.Call> callers = new ArrayList<>();
        ArrayList<BasicBlock> targets = new ArrayList<>();
        for (Use use : function.getUses()) {
            assert use.getUser() instanceof Instruction.Call;
            callers.add((Instruction.Call) use.getUser());
            targets.add(((Instruction.Call) use.getUser()).getParentBlock());
        }
        int idx = 0;
        for (Instruction.Call call : callers) {
//            System.out.println("inline " + function.getName() + " to " + call.getParentBlock().getParentFunction().getName() + " " + idx);
            transCallToFunc(function, call, idx, callers);
            Function curFunc = call.getParentBlock().getParentFunction();
//            System.out.println("inline " + function.getName() + " to " + curFunc.getName() + " " + idx);
            AnalysisManager.refreshCFG(curFunc);
            idx++;
        }
        for (BasicBlock bb : function.getBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof Instruction.Call) {
                    Function callee = ((Instruction.Call) inst).getDestFunction();
                    callee.use_remove(new Use(inst, callee));
                }
            }
        }
    }


    /***
     * 对一条具体的 Call 指令内联
     * @param function 要内联的函数
     * @param call 调用该函数的指令
     * @param idx 对一个函数而言被内联的第几个 call
     * @param callers 全体调用者
     */
    private static void transCallToFunc(Function function, Instruction.Call call, int idx, ArrayList<Instruction.Call> callers) {
//        System.out.println("transCallToFunc: " + call);
        CloneInfo cloneInfo = new CloneInfo();
        // 所在的函数
        Function inFunction = ((Instruction.Call) cloneInfo.getReflectedValue(call)).getParentBlock().getParentFunction();
        // 拆分前的 call Block
        BasicBlock beforeCallBB = call.getParentBlock();
        ArrayList<BasicBlock> afterCallBBs = beforeCallBB.getSucBlocks();
//        CallbbCut.add(beforeCallBB);
        Instruction inst = null;
        for (Instruction tmp : beforeCallBB.getInstructions()) {
            if (tmp == call) {
                inst = tmp;
                break;
            }
        }
        assert inst != null;
        // 命名上 retBB 为 call 的下一条指令所在的基本块
        BasicBlock retBB = new BasicBlock(function.getName() + "_ret_" + idx, inFunction);

        Value ret = function.inlineToFunc(cloneInfo, inFunction, retBB, call, idx);

        BasicBlock afterCallBB = new BasicBlock(inFunction.getName() + "_after_call_" + function.getName() + "_" + idx, inFunction);
//        CallbbCut.add(afterCallBB);
//        Print.output(inFunction,"store.txt");
        for (BasicBlock suc : afterCallBBs) {
            for (Instruction.Phi instr : suc.getPhiInstructions()) {
                instr.changePreBlock(beforeCallBB, afterCallBB);
            }
        }
        LinkedList<Instruction> instrs = new LinkedList<>();
        NelLinkedList.NelLinkNode instr = inst.getNext();
        while (instr instanceof Instruction) {
            instrs.add((Instruction) instr);
            instr = instr.getNext();
        }

        for (Instruction instr1 : instrs) {
            Instruction newInst = instr1.cloneToBBAndAddInfo(cloneInfo, afterCallBB);
            newInst.fix(cloneInfo);
            if (instr1 instanceof Instruction.Call && callers.contains(instr1)) {
                callers.set(callers.indexOf(instr1), (Instruction.Call) newInst);
            }
            else if (instr1 instanceof Instruction.Call) {
                Function callee = ((Instruction.Call) instr1).getDestFunction();
                callee.use_remove(new Use(instr1, callee));
            }
            ArrayList<Use> toFix = new ArrayList<>(instr1.getUses());
            for (Use use : toFix) ((Instruction) use.getUser()).fix(cloneInfo);
        }
        instrs.forEach(Value::delete);

        Instruction jumpToCallBB = new Instruction.Jump(beforeCallBB, (BasicBlock) cloneInfo.getReflectedValue(function.getFirstBlock()));
        jumpToCallBB.remove();
        beforeCallBB.insertInstBefore(jumpToCallBB, inst);
        Instruction jumpToAfterCallBB = new Instruction.Jump(retBB, afterCallBB);
        if (ret instanceof Instruction.Phi && ((Instruction.Phi) ret).getParentBlock().equals(retBB)) {
            ret.remove();
            retBB.insertInstBefore((Instruction.Phi) ret, retBB.getFirstInst());
        }

        if (ret != null) {
            ArrayList<Use> toFix = new ArrayList<>(inst.getUses());
            for (Use use : toFix) {
                use.getUser().replaceUseOfWith(use.getValue(), ret);
            }
        }

        assert inst.getParentBlock().equals(beforeCallBB);

//        while (inst.hasNext()) {
////            System.out.println("1");
//            inst.getNext().remove();
//        }
        //beforeCallBB.getInstructions().setEnd(inst);
        inst.delete();
    }


}
