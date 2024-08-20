package midend.Analysis;

import mir.*;
import midend.Analysis.result.SCEVinfo;

import midend.Analysis.result.CoRInfo;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Scalar Evolution Analysis <br>
 * 标量演化分析 <br>
 * 参考 CoR 的相关论文和往届优秀队伍 CMMC 的部分实现思路
 *
 * @see <a href="https://dl.acm.org/doi/abs/10.1145/190347.190423">Chain of Recurrence</a>
 * @see <a href="https://gitlab.eduxiji.net/educg-group-17291-1894922/202314325201374-1031/-/blob/riscv_fix/src/cmmc/Analysis/SCEVAnalysis.hpp">CMMC优秀作品</a>
 * NOTE: 互相依赖无法处理
 * 必须经过 LSF
 */
public class ScalarEvolution {

    private static final HashSet<Instruction> visited = new HashSet<>();

    private static final HashSet<Instruction> visited_plus = new HashSet<>();

    private static Loop findLoop(Function function, BasicBlock block) {
        Loop ret = null;
        for (Loop loop : function.loopInfo.TopLevelLoops) {
            ret = loopContains(loop, block);
            if (ret != null) {
                return ret;
            }
        }
        return ret;
    }

    private static Loop loopContains(Loop loop, BasicBlock block) {
        Loop ret = null;
        for (Loop child : loop.children) {
            ret = loopContains(child, block);
            if (ret != null) {
                return ret;
            }
        }
        if (loop.header == block) {
            return loop;
        }
        return null;
    }

    public static SCEVinfo runOnFunc(Function func) {
        visited.clear();
        SCEVinfo res = new SCEVinfo();
        CoRInfo corInfo = new CoRInfo();
        for (BasicBlock block : func.getBlocks()) {
            Loop loop = findLoop(func, block);
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof Instruction.Phi phiInst) {
                    if (phiInst.getIncomingValueSize() == 2) {
                        BasicInduceVariableAnalysis(phiInst, res, loop);
//                        BasicInduceVariableAnalysisPlus(phiInst, corInfo, loop);
                    }
                }
            }
        }
        for (BasicBlock block : func.getBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                GeneralInduceVariableAnalysis(inst, res);
            }
        }
