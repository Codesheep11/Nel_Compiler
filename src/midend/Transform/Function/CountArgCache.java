package midend.Transform.Function;

// 在一个纯计算递归函数中
// 返回值是某个参数arg，或者每次递归调用的参数都加上了一个Constant
// 则可以将该参数消除，并将递归函数的返回值 + Constant
// 并且函数外的调用，该参数若不为0，则返回 val + arg

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.Module;
import mir.*;

import java.util.ArrayList;

public class CountArgCache {

    private static Module module;

    public static void run(Module module) {
        CountArgCache.module = module;
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
        Function.Argument meanArg = func.getFuncRArguments().get(0).equals(retArg) ?
                func.getFuncRArguments().get(1) : func.getFuncRArguments().get(0);
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
        //开始变换
        func.isCached = true;
        BasicBlock entry = func.getEntry();
        Instruction.Terminator term = entry.getTerminator();
        if (!(term instanceof Instruction.Branch br)) return;
        if (!(br.getCond() instanceof Instruction.Icmp icmp)) return;
        if (!icmp.getCondCode().equals(Instruction.Icmp.CondCode.EQ)) return;
        GlobalVariable mem = new GlobalVariable(
                new Constant.ConstantZeroInitializer(new Type.ArrayType(100000007, Type.BasicType.I32_TYPE)),
                "mem_" + func.getName());
        GlobalVariable flag = new GlobalVariable(
                new Constant.ConstantZeroInitializer(new Type.ArrayType(100000007, Type.BasicType.I32_TYPE)),
                "flag_" + func.getName());
        module.addGlobalValue(mem);
        module.addGlobalValue(flag);
        BasicBlock memBB = new BasicBlock(func.getBBName() + "_mem", func);
        BasicBlock beginBB = br.getElseBlock();
        term.replaceTarget(br.getElseBlock(), memBB);

        ArrayList<Value> offsets = new ArrayList<>();
        offsets.add(Constant.ConstantInt.get(0));
        offsets.add(meanArg);
        Instruction.GetElementPtr flagPtr = new Instruction.GetElementPtr(entry, flag,
                Type.BasicType.I32_TYPE, offsets);
        flagPtr.remove();
        entry.insertInstBefore(flagPtr, entry.getFirstInst());

        offsets = new ArrayList<>();
        offsets.add(Constant.ConstantInt.get(0));
        offsets.add(meanArg);
        Instruction.GetElementPtr memPtr = new Instruction.GetElementPtr(entry, mem,
                Type.BasicType.I32_TYPE, offsets);
        memPtr.remove();
        entry.insertInstBefore(memPtr, entry.getFirstInst());

        Instruction.Load flagLoad = new Instruction.Load(memBB, flagPtr);
        Instruction.Icmp icmp1 = new Instruction.Icmp(memBB, Instruction.Icmp.CondCode.EQ,
                flagLoad, Constant.ConstantInt.get(1));
        BasicBlock memRetBB = new BasicBlock(func.getBBName() + "_mem_ret", func);
        new Instruction.Branch(memBB, icmp1, memRetBB, beginBB);

        Instruction.Load memLoad = new Instruction.Load(memRetBB, memPtr);
        Instruction.Icmp cond = new Instruction.Icmp(memRetBB, Instruction.Icmp.CondCode.SLE,
                memLoad, Constant.ConstantInt.get(0));
        BasicBlock negBB = new BasicBlock(func.getBBName() + "_neg", func);
        BasicBlock posBB = new BasicBlock(func.getBBName() + "_pos", func);
        new Instruction.Branch(memRetBB, cond, negBB, posBB);
        new Instruction.Return(negBB, Constant.ConstantInt.get(0));
        Instruction.Add add = new Instruction.Add(posBB, retArg.getType(), retArg, memLoad);
        new Instruction.Return(posBB, add);

        Instruction.Store flagStore = new Instruction.Store(beginBB, Constant.ConstantInt.get(1), flagPtr);
        flagStore.remove();
        beginBB.insertInstBefore(flagStore, beginBB.getFirstInst());

        for (Instruction.Return ret : rets) {
            if (ret.getRetValue() instanceof Instruction.Call call) {
                Instruction.Sub newSub = new Instruction.Sub(ret.getParentBlock(), retArg.getType(),
                        call, retArg);
                newSub.remove();
                ret.getParentBlock().insertInstBefore(newSub, ret);
                Instruction.Store store = new Instruction.Store(ret.getParentBlock(), newSub, memPtr);
                store.remove();
                ret.getParentBlock().insertInstBefore(store, ret);
            }
            else if (ret.getRetValue() instanceof Constant.ConstantInt) {
                Instruction.Store store = new Instruction.Store(ret.getParentBlock(), ret.getRetValue(), memPtr);
                store.remove();
                ret.getParentBlock().insertInstBefore(store, ret);
            }
        }
    }
}
