package midend.Analysis;

import mir.*;
import mir.Module;
import mir.result.DGinfo;

import java.util.ArrayList;
import java.util.HashMap;

import static java.lang.Math.*;
import static java.lang.Math.max;


public class I32RangeAnalysis {

    public static HashMap<Function, HashMap<BasicBlock, HashMap<Instruction, I32Range>>> I32RangeBufferMap = new HashMap<>();

    public static HashMap<Function, HashMap<BasicBlock, Boolean>> dirtyMap = new HashMap<>();

    public static class I32Range {
        private int minValue;
        private int maxValue;

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

        @Override
        public boolean equals(Object o) {
            if (o instanceof I32Range or) {
                return this.minValue == or.minValue && this.maxValue == or.maxValue;
            }
            return false;
        }

        private static I32Range Any = new I32Range(Integer.MIN_VALUE, Integer.MAX_VALUE);

        private static HashMap<Integer, I32Range> ConstantRangePool = new HashMap<>();


        public static I32Range Any() {
            return Any;
        }

        public static I32Range gerConstRange(int c) {
            if (!ConstantRangePool.containsKey(c)) ConstantRangePool.put(c, new I32Range(c, c));
            return ConstantRangePool.get(c);
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
//        System.out.println("runOnBasicBlock " + bb.getLabel());
        Function func = bb.getParentFunction();
        if (!func.getEntry().equals(bb)) {
            DGinfo dgInfo = AnalysisManager.getDG(func);
            BasicBlock idom = dgInfo.getIDom(bb);
            if (!dirtyMap.get(func).get(bb)) return;
            if (dirtyMap.get(func).get(idom)) runOnBasicBlock(idom);
        }
        I32RangeBufferMap.get(func).putIfAbsent(bb, new HashMap<>());
        for (Instruction inst : bb.getInstructions()) {
            if (inst.getType().isInt32Ty()) calculateI32Range(inst);
        }
        dirtyMap.get(func).put(bb, false);
    }

