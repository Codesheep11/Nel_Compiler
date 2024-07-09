package midend.Analysis;

import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import static manager.Manager.ExternFunc.*;


public class FuncAnalysis {


    public static void run(Module module) {
        for (Function func : module.getFuncSet()) {
            FuncInfo.callGraph.put(func, new HashSet<>());
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
        for (Function callee : module.getFuncSet()) {
            for (Use use : callee.getUses()) {
                Instruction.Call call = (Instruction.Call) use.getUser();
                Function caller = call.getParentBlock().getParentFunction();
//                System.out.println(caller.getName() + " -> " + callee.getName());
                FuncInfo.addCall(caller, callee);
            }
        }

        ExternFuncInit();
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            BuildAttribute(func);
        }
        TransAttribute();
    }

    public static void ExternFuncInit() {
        if (FuncInfo.callGraph.keySet().contains(GETINT)) FuncInfo.hasReadIn.put(GETINT, true);
        if (FuncInfo.callGraph.keySet().contains(PUTINT)) FuncInfo.hasPutOut.put(PUTINT, true);
        if (FuncInfo.callGraph.keySet().contains(GETCH)) FuncInfo.hasReadIn.put(GETCH, true);
        if (FuncInfo.callGraph.keySet().contains(GETFLOAT)) FuncInfo.hasReadIn.put(GETFLOAT, true);
        if (FuncInfo.callGraph.keySet().contains(PUTCH)) FuncInfo.hasPutOut.put(PUTCH, true);
        if (FuncInfo.callGraph.keySet().contains(PUTFLOAT)) FuncInfo.hasPutOut.put(PUTFLOAT, true);
        if (FuncInfo.callGraph.keySet().contains(STARTTIME)) FuncInfo.hasPutOut.put(STARTTIME, true);
        if (FuncInfo.callGraph.keySet().contains(STOPTIME)) FuncInfo.hasPutOut.put(STOPTIME, true);
        if (FuncInfo.callGraph.keySet().contains(GETARRAY)) FuncInfo.hasReadIn.put(GETARRAY, true);
        if (FuncInfo.callGraph.keySet().contains(GETFARRAY)) FuncInfo.hasReadIn.put(GETFARRAY, true);
        if (FuncInfo.callGraph.keySet().contains(PUTARRAY)) FuncInfo.hasPutOut.put(PUTARRAY, true);
        if (FuncInfo.callGraph.keySet().contains(PUTFARRAY)) FuncInfo.hasPutOut.put(PUTFARRAY, true);
        if (FuncInfo.callGraph.keySet().contains(PUTF)) FuncInfo.hasPutOut.put(PUTF, true);
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
                if (inst instanceof Instruction.Load) {
                    Instruction.Load load = (Instruction.Load) inst;
                    if (GlobalVarAnalysis.isGlobalAddr(load.getAddr()) != null) hasMemoryRead = true;
                }
                else if (inst instanceof Instruction.Store) {
                    Instruction.Store store = (Instruction.Store) inst;
                    if (GlobalVarAnalysis.isGlobalAddr(store.getAddr()) != null) hasMemoryWrite = true;
                    else if (isSideEffect(store.getAddr(), function)) hasSideEffect = true;
                }
                else if (inst instanceof Instruction.Call) {
                    Instruction.Call call = (Instruction.Call) inst;
                    Function callee = call.getDestFunction();
                    if (callee.getName() == function.getName()) {
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
        if (addr instanceof Instruction.GetElementPtr) {
            Instruction.GetElementPtr gep = (Instruction.GetElementPtr) addr;
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
            }
            FuncInfo.hasMemoryRead.put(func, hasMemoryRead);
            FuncInfo.hasMemoryWrite.put(func, hasMemoryWrite);
            FuncInfo.hasMemoryAlloc.put(func, hasMemoryAlloc);
            FuncInfo.hasReadIn.put(func, hasReadIn);
            FuncInfo.hasPutOut.put(func, hasPutOut);
            FuncInfo.isStateless.put(func, (!hasMemoryRead) && (!hasMemoryWrite) && (!hasSideEffect));
        }
//        System.out.println("Function Analysis Done");
    }
}