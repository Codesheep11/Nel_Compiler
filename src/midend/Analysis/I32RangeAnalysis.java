package midend.Analysis;

import mir.*;
import mir.Module;
import mir.result.DGinfo;

import java.util.HashMap;

import static java.lang.Math.*;
import static java.lang.Math.max;

public class I32RangeAnalysis {

    public static HashMap<Function, HashMap<Instruction, I32Range>> I32RangeBufferMap = new HashMap<>();

    //    public static HashMap<GlobalVariable, I32Range> GlobalRangeBufferMap = new HashMap<>();
    public static HashMap<Function, HashMap<BasicBlock, Boolean>> dirtyMap = new HashMap<>();

    public static class I32Range {
        private int minValue;
        private int maxValue;

        private static I32Range Any = new I32Range(Integer.MIN_VALUE, Integer.MAX_VALUE);

        private I32Range(int min, int max) {
            this.minValue = min;
            this.maxValue = max;
        }

        public int getMaxValue() {
            return maxValue;
        }

        public int getMinValue() {
            return minValue;
        }

        public static I32Range Any() {
            return Any;
        }
    }

    public static void run(Module module) {
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            runOnFunction(func);
        }
    }

    private static void clearFunc(Function function) {
        I32RangeBufferMap.put(function, new HashMap<>());
        dirtyMap.put(function, new HashMap<>());
        for (BasicBlock bb : function.getBlocks()) {
            dirtyMap.get(function).put(bb, true);
        }
    }

    public static void runOnFunction(Function function) {
        clearFunc(function);
        for (BasicBlock bb : function.getBlocks()) {
            runOnBasicBlock(bb);
        }
    }

    public static void runOnBasicBlock(BasicBlock bb) {
        Function func = bb.getParentFunction();
        DGinfo dgInfo = AnalysisManager.getDG(func);
        BasicBlock idom = dgInfo.getIDom(bb);
        if (!dirtyMap.get(func).get(bb)) return;
        if (dirtyMap.get(func).get(idom)) runOnBasicBlock(idom);
        for (Instruction inst : bb.getInstructions()) {
            if (inst.getType().isInt32Ty()) {

            }
        }
        dirtyMap.get(func).put(bb, false);
    }

    private static I32Range calculateI32Range(Value value, I32ValueRangeAnalyzer helper) {
        I32Range result;
        if (value instanceof Constant) {
            if (value instanceof Constant constInt) {
                result = new I32Range(constInt.getValue(), constInt.getValue());
            }
            else {
                throw new RuntimeException("Unexpected type of i32 constant.");
            }
        }
        else if (value instanceof IntegerArithmeticInst integerArithmeticInst) {
            I32ValueRange range1 = I32ValueRange.of(integerArithmeticInst.getOperand1(), integerArithmeticInst.getBasicBlock(), helper);
            I32ValueRange range2 = I32ValueRange.of(integerArithmeticInst.getOperand2(), integerArithmeticInst.getBasicBlock(), helper);
            if (integerArithmeticInst instanceof IntegerAddInst) {
                int minValue;
                try {
                    minValue = addExact(range1.minValue, range2.minValue);
                } catch (ArithmeticException e) {
                    minValue = Integer.MIN_VALUE;
                }
                int maxValue;
                try {
                    maxValue = addExact(range1.maxValue, range2.maxValue);
                } catch (ArithmeticException e) {
                    maxValue = Integer.MAX_VALUE;
                }
                result = new I32ValueRange(minValue, maxValue);
            }
            else if (integerArithmeticInst instanceof IntegerSubInst) {
                int minValue;
                try {
                    minValue = subtractExact(range1.minValue, range2.maxValue);
                } catch (ArithmeticException e) {
                    minValue = Integer.MIN_VALUE;
                }
                int maxValue;
                try {
                    maxValue = subtractExact(range1.maxValue, range2.minValue);
                } catch (ArithmeticException e) {
                    maxValue = Integer.MAX_VALUE;
                }
                result = new I32ValueRange(minValue, maxValue);
            }
            else if (integerArithmeticInst instanceof IntegerMultiplyInst) {
                int minValue, maxValue;
                try {
                    var endpointValues = new int[4];
                    endpointValues[0] = multiplyExact(range1.minValue, range2.maxValue);
                    endpointValues[1] = multiplyExact(range1.maxValue, range2.minValue);
                    endpointValues[2] = multiplyExact(range1.minValue, range2.minValue);
                    endpointValues[3] = multiplyExact(range1.maxValue, range2.maxValue);
                    minValue = Integer.MAX_VALUE;
                    maxValue = Integer.MIN_VALUE;
                    for (int endpointValue : endpointValues) {
                        minValue = min(minValue, endpointValue);
                        maxValue = max(maxValue, endpointValue);
                    }
                } catch (ArithmeticException e) {
                    minValue = Integer.MIN_VALUE;
                    maxValue = Integer.MAX_VALUE;
                }
                result = new I32ValueRange(minValue, maxValue);
            }
            else if (integerArithmeticInst instanceof IntegerSignedDivideInst) {
                int minValue = Integer.MAX_VALUE;
                int maxValue = Integer.MIN_VALUE;
                if (range1.maxValue >= 0) {
                    int minPositiveDividend = max(range1.minValue, 0);
                    if (range2.maxValue > 0) {
                        int minPositiveDivisor = max(range2.minValue, 1);
                        maxValue = max(maxValue, range1.maxValue / minPositiveDivisor);
                        minValue = min(minValue, minPositiveDividend / range2.maxValue);
                    }
                    if (range2.minValue < 0) {
                        int maxNegativeDivisor = min(range2.maxValue, -1);
                        maxValue = max(maxValue, minPositiveDividend / range2.minValue);
                        minValue = min(minValue, range1.maxValue / maxNegativeDivisor);
                    }
                }
                if (range1.minValue <= 0) {
                    int maxNegativeDividend = min(range1.maxValue, 0);
                    if (range2.maxValue > 0) {
                        int minPositiveDivisor = max(range2.minValue, 1);
                        maxValue = max(maxValue, maxNegativeDividend / range2.maxValue);
                        minValue = min(minValue, range1.minValue / minPositiveDivisor);
                    }
                    if (range2.minValue < 0) {
                        int maxNegativeDivisor = min(range2.maxValue, -1);
                        maxValue = max(maxValue, range1.minValue / maxNegativeDivisor);
                        minValue = min(minValue, maxNegativeDividend / range2.minValue);
                    }
                }
                result = new I32ValueRange(minValue, maxValue);
            }
            else if (integerArithmeticInst instanceof IntegerSignedRemainderInst) {
                var maxModulus = max(abs(range2.minValue), abs(range2.maxValue));
                int minValue, maxValue;
                if (range1.minValue < 0) {
                    minValue = max(-(maxModulus - 1), range1.minValue);
                }
                else {
                    minValue = 0;
                }
                if (range1.maxValue > 0) {
                    maxValue = min(maxModulus - 1, range1.maxValue);
                }
                else {
                    maxValue = 0;
                }
                result = new I32ValueRange(minValue, maxValue);
            }
            else {
                throw new RuntimeException("Unexpected type of integer arithmetic instruction.");
            }
        }
        else if (value instanceof SignedMinInst || value instanceof SignedMaxInst) {
            if (value instanceof SignedMinInst minInst) {
                I32ValueRange range1 = I32ValueRange.of(minInst.getOperand1(), minInst.getBasicBlock(), helper);
                I32ValueRange range2 = I32ValueRange.of(minInst.getOperand2(), minInst.getBasicBlock(), helper);
                result = new I32ValueRange(min(range1.minValue, range2.minValue), min(range1.maxValue, range2.maxValue));
            }
            else {
                var maxInst = (SignedMaxInst) value;
                I32ValueRange range1 = I32ValueRange.of(maxInst.getOperand1(), maxInst.getBasicBlock(), helper);
                I32ValueRange range2 = I32ValueRange.of(maxInst.getOperand2(), maxInst.getBasicBlock(), helper);
                result = new I32ValueRange(max(range1.minValue, range2.minValue), max(range1.maxValue, range2.maxValue));
            }
        }
        else if (value instanceof BitCastInst || value instanceof LoadInst ||
                value instanceof Function.FormalParameter || value instanceof FloatToSignedIntegerInst ||
                value instanceof TruncInst)
        {
            result = new I32ValueRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        else if (value instanceof CallInst callInst) {
            if (callInst.getCallee() instanceof ExternalFunction externalFunction) {
                result = switch (externalFunction.getFunctionName()) {
                    case "getch" -> new I32ValueRange(-128, 127);
                    case "getarray", "getfarray" -> new I32ValueRange(0, Integer.MAX_VALUE);
                    default -> new I32ValueRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
                };
            }
            else {
                result = new I32ValueRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
            }
        }
        else if (value instanceof PhiInst phiInst) {
            // 为了避免循环求值，phi指令不会递归求解，当入口值未知时，暂时忽略它
            int minValue = Integer.MAX_VALUE;
            int maxValue = Integer.MIN_VALUE;
            for (BasicBlock entryBlock : phiInst.getEntrySet()) {
                var entryValue = phiInst.getValue(entryBlock);
                // 在入口值未知时，首先试图寻找定义值，此行为在 getEntryRange->getValueRangeAtBlock 中实现。
                // 如果定义值也不存在，则认为这是第一次进行搜索，可以暂时忽略，以提升算法效果。此后定义值应当总是存在。
                if (helper != null && helper.hasValueRangeSolved(entryValue)) {
                    I32ValueRange entryRange = getEntryRange(helper, phiInst.getBasicBlock(), entryBlock, entryValue);
                    minValue = min(minValue, entryRange.minValue);
                    maxValue = max(maxValue, entryRange.maxValue);
                }
            }
            result = new I32ValueRange(minValue, maxValue);
        }
        else {
            throw new RuntimeException("Unexpected type of i32 value.");
        }
        return result;
    }

}
