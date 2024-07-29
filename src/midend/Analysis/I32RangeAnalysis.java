package midend.Analysis;

import mir.*;
import mir.Module;
import mir.result.DGinfo;

import java.util.ArrayList;
import java.util.HashMap;

import static java.lang.Math.*;
import static java.lang.Math.max;


public class I32RangeAnalysis {

    /**
     * 存储当前块定义的非Any的I32Range
     */
    public HashMap<BasicBlock, HashMap<Instruction, I32Range>> I32RangeBufferMap = new HashMap<>();

    /**
     * 基本块入口的I32Range
     */
    public HashMap<BasicBlock, HashMap<Value, I32Range>> BlockEntryRange = new HashMap<>();

    private DGinfo DGinfo;

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

        @Override
        public String toString() {
            return "[" + minValue + ", " + maxValue + "]";
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

    public I32RangeAnalysis(Function function) {
        DGinfo = AnalysisManager.getDG(function);
        runAnalysis(function);
    }

    public void runAnalysis(Function function) {
//        System.out.println("runAnalysis " + function.getName());
        for (BasicBlock bb : function.getDomTreeLayerSort()) {
            calBlockEntryRange(bb);
            runOnBasicBlock(bb);
        }
        Update(function);
    }

    private void runOnBasicBlock(BasicBlock bb) {
//        System.out.println("runOnBasicBlock " + bb.getLabel());
//        Function func = bb.getParentFunction();
        I32RangeBufferMap.put(bb, new HashMap<>());
        for (Instruction inst : bb.getInstructions()) {
            if (inst.getType().isInt32Ty()) calculateI32Range(inst);
        }
    }

    private void calBlockEntryRange(BasicBlock block) {
//        System.out.println("calBlockEntryRange " + block.getLabel());
        HashMap<Value, I32Range> EntryMap = new HashMap<>();
        BlockEntryRange.put(block, EntryMap);
        if (block.equals(block.getParentFunction().getEntry())) return;

        BasicBlock idom = DGinfo.getIDom(block);
        for (Value key : BlockEntryRange.get(idom).keySet()) {
            EntryMap.put(key, BlockEntryRange.get(idom).get(key));
        }
        if (block.getPreBlocks().size() == 1 && block.getPreBlocks().get(0).equals(idom)) {
            Instruction.Terminator term = idom.getTerminator();
            if (term instanceof Instruction.Branch branch) {
                Value cond = branch.getCond();
                boolean thenBlock = branch.getThenBlock().equals(block);
                boolean elseBlock = branch.getElseBlock().equals(block);
                if (cond instanceof Instruction.Icmp icmp) {
                    Value src1 = icmp.getSrc1();
                    Value src2 = icmp.getSrc2();
                    Instruction.Icmp.CondCode condCode = icmp.getCondCode();
                    if (src1 instanceof Constant.ConstantInt c1) {
                        I32Range vr = getOperandRange(src2, idom);
                        int num = c1.getIntValue();
                        int min = vr.minValue;
                        int max = vr.maxValue;
                        if (condCode == Instruction.Icmp.CondCode.EQ && thenBlock) {
                            EntryMap.put(src2, I32Range.gerConstRange(num));
                        } else if (condCode == Instruction.Icmp.CondCode.NE && elseBlock) {
                            EntryMap.put(src2, I32Range.gerConstRange(num));
                        } else if (condCode == Instruction.Icmp.CondCode.SGT && thenBlock) {
                            EntryMap.put(src2, new I32Range(min, num - 1));
                        } else if (condCode == Instruction.Icmp.CondCode.SGE && thenBlock) {
                            EntryMap.put(src2, new I32Range(min, num));
                        } else if (condCode == Instruction.Icmp.CondCode.SLT && thenBlock) {
                            EntryMap.put(src2, new I32Range(num + 1, max));
                        } else if (condCode == Instruction.Icmp.CondCode.SLE && thenBlock) {
                            EntryMap.put(src2, new I32Range(num, max));
                        } else if (condCode == Instruction.Icmp.CondCode.SGT && elseBlock) {
                            EntryMap.put(src2, new I32Range(num, max));
                        } else if (condCode == Instruction.Icmp.CondCode.SGE && elseBlock) {
                            EntryMap.put(src2, new I32Range(num + 1, max));
                        } else if (condCode == Instruction.Icmp.CondCode.SLT && elseBlock) {
                            EntryMap.put(src2, new I32Range(min, num));
                        } else if (condCode == Instruction.Icmp.CondCode.SLE && elseBlock) {
                            EntryMap.put(src2, new I32Range(min, num - 1));
                        }
                    }
                    if (src2 instanceof Constant.ConstantInt c2) {
                        I32Range vr = getOperandRange(src1, idom);
                        int num = c2.getIntValue();
                        int min = vr.minValue;
                        int max = vr.maxValue;
                        if (condCode == Instruction.Icmp.CondCode.EQ && thenBlock) {
                            EntryMap.put(src1, I32Range.gerConstRange(num));
                        } else if (condCode == Instruction.Icmp.CondCode.NE && elseBlock) {
                            EntryMap.put(src1, I32Range.gerConstRange(num));
                        } else if (condCode == Instruction.Icmp.CondCode.SGT && thenBlock) {
                            EntryMap.put(src1, new I32Range(num + 1, max));
                        } else if (condCode == Instruction.Icmp.CondCode.SGE && thenBlock) {
                            EntryMap.put(src1, new I32Range(num, max));
                        } else if (condCode == Instruction.Icmp.CondCode.SLT && thenBlock) {
                            EntryMap.put(src1, new I32Range(min, num - 1));
                        } else if (condCode == Instruction.Icmp.CondCode.SLE && thenBlock) {
                            EntryMap.put(src1, new I32Range(min, num));
                        } else if (condCode == Instruction.Icmp.CondCode.SGT && elseBlock) {
                            EntryMap.put(src1, new I32Range(min, num));
                        } else if (condCode == Instruction.Icmp.CondCode.SGE && elseBlock) {
                            EntryMap.put(src1, new I32Range(min, num - 1));
                        } else if (condCode == Instruction.Icmp.CondCode.SLT && elseBlock) {
                            EntryMap.put(src1, new I32Range(num + 1, max));
                        } else if (condCode == Instruction.Icmp.CondCode.SLE && elseBlock) {
                            EntryMap.put(src1, new I32Range(num, max));
                        }
                    }
                }
            }
        }

    }

    private void Update(Function func) {
        ArrayList<Instruction> queue = new ArrayList<>();
        for (BasicBlock bb : func.getDomTreeLayerSort()) {
            for (Instruction.Phi phi : bb.getPhiInstructions()) {
                if (phi.getType().isInt32Ty()) {
                    I32Range ir = getOperandRange(phi, bb);
                    I32Range ret = calculateI32Range(phi);
                    if (!ir.equals(ret)) {
                        for (Instruction user : phi.getUsers()) {
                            if (user.getType().isInt32Ty())
                                queue.add(user);
                        }
                    }
                }
            }
        }
        //todo:归纳变量不收敛
//        while (!queue.isEmpty()) {
//            Instruction inst = queue.remove(0);
////            System.out.println(inst);
//            I32Range ir = getInstRange(inst);
//            I32Range ret = calculateI32Range(inst);
//            if (!ir.equals(ret)) {
//                System.out.println("Update " + inst + " " + ir + " " + ret);
//                for (Instruction user : inst.getUsers())
//                    if (user.getType().isInt32Ty()) queue.add(user);
//            }
//        }
    }

    /**
     * 对于给定的Inst 计算 I32Range
     *
     * @param inst
     * @return
     */
    private I32Range calculateI32Range(Instruction inst) {
//        System.out.println(value);
        if (!inst.getType().isInt32Ty())
            throw new RuntimeException("Value is not I32 Type!");
        BasicBlock bb = inst.getParentBlock();
        HashMap<Instruction, I32Range> bbMap = I32RangeBufferMap.get(bb);
        I32Range result = null;
        if (inst instanceof Instruction.BinaryOperation binaryOperation) {
            I32Range r1 = getOperandRange(binaryOperation.getOperand_1(), bb);
            I32Range r2 = getOperandRange(binaryOperation.getOperand_2(), bb);
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
            } else if (inst instanceof Instruction.Sub) {
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
            } else if (inst instanceof Instruction.Mul) {
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
            } else if (inst instanceof Instruction.Div) {
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
            } else if (inst instanceof Instruction.Rem) {
                var maxM = max(abs(r2.minValue), abs(r2.maxValue));
                int minValue, maxValue;
                if (r1.minValue < 0) {
                    minValue = max(-(maxM - 1), r1.minValue);
                } else {
                    minValue = 0;
                }
                if (r1.maxValue > 0) {
                    maxValue = min(maxM - 1, r1.maxValue);
                } else {
                    maxValue = 0;
                }
                result = new I32Range(minValue, maxValue);
            } else if (inst instanceof Instruction.Shl) {
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
            } else if (inst instanceof Instruction.AShr) {
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
            } else if (inst instanceof Instruction.And) {
                int minValue = Math.min(Math.min(r1.minValue & r2.minValue, r1.minValue & r2.maxValue), Math.min(r1.maxValue & r2.minValue, r1.maxValue & r2.maxValue));
                int maxValue = r1.maxValue & r2.maxValue;
                result = new I32Range(minValue, maxValue);
            } else if (inst instanceof Instruction.Or) {
                int minValue = r1.minValue | r2.minValue;
                int maxValue = Math.max(Math.max(r1.minValue | r2.minValue, r1.minValue | r2.maxValue), Math.max(r1.maxValue | r2.minValue, r1.maxValue | r2.maxValue));
                result = new I32Range(minValue, maxValue);
            } else if (inst instanceof Instruction.Xor) {
                int minValue = Math.min(Math.min(r1.minValue ^ r2.minValue, r1.minValue ^ r2.maxValue), Math.min(r1.maxValue ^ r2.minValue, r1.maxValue ^ r2.maxValue));
                int maxValue = Math.max(Math.max(r1.minValue ^ r2.minValue, r1.minValue ^ r2.maxValue), Math.max(r1.maxValue ^ r2.minValue, r1.maxValue ^ r2.maxValue));
                result = new I32Range(minValue, maxValue);
            } else {
                result = I32Range.Any();
            }
        } else if (inst instanceof Instruction.Call call) {
            Function callee = call.getDestFunction();
            if (callee.isExternal()) {
                if (callee.getName().equals("getch")) result = new I32Range(-128, 127);
                else if (callee.getName().equals("getarray") || callee.getName().equals("getfarray"))
                    result = new I32Range(0, Integer.MAX_VALUE);
                else result = I32Range.Any();
            } else {
                result = I32Range.Any();
            }
        } else if (inst instanceof Instruction.Phi phi) {
            int minValue = Integer.MAX_VALUE;
            int maxValue = Integer.MIN_VALUE;
            for (BasicBlock pre : phi.getPreBlocks()) {
                Value preValue = phi.getOptionalValue(pre);
                I32Range ir = getOperandRange(preValue, pre);
                minValue = min(minValue, ir.minValue);
                maxValue = max(maxValue, ir.maxValue);
            }
            result = new I32Range(minValue, maxValue);
        } else {
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
     * @param bb
     * @return
     */
    private I32Range getOperandRange(Value value, BasicBlock bb) {
        if (!value.getType().isInt32Ty())
            throw new RuntimeException("Value is not I32 Type!");
        if (value instanceof Constant.ConstantInt)
            return I32Range.gerConstRange(((Constant.ConstantInt) value).getIntValue());
        if (!BlockEntryRange.containsKey(bb)) return I32Range.Any();
        if (BlockEntryRange.get(bb).containsKey(value)) return BlockEntryRange.get(bb).get(value);
        if (value instanceof Function.Argument) return I32Range.Any();
        if (value instanceof Instruction) {
            BasicBlock instBB = ((Instruction) value).getParentBlock();
            if (I32RangeBufferMap.containsKey(instBB))
                if (I32RangeBufferMap.get(instBB).containsKey(value))
                    return I32RangeBufferMap.get(instBB).get(value);
            return I32Range.Any();
        }
        return null;
    }


    private I32Range getInstRange(Instruction inst) {
        BasicBlock bb = inst.getParentBlock();
        if (I32RangeBufferMap.get(bb).containsKey(inst))
            return I32RangeBufferMap.get(bb).get(inst);
        return I32Range.Any();
    }

    //对外接口
    public I32Range getValueRange(Value value, BasicBlock cur) {
        if (!value.getType().isInt32Ty())
            throw new RuntimeException("Value is not I32 Type!");
        if (value instanceof Constant.ConstantInt)
            return I32Range.gerConstRange(((Constant.ConstantInt) value).getIntValue());
        if (BlockEntryRange.get(cur).containsKey(value)) return BlockEntryRange.get(cur).get(value);
        if (value instanceof Function.Argument) return I32Range.Any();
        if (value instanceof Instruction inst) return getInstRange(inst);
        return null;
    }

}
