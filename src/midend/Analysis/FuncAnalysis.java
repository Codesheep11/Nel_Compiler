package midend.Analysis;

import midend.Transform.GlobalVarLocalize;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.*;

import static midend.Util.FuncInfo.ExternFunc.*;
import static midend.Util.FuncInfo.FuncAnalysisOpen;
import static midend.Util.FuncInfo.clear;


public class FuncAnalysis {


    public static void run(Module module) {
        if (!FuncAnalysisOpen) FuncAnalysisOpen = true;
        Function main = module.getFunctions().get("main");
        clear();
        for (Function callee : module.getFuncSet()) {
            for (Use use : callee.getUses()) {
                Instruction.Call call = (Instruction.Call) use.getUser();
                Function caller = call.getParentBlock().getParentFunction();
//                System.out.println(caller.getName() + " -> " + callee.getName());
                FuncInfo.addCall(caller, callee);
            }
        }
        HashSet<Value> visited = new HashSet<>();
        LinkedList<Function> queue = new LinkedList<>();
        queue.add(main);
        visited.add(main);
        while (!queue.isEmpty()) {
            Function func = queue.poll();
            if (!FuncInfo.callGraph.containsKey(func)) FuncInfo.callGraph.put(func, new HashSet<>());
            for (Function callee : FuncInfo.callGraph.get(func)) {
                if (!visited.contains(callee)) {
                    queue.add(callee);
                    visited.add(callee);
                }
            }
        }
        ArrayList<Function> deleteList = new ArrayList<>();
        for (Function func : module.getFuncSet()) {
            if (!visited.contains(func)) {
                deleteList.add(func);
            }
        }
        for (Function func : deleteList) {
            func.delete();
            module.removeFunction(func);
        }

        for (Function func : module.getFuncSet()) {
            if (func.getName().equals("main")) FuncInfo.main = func;
            FuncInfo.hasMemoryRead.put(func, false);
            FuncInfo.hasMemoryWrite.put(func, false);
            FuncInfo.hasMemoryAlloc.put(func, false);
            FuncInfo.hasReadIn.put(func, false);
            FuncInfo.hasPutOut.put(func, false);
            FuncInfo.hasReturn.put(func, false);
            FuncInfo.hasSideEffect.put(func, false);
            FuncInfo.isStateless.put(func, true);
            FuncInfo.isRecurse.put(func, false);
        }


        ExternFuncInit();
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            BuildAttribute(func);
        }
        TransAttribute();
    }

    public static void ExternFuncInit() {
        if (FuncInfo.callGraph.containsKey(GETINT)) FuncInfo.hasReadIn.put(GETINT, true);
        if (FuncInfo.callGraph.containsKey(PUTINT)) FuncInfo.hasPutOut.put(PUTINT, true);
        if (FuncInfo.callGraph.containsKey(GETCH)) FuncInfo.hasReadIn.put(GETCH, true);
        if (FuncInfo.callGraph.containsKey(GETFLOAT)) FuncInfo.hasReadIn.put(GETFLOAT, true);
        if (FuncInfo.callGraph.containsKey(PUTCH)) FuncInfo.hasPutOut.put(PUTCH, true);
        if (FuncInfo.callGraph.containsKey(PUTFLOAT)) FuncInfo.hasPutOut.put(PUTFLOAT, true);
        if (FuncInfo.callGraph.containsKey(STARTTIME)) FuncInfo.hasPutOut.put(STARTTIME, true);
        if (FuncInfo.callGraph.containsKey(STOPTIME)) FuncInfo.hasPutOut.put(STOPTIME, true);
        if (FuncInfo.callGraph.containsKey(GETARRAY)) FuncInfo.hasReadIn.put(GETARRAY, true);
        if (FuncInfo.callGraph.containsKey(GETFARRAY)) FuncInfo.hasReadIn.put(GETFARRAY, true);
        if (FuncInfo.callGraph.containsKey(PUTARRAY)) FuncInfo.hasPutOut.put(PUTARRAY, true);
        if (FuncInfo.callGraph.containsKey(PUTFARRAY)) FuncInfo.hasPutOut.put(PUTFARRAY, true);
        if (FuncInfo.callGraph.containsKey(PUTF)) FuncInfo.hasPutOut.put(PUTF, true);
    }

    private static void BuildAttribute(Function function) {
        boolean hasMemoryRead = false;
        boolean hasMemoryWrite = false;
        boolean hasMemoryAlloc = false;
        boolean hasReturn = !(function.getRetType() instanceof Type.VoidType);
        boolean isRecurse = false;
        boolean hasSideEffect = false;
        for (BasicBlock bb : function.getBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof Instruction.Load load) {
                    if (GlobalVarLocalize.isGlobalAddr(load.getAddr()) != null) hasMemoryRead = true;
                }
                else if (inst instanceof Instruction.Store store) {
                    if (GlobalVarLocalize.isGlobalAddr(store.getAddr()) != null) hasMemoryWrite = true;
                    else if (isSideEffect(store.getAddr(), function)) hasSideEffect = true;
                }
                else if (inst instanceof Instruction.Call call) {
                    Function callee = call.getDestFunction();
                    if (Objects.equals(callee.getName(), function.getName())) {
                        isRecurse = true;
                    }
                }
                else if (inst instanceof Instruction.Alloc) {
                    hasMemoryAlloc = true;
                }
                if (hasMemoryRead && hasMemoryWrite && isRecurse && hasSideEffect && hasMemoryAlloc) break;
            }
        }
        FuncInfo.hasMemoryRead.put(function, hasMemoryRead);
        FuncInfo.hasMemoryWrite.put(function, hasMemoryWrite);
        FuncInfo.hasMemoryAlloc.put(function, hasMemoryAlloc);
        FuncInfo.hasReturn.put(function, hasReturn);
        FuncInfo.isRecurse.put(function, isRecurse);
        FuncInfo.hasSideEffect.put(function, hasSideEffect);
    }

    /**
     * sideEffect 只考虑是否对传入的参数进行了地址写入
     *
     * @param addr
     * @param function
     * @return
     */
    public static boolean isSideEffect(Value addr, Function function) {
        if (addr instanceof Instruction.GetElementPtr gep) {
            addr = gep.getBase();
        }
        if (addr instanceof Function.Argument) return true;
        if (addr instanceof Instruction.Alloc) {
            return !((Instruction.Alloc) addr).getParentBlock().getParentFunction().equals(function);
        }
        return false;
    }


    /**
     * 属性传播
     */
    public static void TransAttribute() {
        ArrayList<Function> funcTopoSort = FuncInfo.getFuncTopoSort();
        for (Function func : funcTopoSort) {
            boolean hasMemoryRead = FuncInfo.hasMemoryRead.get(func);
            boolean hasMemoryWrite = FuncInfo.hasMemoryWrite.get(func);
            boolean hasMemoryAlloc = FuncInfo.hasMemoryAlloc.get(func);
            boolean hasReadIn = FuncInfo.hasReadIn.get(func);
            boolean hasPutOut = FuncInfo.hasPutOut.get(func);
            boolean hasSideEffect = FuncInfo.hasSideEffect.get(func);
            for (Function callee : FuncInfo.callGraph.get(func)) {
                hasMemoryRead |= FuncInfo.hasMemoryRead.get(callee);
                hasMemoryWrite |= FuncInfo.hasMemoryWrite.get(callee);
                hasMemoryAlloc |= FuncInfo.hasMemoryAlloc.get(callee);
                hasReadIn |= FuncInfo.hasReadIn.get(callee);
                hasPutOut |= FuncInfo.hasPutOut.get(callee);
                hasSideEffect |= FuncInfo.hasSideEffect.get(callee);
            }
            FuncInfo.hasMemoryRead.put(func, hasMemoryRead);
            FuncInfo.hasMemoryWrite.put(func, hasMemoryWrite);
            FuncInfo.hasMemoryAlloc.put(func, hasMemoryAlloc);
            FuncInfo.hasReadIn.put(func, hasReadIn);
            FuncInfo.hasPutOut.put(func, hasPutOut);
            FuncInfo.hasSideEffect.put(func, hasSideEffect);
            FuncInfo.isStateless.put(func, (!hasMemoryRead) && (!hasMemoryWrite) && (!hasSideEffect));
        }
//        System.out.println("Function Analysis Done");
    }
}