    /**
     * 对于给定的Inst 计算 I32Range
     *
     * @param inst
     * @return
     */
    private static I32Range calculateI32Range(Instruction inst) {
//        System.out.println(value);
        if (!inst.getType().isInt32Ty())
            throw new RuntimeException("Value is not I32 Type!");
        BasicBlock bb = inst.getParentBlock();
        Function func = bb.getParentFunction();
        HashMap<Instruction, I32Range> bbMap = I32RangeBufferMap.get(func).get(bb);
        I32Range result = null;
        if (inst instanceof Instruction.BinaryOperation binaryOperation) {
            I32Range r1 = getOperandRange(binaryOperation.getOperand_1());
            I32Range r2 = getOperandRange(binaryOperation.getOperand_2());
            if (inst instanceof Instruction.Add) {
                int minValue;
                try {
                    minValue = addExact(r1.minValue, r2.minValue);
                } catch (ArithmeticException e) {
                    minValue = Integer.MIN_VALUE;
                }
                int maxValue;
                try {
                    maxValue = addExact(r1.maxValue, r2.maxValue);
                } catch (ArithmeticException e) {
                    maxValue = Integer.MAX_VALUE;
                }
                result = new I32Range(minValue, maxValue);
            }
            else if (inst instanceof Instruction.Sub) {
                int minValue;
                try {
                    minValue = subtractExact(r1.minValue, r2.maxValue);
                } catch (ArithmeticException e) {
                    minValue = Integer.MIN_VALUE;
                }
                int maxValue;
                try {
                    maxValue = subtractExact(r1.maxValue, r2.minValue);
                } catch (ArithmeticException e) {
                    maxValue = Integer.MAX_VALUE;
                }
                result = new I32Range(minValue, maxValue);
            }
            else if (inst instanceof Instruction.Mul) {
                int minValue;
                try {
                    minValue = Math.min(
                            Math.min(
                                    multiplyExact(r1.minValue, r2.maxValue),
                                    multiplyExact(r1.minValue, r2.minValue)),
                            Math.min(
                                    multiplyExact(r1.maxValue, r2.minValue),
                                    multiplyExact(r1.maxValue, r2.maxValue)));
                } catch (ArithmeticException e) {
                    minValue = Integer.MIN_VALUE;
                }
                int maxValue;
                try {
                    maxValue = Math.max(
                            Math.max(
                                    multiplyExact(r1.minValue, r2.maxValue),
                                    multiplyExact(r1.minValue, r2.minValue)),
                            Math.max(
                                    multiplyExact(r1.maxValue, r2.minValue),
                                    multiplyExact(r1.maxValue, r2.maxValue)));
                } catch (ArithmeticException e) {
                    maxValue = Integer.MAX_VALUE;
                }
                result = new I32Range(minValue, maxValue);
            }
            else if (inst instanceof Instruction.Div) {
                int minValue = Integer.MAX_VALUE;
                int maxValue = Integer.MIN_VALUE;
                if (r1.maxValue >= 0) {
                    int minPositiveDividend = Math.max(r1.minValue, 0);
                    if (r2.maxValue > 0) {
                        int minPositiveDivisor = Math.max(r2.minValue, 1);
                        maxValue = Math.max(maxValue, r1.maxValue / minPositiveDivisor);
                        minValue = Math.min(minValue, minPositiveDividend / r2.maxValue);
                    }
                    if (r2.minValue < 0) {
                        int maxNegativeDivisor = Math.min(r2.maxValue, -1);
                        maxValue = Math.max(maxValue, minPositiveDividend / r2.minValue);
                        minValue = Math.min(minValue, r1.maxValue / maxNegativeDivisor);
                    }
                }
                if (r1.minValue <= 0) {
                    int maxNegativeDividend = Math.min(r1.maxValue, 0);
                    if (r2.maxValue > 0) {
                        int minPositiveDivisor = Math.max(r2.minValue, 1);
                        maxValue = Math.max(maxValue, maxNegativeDividend / r2.maxValue);
                        minValue = Math.min(minValue, r1.minValue / minPositiveDivisor);
                    }
                    if (r2.minValue < 0) {
                        int maxNegativeDivisor = Math.min(r2.maxValue, -1);
                        maxValue = Math.max(maxValue, r1.minValue / maxNegativeDivisor);
                        minValue = Math.min(minValue, maxNegativeDividend / r2.minValue);
                    }
                }
                result = new I32Range(minValue, maxValue);
            }
            else if (inst instanceof Instruction.Rem) {
                var maxM = max(abs(r2.minValue), abs(r2.maxValue));
                int minValue, maxValue;
                if (r1.minValue < 0) {
                    minValue = max(-(maxM - 1), r1.minValue);
                }
                else {
                    minValue = 0;
                }
                if (r1.maxValue > 0) {
                    maxValue = min(maxM - 1, r1.maxValue);
                }
                else {
                    maxValue = 0;
                }
                result = new I32Range(minValue, maxValue);
            }
            else if (inst instanceof Instruction.Shl) {
                int minValue;
                try {
                    minValue = Math.min(Math.min(r1.minValue << r2.minValue, r1.minValue << r2.maxValue), Math.min(r1.maxValue << r2.minValue, r1.maxValue << r2.maxValue));
                } catch (ArithmeticException e) {
                    minValue = Integer.MIN_VALUE;
                }
                int maxValue;
                try {
                    maxValue = Math.max(Math.max(r1.minValue << r2.minValue, r1.minValue << r2.maxValue), Math.max(r1.maxValue << r2.minValue, r1.maxValue << r2.maxValue));
                } catch (ArithmeticException e) {
                    maxValue = Integer.MAX_VALUE;
                }
                result = new I32Range(minValue, maxValue);
            }
            else if (inst instanceof Instruction.AShr) {
                int minValue;
                try {
                    minValue = Math.min(Math.min(r1.minValue >> r2.minValue, r1.minValue >> r2.maxValue), Math.min(r1.maxValue >> r2.minValue, r1.maxValue >> r2.maxValue));
                } catch (ArithmeticException e) {
                    minValue = Integer.MIN_VALUE;
                }
                int maxValue;
                try {
                    maxValue = Math.max(Math.max(r1.minValue >> r2.minValue, r1.minValue >> r2.maxValue), Math.max(r1.maxValue >> r2.minValue, r1.maxValue >> r2.maxValue));
                } catch (ArithmeticException e) {
                    maxValue = Integer.MAX_VALUE;
                }
                result = new I32Range(minValue, maxValue);
            }
            else if (inst instanceof Instruction.And) {
                int minValue = Math.min(Math.min(r1.minValue & r2.minValue, r1.minValue & r2.maxValue), Math.min(r1.maxValue & r2.minValue, r1.maxValue & r2.maxValue));
                int maxValue = r1.maxValue & r2.maxValue;
                result = new I32Range(minValue, maxValue);
            }
            else if (inst instanceof Instruction.Or) {
                int minValue = r1.minValue | r2.minValue;
                int maxValue = Math.max(Math.max(r1.minValue | r2.minValue, r1.minValue | r2.maxValue), Math.max(r1.maxValue | r2.minValue, r1.maxValue | r2.maxValue));
                result = new I32Range(minValue, maxValue);
            }
            else if (inst instanceof Instruction.Xor) {
                int minValue = Math.min(Math.min(r1.minValue ^ r2.minValue, r1.minValue ^ r2.maxValue), Math.min(r1.maxValue ^ r2.minValue, r1.maxValue ^ r2.maxValue));
                int maxValue = Math.max(Math.max(r1.minValue ^ r2.minValue, r1.minValue ^ r2.maxValue), Math.max(r1.maxValue ^ r2.minValue, r1.maxValue ^ r2.maxValue));
                result = new I32Range(minValue, maxValue);
            }
            else {
                result = I32Range.Any();
            }
        }
        else if (inst instanceof Instruction.Call call) {
            Function callee = call.getDestFunction();
            if (callee.isExternal()) {
                if (callee.getName().equals("getch")) result = new I32Range(-128, 127);
                else if (callee.getName().equals("getarray") || callee.getName().equals("getfarray"))
                    result = new I32Range(0, Integer.MAX_VALUE);
                else result = I32Range.Any();
            }
            else {
                result = I32Range.Any();
            }
        }
        else if (inst instanceof Instruction.Phi phi) {
            int minValue = Integer.MAX_VALUE;
            int maxValue = Integer.MIN_VALUE;
            for (BasicBlock pre : phi.getPreBlocks()) {
                Value preValue = phi.getOptionalValue(pre);
                if (!dirtyMap.get(func).get(pre)) {
                    I32Range ir = getOperandRange(preValue);
                    minValue = min(minValue, ir.minValue);
                    maxValue = max(maxValue, ir.maxValue);
                }
                else {
                    minValue = Integer.MIN_VALUE;
                    maxValue = Integer.MAX_VALUE;
                    break;
                }
            }
            result = new I32Range(minValue, maxValue);
        }
        else {
            result = I32Range.Any();
        }
        if (result.equals(I32Range.Any())) return I32Range.Any();
        else {
            bbMap.put(inst, result);
            return result;
        }
    }

