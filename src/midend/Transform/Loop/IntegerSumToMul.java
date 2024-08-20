package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import midend.Analysis.result.SCEVinfo;
import mir.*;
import mir.Module;

import java.util.ArrayList;

/**
 * 循环整数求和变乘法
 */
//Note: 在4路Unroll之前跑
public class IntegerSumToMul {

    private static SCEVinfo scevInfo;
    private static int init;
    @SuppressWarnings("FieldCanBeLocal")
    private static int step;
    private static Instruction indvar;
    private static Instruction.Icmp indvar_cmp;

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        IndVars.runOnFunc(function);
        scevInfo = AnalysisManager.getSCEV(function);
        for (Loop loop : function.loopInfo.TopLevelLoops) {
            runLoop(loop);
        }
    }

    private static boolean canTransform(Loop loop) {
        if (!loop.children.isEmpty()) return false;
        if (loop.getAllBlocks().size() > 2) return false;
        if (loop.tripCount > 0) return false;
        if (loop.exits.size() != 1) return false;
        // 退出条件复杂
        ArrayList<BasicBlock> _pre = loop.getExit().getPreBlocks();
        if (_pre.size() > 1 || _pre.get(0) != loop.header) return false;
        Instruction inst = loop.header.getTerminator();
        if (!(inst instanceof Instruction.Branch br)) return false;
        Value cond = br.getCond();
        if (!(cond instanceof Instruction.Icmp icmp)) return false;
        if (!(icmp.getSrc2() instanceof Constant.ConstantInt) && scevInfo.contains(icmp.getSrc2()))
            icmp.swap();
        if (icmp.getCondCode() == Instruction.Icmp.CondCode.EQ || icmp.getCondCode() == Instruction.Icmp.CondCode.NE) {
            return false;
        }
        switch (icmp.getCondCode()) {
            case EQ, NE, SGE, SGT -> {
                return false;
            }
            default -> {
            }
        }
        if (scevInfo.contains(icmp.getSrc2()))
            return false;
        if (!scevInfo.contains(icmp.getSrc1()))
            return false;
        if (!scevInfo.query(icmp.getSrc1()).isNotNegative())
            return false;
        if (!(icmp.getSrc1() instanceof Instruction))
            return false;
        indvar_cmp = icmp;
        indvar = (Instruction) icmp.getSrc1();
        init = scevInfo.query(icmp.getSrc1()).getInit();
        step = scevInfo.query(icmp.getSrc1()).getStep();
        if (step != 1) return false;
        return true;
    }

    private static void runLoop(Loop loop) {
        for (Loop child : loop.children) {
            runLoop(child);
        }
        if (!canTransform(loop)) return;
        if (loop.header.getInstructions().size() > 4) return;
        Value sum = null;
        Value initial_sum = null;
        for (Instruction inst : loop.header.getInstructions()) {
            if (inst == indvar) continue;
            if (inst instanceof Instruction.Phi phiInst) {
                if (sum == null) {
                    sum = phiInst;
                    initial_sum = phiInst.getOptionalValue(loop.getPreHeader());
                } else {
                    return;
                }
            }
        }
        BasicBlock body = loop.header.getSucBlocks().get(0) == loop.getExit() ? loop.header.getSucBlocks().get(1) : loop.header.getSucBlocks().get(0);
        if (body.getInstructions().size() > 4) return;
        Value sum_inc = null;
        Instruction sum_inc_inst = null;
        Value rem_mod = null;
        for (Instruction inst : body.getInstructions()) {
            if (inst instanceof Instruction.Add addInst) {
                if (addInst.getOperand_1() == indvar || addInst.getOperand_2() == indvar) {
                    continue;
                }
                if (addInst.getOperand_1() == sum || addInst.getOperand_2() == sum) {
                    if (sum_inc != null) return;
                    sum_inc = addInst.getOperand_1() == sum ? addInst.getOperand_2() : addInst.getOperand_1();
                    sum_inc_inst = addInst;
                    if (loop.defValue(sum_inc)) return;
                }
            } else if (inst instanceof Instruction.Rem remInst) {
                if (remInst.getOperand_1() == sum_inc_inst) {
                    if (rem_mod != null) return;
                    rem_mod = remInst.getOperand_2();
                }
            }
        }
        if (sum_inc == null || rem_mod == null) return;
//        BasicBlock cond_block = new BasicBlock(loop.header.getDescriptor() + "_2mul_cond", loop.header.getParentFunction());
        BasicBlock transformBlock = new BasicBlock(loop.header.getDescriptor() + "_2mul", loop.header.getParentFunction());
//        Instruction.Icmp newIcmp = new Instruction.Icmp(cond_block, indvar_cmp.getCondCode(), Constant.ConstantInt.get(init), indvar_cmp.getSrc2());
//        new Instruction.Branch(cond_block, newIcmp, transformBlock, loop.getExit());

//        loop.getPreHeader().getTerminator().replaceTarget(loop.header, cond_block);
        loop.getPreHeader().getTerminator().replaceTarget(loop.header, transformBlock);

        // calc
        // slt: tripCount = (limit_i - initial_i) sle: tripCount = (limit_i - initial_i + 1)
        int _tmp = init;
        if (indvar_cmp.getCondCode() == Instruction.Icmp.CondCode.SLE)
            _tmp--;
        Instruction.Sub _tripCount = new Instruction.Sub(transformBlock, indvar_cmp.getSrc2().getType(), indvar_cmp.getSrc2(), Constant.ConstantInt.get(_tmp));
        // max改进
        Instruction.Max tripCount = new Instruction.Max(transformBlock, _tripCount.getType(), _tripCount, Constant.ConstantInt.get(0));

        Instruction.Sext sext_inc = new Instruction.Sext(transformBlock, sum_inc, Type.BasicType.I64_TYPE);
        Instruction.Sext sext_Count = new Instruction.Sext(transformBlock, tripCount, Type.BasicType.I64_TYPE);
        Instruction.Mul mul = new Instruction.Mul(transformBlock, sext_inc.getType(), sext_inc, sext_Count);
        Instruction.Sext sext_initial = new Instruction.Sext(transformBlock, initial_sum, Type.BasicType.I64_TYPE);
        Instruction.Add add_final = new Instruction.Add(transformBlock, sext_initial.getType(), sext_initial, mul);
        Instruction.Sext sext_mod = new Instruction.Sext(transformBlock, rem_mod, Type.BasicType.I64_TYPE);
        Instruction.Rem rem = new Instruction.Rem(transformBlock, sext_mod.getType(), add_final, sext_mod);
        Instruction.Trunc trunc = new Instruction.Trunc(transformBlock, rem, Type.BasicType.I32_TYPE);
        new Instruction.Jump(transformBlock, loop.getExit());
        for (Instruction.Phi phi : loop.getExit().getPhiInstructions()) {
            Value val = phi.getOptionalValue(loop.header);
            if (val == sum) {
                phi.removeOptionalValue(loop.header);
                phi.addOptionalValue(transformBlock, trunc);
//                phi.addOptionalValue(cond_block, initial_sum);
            } else if (val == indvar) {
                phi.removeOptionalValue(loop.header);
                // FIXME: 可能有误
                phi.replaceOptionalValueAtWith(loop.header, tripCount);
//                phi.addOptionalValue(cond_block, Constant.ConstantInt.get(init));
            } else {
//                phi.addOptionalValue(cond_block, val);
                phi.addOptionalValue(transformBlock, val);
                phi.removeOptionalValue(loop.header);
            }
        }
    }
}