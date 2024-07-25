package midend.Transform;

import mir.*;
import mir.Module;

import java.util.ArrayList;


public class BitwiseOperation {

    private static final int MUL_COST = 0; // 经测试, 乘法效率已经足够高

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        for (BasicBlock block : function.getBlocks()) {
            runOnBlock(block);
        }
    }

    public static void runOnBlock(BasicBlock block) {
        for (var inst : block.getInstructionsSnap()) {
            runOnInst(inst);
        }
    }

    public static void runOnInst(Instruction inst) {
        BasicBlock parentBlock = inst.getParentBlock();
        Type type = inst.getType();
        switch (inst.getInstType()) {
            case MUL -> {
                Instruction.Mul mulInst = (Instruction.Mul) inst;
                // a * 2^n -> a << n
                Value op1 = mulInst.getOperand_1();
                Value op2 = mulInst.getOperand_2();
                if (op1 instanceof Constant) {
                    Value _temp = op1;
                    op1 = op2;
                    op2 = _temp;
                }
                if (op2 instanceof Constant.ConstantInt constant) {
                    int val = constant.getIntValue();
                    if (val == 1) {
                        inst.replaceAllUsesWith(op1);
                        inst.delete();
                        return;
                    }
                    if (val > 0 && (val & (val - 1)) == 0) {
                        Instruction.Shl shl = new Instruction.Shl(parentBlock, type, op1, Constant.ConstantInt.get(log2(val)));
                        shl.remove();
                        inst.addNext(shl);
                        inst.replaceAllUsesWith(shl);
                        inst.delete();
                        return;
                    }
                    // TODO: 负数 非2^n的情况
                    if (val > 0 && mulEval(val)) {
                        ArrayList<Instruction> recs = new ArrayList<>();
                        for (int i = 0; (1 << i) <= val; ++ i) {
                            if ((val & (1 << i)) != 0) {
                                Instruction.Shl shl = new Instruction.Shl(parentBlock, type, op1, Constant.ConstantInt.get(i));
                                recs.add(shl);
                                shl.remove();
                            }
                        }
                        Instruction.Add addInst = null;
                        inst.addNext(recs.get(0));
                        for (int i = 0; i < recs.size() - 1; ++ i) {
                            if (addInst == null) {
                                recs.get(i).addNext(recs.get(i + 1));
                                addInst = new Instruction.Add(parentBlock, type, recs.get(i), recs.get(i + 1));
                            } else {
                                addInst.addNext(recs.get(i + 1));
                                addInst = new Instruction.Add(parentBlock, type, addInst, recs.get(i + 1));
                            }
                            addInst.remove();
                            recs.get(i + 1).addNext(addInst);
                        }
                        if (addInst != null)
                            inst.replaceAllUsesWith(addInst);
                        else
                            inst.replaceAllUsesWith(recs.get(0));
                        inst.delete();
                        return;
                    }
                }
            }
            case DIV -> {
                // TODO: 需要保证 op1 非负
//                Instruction.Div divInst = (Instruction.Div) inst;
//                Value op1 = divInst.getOperand_1();
//                Value op2 = divInst.getOperand_2();
//                if (op2 instanceof Constant.ConstantInt constant) {
//                    int val = constant.getIntValue();
//                    if (val == 1) {
//                        inst.replaceAllUsesWith(op1);
//                        inst.delete();
//                        return;
//                    }
//                    // 无法确定op1范围
//                    if (val > 0 && (val & (val - 1)) == 0) {
//                        Instruction.LShr sign = new Instruction.LShr(parentBlock, type, op1, Constant.ConstantInt.get(31));
//                        sign.remove();
//                        Instruction.AShr ashr = new Instruction.AShr(parentBlock, type, op1, Constant.ConstantInt.get(log2(val)));
//                        ashr.remove();
//                        Instruction.Add add = new Instruction.Add(parentBlock, type, ashr, sign);
//                        add.remove();
//                        inst.addNext(sign);
//                        sign.addNext(ashr);
//                        ashr.addNext(add);
//                        inst.replaceAllUsesWith(add);
//                        inst.delete();
//                        return;
//                    }
//                }
            }
            case REM -> {
                return;
//                Instruction.Rem remInst = (Instruction.Rem) inst;
//                Value op1 = remInst.getOperand_1();
//                Value op2 = remInst.getOperand_2();
//                if (op2 instanceof Constant.ConstantInt constant) {
//                    int val = constant.getIntValue();
//                    if (val == 1) {
//                        inst.replaceAllUsesWith(new Constant.ConstantInt(0));
//                        inst.delete();
//                        return;
//                    }
//                    if (val > 0 && (val & (val - 1)) == 0) {
//                        Instruction.And and = new Instruction.And(parentBlock, type, op1, new Constant.ConstantInt(val - 1));
//                        and.remove();
//                        inst.addNext(and);
//                        inst.replaceAllUsesWith(and);
//                        inst.delete();
//                        return;
//                    }
//                }
            }
        }

    }


    private static boolean mulEval(int x) {
        return 2 * Integer.bitCount(x) < MUL_COST;
    }

    /**
     * 计算log2(x) 向下取整
     * @param x 正整数
     * @return log2(x)
     */
    private static int log2(int x) {
        return 31 - Integer.numberOfLeadingZeros(x);
    }

}
