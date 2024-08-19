package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.Module;
import mir.*;

import javax.swing.*;
import java.util.ArrayList;

import static mir.Type.BasicType.*;

//define i32 @multiply(i32 %arg_0, i32 %arg_1) {
//multiply_BB0:
//	%icmp_0 = icmp eq i32 %arg_1, 0
//	br i1 %icmp_0, label %multiply_BB1, label %multiply_BB2 ;0.200000
//
//
//multiply_BB1:
//	ret i32 0
//
//
//multiply_BB2:
//	%icmp_1 = icmp eq i32 %arg_1, 1
//	br i1 %icmp_1, label %multiply_BB3, label %multiply_BB4 ;0.200000
//
//
//multiply_BB3:
//	%rem_0 = srem i32 %arg_0, 998244353
//	ret i32 %rem_0
//
//
//multiply_BB4:
//	%div_0 = sdiv i32 %arg_1, 2
//	%call_0 = call i32 @multiply(i32 %arg_0, i32 %div_0)
//	%mul_2 = mul i32 2, %call_0
//	%rem_1 = srem i32 %mul_2, 998244353
//	%rem_2 = srem i32 %arg_1, 2
//	%icmp_2 = icmp eq i32 %rem_2, 1
//	br i1 %icmp_2, label %multiply_BB5, label %multiply_BB6 ;0.200000
//
//
//multiply_BB5:
//	%add_1 = add i32 %rem_1, %arg_0
//	%rem_3 = srem i32 %add_1, 998244353
//	ret i32 %rem_3
//
//
//multiply_BB6:
//	ret i32 %rem_1
//
//
//}
public class Multiply {

    private static Value Rem_Value;

    public static void run(Module module) {
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            if (detectMultiply(func)) {
                BasicBlock newEntry = new BasicBlock(func.getBBName(), func);
                newEntry.remove();
                func.insertBlockBefore(newEntry, func.getEntry());
                Function.Argument arg0 = func.getFuncRArguments().get(0);
                Function.Argument arg1 = func.getFuncRArguments().get(1);
                Instruction.Sext sext_arg0 = new Instruction.Sext(newEntry, arg0, I64_TYPE);
                Instruction.Sext sext_arg1 = new Instruction.Sext(newEntry, arg1, I64_TYPE);
                Instruction.Mul mul = new Instruction.Mul(newEntry, I64_TYPE, sext_arg0, sext_arg1);
                Instruction.Sext rem_value = new Instruction.Sext(newEntry, Rem_Value, I64_TYPE);
                Instruction.Rem rem = new Instruction.Rem(newEntry, I64_TYPE, mul, rem_value);
                Instruction.Trunc trunc = new Instruction.Trunc(newEntry, rem, I32_TYPE);
                Instruction.Return ret = new Instruction.Return(newEntry, trunc);
                ArrayList<BasicBlock> delList = new ArrayList<>();
                for (BasicBlock bb : func.getBlocks()) {
                    if (bb.equals(newEntry)) continue;
                    delList.add(bb);
                }
                for (BasicBlock bb : delList) {
                    bb.delete();
                }
            }
        }
    }

    private static boolean detectMultiply(Function func) {
        if (func.getFuncRArguments().size() != 2) return false;
        if (!func.getRetType().isInt32Ty()) return false;
        FuncInfo funcInfo = AnalysisManager.getFuncInfo(func);
        if (!funcInfo.isRecursive) return false;
        //四条返回值 一条0 三条rem
        ArrayList<Instruction.Return> returns = new ArrayList<>();
        for (BasicBlock bb : func.getBlocks()) {
            Instruction term = bb.getTerminator();
            if (term instanceof Instruction.Return ret) {
                returns.add(ret);
            }
        }
        if (returns.size() != 4) return false;
        int zeroCnt = 0;
        int remCnt = 0;
        ArrayList<Instruction.Rem> rems = new ArrayList<>();
        for (Instruction.Return ret : returns) {
            if (ret.getRetValue().equals(Constant.ConstantInt.get(0))) {
                zeroCnt++;
            }
            else if (ret.getRetValue() instanceof Instruction.Rem rem) {
                rems.add(rem);
                remCnt++;
            }
            else {
                return false;
            }
        }
        if (zeroCnt != 1 || remCnt != 3) return false;
        for (Instruction.Rem rem : rems) {
            if (rem.getOperand_2() instanceof Constant.ConstantInt c) {
                if (Rem_Value == null) {
                    Rem_Value = c;
                }
                else if (!Rem_Value.equals(c)) {
                    return false;
                }
            }
            else {
                return false;
            }
        }
//        System.out.println("Detect multiply function: " + func.getName());
        return true;
    }
}