//        for (Loop loop : func.loopInfo.TopLevelLoops) {
//            runLoop(loop, res, corInfo);
//        }

        return res;
    }

    public static void runLoop(Loop loop, SCEVinfo res, CoRInfo corInfo) {
        for (Loop child : loop.children) {
            runLoop(child, res, corInfo);
        }
        for (Instruction inst : loop.header.getInstructions()) {
            if (inst instanceof Instruction.Phi phiInst) {
                if (phiInst.getIncomingValueSize() == 2) {
                    BasicInduceVariableAnalysis(phiInst, res, loop);
//                    BasicInduceVariableAnalysisPlus(phiInst, corInfo, loop);
                }
            } else {
                GeneralInduceVariableAnalysis(inst, res);
            }
        }
    }

    /**
     * 基础归纳变量分析
     *
     * @param aimPhi phi指令 应该位于 header处
     * @param res    结果
     * @param loop   循环
     */
    private static void BasicInduceVariableAnalysis(Instruction.Phi aimPhi, SCEVinfo res, Loop loop) {
        if (visited.contains(aimPhi)) return;
        visited.add(aimPhi);
        Value initial = getInitial(aimPhi, loop);
        Value next = getNext(aimPhi, loop);

        if (next instanceof Constant) {
//            System.out.println("Invariant");
            return;
        }
        if (!(next instanceof Instruction nextInst)) {
            return;
        }
        if (nextInst instanceof Instruction.BinaryOperation.Add addInst) {
            Value op1 = addInst.getOperand_1();
            Value op2 = addInst.getOperand_2();
            if (op1 == aimPhi || op2 == aimPhi) {
                // 获取递增量
                Value c = (op1 == aimPhi) ? op2 : op1;
                if (!(c instanceof Constant.ConstantInt))  return;
                SCEVExpr initialSCEV = res.query(initial);
                SCEVExpr incSCEV = res.query(c);
                if (initialSCEV != null && incSCEV != null) {
                    SCEVExpr scev = new SCEVExpr(SCEVExpr.SCEVType.AddRec);
                    scev.operands.add(initialSCEV);
                    scev.operands.add(incSCEV);
                    scev.loop = loop;
                    res.addSCEV(aimPhi, scev);
                }
            }
        }
    }


    private static void BasicInduceVariableAnalysisPlus(Instruction.Phi aimPhi, CoRInfo res, Loop loop) {
        if (visited_plus.contains(aimPhi)) return;
        visited_plus.add(aimPhi);
        Value initial = getInitial(aimPhi, loop);
        Value next = getNext(aimPhi, loop);

        if (next instanceof Constant) {
            return;
        }
        Instruction nextInst = (Instruction) next;
        if (nextInst instanceof Instruction.BinaryOperation.Add addInst) {
            Value op1 = addInst.getOperand_1();
            Value op2 = addInst.getOperand_2();
            if (op1 == aimPhi || op2 == aimPhi) {
                // 获取递增量
                Value c = (op1 == aimPhi) ? op2 : op1;
                CoR initialCoR = res.query(initial, loop);
                CoR incCoR = res.query(c, loop);
                if (initialCoR != null && incCoR != null) {
                    CoR cor = new CoR(CoR.CoRType.AddRec);
                    cor.operands.add(initialCoR);
                    cor.operands.add(incCoR);
                    cor.loop = loop;
                    res.addCoR(aimPhi, cor);
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
                    if (lhs.loop != null && rhs.loop != null && lhs.loop != rhs.loop) {
                        return;
                    }
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
            default -> {
            }
        }
    }

//    private static void GeneralInduceVariableAnalysisPlus(Instruction inst, CoRInfo res, Loop loop) {
//        switch (inst.getInstType()) {
//            case ADD -> {
//                Instruction.Add add = (Instruction.Add) inst;
//                CoR lhs = res.query(add.getOperand_1(), loop);
//                CoR rhs = res.query(add.getOperand_2(), loop);
//                if (lhs != null && rhs != null) {
//                    SCEVExpr scev = foldAdd(res, lhs, rhs);
//                    if (scev != null) {
//                        res.addSCEV(inst, scev);
//                    }
//                }
//            }
//            case MUL -> {
//                Instruction.Mul mul = (Instruction.Mul) inst;
//                SCEVExpr lhs = res.query(mul.getOperand_1());
//                SCEVExpr rhs = res.query(mul.getOperand_2());
//                if (lhs != null && rhs != null) {
//                    SCEVExpr scev = foldMul(res, lhs, rhs);
//                    if (scev != null) {
//                        res.addSCEV(inst, scev);
//                    }
//                }
//            }
//            default -> { }
//        }
//    }

    private static SCEVExpr foldAdd(SCEVinfo res, SCEVExpr lhs, SCEVExpr rhs) {
        if (lhs.type == SCEVExpr.SCEVType.Constant && rhs.type == SCEVExpr.SCEVType.Constant) {
            SCEVExpr scev = new SCEVExpr(SCEVExpr.SCEVType.Constant);
            scev.constant = lhs.constant + rhs.constant;
            return scev;
        }
        if (lhs.type == SCEVExpr.SCEVType.AddRec && rhs.type == SCEVExpr.SCEVType.Constant) {
            SCEVExpr base = lhs.operands.get(0);
            SCEVExpr newBase = foldAdd(res, base, rhs);
            if (newBase != null) {
                SCEVExpr scev = (SCEVExpr) lhs.clone();
                scev.operands.add(0, newBase);
                scev.operands.remove(1);
                return scev;
            }
        }
        if (lhs.type == SCEVExpr.SCEVType.AddRec && rhs.type == SCEVExpr.SCEVType.AddRec && inSameLoop(lhs, rhs)) {
            ArrayList<SCEVExpr> operands = new ArrayList<>();
            int size = Math.max(lhs.operands.size(), rhs.operands.size());
            for (int i = 0; i < size; ++i) {
                SCEVExpr l = i < lhs.operands.size() ? lhs.operands.get(i) : null;
                SCEVExpr r = i < rhs.operands.size() ? rhs.operands.get(i) : null;
                if (l != null && r != null) {
                    SCEVExpr scev = foldAdd(res, l, r);
                    if (scev != null) {
                        operands.add(scev);
                    } else {
                        return null;
                    }
                } else if (l != null) {
                    operands.add(l);
                } else if (r != null) {
                    operands.add(r);
                }
                SCEVExpr ret = new SCEVExpr(SCEVExpr.SCEVType.AddRec);
                ret.operands = operands;
                ret.loop = lhs.loop;
                return ret;
            }
        }
        return null;
    }

    private static SCEVExpr foldMul(SCEVinfo res, SCEVExpr lhs, SCEVExpr rhs) {
        if (lhs.type == SCEVExpr.SCEVType.Constant && rhs.type == SCEVExpr.SCEVType.Constant) {
            SCEVExpr scev = new SCEVExpr(SCEVExpr.SCEVType.Constant);
            scev.constant = lhs.constant * rhs.constant;
            return scev;
        }
        if (lhs.type == SCEVExpr.SCEVType.AddRec && rhs.type == SCEVExpr.SCEVType.Constant) {
            ArrayList<SCEVExpr> operands = new ArrayList<>();
            for (SCEVExpr operand : lhs.operands) {
                SCEVExpr scev = foldMul(res, operand, rhs);
                if (scev != null) {
                    operands.add(scev);
                } else {
                    return null;
                }
            }
            SCEVExpr ret = new SCEVExpr(SCEVExpr.SCEVType.AddRec);
            ret.operands = operands;
            ret.loop = lhs.loop;
            return ret;
        }
        return null;
    }

    private static Value getInitial(Instruction.Phi phiInst, Loop loop) {
        if (loop == null) {
            return phiInst.getOptionalValue(phiInst.getPreBlocks().get(0));
        }
        return phiInst.getOptionalValue(loop.getPreHeader());
    }

    private static Value getNext(Instruction.Phi phiInst, Loop loop) {
        if (loop == null) {
            return phiInst.getOptionalValue(phiInst.getPreBlocks().get(1));
        }
        return phiInst.getOptionalValue(loop.getLatch());
    }

    private static boolean inSameLoop(SCEVExpr inst1, SCEVExpr inst2) {
        return inst1.loop == inst2.loop && inst1.loop != null;
    }

}
