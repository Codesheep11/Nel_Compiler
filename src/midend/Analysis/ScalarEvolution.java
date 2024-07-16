package midend.Analysis;

import mir.*;
import mir.result.SCEVinfo;

/**
 * Scalar Evolution Analysis <br>
 * 标量演化分析 <br>
 * @author Srchycz
 */
public class ScalarEvolution {

    public static SCEVinfo run(Function func) {
        SCEVinfo res = new SCEVinfo();

        for (Loop loop : func.loopInfo.TopLevelLoops) {
            for (Instruction inst : loop.header.getInstructions()) {
                if (inst instanceof Instruction.Phi phiInst) {
                    if (phiInst.getIncomingValueSize() == 2) {
                        BasicInduceVariableAnalysis(phiInst, res, loop);
                    }
                } else {
                    GeneralInduceVariableAnalysis(inst, res);
                }
            }
        }

        return res;
    }

    /**
     * 基础归纳变量分析
     * @param phiInst phi指令 应该位于 header处
     * @param res 结果
     * @param loop 循环
     */
    private static void BasicInduceVariableAnalysis(Instruction.Phi phiInst, SCEVinfo res, Loop loop) {
        // TODO: 需要 check
        Value initial = getInitial(phiInst, loop);
        Value next = getNext(phiInst, loop);

        if (next instanceof Constant) {
//            System.out.println("Invariant");
            return;
        }
        Instruction nextInst = (Instruction) next;
        if (nextInst instanceof Instruction.BinaryOperation bo && nextInst.getInstType() == Instruction.InstType.ADD) {
            Value op1 = bo.getOperand_1();
            Value op2 = bo.getOperand_2();
            if (op1 == phiInst || op2 == phiInst) {
                // 获取递增量
                Value c = (op1 == phiInst) ? op2 : op1;
                SCEVExpr initialSCEV = res.query(initial);
                SCEVExpr incSCEV = res.query(c);
                if (initialSCEV != null && incSCEV != null) {
                    SCEVExpr scev = new SCEVExpr(SCEVExpr.SCEVType.AddRec);
                    scev.operands.add(initialSCEV);
                    scev.operands.add(incSCEV);
                    scev.loop = loop;
                    res.addSCEV(phiInst, scev);
                }
            }
        }
    }

    /**
     * 通用归纳变量分析
     */
    private static void GeneralInduceVariableAnalysis(Instruction inst, SCEVinfo res) {
        switch (inst.getInstType()) {
            case ADD -> {
                Instruction.Add add = (Instruction.Add) inst;
                SCEVExpr lhs = res.query(add.getOperand_1());
                SCEVExpr rhs = res.query(add.getOperand_2());
                if (lhs != null && rhs != null) {
                    SCEVExpr scev = foldAdd(res, lhs, rhs);
                    if (scev != null) {
                        res.addSCEV(inst, scev);
                    }
                }
            }
            case MUL -> {
                Instruction.Mul mul = (Instruction.Mul) inst;
                SCEVExpr lhs = res.query(mul.getOperand_1());
                SCEVExpr rhs = res.query(mul.getOperand_2());
                if (lhs != null && rhs != null) {
                    SCEVExpr scev = foldMul(res, lhs, rhs);
                    if (scev != null) {
                        res.addSCEV(inst, scev);
                    }
                }
            }
            default -> { }
        }
    }

    private static SCEVExpr foldAdd(SCEVinfo res, SCEVExpr lhs, SCEVExpr rhs) {
        if (lhs.type == SCEVExpr.SCEVType.Constant && rhs.type == SCEVExpr.SCEVType.Constant) {
            SCEVExpr scev = new SCEVExpr(SCEVExpr.SCEVType.Constant);
            scev.constant = lhs.constant + rhs.constant;
            return scev;
        }
        return null;
    }

    private static SCEVExpr foldMul(SCEVinfo res, SCEVExpr lhs, SCEVExpr rhs) {
        if (lhs.type == SCEVExpr.SCEVType.Constant && rhs.type == SCEVExpr.SCEVType.Constant) {
            SCEVExpr scev = new SCEVExpr(SCEVExpr.SCEVType.Constant);
            scev.constant = lhs.constant * rhs.constant;
            return scev;
        }
        return null;
    }

    private static Value getInitial(Instruction.Phi phiInst, Loop loop) {
        return phiInst.getOptionalValue(loop.getPreHeader());
    }

    private static Value getNext(Instruction.Phi phiInst, Loop loop) {
        for (BasicBlock block : phiInst.getPreBlocks())
            if (block != loop.getPreHeader())
                return phiInst.getOptionalValue(block);
        throw new RuntimeException("getNext: no next value");
    }

    // Main方法示例
//    public static void main(String[] args) {
//        Function func = new Function();
//        AnalysisPassManager analysis = new AnalysisPassManager();
//        ScalarEvolutionAnalysis analysisPass = new ScalarEvolutionAnalysis();
//        SCEVAnalysisResult result = analysisPass.run(func, analysis);
//        System.out.println("SCEV Analysis Completed.");
//    }
}
