package midend.Transform.DCE;

import midend.Util.FuncInfo;
import mir.Function;
import mir.Module;
import mir.*;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import static manager.CentralControl._DCD_OPEN;

public class DeadCodeEliminate {
    private final static HashSet<Value> usefulVar = new HashSet<>();//所有有用的Value
    private static final HashSet<Value> newUsefulVar = new HashSet<>();//每一轮迭代新加入的有用的Value


    public static void run(Module module) {
        if (!_DCD_OPEN) return;
        clear();
        InitUsefulVar(module);
        FindAllUsefulVar();
        DeleteUselessVar(module);
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) {
                continue;
            }
            function.buildControlFlowGraph();
        }
    }

    public static void clear() {
        usefulVar.clear();
        newUsefulVar.clear();
    }

    public static void InitUsefulVar(Module module) {
        //在init中，只认为main的ret是有用的
        Function main = module.getFunctions().get("main");
        //倒序遍历找ret
        int blockLen = main.getBlocks().size();
        for (int i = blockLen - 1; i >= 0; i--) {
            BasicBlock block = main.getBlocks().get(i);
            Instruction inst = block.getInstructions().getLast();
//            System.out.println(inst.getDescriptor());
            if (inst.getInstType() == Instruction.InstType.RETURN) newUsefulVar.add(inst);
        }
    }

    private static void FindAllUsefulVar() {
        boolean steady = false;
        HashSet<Value> temp;
        while (!steady) {
            usefulVar.addAll(newUsefulVar);
            temp = new HashSet<>(newUsefulVar);
            newUsefulVar.clear();
            for (Value var : temp) {
                updateUse(var);
            }
            newUsefulVar.removeAll(usefulVar);
            steady = newUsefulVar.isEmpty();
        }
    }

    /**
     * 将传入的Value的use进行更新
     *
     * @param value
     */
    public static void updateUse(Value value) {
//        System.out.println("updateUse: " + value);
//        newUsefulVar.add(value);
        if (value.getType().isPointerTy()) {
            for (Use use : value.getUses()) {
                newUsefulVar.add(use.getUser());
            }
        }
        if (value instanceof Instruction inst) {
            newUsefulVar.add(inst.getParentBlock());
            newUsefulVar.add(inst.getParentBlock().getParentFunction());
            newUsefulVar.addAll(inst.getOperands());
        }
        else if (value instanceof BasicBlock block) {
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof Instruction.Call call) {
                    Function callee = call.getDestFunction();
                    if (isUsefulCall(callee)) {
                        newUsefulVar.add(inst);
                    }
                }
            }

            for (Use use : block.getUses()) {
                newUsefulVar.add(use.getUser());
            }
        }
        else if (value instanceof Function func) {
            //倒序遍历找ret
            int blockLen = func.getBlocks().size();
            for (int i = blockLen - 1; i >= 0; i--) {
                BasicBlock block = func.getBlocks().get(i);
                Instruction inst = block.getInstructions().getLast();
                if (inst.getInstType() == Instruction.InstType.RETURN) newUsefulVar.add(inst);
            }
            //地址作为函数参数,考虑call的实参是否活跃?
//            for (Use use : func.getUses()) {
//                Instruction.Call call = (Instruction.Call) use.getUser();
//                for (int i = 0; i < call.getParams().size(); i++) {
//                    if (call.get)
//                }
//            }
            //todo 现在默认有用函数的传入数组都是有用的，以及所有的调用点都是有用的
            //todo 但是这样可能会导致一些不必要的参数传递和冗余的调用
            for (Value p : func.getFuncRArguments()) {
                if (p.getType().isPointerTy()) newUsefulVar.add(p);
            }
            for (Use use : func.getUses()) {
                Instruction.Call call = (Instruction.Call) use.getUser();
                if (usefulVar.contains(call.getParentBlock()) || newUsefulVar.contains(call.getParentBlock())) {
                    newUsefulVar.add(call);
                }
            }
        }
    }

    public static boolean isUsefulCall(Function callee) {
        if (!FuncInfo.FuncAnalysisOpen) return true;
        return usefulVar.contains(callee)
                || (callee.isExternal() && !callee.getName().equals("memset"))
                || FuncInfo.hasReadIn.get(callee) || FuncInfo.hasPutOut.get(callee);
    }

    private static void DeleteUselessVar(Module module) {
        ArrayList<Function> delList = new ArrayList<>();
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            if (!usefulVar.contains(function)) delList.add(function);
            else uselessBBDelete(function);
        }
        for (Function func : delList) {
            func.release();
            module.removeFunction(func);
        }
        ArrayList<GlobalVariable> delGlobals = new ArrayList<>();
        for (GlobalVariable gv : module.getGlobalValues()) {
            if (!usefulVar.contains(gv)) delGlobals.add(gv);
        }
        for (GlobalVariable gv : delGlobals) {
            module.getGlobalValues().remove(gv);
            gv.release();
        }
    }

    private static void uselessBBDelete(Function function) {
        ArrayList<BasicBlock> delList = new ArrayList<>();
        for (BasicBlock block : function.getBlocks()) {
            if (!usefulVar.contains(block)) {
//                System.out.println("uselessInstDelete: " + inst.getDescriptor());
//                iterator.remove();
                delList.add(block);
            } else uselessInstDelete(block);
        }
        delList.forEach(BasicBlock::delete);
    }

    private static void uselessInstDelete(BasicBlock block) {
        ArrayList<Instruction> delList = new ArrayList<>();
        Iterator<Instruction> iterator = block.getInstructions().iterator();
        while (iterator.hasNext()) {
            Instruction inst = iterator.next();
            if (!usefulVar.contains(inst)) {
//                System.out.println("uselessInstDelete: " + inst.getDescriptor());
                delList.add(inst);
            }
        }
        delList.forEach(Value::delete);
    }

}
