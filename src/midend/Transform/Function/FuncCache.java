package midend.Transform.Function;

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.ArrayList;

public class FuncCache {
    private static Module module;

    private static Function Lookup = null;

    private static final int tableSize = 1021;

    private static final Type.ArrayType tableArrayType = new Type.ArrayType(tableSize * 4, Type.BasicType.I32_TYPE);

    public static void run(Module module) {
        FuncCache.module = module;
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            if (function.isCached) continue;
            FuncInfo funcInfo = AnalysisManager.getFuncInfo(function);
            if (funcInfo.hasMemoryAlloc || funcInfo.hasSideEffect || funcInfo.hasMemoryWrite)
                continue;
//            if (!funcInfo.isRecursive || !funcInfo.isStateless || funcInfo.hasSideEffect)
//                continue;
            if (function.getRetType().equals(Type.VoidType.VOID_TYPE)) continue;
            if (function.getArgumentsTP().isEmpty()) continue;
            if (function.getArgumentsTP().size() > 2) continue;
            int recursiveCallCnt = 0;
            for (BasicBlock block : function.getBlocks()) {
                for (Instruction instruction : block.getInstructions()) {
                    if (instruction instanceof Instruction.Call call) {
                        if (call.getDestFunction().equals(function)) {
                            recursiveCallCnt++;
                        }
                    }
                }
            }
            if (recursiveCallCnt < 2) continue;
            runOnFunc(function);
        }
        if (Lookup != null) module.addFunction(Lookup);
    }

    private static Function getLookupLibFunc() {
        if (Lookup != null) return Lookup;
        Lookup = new Function(new Type.PointerType(Type.BasicType.I32_TYPE),
                "NELCacheLookup",
                new Type.PointerType(tableArrayType),
                Type.BasicType.I32_TYPE,
                Type.BasicType.I32_TYPE);
        return Lookup;
    }

    //todo:也许可以把查找表操作放在默认返回值的后面，可以快一点点
    public static void runOnFunc(Function function) {
        Function lookupFunc = getLookupLibFunc();
        GlobalVariable lut = new GlobalVariable(new Constant.ConstantArray(tableArrayType),
                "lut_" + function.getName());
        module.addGlobalValue(lut);
        ArrayList<Value> lut_args = new ArrayList<>();
        lut_args.add(lut);
        BasicBlock oldEntry = function.getEntry();
        BasicBlock newEntry = new BasicBlock(function.getBBName() + "_LUT", function);
        newEntry.remove();
        function.insertBlockBefore(newEntry, function.getEntry());
        for (Function.Argument arg : function.getFuncRArguments()) {
            if (arg.getType().isInt32Ty()) {
                lut_args.add(arg);
            }
            else {
                Instruction.BitCast bitCast = new Instruction.BitCast(newEntry, arg, Type.BasicType.I32_TYPE);
                lut_args.add(bitCast);
            }
        }
        while (lut_args.size() < 3) {
            lut_args.add(Constant.ConstantInt.get(0));
        }
        Instruction.Call call = new Instruction.Call(newEntry, lookupFunc, lut_args);
        ArrayList<Value> offsets = new ArrayList<>();
        offsets.add(Constant.ConstantInt.get(2));
        Instruction valPtr = new Instruction.GetElementPtr(newEntry, call, Type.BasicType.I32_TYPE, offsets);
        if (!((Type.PointerType) valPtr.getType()).getInnerType().equals(function.getRetType())) {
            valPtr = new Instruction.BitCast(newEntry, valPtr, new Type.PointerType(function.getRetType()));
        }
        offsets = new ArrayList<>();
        offsets.add(Constant.ConstantInt.get(3));
        Instruction hasValPtr = new Instruction.GetElementPtr(newEntry, call, Type.BasicType.I32_TYPE, offsets);
        Instruction.Load loadHasVal = new Instruction.Load(newEntry, hasValPtr);
        Instruction.Icmp hasValCond = new Instruction.Icmp(newEntry, Instruction.Icmp.CondCode.NE,
                loadHasVal, Constant.ConstantInt.get(0));
        BasicBlock lutExit = new BasicBlock(function.getBBName() + "_LUT_EXIT", function);
        Instruction.Branch br = new Instruction.Branch(newEntry, hasValCond, lutExit, oldEntry);
        br.setProbability(0.9);
        Instruction.Load retVal = new Instruction.Load(lutExit, valPtr);
        new Instruction.Return(lutExit, retVal);
        for (BasicBlock block : function.getBlocks()) {
            if (block.equals(lutExit)) continue;
            Instruction.Terminator terminator = block.getTerminator();
            if (terminator instanceof Instruction.Return ret) {
                Value retValue = ret.getRetValue();
                Instruction.Store store1 = new Instruction.Store(block, Constant.ConstantInt.get(1), hasValPtr);
                store1.remove();
                block.insertInstBefore(store1, ret);
                Instruction.Store store2 = new Instruction.Store(block, retValue, valPtr);
                store2.remove();
                block.insertInstBefore(store2, ret);
            }
        }
    }

}
