package midend.Transform;

import midend.Transform.DCE.RemoveBlocks;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;

public class Mem2Reg {
    private static Function function;
    // 描述变量， 及其def, use 侧的指令，基本块
    private static Instruction.Alloc var;
    private static final ArrayList<Instruction> varNames = new ArrayList<>();
    private static final HashSet<Instruction> defInsts = new HashSet<>();
    private static final HashSet<BasicBlock> defBlocks = new HashSet<>();
    private static final HashSet<Instruction> useInsts = new HashSet<>();
    private static final HashSet<BasicBlock> useBlocks = new HashSet<>();
    // 描述重命名过程的reachingDef栈
    private static final LinkedList<Value> stack = new LinkedList<>();

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            function.buildControlFlowGraph();
            RemoveBlocks.runOnFunc(function);
            // function.checkCFG();
            function.buildDominanceGraph();
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        init();
        // System.out.println("Mem2Reg: " + function.getDescriptor());
        Mem2Reg.function = function;
        buildVariableName();
        for (Instruction varName : varNames) {
//            Print.outputLLVM(function, "debug.txt");
//            System.out.println("Mem2Reg: " + varName.getDescriptor());
            var = (Instruction.Alloc) varName;
            buildDefUse();
            //defUseCheck();
            //System.out.println("buildDefUse carried ");
            phiInserting();
            //System.out.println("phiInserting carried ");
            renameDfs(function.getEntry());
            //System.out.println("renameDfs carried ");
            var.delete();
            removeDefUse();
            //System.out.println("removeDefUse carried ");
        }
    }

    private static void init() {
        var = null;
        varNames.clear();
        defInsts.clear();
        defBlocks.clear();
        useInsts.clear();
        useBlocks.clear();
        stack.clear();
    }

    private static void defUseCheck() {
        System.out.println("defInsts: ");
        for (Instruction defInst : defInsts) {
            System.out.println(defInst.getDescriptor());
        }
        System.out.println("defBlocks: ");
        for (BasicBlock defBlock : defBlocks) {
            System.out.println(defBlock.getLabel());
        }
        System.out.println("useInsts: ");
        for (Instruction useInst : useInsts) {
            System.out.println(useInst.getDescriptor());
        }
        System.out.println("useBlocks: ");
        for (BasicBlock useBlock : useBlocks) {
            System.out.println(useBlock.getLabel());
        }
    }

    private static void removeDefUse() {
        for (Instruction defInst : defInsts) {
            if (!(defInst instanceof Instruction.Phi)) {
                defInst.delete();
            }
        }
        for (Instruction useInst : useInsts) {
            if (!(useInst instanceof Instruction.Phi)) {
                useInst.delete();
            }
        }
    }

    private static void buildVariableName() {
        for (BasicBlock block : function.getBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof Instruction.Alloc && !((Instruction.Alloc) inst).isArrayAlloc()) {
                    varNames.add(inst);
                }
            }
        }
    }

    private static void buildDefUse() {
        // Alloc 的地址描述变量名
        // def-use chain 此时的形式是地址 Alloc(name), Store(def), Load(use) 表征
        defInsts.clear();
        defBlocks.clear();
        useInsts.clear();
        useBlocks.clear();
        for (Use use : var.getUses()) {
            Instruction user = (Instruction) use.getUser();
            if (user instanceof Instruction.Store) {
                // add to defSet
                defInsts.add(user);
                // init blocks that contain def
                defBlocks.add(user.getParentBlock());
            }
            if (user instanceof Instruction.Load) {
                // add to useSet
                useInsts.add(user);
                // init blocks that contain use
                useBlocks.add(user.getParentBlock());
            }
        }
    }

    /**
     * 对变量 var 插入 phi 指令
     */
    private static void phiInserting() {
        // target blocks that phi to be inserted
        HashSet<BasicBlock> F = new HashSet<>();
            /*
                block that contains definition of var
                迭代支配边界的遍历队列， 迭代方程 DF(S) = DF(S U DF(S))
             */
        // 初始化为 S = defBlocks
        LinkedList<BasicBlock> W = new LinkedList<>(defBlocks);

        while (!W.isEmpty()) {
            BasicBlock X = W.remove();
            // System.out.println("phiInserting: now x is :" + X.getLabel());
            for (BasicBlock Y : X.getDomFrontiers()) {
                if (!F.contains(Y)) {
                    // insert phi
                    // System.out.println("phiInserting: " + Y.getLabel()+ " "+ X.getLabel());
                    F.add(Y);
                    // 加入迭代队列
                    if (!defBlocks.contains(Y)) {
                        W.add(Y);
                    }
                }
            }
        }
        // 输出F 集合
        //        for (BasicBlock basicBlock : F) {
        //             System.out.println("phiInserting: " + basicBlock.getLabel());
        //        }

        for (BasicBlock insertBlock : F) {
            // 用new Value构建相互区分的待调整的变量地址
            LinkedHashMap<BasicBlock, Value> operands = new LinkedHashMap<>();
            for (BasicBlock pred : insertBlock.getPreBlocks()) {
                operands.put(pred, new Value(var.getContentType()));
            }
            Instruction phi = new Instruction.Phi(insertBlock, var.getContentType(), operands);

            // 维护def, use 指令集
            defInsts.add(phi);
            useInsts.add(phi);

            // 调整到指令队列头部
            phi.remove();
            insertBlock.addInstFirst(phi);
            // System.out.println("phiInserting: insert " + phi.getDescriptor() + " to " + insertBlock.getLabel());
        }
    }

    private static Value getReachingDef(Type type) {
        if (stack.isEmpty()) {
            // 尝试用 Value 代替
            return GlobalVariable.getUndef(type);
        } else {
            return stack.getLast();
        }
    }

    private static void renamePhi(BasicBlock src) {
        for (BasicBlock block : src.getSucBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                if (!(inst instanceof Instruction.Phi)) {
                    break;
                }
                if (useInsts.contains(inst)) {
                    //                     System.out.println("renamePhi: " + inst.getDescriptor());
                    // replace phi's use with reachingDef
                    //                    int idx = ((Instruction.Phi) inst).getOptionalValueIdx(src);
                    ((Instruction.Phi) inst).replaceOptionalValueAtWith(src, getReachingDef(inst.getType()));
                }
            }
        }
    }

    private static void renameDfs(BasicBlock cur) {

        // print cur Block

        int count = 0;
        for (Instruction inst : cur.getInstructions()) {

            // 输出当前指令的前后指令
            // System.out.println(inst.getDescriptor());
            // used by non-phi-function
            if (useInsts.contains(inst) && !(inst instanceof Instruction.Phi)) {
                assert inst instanceof Instruction.Load;
                inst.replaceAllUsesWith(getReachingDef(((Instruction.Load) inst).getInnerType()));
            }
            // def
            if (defInsts.contains(inst)) {
                assert inst instanceof Instruction.Store || inst instanceof Instruction.Phi;
                if (inst instanceof Instruction.Store) {
                    stack.add(((Instruction.Store) inst).getValue());
                    // print inst, descriptor with its value
                    // System.out.println("renameDfs: " + inst.getDescriptor() + " : " + inst + " " + ((Instruction.Store) inst).getValue().getDescriptor());
                } else {
                    stack.add(inst);
                }
                count++;
            }
        }
        // print finished replace
        //System.out.println("renameDfs finished replace");
        // rename phi
        renamePhi(cur);
        //System.out.println("renameDfs finished renamePhi");
        // dfs
        for (BasicBlock suc : cur.getDomTreeChildren()) {
            renameDfs(suc);
        }
        //System.out.println("renameDfs finished dfs");
        // pop stack
        while (count > 0) {
            stack.removeLast();
            count--;
        }
        //System.out.println("renameDfs finished pop stack");
    }

}