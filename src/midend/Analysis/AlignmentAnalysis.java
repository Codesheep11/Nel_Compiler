package midend.Analysis;

import midend.Transform.Loop.IndVars;
import mir.*;
import mir.Module;
import mir.result.SCEVinfo;

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


    public static AlignMap run(Module module) {
        cur_module = module;
        alignMap = new AlignMap();
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
        return alignMap;
    }

    private static final int ANA_LEN = 64;

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
                    Value baseAlign = gep.getIdx();
                    SCEVExpr scevExpr = scevInfo.query(baseAlign);
                    if (scevExpr != null) {
                        if (scevExpr.isEvenAll())
                            updateAlign.accept(inst, AlignType.ALIGN_BYTE_8);
                        else if (scevExpr.isOddAll())
                            updateAlign.accept(inst, AlignType.ALIGN_BYTE_4);
                    } else {
                        AlignType alignType = alignMap.get(baseAlign);

                        updateAlign.accept(inst, alignType);
                    }

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
            if (scevExpr.isEvenAll())
                return AlignType.ALIGN_BYTE_8;
            else if (scevExpr.isOddAll())
                return AlignType.ALIGN_BYTE_4;
        }
        switch (inst.getInstType()) {
            case ADD -> {
                Instruction.Add add = (Instruction.Add) inst;
                AlignType align1 = GepSpread(add.getOperand_1());
                AlignType align2 = GepSpread(add.getOperand_2());
                if (align1 == AlignType.ALIGN_BYTE_8 && align2 == AlignType.ALIGN_BYTE_8)
                    return AlignType.ALIGN_BYTE_8;
                else return AlignType.ALIGN_BYTE_4;
            }
            case MUL -> {
                Instruction.Mul mul = (Instruction.Mul) inst;
                AlignType align1 = GepSpread(mul.getOperand_1());
                AlignType align2 = GepSpread(mul.getOperand_2());
                if (align1 == AlignType.ALIGN_BYTE_8 || align2 == AlignType.ALIGN_BYTE_8)
                    return AlignType.ALIGN_BYTE_8;
                else return AlignType.ALIGN_BYTE_4;
            }
            default -> {
                return alignMap.get(value);
            }
        }
    }


}
