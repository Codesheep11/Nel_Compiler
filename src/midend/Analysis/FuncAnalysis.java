package midend.Analysis;

import midend.Transform.GlobalVarLocalize;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.*;

import static midend.Util.FuncInfo.ExternFunc.*;


public class FuncAnalysis {

    public static boolean FuncAnalysisOpen = false;

    public static final HashMap<Function, HashSet<Function>> callGraph = new HashMap<>();

    public static Function main;

    private static boolean topoSortFlag = false;

    private static final ArrayList<Function> funcTopoSort = new ArrayList<>();

    public static void run(Module module) {
        if (!FuncAnalysisOpen) FuncAnalysisOpen = true;
        main = module.getFunctions().get("main");
        callGraph.clear();
        topoSortFlag = false;
        funcTopoSort.clear();
        for (Function callee : module.getFuncSet()) {
            AnalysisManager.setFuncInfo(callee);
            for (Instruction user : callee.getUsers()) {
                if (user instanceof Instruction.Call call) {
                    Function caller = call.getParentBlock().getParentFunction();
                    addCall(caller, callee);
                }
            }
            if (callee.getName().equals("NELParallelFor")) {
                for (Instruction user : callee.getUsers()) {
                    if (user instanceof Instruction.Call call) {
                        Function body = (Function) call.getParams().get(2);
                        addCall(callee, body);
                    }
                }
            }
            if (!callee.getUsers().isEmpty()) {
                FuncInfo calleeInfo = AnalysisManager.getFuncInfo(callee);
                calleeInfo.isLeaf = true;
            }
        }
        HashSet<Value> visited = new HashSet<>();
        LinkedList<Function> queue = new LinkedList<>();
        queue.add(main);
        visited.add(main);
        while (!queue.isEmpty()) {
            Function func = queue.poll();
            if (!callGraph.containsKey(func)) callGraph.put(func, new HashSet<>());
            for (Function callee : callGraph.get(func)) {
                if (!visited.contains(callee)) {
                    queue.add(callee);
                    visited.add(callee);
                }
            }
        }
        ArrayList<Function> deleteList = new ArrayList<>();
        for (Function func : module.getFuncSet()) {
            if (!visited.contains(func)) deleteList.add(func);
        }
        for (Function func : deleteList) {
            func.delete();
            module.removeFunction(func);
        }
        ExternFuncInit();
        for (Function func : module.getFuncSet()) {
            if (func.getName().equals("NELCacheLookup") || func.getName().equals("NELParallelFor") || func.getName().equals("NELReduceAddF32")) {
                BuildLibAttribute(func);
            }
            if (func.isExternal()) continue;
            BuildAttribute(func);
        }
        TransAttribute();
    }

    public static void addCall(Function caller, Function callee) {
        if (!callGraph.containsKey(caller)) {
            callGraph.put(caller, new HashSet<>());
        }
        callGraph.get(caller).add(callee);
    }

    public static void ExternFuncInit() {
        if (callGraph.containsKey(GETINT)) AnalysisManager.getFuncInfo(GETINT).hasReadIn = true;
        if (callGraph.containsKey(PUTINT)) AnalysisManager.getFuncInfo(PUTINT).hasPutOut = true;
        if (callGraph.containsKey(GETCH)) AnalysisManager.getFuncInfo(GETCH).hasReadIn = true;
        if (callGraph.containsKey(GETFLOAT)) AnalysisManager.getFuncInfo(GETFLOAT).hasReadIn = true;
        if (callGraph.containsKey(PUTCH)) AnalysisManager.getFuncInfo(PUTCH).hasPutOut = true;
        if (callGraph.containsKey(PUTFLOAT)) AnalysisManager.getFuncInfo(PUTFLOAT).hasPutOut = true;
        if (callGraph.containsKey(STARTTIME)) AnalysisManager.getFuncInfo(STARTTIME).hasReadIn = true;
        if (callGraph.containsKey(STOPTIME)) AnalysisManager.getFuncInfo(STOPTIME).hasReadIn = true;
        if (callGraph.containsKey(GETARRAY)) AnalysisManager.getFuncInfo(GETARRAY).hasReadIn = true;
        if (callGraph.containsKey(GETFARRAY)) AnalysisManager.getFuncInfo(GETFARRAY).hasReadIn = true;
        if (callGraph.containsKey(PUTARRAY)) AnalysisManager.getFuncInfo(PUTARRAY).hasPutOut = true;
        if (callGraph.containsKey(PUTFARRAY)) AnalysisManager.getFuncInfo(PUTFARRAY).hasPutOut = true;
        if (callGraph.containsKey(PUTF)) AnalysisManager.getFuncInfo(PUTF).hasPutOut = true;
        if (callGraph.containsKey(MEMSET)) {
            AnalysisManager.getFuncInfo(MEMSET).hasMemoryWrite = true;
            AnalysisManager.getFuncInfo(MEMSET).hasSideEffect = true;
        }
    }

