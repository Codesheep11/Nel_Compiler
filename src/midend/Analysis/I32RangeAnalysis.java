package midend.Analysis;

import midend.Analysis.result.SCEVinfo;
import midend.Transform.Loop.LoopInfo;
import midend.Transform.RangeFolding;
import mir.*;
import midend.Analysis.result.DGinfo;

import java.util.ArrayList;
import java.util.HashMap;

import static java.lang.Math.*;


public class I32RangeAnalysis {

    /**
     * 存储当前块定义的非Any的I32Range
     */
    public final HashMap<BasicBlock, HashMap<Instruction, I32Range>> I32RangeBufferMap = new HashMap<>();

    /**
     * 通过branch语句的icmp计算的Value值域
     */
    public final HashMap<BasicBlock, HashMap<Value, I32Range>> BlockEntryRange = new HashMap<>();

    private final DGinfo DGinfo;

    private final SCEVinfo SCEVinfo;

    /**
     * 值域 闭区间
     */
    public static class I32Range {
        private final int minValue;
        private final int maxValue;

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

        private static final I32Range Any = new I32Range(Integer.MIN_VALUE, Integer.MAX_VALUE);

        //常量区间复用
        private static final HashMap<Integer, I32Range> ConstantRangePool = new HashMap<>();


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
        LoopInfo.runOnFunc(function);
        SCEVinfo = AnalysisManager.getSCEV(function);
        runAnalysis(function);
    }

    public void runAnalysis(Function function) {
        ArrayList<BasicBlock> blocks = function.getTopoSortWithoutLatch();
        for (BasicBlock bb : blocks) {
            I32RangeBufferMap.put(bb, new HashMap<>());
            runOnBasicBlock(bb);
        }
        Update(function);
    }

    private void runOnBasicBlock(BasicBlock bb) {
        //计算当前块的入口值域
        calBlockEntryRange(bb);
        //计算并更新当前块的phi值域
        if (bb.loop != null && bb.loop.header.equals(bb)) {
            //如果循环头块，则循环头的phi值域很难分析
            for (Instruction.Phi phi : bb.getPhiInstructions()) {
                if (!phi.getType().isInt32Ty()) continue;
                if (SCEVinfo.contains(phi)) {
                    SCEVExpr expr = SCEVinfo.query(phi);
                    if (expr.getStep() > 0) {
                        I32RangeBufferMap.get(bb).put(phi, new I32Range(expr.getInit(), Integer.MAX_VALUE));
                    }
                    else {
                        I32RangeBufferMap.get(bb).put(phi, new I32Range(Integer.MIN_VALUE, expr.getInit()));
                    }
                }
                else {
                    I32RangeBufferMap.get(bb).put(phi, I32Range.Any());
                }
            }
        }
        else {
            for (Instruction.Phi phi : bb.getPhiInstructions()) {
                if (phi.getType().isInt32Ty()) {
                    int minValue = Integer.MAX_VALUE;
                    int maxValue = Integer.MIN_VALUE;
                    for (BasicBlock pre : phi.getPreBlocks()) {
                        Value preValue = phi.getOptionalValue(pre);
                        I32Range ir = getPhiRange(preValue, pre, bb);
                        minValue = min(minValue, ir.minValue);
                        maxValue = max(maxValue, ir.maxValue);
                    }
                    I32Range result = new I32Range(minValue, maxValue);
                    if (!result.equals(I32Range.Any()))
                        I32RangeBufferMap.get(bb).put(phi, result);
                }
            }
        }
        for (Instruction inst : bb.getMainInstructions()) {
            if (inst.getType().isInt32Ty()) {
                calculateI32Range(inst);
            }
        }
    }