    /**
     * 仅被calculateI32Range调用
     *
     * @param value
     * @return
     */
    private static I32Range getOperandRange(Value value) {
        if (!value.getType().isInt32Ty())
            throw new RuntimeException("Value is not I32 Type!");
        if (value instanceof Constant.ConstantInt)
            return I32Range.gerConstRange(((Constant.ConstantInt) value).getIntValue());
        if (value instanceof Function.Argument) return I32Range.Any();
        if (value instanceof Instruction) {
            BasicBlock bb = ((Instruction) value).getParentBlock();
            Function func = bb.getParentFunction();
            if (I32RangeBufferMap.get(func).get(bb).containsKey(value))
                return I32RangeBufferMap.get(func).get(bb).get(value);
            return I32Range.Any();
        }
        return null;
    }

    public static I32Range getInstRange(Instruction inst) {
        BasicBlock bb = inst.getParentBlock();
        Function func = bb.getParentFunction();
        if (dirtyMap.get(func).get(bb)) runOnBasicBlock(bb);
        if (I32RangeBufferMap.get(func).get(bb).containsKey(inst))
            return I32RangeBufferMap.get(func).get(bb).get(inst);
        return I32Range.Any();
    }

    public static I32Range getValueRange(Value value) {
        if (!value.getType().isInt32Ty())
            throw new RuntimeException("Value is not I32 Type!");
        if (value instanceof Constant.ConstantInt)
            return I32Range.gerConstRange(((Constant.ConstantInt) value).getIntValue());
        if (value instanceof Function.Argument) return I32Range.Any();
        if (value instanceof Instruction inst) return getInstRange(inst);
        return null;
    }

    public static void setDirtyBB(BasicBlock bb) {
        Function func = bb.getParentFunction();
        HashMap<BasicBlock, Boolean> funcDirty = dirtyMap.get(func);
        DGinfo dGinfo = AnalysisManager.getDG(func);
        ArrayList<BasicBlock> queue = new ArrayList<>();
        queue.add(bb);
        while (!queue.isEmpty()) {
            BasicBlock cur = queue.remove(0);
            funcDirty.put(bb, true);
            I32RangeBufferMap.get(func).putIfAbsent(bb, new HashMap<>());
            I32RangeBufferMap.get(func).get(bb).clear();
            for (BasicBlock child : dGinfo.getDomTreeChildren(cur)) {
                queue.add(child);
            }
        }
    }

}
