package midend;

import mir.Function;
import mir.Module;
import mir.*;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import static manager.CentralControl._DCD_OPEN;

public class DeadCodeDelete {
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
        int blockLen = main.getBlocks().getSize();
        for (int i = blockLen - 1; i >= 0; i--) {
            BasicBlock block = main.getBlocks().get(i);
            int instLen = block.getInstructions().getSize();
            for (int j = instLen - 1; j >= 0; j--) {
                Instruction inst = block.getInstructions().get(j);
                if (inst.getInstType() == Instruction.InstType.RETURN) {
                    newUsefulVar.add(inst);
                }
            }
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
        if (value instanceof Instruction) {
            Instruction inst = (Instruction) value;
            newUsefulVar.add(inst.getParentBlock());
            newUsefulVar.add(inst.getParentBlock().getParentFunction());
            for (Value operand : inst.getOperands()) {
                newUsefulVar.add(operand);
            }
        }
        else if (value instanceof BasicBlock) {
            BasicBlock block = (BasicBlock) value;
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof Instruction.Call) {
                    Instruction.Call call = (Instruction.Call) inst;
                    Function callee = call.getDestFunction();
                    newUsefulVar.add(inst);
                }
            }
            for (Use use : block.getUses()) {
                newUsefulVar.add(use.getUser());
            }
        }
        else if (value instanceof Function) {
            Function func = (Function) value;
            //倒序遍历找ret
            int blockLen = func.getBlocks().getSize();
            for (int i = blockLen - 1; i >= 0; i--) {
                BasicBlock block = func.getBlocks().get(i);
                int instLen = block.getInstructions().getSize();
                for (int j = instLen - 1; j >= 0; j--) {
                    Instruction inst = block.getInstructions().get(j);
                    if (inst.getInstType() == Instruction.InstType.RETURN) {
                        newUsefulVar.add(inst);
                    }
                }
            }
            //地址作为函数参数在这里认为是活跃的
            for (Value p : func.getFuncRArguments()) {
                if (p.getType().isPointerTy()) newUsefulVar.add(p);
            }
        }
    }

    private static void DeleteUselessVar(Module module) {
        Iterator<Function> functionIterator = module.getFuncSet().iterator();
        while (functionIterator.hasNext()) {
            Function function = functionIterator.next();
            if (function.isExternal()) {
                continue;
            }
            uselessBBDelete(function);
            if (function.getBlocks().isEmpty()) {
                functionIterator.remove();
            }
        }
        Iterator<GlobalVariable> iterator = module.getGlobalValues().iterator();
        while (iterator.hasNext()) {
            GlobalVariable globalVar = iterator.next();
            if (!usefulVar.contains(globalVar)) {
                iterator.remove();
            }
        }
    }

    private static void uselessBBDelete(Function function) {
        Iterator<BasicBlock> iterator = function.getBlocks().iterator();
        while (iterator.hasNext()) {
            BasicBlock block = iterator.next();
            uselessInstDelete(block);
            if (block.getInstructions().isEmpty()) {
//                System.out.println("uselessBBDelete: " + block.getName());
                iterator.remove();
            }
        }
    }

    private static void uselessInstDelete(BasicBlock block) {
        Iterator<Instruction> iterator = block.getInstructions().iterator();
        while (iterator.hasNext()) {
            Instruction inst = iterator.next();
            if (!usefulVar.contains(inst)) {
//                System.out.println("uselessInstDelete: " + inst.getDescriptor());
                iterator.remove();
            }
        }
    }

}
