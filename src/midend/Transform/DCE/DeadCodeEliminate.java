package midend.Transform.DCE;

import midend.Analysis.AnalysisManager;
import midend.Analysis.FuncAnalysis;
import midend.Util.FuncInfo;
import mir.Module;
import mir.*;


import java.util.ArrayList;
import java.util.HashSet;

public class DeadCodeEliminate {
    private final static HashSet<Value> usefulVar = new HashSet<>();//所有有用的Value
    private static final HashSet<Value> newUsefulVar = new HashSet<>();//每一轮迭代新加入的有用的Value

    public static boolean run(Module module) {
        clear();
        InitUsefulVar(module);
        FindAllUsefulVar();
        return DeleteUselessVar(module);
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
     */
    public static void updateUse(Value value) {
//        System.out.println("updateUse: " + value);
//        newUsefulVar.add(value);
        if (value.getType().isPointerTy()) {
            for (Instruction inst : value.getUsers()) {
                if (inst instanceof Instruction.Store || inst instanceof Instruction.Call
                        || inst instanceof Instruction.AtomicAdd || inst instanceof Instruction.GetElementPtr)
                {
                    newUsefulVar.add(inst);
                    continue;
                }
                if (!inst.getUses().isEmpty()) newUsefulVar.add(inst);
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
        if (!FuncAnalysis.FuncAnalysisOpen) return true;
        FuncInfo funcInfo = AnalysisManager.getFuncInfo(callee);
        return usefulVar.contains(callee)
                || (callee.isExternal() && !callee.getName().equals("memset"))
                || callee.getName().equals("NELParallelFor")
                || funcInfo.hasReadIn || funcInfo.hasPutOut;
    }

    private static boolean DeleteUselessVar(Module module) {
        boolean modified = false;
        ArrayList<Function> delList = new ArrayList<>();
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            if (!usefulVar.contains(function)) delList.add(function);
            else {
                modified |= uselessBBDelete(function);
            }
        }
        for (Function func : delList) {
            func.delete();
            module.removeFunction(func);
        }
        modified |= !delList.isEmpty();
        ArrayList<GlobalVariable> delGlobals = new ArrayList<>();
        for (GlobalVariable gv : module.getGlobalValues()) {
            if (!usefulVar.contains(gv)) delGlobals.add(gv);
        }
        for (GlobalVariable gv : delGlobals) {
            module.getGlobalValues().remove(gv);
            gv.release();
        }
        modified |= !delGlobals.isEmpty();
        return modified;
    }

    private static boolean uselessBBDelete(Function function) {
        boolean modified = false;
        ArrayList<BasicBlock> delList = new ArrayList<>();
        for (BasicBlock block : function.getBlocks()) {
            if (!usefulVar.contains(block)) {
                delList.add(block);
            }
            else
                modified |= uselessInstDelete(block);
        }
        delList.forEach(BasicBlock::delete);
        modified |= !delList.isEmpty();
        return modified;
    }

    private static boolean uselessInstDelete(BasicBlock block) {
        ArrayList<Instruction> delList = new ArrayList<>();
        for (Instruction inst : block.getInstructions()) {
            if (!usefulVar.contains(inst)) {
//                System.out.println("uselessInstDelete: " + inst.getDescriptor());
                delList.add(inst);
            }
        }
        delList.forEach(Value::delete);
        return !delList.isEmpty();
    }

}
