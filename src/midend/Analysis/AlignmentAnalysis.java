package midend.Analysis;

import midend.Transform.Loop.IndVars;
import mir.*;
import mir.Module;
import midend.Analysis.result.SCEVinfo;

import java.util.*;
import java.util.function.BiConsumer;

public final class AlignmentAnalysis {

    public enum AlignType {
        UNKNOWN, ALIGN_BYTE_4, ALIGN_BYTE_8;

        public boolean morePreciseThan(AlignType other) {
            return this.ordinal() > other.ordinal();
        }

        /**
         * 合并两个对齐信息 返回更模糊的结果
         */
        public static AlignType merge(AlignType a, AlignType b) {
            return a.morePreciseThan(b) ? b : a;
        }

        public static AlignType mul(AlignType a, AlignType b) {
            if (a == ALIGN_BYTE_8 || b == ALIGN_BYTE_8)
                return ALIGN_BYTE_8;
            if (a == UNKNOWN || b == UNKNOWN)
                return UNKNOWN;
            return ALIGN_BYTE_4;
        }

        public static AlignType add(AlignType a, AlignType b) {
            if (a == ALIGN_BYTE_8 && b == ALIGN_BYTE_8)
                return ALIGN_BYTE_8;
            if (a == ALIGN_BYTE_4 && b == ALIGN_BYTE_4)
                return ALIGN_BYTE_8;
            if (a == UNKNOWN || b == UNKNOWN)
                return UNKNOWN;
            return ALIGN_BYTE_4;
        }
    }

    public static final class AlignMap extends HashMap<Value, AlignType> {

        @Override
        public AlignType get(Object key) {
            if (key instanceof Constant.ConstantInt constantInt) {
                return (constantInt.getIntValue() & 1) == 0 ? AlignType.ALIGN_BYTE_8 : AlignType.ALIGN_BYTE_4;
            }
            return super.getOrDefault(key, AlignType.UNKNOWN);
        }

    }

    private static Module cur_module;

    private static SCEVinfo scevInfo;

    private static AlignMap alignMap;


    public static void run(Module module) {
        cur_module = module;
        alignMap = new AlignMap();
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
        AnalysisManager.setAlignMap(alignMap);
    }

    private static final int ANA_LEN = 5000;

    public static void runOnFunc(Function function) {
        IndVars.runOnFunc(function);
        scevInfo = AnalysisManager.getSCEV(function);
        final HashSet<Value> inList = new HashSet<>();
        Queue<Instruction> workList = new LinkedList<>();

        BiConsumer<Value, AlignType> updateAlign = (value, alignType) -> {
            if (alignType.morePreciseThan(alignMap.get(value))) {
                alignMap.put(value, alignType);
                for (Value operand : value.getUsers()) {
                    if (operand.getType().isPointerTy() && operand instanceof Instruction inst) {
                        if (workList.size() < ANA_LEN && inList.add(operand)) {
                            workList.add(inst);
                        }
                    }
                }
            }
        };

        for (GlobalVariable globalVariable : cur_module.getGlobalValues()) {
            updateAlign.accept(globalVariable, AlignType.ALIGN_BYTE_8);
        }

        for (Function.Argument argument : function.getFuncRArguments()) {
            if (argument.getType().isPointerTy()) {
                updateAlign.accept(argument, AlignType.ALIGN_BYTE_8);
            }
        }

        for (BasicBlock bb : function.getBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (!inst.getType().isPointerTy())
                    continue;
                if (inst instanceof Instruction.Alloc) {
                    updateAlign.accept(inst, AlignType.ALIGN_BYTE_8);
                }
            }
        }

        while (!workList.isEmpty()) {
            Instruction inst = workList.poll();
            inList.remove(inst);
            switch (inst.getInstType()) {
                case BitCast -> {
                    Instruction.BitCast bitCast = (Instruction.BitCast) inst;
                    AlignType alignType = alignMap.get(bitCast.getSrc());
                    updateAlign.accept(inst, alignType);
                }
                case GEP -> {
                    Instruction.GetElementPtr gep = (Instruction.GetElementPtr) inst;
                    Value baseAlign = gep.getBase();
                    Value idxAlign = gep.getIdx();

                    AlignType idxAlignType = GepSpread(idxAlign);
                    AlignType baseAlignType = alignMap.get(baseAlign);
                    Type innerType = ((Type.PointerType) gep.getType()).getInnerType();
                    // base + idx * innerType
                    AlignType innerAlign = (innerType.queryBytesSizeOfType() & 7) == 0 ? AlignType.ALIGN_BYTE_8 : AlignType.ALIGN_BYTE_4;

                    updateAlign.accept(inst, AlignType.add(baseAlignType, AlignType.mul(idxAlignType, innerAlign)));


                }
                case PHI -> {
                    Instruction.Phi phi = (Instruction.Phi) inst;
                    AlignType res = AlignType.ALIGN_BYTE_8;
                    for (Map.Entry<BasicBlock, Value> entry : phi.getOptionalValues().entrySet()) {
                        if (entry.getValue() == inst) continue;
                        AlignType alignType = alignMap.get(entry.getValue());
                        res = AlignType.merge(res, alignType);
                    }
                    updateAlign.accept(inst, res);
                }

                default -> {
                }
            }
        }
    }

    public static AlignType GepSpread(Value value) {
        if (!(value instanceof Instruction inst))
            return alignMap.get(value);
        SCEVExpr scevExpr = scevInfo.query(inst);
        if (scevExpr != null) {
            return scev2align(scevExpr);
//            if (scevExpr.isEvenAll())
//                return AlignType.ALIGN_BYTE_8;
//            else if (scevExpr.isOddAll())
//                return AlignType.ALIGN_BYTE_4;
        }
        switch (inst.getInstType()) {
            case ADD -> {
                Instruction.Add add = (Instruction.Add) inst;
                AlignType align1 = GepSpread(add.getOperand_1());
                AlignType align2 = GepSpread(add.getOperand_2());

                return AlignType.add(align1, align2);
            }
            case MUL -> {
                Instruction.Mul mul = (Instruction.Mul) inst;
                AlignType align1 = GepSpread(mul.getOperand_1());
                AlignType align2 = GepSpread(mul.getOperand_2());

                return AlignType.mul(align1, align2);
            }
            default -> {
                return alignMap.get(value);
            }
        }
    }

    private static AlignType scev2align(SCEVExpr scevExpr) {
        if (scevExpr.isInSameLoop()) {
            if (scevExpr.isEvenAll())
                return AlignType.ALIGN_BYTE_8;
            else if (scevExpr.isOddAll())
                return AlignType.ALIGN_BYTE_4;
            return AlignType.UNKNOWN;
        }
        //FIXME: more precise
        return AlignType.UNKNOWN;
    }


}
