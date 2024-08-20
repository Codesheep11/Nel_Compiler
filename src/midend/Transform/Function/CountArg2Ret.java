package midend.Transform.Function;

// 在一个纯计算递归函数中
// 返回值是某个参数arg，或者每次递归调用的参数都加上了一个Constant
// 则可以将该参数消除，并将递归函数的返回值 + Constant
// 并且函数外的调用，该参数若不为0，则返回 val + arg

//define i32 @fun(i32 %arg_0, i32 %arg_1) {
//fun_BB0:
//	%icmp_0 = icmp eq i32 %arg_0, 1
//	br i1 %icmp_0, label %fun_BB1, label %fun_BB2 ;0.200000
//
//
//fun_BB1:
//	ret i32 %arg_1
//
//
//fun_BB2:
//	%rem_0 = srem i32 %arg_0, 2
//	%icmp_1 = icmp eq i32 %rem_0, 0
//	%add_0 = add i32 1, %arg_1
//	br i1 %icmp_1, label %fun_BB4, label %fun_BB5 ;0.200000
//
//
//fun_BB4:
//	%div_0 = sdiv i32 %arg_0, 2
//	%call_0 = call i32 @fun(i32 %div_0, i32 %add_0)
//	ret i32 %call_0
//
//
//fun_BB5:
//	%mul_0 = mul i32 3, %arg_0
//	%add_1 = add i32 1, %mul_0
//	%load_6 = load i32, i32* @lim
//	%icmp_2 = icmp sle i32 %add_1, %load_6
//	br i1 %icmp_2, label %fun_BB7, label %fun_BB8 ;0.500000
//
//
//fun_BB7:
//	%call_1 = call i32 @fun(i32 %add_1, i32 %add_0)
//	ret i32 %call_1
//
//
//fun_BB8:
//	ret i32 0
//
//
//}

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.Module;
import mir.*;

import java.util.ArrayList;

public class CountArg2Ret {
    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function func) {
        FuncInfo funcInfo = AnalysisManager.getFuncInfo(func);
        if (!funcInfo.isRecursive) return;
        if (funcInfo.hasSideEffect) return;
        if (funcInfo.hasMemoryWrite) return;
        if (!func.getRetType().isInt32Ty()) return;
        if (func.getFuncRArguments().size() != 2) return;
        //收集所有返回值
        ArrayList<Instruction.Return> rets = new ArrayList<>();
        for (BasicBlock bb : func.getBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof Instruction.Return ret) {
                    rets.add(ret);
                }
            }
        }
        //得到返回参数
        Function.Argument retArg = null;
        ArrayList<Instruction.Call> selfCalls = new ArrayList<>();
        for (Instruction.Return ret : rets) {
            if (ret.getRetValue() instanceof Instruction.Call call
                    && call.getDestFunction().equals(func))
            {
                selfCalls.add(call);
            }
            else if (ret.getRetValue() instanceof Function.Argument arg) {
                if (retArg == null) {
                    retArg = arg;
                }
                else return;
            }
            else if (!ret.getRetValue().equals(Constant.ConstantInt.get(0))) {
                return;
            }
        }
        if (retArg == null) return;
        int idx = retArg.idx;
        for (Instruction.Call call : selfCalls) {
            if (!(call.getParams().get(idx) instanceof Instruction.Add add)) return;
            if (!(add.getOperand_1().equals(retArg) || add.getOperand_2().equals(retArg))) return;
            if (add.getOperand_1().equals(retArg) && add.getOperand_2().equals(retArg)) return;
        }
        ArrayList<Instruction.Call> OutCalls = new ArrayList<>();
        for (Instruction user : func.getUsers()) {
            Instruction.Call call = (Instruction.Call) user;
            if (selfCalls.contains(call)) continue;
            if (!call.getParams().get(idx).equals(Constant.ConstantInt.get(0))) return;
            OutCalls.add(call);
        }
        for (Instruction.Call call : OutCalls) {
            call.getParams().remove(idx);
            call.use_remove(new Use(call, Constant.ConstantInt.get(0)));
            Constant.ConstantInt.get(0).use_remove(new Use(call, Constant.ConstantInt.get(0)));
        }
        for (Instruction.Call call : selfCalls) {
            Instruction.Add add = (Instruction.Add) call.getParams().remove(idx);
            call.use_remove(new Use(call, add));
            add.use_remove(new Use(call, add));
            Value inc = add.getOperand_1().equals(retArg) ? add.getOperand_2() : add.getOperand_1();
            Instruction.Add newAdd = new Instruction.Add(call.getParentBlock(), call.getType(), call, inc);
            newAdd.remove();
            call.getParentBlock().insertInstAfter(newAdd, call);
            ArrayList<Instruction> users = new ArrayList<>(call.getUsers());
            users.remove(newAdd);
            for (Instruction user2 : users) {
                user2.replaceUseOfWith(call, newAdd);
            }
        }
        for (Instruction user : retArg.getUsers()) {
            if (user instanceof Instruction.Return) {
                user.replaceUseOfWith(retArg, Constant.ConstantInt.get(0));
            }
            else if (user.getUsers().isEmpty()) {
                user.delete();
            }
        }
        func.getFuncRArguments().remove(idx);
        for (int i = 0; i < func.getFuncRArguments().size(); i++) {
            func.getFuncRArguments().get(i).idx = i;
        }
        System.out.println("CountArg2Ret: " + func.getName());
    }
}
