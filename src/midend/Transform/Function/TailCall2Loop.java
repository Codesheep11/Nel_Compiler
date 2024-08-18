package midend.Transform.Function;


import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * 尾递归优化
 * 递归函数 -> 尾递归函数
 * 尾递归函数 -> 循环
 */
public class TailCall2Loop {
    private static Function curFunc;

    public static void run(Module module) {
        for (Function func : module.getFuncSet()) {
            FuncInfo funcInfo = AnalysisManager.getFuncInfo(func);
            if (func.isExternal()) continue;
            if (funcInfo.isRecursive && !funcInfo.hasMemoryAlloc && !funcInfo.hasSideEffect && !funcInfo.hasMemoryWrite)
                RunOnFunc(func);
        }
    }

    private static void RunOnFunc(Function func) {
//        System.out.println("TailCall2Loop");
        curFunc = func;
        Instruction.Call tailCall = Recurse2TailRecurse();
        while (tailCall != null) {
            TransCall2Loop(tailCall);
            tailCall = Recurse2TailRecurse();
        }
    }

    private static Instruction.Call Recurse2TailRecurse() {
        for (BasicBlock block : curFunc.getBlocks()) {
            if (block.getInstructions().isEmpty()) continue;
            // 入口块不可能递归，否则会无限循环
            if (block == curFunc.getEntry()) continue;
            Instruction.Terminator term = block.getTerminator();
            if (term instanceof Instruction.Return ret) {
                Instruction.Call lastRecursiveCall = getLastRecursiveCall(block);
                if (lastRecursiveCall != null) {
                    //本身已经是尾递归的形式
                    if (ret.getRetValue() == null) return lastRecursiveCall;
                    if (ret.getRetValue().equals(lastRecursiveCall)) return lastRecursiveCall;
//                    //存在递归函数，但是不是尾递归，则将其改写成尾递归
//                    //fixme:决赛再做！！！
//                    //找到当前基本块内的所有递归调用
//                    ArrayList<Instruction.Call> recursiveCalls = getAllRecursiveCall(block);
//                    int idx = curFunc.getFuncRArguments().size();
//                    for (Instruction.Call call : recursiveCalls) {
//                        Function.Argument arg = new Function.Argument(call.getType(), curFunc);
//                        arg.idx = idx++;
//                        curFunc.getFuncRArguments().add(arg);
//                        curFunc.getArgumentsTP().add(call.getType());
//                    }
                }
            }
        }
        return null;
    }

    private static void TransCall2Loop(Instruction.Call call) {
//        System.out.println("TransCall2Loop");
        BasicBlock block = call.getParentBlock();
        Instruction.Return ret = (Instruction.Return) block.getInstructions().getLast();
        BasicBlock oldEntry = curFunc.getEntry();
        BasicBlock newEntry = new BasicBlock(curFunc.getBBName() + "_C2L_header", curFunc);
        new Instruction.Jump(newEntry, oldEntry);
        newEntry.remove();
        curFunc.addBlockFirst(newEntry);
        for (int i = curFunc.getFuncRArguments().size() - 1; i >= 0; i--) {
            Function.Argument arg = curFunc.getFuncRArguments().get(i);
            Instruction.Phi phi = new Instruction.Phi(oldEntry, arg.getType(), new LinkedHashMap<>());
            phi.remove();
            arg.replaceAllUsesWith(phi);
            oldEntry.addInstFirst(phi);
            phi.addOptionalValue(newEntry, arg);
            phi.addOptionalValue(block, call.getParams().get(i));
        }
        call.delete();
        ret.delete();
        new Instruction.Jump(call.getParentBlock(), oldEntry);
    }

    private static boolean isRecurseCall(Instruction.Call call) {
        return call.getDestFunction().equals(curFunc);
    }

    private static Instruction.Call getLastRecursiveCall(BasicBlock block) {
        int size = block.getInstructions().size();
        for (int i = size - 1; i >= 0; i--) {
            if (block.getInstructions().get(i) instanceof Instruction.Call call) {
                if (isRecurseCall(call)) return call;
            }
        }
        return null;
    }

    private static ArrayList<Instruction.Call> getAllRecursiveCall(BasicBlock block) {
        ArrayList<Instruction.Call> res = new ArrayList<>();
        for (Instruction inst : block.getInstructions()) {
            if (inst instanceof Instruction.Call call) {
                if (isRecurseCall(call)) res.add(call);
            }
        }
        return res;
    }

}