    private void calBlockEntryRange(BasicBlock block) {
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
                if (cond instanceof Instruction.Icmp icmp) {
                    addRange2Map(branch, icmp, block, EntryMap);
                }
            }
        }
    }

    private void addRange2Map(Instruction.Branch branch, Instruction.Icmp icmp, BasicBlock block, HashMap<Value, I32Range> EntryMap) {
        boolean thenBlock = branch.getThenBlock().equals(block);
        boolean elseBlock = branch.getElseBlock().equals(block);
        BasicBlock branchBlock = branch.getParentBlock();
        if (icmp.getSrc1() instanceof Constant.ConstantInt) {
            icmp.swap();
        }
        Value src1 = icmp.getSrc1();
        Value src2 = icmp.getSrc2();
        Instruction.Icmp.CondCode condCode = icmp.getCondCode();
        if (src2 instanceof Constant.ConstantInt c2) {
            I32Range vr = getOperandRange(src1, branchBlock);
            int num = c2.getIntValue();
            int min = vr.minValue;
            int max = vr.maxValue;
            switch (condCode) {
                case EQ -> {
                    if (thenBlock) {
                        EntryMap.put(src1, I32Range.gerConstRange(num));
                    }
                }
                case NE -> {
                    if (elseBlock) {
                        EntryMap.put(src1, I32Range.gerConstRange(num));
                    }
                }
                case SGE -> {
                    if (thenBlock) {
                        EntryMap.put(src1, new I32Range(num, max));
                    }
                    else if (elseBlock) {
                        EntryMap.put(src1, new I32Range(min, num - 1));
                    }
                }
                case SGT -> {
                    if (thenBlock) {
                        EntryMap.put(src1, new I32Range(num + 1, max));
                    }
                    else if (elseBlock) {
                        EntryMap.put(src1, new I32Range(min, num));
                    }
                }
                case SLE -> {
                    if (thenBlock) {
                        EntryMap.put(src1, new I32Range(min, num));
                    }
                    else if (elseBlock) {
                        EntryMap.put(src1, new I32Range(num + 1, max));
                    }
                }
                case SLT -> {
                    if (thenBlock) {
                        EntryMap.put(src1, new I32Range(min, num - 1));
                    }
                    else if (elseBlock) {
                        EntryMap.put(src1, new I32Range(num, max));
                    }
                }
            }
        }
        else {
            I32Range ir1 = getOperandRange(src1, branchBlock);
            I32Range ir2 = getOperandRange(src2, branchBlock);
            if (!ir1.equals(I32Range.Any()) || !ir2.equals(I32Range.Any())) {
                int min1 = ir1.minValue;
                int max1 = ir1.maxValue;
                int min2 = ir2.minValue;
                int max2 = ir2.maxValue;
                switch (condCode) {
                    case EQ -> {
                        if (thenBlock) {
                            EntryMap.put(src1, new I32Range(max(min1, min2), min(max1, max2)));
                            EntryMap.put(src2, new I32Range(max(min1, min2), min(max1, max2)));
                        }
                    }
                    case NE -> {
                        if (elseBlock) {
                            EntryMap.put(src1, new I32Range(max(min1, min2), min(max1, max2)));
                            EntryMap.put(src2, new I32Range(max(min1, min2), min(max1, max2)));
                        }
                    }
                    //todo:其他情况
                }
            }
        }
    }

    private void Update(Function func) {
        for (BasicBlock bb : func.getDomTreeLayerSort()) {
            for (Instruction.Phi phi : bb.getPhiInstructions()) {
                if (phi.getType().isInt32Ty()) {
                    calculateI32Range(phi);
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
                if (((Instruction.Mul) inst).getOperand_1().equals(((Instruction.Mul) inst).getOperand_2())) {
                    minValue = 0;
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
            else if (inst instanceof Instruction.Max) {
                int minValue = Math.max(r1.minValue, r2.minValue);
                int maxValue = Math.max(r1.maxValue, r2.maxValue);
                result = new I32Range(minValue, maxValue);
            }
            else if (inst instanceof Instruction.Min) {
                int minValue = Math.min(r1.minValue, r2.minValue);
                int maxValue = Math.min(r1.maxValue, r2.maxValue);
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
            I32Range ir = getOperandRange(phi, bb);
            int minValue = ir.getMinValue();
            int maxValue = ir.getMaxValue();
            for (BasicBlock pre : phi.getPreBlocks()) {
                Value preValue = phi.getOptionalValue(pre);
                I32Range newIr = getPhiRange(preValue, pre, bb);
                minValue = min(minValue, newIr.minValue);
                maxValue = max(maxValue, newIr.maxValue);
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


    private I32Range getPhiRange(Value value, BasicBlock pre, BasicBlock bb) {
        if (!value.getType().isInt32Ty())
            throw new RuntimeException("Value is not I32 Type!");
        if (value instanceof Constant.ConstantInt)
            return I32Range.gerConstRange(((Constant.ConstantInt) value).getIntValue());
        if (!(pre.getTerminator() instanceof Instruction.Branch br)) return getOperandRange(value, pre);
        if (br.getCond() instanceof Instruction.Icmp icmp) {
            if (icmp.getSrc1() instanceof Constant.ConstantInt) {
                icmp.swap();
            }
            Value src1 = icmp.getSrc1();
            Value src2 = icmp.getSrc2();
            if (src1.equals(value) && src2 instanceof Constant.ConstantInt c2) {
                int num = c2.getIntValue();
                boolean thenBlock = br.getThenBlock().equals(bb);
                I32Range vr = getOperandRange(src1, pre);
                int min = vr.minValue;
                int max = vr.maxValue;
                Instruction.Icmp.CondCode condCode = icmp.getCondCode();
                switch (condCode) {
                    case EQ -> {
                        if (thenBlock) {
                            return I32Range.gerConstRange(num);
                        }
                    }
                    case NE -> {
                        if (!thenBlock) {
                            return I32Range.gerConstRange(num);
                        }
                    }
                    case SGE -> {
                        if (thenBlock) {
                            return new I32Range(num, max);
                        }
                        else {
                            return new I32Range(min, num - 1);
                        }
                    }
                    case SGT -> {
                        if (thenBlock) {
                            return new I32Range(num + 1, max);
                        }
                        else {
                            return new I32Range(min, num);
                        }
                    }
                    case SLE -> {
                        if (thenBlock) {
                            return new I32Range(min, num);
                        }
                        else {
                            return new I32Range(num + 1, max);
                        }
                    }
                    case SLT -> {
                        if (thenBlock) {
                            return new I32Range(min, num - 1);
                        }
                        else {
                            return new I32Range(num, max);
                        }
                    }
                }
            }
        }
        return getOperandRange(value, pre);
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
        if (!BlockEntryRange.containsKey(cur)) {
            //关键边
            if (cur.getPreBlocks().size() != 1) {
                throw new RuntimeException("BlockEntryRange not contains key " + cur.getLabel());
            }
            return getValueRange(value, cur.getPreBlocks().get(0));
        }
        if (BlockEntryRange.get(cur).containsKey(value)) return BlockEntryRange.get(cur).get(value);
        if (value instanceof Function.Argument) return I32Range.Any();
        if (value instanceof Instruction inst) return getInstRange(inst);
        return null;
    }

    public Value icmpFold(Instruction.Icmp icmp, BasicBlock block) {
        BasicBlock parentBlock = icmp.getParentBlock();
        if (parentBlock.getPreBlocks().contains(block)) {
            //暂存
            HashMap<Instruction, I32Range> storeBufferMap = I32RangeBufferMap.get(parentBlock);
            HashMap<Value, I32Range> storeEntryMap = BlockEntryRange.get(parentBlock);

            HashMap<Instruction, I32Range> BufferTemp = new HashMap<>();
            HashMap<Value, I32Range> EntryTemp = new HashMap<>();
            I32RangeBufferMap.put(parentBlock, BufferTemp);
            BlockEntryRange.put(parentBlock, EntryTemp);

            for (Value v : BlockEntryRange.get(block).keySet()) {
                if (!parentBlock.getInstructionsSnap().contains(v))
                    EntryTemp.put(v, BlockEntryRange.get(block).get(v));
            }
            if (block.getTerminator() instanceof Instruction.Branch branch) {
                Value cond = branch.getCond();
                if (cond instanceof Instruction.Icmp icmp1) {
                    addRange2Map(branch, icmp1, parentBlock, EntryTemp);
                }
            }
            BlockEntryRange.put(parentBlock, EntryTemp);
            for (Instruction.Phi phi : parentBlock.getPhiInstructions()) {
                if (!phi.getType().isInt32Ty()) continue;
                I32Range ir = getOperandRange(phi.getOptionalValue(block), block);
                BufferTemp.put(phi, ir);
            }
            for (Instruction inst : parentBlock.getMainInstructions()) {
                if (inst.getType().isInt32Ty()) {
                    I32Range ir = calculateI32Range(inst);
                    BufferTemp.put(inst, ir);
                }
            }
            //尝试折叠icmp
            Value v = RangeFolding.icmpSimplify(icmp);
            I32RangeBufferMap.put(parentBlock, storeBufferMap);
            BlockEntryRange.put(parentBlock, storeEntryMap);
            return v;
        }
        return icmp;
    }

}
