package midend;

import mir.*;
import mir.Module;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;

import static manager.Manager.ExternFunc.*;


public class FuncAnalysis {

    public static HashMap<Function, HashSet<Function>> callGraph = new HashMap<>();

    public static void run(Module module) {
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            callGraph.put(func, new HashSet<>());
        }
        for (Function callee : module.getFuncSet()) {
            for (Use use : callee.getUses()) {
                Instruction.Call call = (Instruction.Call) use.getUser();
                Function caller = call.getParentBlock().getParentFunction();
//                System.out.println(caller.getName() + " -> " + callee.getName());
                callGraph.get(caller).add(callee);
            }
        }
        ExternFuncInit();
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            BuildAttribute(func);
        }
        TransAttribute(module);
    }

    public static void ExternFuncInit() {
        GETINT.hasReadIn = true;
        PUTINT.hasPutOut = true;
        GETCH.hasReadIn = true;
        GETFLOAT.hasReadIn = true;
        PUTCH.hasPutOut = true;
        PUTFLOAT.hasPutOut = true;
        STARTTIME.hasPutOut = true;
        STOPTIME.hasPutOut = true;
        GETARRAY.hasReadIn = true;
        GETFARRAY.hasReadIn = true;
        PUTARRAY.hasPutOut = true;
        PUTFARRAY.hasPutOut = true;
        PUTF.hasPutOut = true;
    }

    private static void BuildAttribute(Function function) {
        boolean hasMemoryRead = false;
        boolean hasMemoryWrite = false;
        boolean hasReturn = !(function.getRetType() instanceof Type.VoidType);
        boolean isRecurse = false;
        boolean hasSideEffect = false;
        for (BasicBlock bb : function.getBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof Instruction.Load) {
                    Instruction.Load load = (Instruction.Load) inst;
                    if (isGlobalAddr(load.getAddr())) hasMemoryRead = true;
                }
                else if (inst instanceof Instruction.Store) {
                    Instruction.Store store = (Instruction.Store) inst;
                    if (isGlobalAddr(store.getAddr())) hasMemoryWrite = true;
                    else if (isSideEffect(store.getAddr(), function)) hasSideEffect = true;
                }
                else if (inst instanceof Instruction.Call) {
                    Instruction.Call call = (Instruction.Call) inst;
                    Function callee = call.getDestFunction();
                    if (callee.getName() == function.getName()) {
                        isRecurse = true;
                    }
                }
                if (hasMemoryRead && hasMemoryWrite && isRecurse && hasSideEffect) break;
            }
        }
        function.hasMemoryRead = hasMemoryRead;
        function.hasMemoryWrite = hasMemoryWrite;
        function.hasReturn = hasReturn;
        function.isRecurse = isRecurse;
        function.hasSideEffect = hasSideEffect;
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
     * 判断是否是全局变量
     *
     * @param addr
     * @return
     */

    public static boolean isGlobalAddr(Value addr) {
        if (addr instanceof Instruction.GetElementPtr) {
            Instruction.GetElementPtr gep = (Instruction.GetElementPtr) addr;
            addr = gep.getBase();
        }
        if (addr instanceof GlobalVariable) return true;
        return false;
    }

    /**
     * 属性传播
     *
     * @param module
     */
    public static void TransAttribute(Module module) {
        LinkedList<Function> workList = new LinkedList<>();
        Function main = module.getFunctions().get("main");
        workList.add(main);
        int idx = 0;
        while (workList.size() != idx) {
            Function func = workList.get(idx);
            idx++;
            for (Function callee : callGraph.get(func)) {
                if (callee.isExternal()) continue;
                if (!workList.contains(callee)) workList.add(callee);
            }
        }
        //倒序遍历worklist 属性传播
        for (int i = workList.size() - 1; i >= 0; i--) {
            Function func = workList.get(i);
            boolean hasMemoryRead = func.hasMemoryRead;
            boolean hasMemoryWrite = func.hasMemoryWrite;
            boolean hasReadIn = func.hasReadIn;
            boolean hasPutOut = func.hasPutOut;
            boolean hasSideEffect = func.hasSideEffect;
            for (Function callee : callGraph.get(func)) {
                hasMemoryRead |= callee.hasMemoryRead;
                hasMemoryWrite |= callee.hasMemoryWrite;
                hasReadIn |= callee.hasReadIn;
                hasPutOut |= callee.hasPutOut;
            }
            func.hasMemoryRead = hasMemoryRead;
            func.hasMemoryWrite = hasMemoryWrite;
            func.hasReadIn = hasReadIn;
            func.hasPutOut = hasPutOut;
            func.isStateless = (!hasMemoryRead) && (!hasMemoryWrite) && (!hasSideEffect);
        }
//        System.out.println("Function Analysis Done");
    }
}