    private static void BuildAttribute(Function function) {
        FuncInfo funcInfo = AnalysisManager.getFuncInfo(function);
        boolean hasMemoryRead = false;
        boolean hasMemoryWrite = false;
        boolean hasMemoryAlloc = false;
        boolean isRecursive = false;
        boolean hasSideEffect = false;
        boolean isStateless = true;
        for (BasicBlock bb : function.getBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof Instruction.Load load) {
                    Value val = GlobalVarLocalize.isGlobalAddr(load.getAddr());
                    if (val != null) {
                        funcInfo.usedGlobalVariables.add((GlobalVariable) val);
                        hasMemoryRead = true;
                    }
                    else if (isSideEffect(load.getAddr(), function)) isStateless = false;
                }
                else if (inst instanceof Instruction.Store store) {
                    Value val = GlobalVarLocalize.isGlobalAddr(store.getAddr());
                    if (val != null) {
                        funcInfo.usedGlobalVariables.add((GlobalVariable) val);
                        hasMemoryWrite = true;
                    }
                    else if (isSideEffect(store.getAddr(), function)) hasSideEffect = true;
                }
                else if (inst instanceof Instruction.AtomicAdd atmoicadd) {
                    Value val = GlobalVarLocalize.isGlobalAddr(atmoicadd.getPtr());
                    if (val != null) {
                        funcInfo.usedGlobalVariables.add((GlobalVariable) val);
                        hasMemoryWrite = true;
                    }
                    else if (isSideEffect(atmoicadd.getPtr(), function)) hasSideEffect = true;
                }
                else if (inst instanceof Instruction.Call call) {
                    Function callee = call.getDestFunction();
                    if (Objects.equals(callee.getName(), function.getName())) {
                        isRecursive = true;
                    }
                }
                else if (inst instanceof Instruction.Alloc) {
                    hasMemoryAlloc = true;
                }
                if (hasMemoryRead && hasMemoryWrite && isRecursive && hasSideEffect && hasMemoryAlloc) break;
            }
        }
        for (Function.Argument argument : function.getFuncRArguments()) {
            if (argument.getType().isPointerTy()) {
                isStateless = false;
            }
        }
        funcInfo.hasMemoryRead = hasMemoryRead;
        funcInfo.hasMemoryWrite = hasMemoryWrite;
        funcInfo.hasMemoryAlloc = hasMemoryAlloc;
        funcInfo.hasReturn = !(function.getRetType() instanceof Type.VoidType);
        funcInfo.isRecursive = isRecursive;
        funcInfo.hasSideEffect = hasSideEffect;
        funcInfo.isStateless = isStateless && !hasMemoryWrite && !hasMemoryRead && !hasSideEffect;
    }

    private static void BuildLibAttribute(Function function) {
        FuncInfo funcInfo = AnalysisManager.getFuncInfo(function);
        funcInfo.hasMemoryAlloc = true;
        funcInfo.hasMemoryRead = true;
        funcInfo.hasMemoryWrite = true;
        funcInfo.hasSideEffect = true;
        funcInfo.isStateless = false;
    }

    /**
     * sideEffect 只考虑是否对传入的参数进行了地址写入
     */
    public static boolean isSideEffect(Value addr, Function function) {
        while (addr instanceof Instruction.GetElementPtr gep) {
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
        ArrayList<Function> funcTopoSort = getFuncTopoSort();
        for (Function func : funcTopoSort) {
//            System.out.println(func.getName());
            FuncInfo funcInfo = AnalysisManager.getFuncInfo(func);
            for (Function callee : callGraph.get(func)) {
                FuncInfo calleeInfo = AnalysisManager.getFuncInfo(callee);
                funcInfo.hasMemoryRead |= calleeInfo.hasMemoryRead;
                funcInfo.hasMemoryWrite |= calleeInfo.hasMemoryWrite;
                funcInfo.hasMemoryAlloc |= calleeInfo.hasMemoryAlloc;
                funcInfo.hasReadIn |= calleeInfo.hasReadIn;
                funcInfo.hasPutOut |= calleeInfo.hasPutOut;
                funcInfo.hasSideEffect |= calleeInfo.hasSideEffect;
                funcInfo.usedGlobalVariables.addAll(calleeInfo.usedGlobalVariables);
            }
            funcInfo.isStateless = funcInfo.isStateless && !funcInfo.hasMemoryWrite && !funcInfo.hasMemoryRead && !funcInfo.hasSideEffect;
        }
//        System.out.println("Function Analysis Done");
    }

    public static ArrayList<Function> getFuncTopoSort() {
        if (!topoSortFlag) {
            // 拓扑排序
            HashSet<Function> vis = new HashSet<>();
            dfs(main, vis, funcTopoSort);
            topoSortFlag = true;
        }
        return funcTopoSort;
    }

    private static void dfs(Function func, HashSet<Function> vis, ArrayList<Function> res) {
        vis.add(func);
        if (callGraph.containsKey(func)) {
            for (Function callee : callGraph.get(func)) {
                if (!vis.contains(callee)) {
                    dfs(callee, vis, res);
                }
            }
        }
        res.add(func);
    }
}
