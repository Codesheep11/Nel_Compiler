package midend.Transform;

import manager.Manager;
import mir.Module;
import mir.*;

import java.util.*;

public class ArithReduce {

    private static final ArrayList<Instruction> reducedList = new ArrayList<>();
    private static final ArrayList<Instruction> delList = new ArrayList<>();

    private static ArrayList<Instruction> snap;
    private static int idx;

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
//            Print.outputLLVM(function,"debug.ll");
            ArrayList<BasicBlock> blocks = function.getDomTreeLayerSort();
            blocks.forEach(ArithReduce::runOnBlock);
        }
    }

    private static void runOnBlock(BasicBlock block) {
        reducedList.clear();
        delList.clear();
        snap = block.getInstructionsSnap();
        idx = 0;
        while (idx < snap.size()) {
            Instruction instruction = snap.get(idx);
            if (instruction instanceof Instruction.BinaryOperation) {
//                System.out.println("runOnBinaryInst:" + instruction);
                runOnBinaryInst(instruction);
            }
            else reducedList.add(instruction);
            idx++;
        }
        for (Instruction inst : delList) inst.delete();
        block.getInstructions().clear();
        for (Instruction inst : reducedList) {
//            if (inst.getUsers().size() == 0 && inst.isNoSideEffect()) continue;
//            System.out.println("add:" + inst);
            block.addInstLast(inst);
        }
//        System.out.println();
    }

    private static void runOnBinaryInst(Instruction inst) {
        //默认指令已经参与常量折叠，二元操作数只可能有一个常量
        //把常数放到操作数1
        if (inst.isAssociative()) {
            Instruction.BinaryOperation inst1 = (Instruction.BinaryOperation) inst;
            if (inst1.getOperand_2() instanceof Constant) inst1.swap();
        }
        switch (inst.getInstType()) {
            case ADD -> reduceAdd((Instruction.Add) inst);
            case SUB -> reduceSub((Instruction.Sub) inst);
            case MUL -> reduceMul((Instruction.Mul) inst);
            case DIV -> reduceDiv((Instruction.Div) inst);
            case REM -> reduceRem((Instruction.Rem) inst);
            case MIN -> reduceMin((Instruction.Min) inst);
            case MAX -> reduceMax((Instruction.Max) inst);
//            case FADD -> reduceFADD((Instruction.FAdd) inst);
//            case FSUB -> reduceFSUB((Instruction.FSub) inst);
//            case FMUL -> reduceFMUL((Instruction.FMul) inst);
//            case FDIV -> reduceFDIV((Instruction.FDiv) inst);
            default -> reducedList.add(inst);
        }
    }

    private static void reduceAdd(Instruction.Add inst) {
        if (inst.getOperand_1() instanceof Constant) {
            //0 + v -> v
            if (((Constant) inst.getOperand_1()).isZero()) {
                inst.replaceAllUsesWith(inst.getOperand_2());
                delList.add(inst);
                return;
            }
            //c2 + (c1 + x) + -> (c1 + c2) + x
            if (inst.getOperand_2() instanceof Instruction.Add add) {
                if (add.getOperand_1() instanceof Constant) {
                    Instruction.Add newAdd = new Instruction.Add(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(((int) ((Constant) inst.getOperand_1()).getConstValue()) + ((int) (((Constant) add.getOperand_1()).getConstValue()))), add.getOperand_2());
                    inst.replaceAllUsesWith(newAdd);
                    delList.add(inst);
                    snap.add(idx + 1, newAdd);
                    return;
                }
            }
            if (inst.getOperand_2() instanceof Instruction.Sub sub) {
                //c2 + (c1 - x) -> (c2 + c1) - x
                if (sub.getOperand_1() instanceof Constant) {
                    Instruction.Sub newSub = new Instruction.Sub(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(((int) ((Constant) inst.getOperand_1()).getConstValue()) + ((int) (((Constant) sub.getOperand_1()).getConstValue()))),
                            sub.getOperand_2());
//                    newSub.remove();
                    inst.replaceAllUsesWith(newSub);
                    delList.add(inst);
                    snap.add(idx + 1, newSub);
                    return;
                }
                //c2 + (x - c1) -> (c2 - c1) + x
                if (sub.getOperand_2() instanceof Constant) {
                    Instruction.Add newAdd = new Instruction.Add(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(((int) ((Constant) inst.getOperand_1()).getConstValue()) - ((int) (((Constant) sub.getOperand_2()).getConstValue()))),
                            sub.getOperand_1());
//                    newAdd.remove();
                    inst.replaceAllUsesWith(newAdd);
                    delList.add(inst);
                    snap.add(idx + 1, newAdd);
                    return;
                }
            }
        }
        // a + (0 - b) -> a - b
        if (inst.getOperand_2() instanceof Instruction.Sub sub) {
            if (sub.getOperand_1() instanceof Constant && ((Constant) sub.getOperand_1()).isZero()) {
                Instruction.Sub newSub = new Instruction.Sub(inst.getParentBlock(), inst.getType(),
                        inst.getOperand_1(), sub.getOperand_2());
//                newSub.remove();
                inst.replaceAllUsesWith(newSub);
                delList.add(inst);
                snap.add(idx + 1, newSub);
                return;
            }
        }
        /*
         a * b + a -> (1 + b) * a
         b * a + a -> (1 + b) * a
         */
        if (inst.getOperand_1() instanceof Instruction.Mul mul1) {
            if (mul1.getUsers().size() == 1) {
                if (mul1.getOperand_1().equals(inst.getOperand_2())) {
                    Instruction.Add add = new Instruction.Add(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(1), mul1.getOperand_2());
                    Instruction.Mul newMul = new Instruction.Mul(inst.getParentBlock(), inst.getType(),
                            add, inst.getOperand_2());
                    inst.replaceAllUsesWith(newMul);
                    delList.add(inst);
                    snap.add(idx + 1, add);
                    snap.add(idx + 2, newMul);
                    return;
                }
                else if (mul1.getOperand_2().equals(inst.getOperand_2())) {
                    Instruction.Add add = new Instruction.Add(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(1), mul1.getOperand_1());
                    Instruction.Mul newMul = new Instruction.Mul(inst.getParentBlock(), inst.getType(),
                            add, inst.getOperand_2());
                    inst.replaceAllUsesWith(newMul);
                    delList.add(inst);
                    snap.add(idx + 1, add);
                    snap.add(idx + 2, newMul);
                    return;
                }
            }


        }
        /*
         a + a * b -> (1 + b) * a
         a + b * a -> (1 + b) * a
         */
        if (inst.getOperand_2() instanceof Instruction.Mul mul2) {
            if (mul2.getUsers().size() == 1) {
                if (mul2.getOperand_1().equals(inst.getOperand_1())) {
                    Instruction.Add add = new Instruction.Add(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(1), mul2.getOperand_2());
                    Instruction.Mul newMul = new Instruction.Mul(inst.getParentBlock(), inst.getType(),
                            add, inst.getOperand_1());
                    inst.replaceAllUsesWith(newMul);
                    delList.add(inst);
                    snap.add(idx + 1, add);
                    snap.add(idx + 2, newMul);
                    return;
                }
                else if (mul2.getOperand_2().equals(inst.getOperand_1())) {
                    Instruction.Add add = new Instruction.Add(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(1), mul2.getOperand_1());
                    Instruction.Mul newMul = new Instruction.Mul(inst.getParentBlock(), inst.getType(),
                            add, inst.getOperand_1());
                    inst.replaceAllUsesWith(newMul);
                    delList.add(inst);
                    snap.add(idx + 1, add);
                    snap.add(idx + 2, newMul);
                    return;
                }
            }
        }
        /*
         b * a + c * a -> (b + c) * a
         a * b + c * a -> (b + c) * a
         a * b + a * c -> (b + c) * a
         b * a + a * c -> (b + c) * a
         */
        if (inst.getOperand_1() instanceof Instruction.Mul mul1 && inst.getOperand_2() instanceof Instruction.Mul mul2) {
            if (mul1.getUsers().size() == 1 || mul2.getUsers().size() == 1) {
                Value a = null, b = null, c = null;
                if (mul1.getOperand_1().equals(mul2.getOperand_1())) {
                    a = mul1.getOperand_1();
                    b = mul1.getOperand_2();
                    c = mul2.getOperand_2();
                }
                else if (mul1.getOperand_1().equals(mul2.getOperand_2())) {
                    a = mul1.getOperand_1();
                    b = mul1.getOperand_2();
                    c = mul2.getOperand_1();
                }
                else if (mul1.getOperand_2().equals(mul2.getOperand_1())) {
                    a = mul1.getOperand_2();
                    b = mul1.getOperand_1();
                    c = mul2.getOperand_2();
                }
                else if (mul1.getOperand_2().equals(mul2.getOperand_2())) {
                    a = mul1.getOperand_2();
                    b = mul1.getOperand_1();
                    c = mul2.getOperand_1();
                }
                if (a != null) {
                    Instruction.Add add = new Instruction.Add(inst.getParentBlock(), inst.getType(), b, c);
//                add.remove();
                    Instruction.Mul newMul = new Instruction.Mul(inst.getParentBlock(), inst.getType(), a, add);
//                newMul.remove();
                    inst.replaceAllUsesWith(newMul);
                    delList.add(inst);
                    snap.add(idx + 1, add);
                    snap.add(idx + 2, newMul);
                    return;
                }
            }
        }
        reducedList.add(inst);
    }

    private static void reduceSub(Instruction.Sub inst) {
        if (inst.getOperand_1() instanceof Constant c1) {
            if (c1.isZero() && inst.getOperand_2() instanceof Instruction.Sub sub) {
                //0 - (0 - a) -> a
                if (sub.getOperand_1() instanceof Constant c2 && c2.isZero()) {
                    inst.replaceAllUsesWith(sub.getOperand_2());
                    delList.add(inst);
                    return;
                }
                //0 - (a - b) -> (b - a)
                if (sub.getUsers().size() == 1) {
                    Instruction.Sub newSub = new Instruction.Sub(inst.getParentBlock(), inst.getType(),
                            sub.getOperand_2(), sub.getOperand_1());
                    inst.replaceAllUsesWith(newSub);
                    delList.add(inst);
                    snap.add(idx + 1, newSub);
                    return;
                }
            }
            //c1 - (c2 + x) -> (c1 - c2) - x
            if (inst.getOperand_2() instanceof Instruction.Add add) {
                if (add.getOperand_1() instanceof Constant) {
                    Constant.ConstantInt newConst = Constant.ConstantInt.get(((int) c1.getConstValue()) - ((int) ((Constant) add.getOperand_1()).getConstValue()));
                    Instruction.Sub newSub = new Instruction.Sub(inst.getParentBlock(), inst.getType(),
                            newConst, add.getOperand_2());
                    inst.replaceAllUsesWith(newSub);
                    delList.add(inst);
                    snap.add(idx + 1, newSub);
                    return;
                }
            }
        }
        if (inst.getOperand_2() instanceof Constant c2) {
            //a - 0 -> a
            if (c2.isZero()) {
                inst.replaceAllUsesWith(inst.getOperand_1());
                delList.add(inst);
                return;
            }
            if (inst.getOperand_1() instanceof Instruction.Sub sub) {
                //(x - c1) - c2 -> x + -(c1 + c2)
                if (sub.getOperand_2() instanceof Constant) {
                    Instruction.Add add = new Instruction.Add(inst.getParentBlock(), inst.getType(),
                            sub.getOperand_1(), Constant.ConstantInt.get(
                            -1 * ((int) ((Constant) sub.getOperand_2()).getConstValue()) + (int) c2.getConstValue()));
                    inst.replaceAllUsesWith(add);
                    delList.add(inst);
                    snap.add(idx + 1, add);
                    return;
                }
                //(c1 - x) - c2 -> (c1 - c2) - x
                if (sub.getOperand_1() instanceof Constant) {
                    Instruction.Sub newSub = new Instruction.Sub(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(((int) ((Constant) sub.getOperand_1()).getConstValue()) - ((int) c2.getConstValue())), sub.getOperand_2());
                    inst.replaceAllUsesWith(newSub);
                    delList.add(inst);
                    snap.add(idx + 1, newSub);
                    return;
                }
            }
            //(c1 + x) - c2 -> (c1 - c2) + x
            if (inst.getOperand_1() instanceof Instruction.Add add) {
                if (add.getOperand_1() instanceof Constant) {
                    Instruction.Add newAdd = new Instruction.Add(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(((int) ((Constant) add.getOperand_1()).getConstValue()) - ((int) c2.getConstValue())),
                            add.getOperand_2());
                    inst.replaceAllUsesWith(newAdd);
                    delList.add(inst);
                    snap.add(idx + 1, newAdd);
                    return;
                }
            }
            //a - c ->  (-c) + a
            Instruction.Add add = new Instruction.Add(inst.getParentBlock(), inst.getType(),
                    Constant.ConstantInt.get(-1 * (int) ((Constant) inst.getOperand_2()).getConstValue()), inst.getOperand_1());
//            add.remove();
            inst.replaceAllUsesWith(add);
            delList.add(inst);
            snap.add(idx + 1, add);
            return;
        }
        //a - a -> 0
        if (inst.getOperand_1().equals(inst.getOperand_2())) {
            inst.replaceAllUsesWith(Constant.ConstantInt.get(0));
            delList.add(inst);
            return;
        }
        //a - (0 - b) -> a + b
        if (inst.getOperand_2() instanceof Instruction.Sub sub) {
            if (sub.getOperand_1() instanceof Constant && ((Constant) sub.getOperand_1()).isZero()) {
                Instruction.Add add = new Instruction.Add(inst.getParentBlock(), inst.getType(), inst.getOperand_1(), sub.getOperand_2());
//                add.remove();
                inst.replaceAllUsesWith(add);
                delList.add(inst);
                snap.add(idx + 1, add);
                return;
            }
        }
        //a - (a + b) -> 0 - b
        //a - (b + a) -> 0 - b
        if (inst.getOperand_2() instanceof Instruction.Add add) {
            if (add.getOperand_1().equals(inst.getOperand_1())) {
                Instruction.Sub newSub = new Instruction.Sub(inst.getParentBlock(), inst.getType(),
                        Constant.ConstantInt.get(0), add.getOperand_2());
//                newSub.remove();
                inst.replaceAllUsesWith(newSub);
                delList.add(inst);
                snap.add(idx + 1, newSub);
                return;
            }
            if (add.getOperand_2().equals(inst.getOperand_1())) {
                Instruction.Sub newSub = new Instruction.Sub(inst.getParentBlock(), inst.getType(),
                        Constant.ConstantInt.get(0), add.getOperand_1());
//                newSub.remove();
                inst.replaceAllUsesWith(newSub);
                delList.add(inst);
                snap.add(idx + 1, newSub);
                return;
            }
        }
        /*
         b * a - c * a -> (b - c) * a
         a * b - c * a -> (b - c) * a
         a * b - a * c -> (b - c) * a
         b * a - a * c -> (b - c) * a
         */
        if (inst.getOperand_1() instanceof Instruction.Mul mul1 && inst.getOperand_2() instanceof Instruction.Mul mul2) {
            if (mul1.getUsers().size() == 1 || mul2.getUsers().size() == 1) {
                Value a = null, b = null, c = null;
                if (mul1.getOperand_1().equals(mul2.getOperand_1())) {
                    a = mul1.getOperand_1();
                    b = mul1.getOperand_2();
                    c = mul2.getOperand_2();
                }
                else if (mul1.getOperand_1().equals(mul2.getOperand_2())) {
                    a = mul1.getOperand_1();
                    b = mul1.getOperand_2();
                    c = mul2.getOperand_1();
                }
                else if (mul1.getOperand_2().equals(mul2.getOperand_1())) {
                    a = mul1.getOperand_2();
                    b = mul1.getOperand_1();
                    c = mul2.getOperand_2();
                }
                else if (mul1.getOperand_2().equals(mul2.getOperand_2())) {
                    a = mul1.getOperand_2();
                    b = mul1.getOperand_1();
                    c = mul2.getOperand_1();
                }
                if (a != null) {
                    Instruction.Sub sub = new Instruction.Sub(inst.getParentBlock(), inst.getType(), b, c);
//                sub.remove();
                    Instruction.Mul newMul = new Instruction.Mul(inst.getParentBlock(), inst.getType(), a, sub);
//                newMul.remove();
                    inst.replaceAllUsesWith(newMul);
                    delList.add(inst);
                    snap.add(idx + 1, sub);
                    snap.add(idx + 2, newMul);
                    return;
                }
            }
        }
        reducedList.add(inst);
    }

    private static void reduceMul(Instruction.Mul inst) {
        if (inst.getOperand_1() instanceof Constant c) {
            // 0 * v -> 0
            if (c.isZero()) {
                inst.replaceAllUsesWith(Constant.ConstantInt.get(0));
                delList.add(inst);
                return;
            }
            // 1 * v -> v
            if (c.getConstValue().equals(1)) {
                inst.replaceAllUsesWith(inst.getOperand_2());
                delList.add(inst);
                return;
            }
            // -1 * v -> 0 - v
            if (c.getConstValue().equals(1)) {
                Instruction.Sub sub = new Instruction.Sub(inst.getParentBlock(), inst.getType(),
                        Constant.ConstantInt.get(0), inst.getOperand_2());
//                sub.remove();
                inst.replaceAllUsesWith(sub);
                delList.add(inst);
                snap.add(idx + 1, sub);
                return;
            }
            //c * (0 - x) -> -c * x
            if (inst.getOperand_2() instanceof Instruction.Sub sub) {
                if (sub.getOperand_1() instanceof Constant c1 && c1.isZero()) {
                    Instruction.Mul newMul = new Instruction.Mul(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(-1 * (int) c.getConstValue()), sub.getOperand_2());
                    inst.replaceAllUsesWith(newMul);
                    delList.add(inst);
                    snap.add(idx + 1, newMul);
                    return;
                }
            }
        }
        reducedList.add(inst);
    }

    private static void reduceDiv(Instruction.Div inst) {
        if (inst.getOperand_1() instanceof Constant constant) {
            // 0 / v -> 0
            if (constant.isZero()) {
                inst.replaceAllUsesWith(Constant.ConstantInt.get(0));
                delList.add(inst);
                return;
            }
        }
        if (inst.getOperand_2() instanceof Constant constant) {
            // v / 1 -> v
            if (constant.getConstValue().equals(1)) {
                inst.replaceAllUsesWith(inst.getOperand_1());
                delList.add(inst);
                return;
            }
            // v / -1 -> 0 - v
            if (constant.getConstValue().equals(-1)) {
                Instruction.Sub sub = new Instruction.Sub(inst.getParentBlock(), inst.getType(),
                        Constant.ConstantInt.get(0), inst.getOperand_1());
//                sub.remove();
                inst.replaceAllUsesWith(sub);
                delList.add(inst);
                snap.add(idx + 1, sub);
                return;
            }
            // (c1 * x) / c2 -> (c1 / c2) * x if c1 % c2 == 0, 确保 c1 * x 只有一个作用点
            if (inst.getOperand_1() instanceof Instruction.Mul mul) {
                if (mul.getUsers().size() == 1) {
                    if (mul.getOperand_1() instanceof Constant c1) {
                        if (((int) c1.getConstValue()) % ((int) constant.getConstValue()) == 0) {
                            Constant.ConstantInt newConst = Constant.ConstantInt.get((int) c1.getConstValue() / (int) constant.getConstValue());
                            Instruction.Mul newMul = new Instruction.Mul(inst.getParentBlock(), inst.getType(),
                                    newConst, mul.getOperand_2());
                            inst.replaceAllUsesWith(newMul);
                            delList.add(inst);
                            snap.add(idx + 1, newMul);
                            return;
                        }
                    }
                }
            }
            // (0 - x) / c -> x / -c
            if (inst.getOperand_1() instanceof Instruction.Sub sub) {
                if (sub.getOperand_1() instanceof Constant c && c.isZero()) {
                    Instruction.Div newDiv = new Instruction.Div(inst.getParentBlock(), inst.getType(),
                            sub.getOperand_2(), Constant.ConstantInt.get(-1 * (int) constant.getConstValue()));
                    inst.replaceAllUsesWith(newDiv);
                    delList.add(inst);
                    snap.add(idx + 1, newDiv);
                    return;
                }
            }
        }
        //v / v -> 1
        if (inst.getOperand_1().equals(inst.getOperand_2())) {
            inst.replaceAllUsesWith(Constant.ConstantInt.get(1));
            delList.add(inst);
            return;
        }
        //v / (0 - v) -> -1
        if (inst.getOperand_2() instanceof Instruction.Sub sub) {
            if (sub.getOperand_1() instanceof Constant c && c.isZero() && sub.getOperand_2().equals(inst.getOperand_1())) {
                inst.replaceAllUsesWith(Constant.ConstantInt.get(-1));
                delList.add(inst);
                return;
            }
        }
        if (inst.getOperand_1() instanceof Instruction.Sub sub) {
            if (sub.getOperand_1() instanceof Constant c && c.isZero()) {
                //( 0 - v ) / v -> -1
                if (sub.getOperand_2().equals(inst.getOperand_2())) {
                    inst.replaceAllUsesWith(Constant.ConstantInt.get(-1));
                    delList.add(inst);
                    return;
                }
                //( 0 - v ) / c -> v / -c
                if (inst.getOperand_2() instanceof Constant constant) {
                    Instruction.Div newDiv = new Instruction.Div(inst.getParentBlock(), inst.getType(),
                            sub.getOperand_2(), Constant.ConstantInt.get(-1 * (int) constant.getConstValue()));
                    inst.replaceAllUsesWith(newDiv);
                    delList.add(inst);
                    snap.add(idx + 1, newDiv);
                    return;
                }
            }
        }
        //v / (v * a) or (a * v) -> 1 / a 确保 v * a 只有一个作用点
        if (inst.getOperand_2() instanceof Instruction.Mul mul) {
            if (mul.getUsers().size() == 1) {
                if (mul.getOperand_1().equals(inst.getOperand_1())) {
                    Instruction.Div newDiv = new Instruction.Div(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(1), mul.getOperand_2());
                    inst.replaceAllUsesWith(newDiv);
                    delList.add(inst);
                    snap.add(idx + 1, newDiv);
                    return;
                }
                else if (mul.getOperand_2().equals(inst.getOperand_1())) {
                    Instruction.Div newDiv = new Instruction.Div(inst.getParentBlock(), inst.getType(),
                            Constant.ConstantInt.get(1), mul.getOperand_1());
                    inst.replaceAllUsesWith(newDiv);
                    delList.add(inst);
                    snap.add(idx + 1, newDiv);
                    return;
                }
            }
        }
        //v / a / b -> v / (a * b) 确保 a / b 只有一个作用点
        if (inst.getOperand_1() instanceof Instruction.Div div) {
            if (div.getUsers().size() == 1) {
                Instruction.Mul mul = new Instruction.Mul(inst.getParentBlock(), inst.getType(),
                        div.getOperand_2(), inst.getOperand_2());
                Instruction.Div newDiv = new Instruction.Div(inst.getParentBlock(), inst.getType(),
                        div.getOperand_1(), mul);
                inst.replaceAllUsesWith(newDiv);
                delList.add(inst);
                snap.add(idx + 1, mul);
                snap.add(idx + 2, newDiv);
                return;
            }
        }
        reducedList.add(inst);
    }

    private static void reduceRem(Instruction.Rem inst) {
        if (inst.getOperand_1() instanceof Constant constant) {
            // 0 % v -> 0
            if (constant.isZero()) {
                inst.replaceAllUsesWith(Constant.ConstantInt.get(0));
                delList.add(inst);
                return;
            }
        }
        if (inst.getOperand_2() instanceof Constant constant) {
            // v % 1 -> 0
            if (constant.getConstValue().equals(1)) {
                inst.replaceAllUsesWith(Constant.ConstantInt.get(0));
                delList.add(inst);
                return;
            }
            // v % -1 -> 0
            if (constant.getConstValue().equals(-1)) {
                inst.replaceAllUsesWith(Constant.ConstantInt.get(0));
                delList.add(inst);
                return;
            }
            // (c1 * x) % c2 -> 0 if c1 % c2 == 0
            if (inst.getOperand_1() instanceof Instruction.Mul mul) {
                if (mul.getOperand_1() instanceof Constant c1) {
                    if (((int) c1.getConstValue()) % ((int) constant.getConstValue()) == 0) {
                        inst.replaceAllUsesWith(Constant.ConstantInt.get(0));
                        delList.add(inst);
                        return;
                    }
                }
            }
        }
        // v % v -> 0
        if (inst.getOperand_1().equals(inst.getOperand_2())) {
            inst.replaceAllUsesWith(Constant.ConstantInt.get(0));
            delList.add(inst);
            return;
        }
        reducedList.add(inst);
    }

    private static void reduceMin(Instruction.Min inst) {
        // min(a, a) -> a
        if (inst.getOperand_1().equals(inst.getOperand_2())) {
            inst.replaceAllUsesWith(inst.getOperand_1());
            delList.add(inst);
            return;
        }
        //min(min(a, b), c) -> min(a,b) if a==c or b==c
        if (inst.getOperand_1() instanceof Instruction.Min min1) {
            if (min1.getOperand_1().equals(inst.getOperand_2()) || min1.getOperand_2().equals(inst.getOperand_2())) {
                inst.replaceAllUsesWith(min1);
                delList.add(inst);
                return;
            }
        }
        //min(a, min(b, c)) -> min(b, c) if a==c or a==b
        if (inst.getOperand_2() instanceof Instruction.Min min2) {
            if (min2.getOperand_1().equals(inst.getOperand_1()) || min2.getOperand_2().equals(inst.getOperand_1())) {
                inst.replaceAllUsesWith(min2);
                delList.add(inst);
                return;
            }
        }
        //min(max(a, b), c) -> c if a==c or b==c
        if (inst.getOperand_1() instanceof Instruction.Max max1) {
            if (max1.getOperand_1().equals(inst.getOperand_2()) || max1.getOperand_2().equals(inst.getOperand_2())) {
                inst.replaceAllUsesWith(inst.getOperand_2());
                delList.add(inst);
                return;
            }
        }
        //min(a, max(b, c)) -> a if a==b or a==c
        if (inst.getOperand_2() instanceof Instruction.Max max2) {
            if (max2.getOperand_1().equals(inst.getOperand_1()) || max2.getOperand_2().equals(inst.getOperand_1())) {
                inst.replaceAllUsesWith(inst.getOperand_1());
                delList.add(inst);
                return;
            }
        }
        reducedList.add(inst);
    }

    private static void reduceMax(Instruction.Max inst) {
        // max(a, a) -> a
        if (inst.getOperand_1().equals(inst.getOperand_2())) {
            inst.replaceAllUsesWith(inst.getOperand_1());
            delList.add(inst);
            return;
        }
        //max(max(a, b), c) -> max(a,b) if a==c or b==c
        if (inst.getOperand_1() instanceof Instruction.Max max1) {
            if (max1.getOperand_1().equals(inst.getOperand_2()) || max1.getOperand_2().equals(inst.getOperand_2())) {
                inst.replaceAllUsesWith(max1);
                delList.add(inst);
                return;
            }
        }
        //max(a, max(b, c)) -> max(b, c) if a==c or a==b
        if (inst.getOperand_2() instanceof Instruction.Max max2) {
            if (max2.getOperand_1().equals(inst.getOperand_1()) || max2.getOperand_2().equals(inst.getOperand_1())) {
                inst.replaceAllUsesWith(max2);
                delList.add(inst);
                return;
            }
        }
        //max(min(a, b), c) -> c if a==c or b==c
        if (inst.getOperand_1() instanceof Instruction.Min min1) {
            if (min1.getOperand_1().equals(inst.getOperand_2()) || min1.getOperand_2().equals(inst.getOperand_2())) {
                inst.replaceAllUsesWith(inst.getOperand_2());
                delList.add(inst);
                return;
            }
        }
        //max(a, min(b, c)) -> a if a==b or a==c
        if (inst.getOperand_2() instanceof Instruction.Min min2) {
            if (min2.getOperand_1().equals(inst.getOperand_1()) || min2.getOperand_2().equals(inst.getOperand_1())) {
                inst.replaceAllUsesWith(inst.getOperand_1());
                delList.add(inst);
                return;
            }
        }
        reducedList.add(inst);
    }

    private static void reduceFADD(Instruction.FAdd inst) {
        if (!Manager.isO1) {
            reducedList.add(inst);
            return;
        }
        if (inst.getOperand_1() instanceof Constant) {
            //0 + v -> v
            if (((Constant) inst.getOperand_1()).isZero()) {
                inst.replaceAllUsesWith(inst.getOperand_2());
                delList.add(inst);
                return;
            }
            //c2 + (c1 + x) + -> (c1 + c2) + x
            if (inst.getOperand_2() instanceof Instruction.FAdd fadd) {
                if (fadd.getOperand_1() instanceof Constant) {
                    Instruction.FAdd newFAdd = new Instruction.FAdd(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(((float) ((Constant) inst.getOperand_1()).getConstValue())
                                    + ((float) ((Constant) fadd.getOperand_1()).getConstValue())), fadd.getOperand_2());
                    inst.replaceAllUsesWith(newFAdd);
                    delList.add(inst);
                    snap.add(idx + 1, newFAdd);
                    return;
                }
            }
            if (inst.getOperand_2() instanceof Instruction.FSub fsub) {
                //c2 + (c1 - x) -> (c2 + c1) - x
                if (fsub.getOperand_1() instanceof Constant) {
                    Instruction.FSub newFSub = new Instruction.FSub(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(((float) ((Constant) inst.getOperand_1()).getConstValue())
                                    + ((float) ((Constant) fsub.getOperand_1()).getConstValue())), fsub.getOperand_2());
//                    newSub.remove();
                    inst.replaceAllUsesWith(newFSub);
                    delList.add(inst);
                    snap.add(idx + 1, newFSub);
                    return;
                }
                //c2 + (x - c1) -> (c2 - c1) + x
                if (fsub.getOperand_2() instanceof Constant) {
                    Instruction.FAdd newFAdd = new Instruction.FAdd(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(((float) ((Constant) inst.getOperand_1()).getConstValue())
                                    - ((float) ((Constant) fsub.getOperand_2()).getConstValue())), fsub.getOperand_1());
//                    newAdd.remove();
                    inst.replaceAllUsesWith(newFAdd);
                    delList.add(inst);
                    snap.add(idx + 1, newFAdd);
                    return;
                }
            }
        }
        // a + (0 - b) -> a - b
        if (inst.getOperand_2() instanceof Instruction.FSub fsub) {
            if (fsub.getOperand_1() instanceof Constant && ((Constant) fsub.getOperand_1()).isZero()) {
                Instruction.FSub newFSub = new Instruction.FSub(inst.getParentBlock(), inst.getType(),
                        inst.getOperand_1(), fsub.getOperand_2());
//                newSub.remove();
                inst.replaceAllUsesWith(newFSub);
                delList.add(inst);
                snap.add(idx + 1, newFSub);
                return;
            }
        }
        /*
         a * b + a -> (1 + b) * a
         b * a + a -> (1 + b) * a
         */
        if (inst.getOperand_1() instanceof Instruction.FMul fmul1) {
            if (fmul1.getUsers().size() == 1) {
                if (fmul1.getOperand_1().equals(inst.getOperand_2())) {
                    Instruction.FAdd fadd = new Instruction.FAdd(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(1), fmul1.getOperand_2());
                    Instruction.FMul newFMul = new Instruction.FMul(inst.getParentBlock(), inst.getType(),
                            fadd, inst.getOperand_2());
                    inst.replaceAllUsesWith(newFMul);
                    delList.add(inst);
                    snap.add(idx + 1, fadd);
                    snap.add(idx + 2, newFMul);
                    return;
                }
                else if (fmul1.getOperand_2().equals(inst.getOperand_2())) {
                    Instruction.FAdd fadd = new Instruction.FAdd(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(1), fmul1.getOperand_1());
                    Instruction.FMul newFMul = new Instruction.FMul(inst.getParentBlock(), inst.getType(),
                            fadd, inst.getOperand_2());
                    inst.replaceAllUsesWith(newFMul);
                    delList.add(inst);
                    snap.add(idx + 1, fadd);
                    snap.add(idx + 2, newFMul);
                    return;
                }
            }
        }
        /*
         a + a * b -> (1 + b) * a
         a + b * a -> (1 + b) * a
         */
        if (inst.getOperand_2() instanceof Instruction.FMul fmul2) {
            if (fmul2.getUsers().size() == 1) {
                if (fmul2.getOperand_1().equals(inst.getOperand_1())) {
                    Instruction.FAdd fadd = new Instruction.FAdd(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(1), fmul2.getOperand_2());
                    Instruction.FMul newFMul = new Instruction.FMul(inst.getParentBlock(), inst.getType(),
                            fadd, inst.getOperand_1());
                    inst.replaceAllUsesWith(newFMul);
                    delList.add(inst);
                    snap.add(idx + 1, fadd);
                    snap.add(idx + 2, newFMul);
                    return;
                }
                else if (fmul2.getOperand_2().equals(inst.getOperand_1())) {
                    Instruction.FAdd fadd = new Instruction.FAdd(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(1), fmul2.getOperand_1());
                    Instruction.FMul newFMul = new Instruction.FMul(inst.getParentBlock(), inst.getType(),
                            fadd, inst.getOperand_1());
                    inst.replaceAllUsesWith(newFMul);
                    delList.add(inst);
                    snap.add(idx + 1, fadd);
                    snap.add(idx + 2, newFMul);
                    return;
                }
            }
        }
        /*
         b * a + c * a -> (b + c) * a
         a * b + c * a -> (b + c) * a
         a * b + a * c -> (b + c) * a
         b * a + a * c -> (b + c) * a
         */
        if (inst.getOperand_1() instanceof Instruction.FMul fmul1 && inst.getOperand_2() instanceof Instruction.FMul fmul2) {
            if (fmul1.getUsers().size() == 1 || fmul2.getUsers().size() == 1) {
                Value a = null, b = null, c = null;
                if (fmul1.getOperand_1().equals(fmul2.getOperand_1())) {
                    a = fmul1.getOperand_1();
                    b = fmul1.getOperand_2();
                    c = fmul2.getOperand_2();
                }
                else if (fmul1.getOperand_1().equals(fmul2.getOperand_2())) {
                    a = fmul1.getOperand_1();
                    b = fmul1.getOperand_2();
                    c = fmul2.getOperand_1();
                }
                else if (fmul1.getOperand_2().equals(fmul2.getOperand_1())) {
                    a = fmul1.getOperand_2();
                    b = fmul1.getOperand_1();
                    c = fmul2.getOperand_2();
                }
                else if (fmul1.getOperand_2().equals(fmul2.getOperand_2())) {
                    a = fmul1.getOperand_2();
                    b = fmul1.getOperand_1();
                    c = fmul2.getOperand_1();
                }
                if (a != null) {
                    Instruction.FAdd fadd = new Instruction.FAdd(inst.getParentBlock(), inst.getType(), b, c);
//                add.remove();
                    Instruction.FMul newFMul = new Instruction.FMul(inst.getParentBlock(), inst.getType(), a, fadd);
//                newMul.remove();
                    inst.replaceAllUsesWith(newFMul);
                    delList.add(inst);
                    snap.add(idx + 1, fadd);
                    snap.add(idx + 2, newFMul);
                    return;
                }
            }
        }
        reducedList.add(inst);
    }

    private static void reduceFSUB(Instruction.FSub inst) {
        if (!Manager.isO1) {
            reducedList.add(inst);
            return;
        }
        if (inst.getOperand_1() instanceof Constant c1) {
            //0 - (0 - a) -> a
            if (c1.isZero() && inst.getOperand_2() instanceof Instruction.FSub fsub) {
                if (fsub.getOperand_1() instanceof Constant c2 && c2.isZero()) {
                    inst.replaceAllUsesWith(fsub.getOperand_2());
                    delList.add(inst);
                    return;
                }
            }
            //c1 - (c2 + x) -> (c1 - c2) - x
            if (inst.getOperand_2() instanceof Instruction.FAdd fadd) {
                if (fadd.getOperand_1() instanceof Constant) {
                    Constant.ConstantFloat newConst = new Constant.ConstantFloat(((float) c1.getConstValue()) - ((float) ((Constant) fadd.getOperand_1()).getConstValue()));
                    Instruction.FSub newFSub = new Instruction.FSub(inst.getParentBlock(), inst.getType(),
                            newConst, fadd.getOperand_2());
                    inst.replaceAllUsesWith(newFSub);
                    delList.add(inst);
                    snap.add(idx + 1, newFSub);
                    return;
                }
            }
        }
        if (inst.getOperand_2() instanceof Constant c2) {
            //a - 0 -> a
            if (c2.isZero()) {
                inst.replaceAllUsesWith(inst.getOperand_1());
                delList.add(inst);
                return;
            }
            if (inst.getOperand_1() instanceof Instruction.FSub fsub) {
                //(x - c1) - c2 -> x + -(c1 + c2)
                if (fsub.getOperand_2() instanceof Constant) {
                    Instruction.FAdd fadd = new Instruction.FAdd(inst.getParentBlock(), inst.getType(),
                            fsub.getOperand_1(), new Constant.ConstantFloat(
                            -1 * ((float) ((Constant) fsub.getOperand_2()).getConstValue()) + (float) c2.getConstValue()));
                    inst.replaceAllUsesWith(fadd);
                    delList.add(inst);
                    snap.add(idx + 1, fadd);
                    return;
                }
                //(c1 - x) - c2 -> (c1 - c2) - x
                if (fsub.getOperand_1() instanceof Constant) {
                    Instruction.FSub newFSub = new Instruction.FSub(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(((float) ((Constant) fsub.getOperand_1()).getConstValue()) - ((float) c2.getConstValue())), fsub.getOperand_2());
                    inst.replaceAllUsesWith(newFSub);
                    delList.add(inst);
                    snap.add(idx + 1, newFSub);
                    return;
                }
            }
            //(c1 + x) - c2 -> (c1 - c2) + x
            if (inst.getOperand_1() instanceof Instruction.FAdd fadd) {
                if (fadd.getOperand_1() instanceof Constant) {
                    Instruction.FAdd newFAdd = new Instruction.FAdd(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(((float) ((Constant) fadd.getOperand_1()).getConstValue()) - ((float) c2.getConstValue())),
                            fadd.getOperand_2());
                    inst.replaceAllUsesWith(newFAdd);
                    delList.add(inst);
                    snap.add(idx + 1, newFAdd);
                    return;
                }
            }
//a - c ->  (-c) + a
            Instruction.FAdd fadd = new Instruction.FAdd(inst.getParentBlock(), inst.getType(),
                    new Constant.ConstantFloat(-1 * (float) ((Constant) inst.getOperand_2()).getConstValue()), inst.getOperand_1());
//            add.remove();
            inst.replaceAllUsesWith(fadd);
            delList.add(inst);
            snap.add(idx + 1, fadd);
            return;
        }
        //a - a -> 0
        if (inst.getOperand_1().equals(inst.getOperand_2())) {
            inst.replaceAllUsesWith(new Constant.ConstantFloat(0));
            delList.add(inst);
            return;
        }
        //a - (0 - b) -> a + b
        if (inst.getOperand_2() instanceof Instruction.FSub fsub) {
            if (fsub.getOperand_1() instanceof Constant c && c.isZero()) {
                Instruction.FAdd fadd = new Instruction.FAdd(inst.getParentBlock(), inst.getType(), inst.getOperand_1(), fsub.getOperand_2());
//                add.remove();
                inst.replaceAllUsesWith(fadd);
                delList.add(inst);
                snap.add(idx + 1, fadd);
                return;
            }
        }
        //a - (a + b) -> 0 - b
        //a - (b + a) -> 0 - b
        if (inst.getOperand_2() instanceof Instruction.FAdd fadd) {
            if (fadd.getOperand_1().equals(inst.getOperand_1())) {
                Instruction.FSub newFSub = new Instruction.FSub(inst.getParentBlock(), inst.getType(),
                        new Constant.ConstantFloat(0), fadd.getOperand_2());
//                newSub.remove();
                inst.replaceAllUsesWith(newFSub);
                delList.add(inst);
                snap.add(idx + 1, newFSub);
                return;
            }
            if (fadd.getOperand_2().equals(inst.getOperand_1())) {
                Instruction.FSub newFSub = new Instruction.FSub(inst.getParentBlock(), inst.getType(),
                        new Constant.ConstantFloat(0), fadd.getOperand_1());
//                newSub.remove();
                inst.replaceAllUsesWith(newFSub);
                delList.add(inst);
                snap.add(idx + 1, newFSub);
                return;
            }
        }
        /*
         b * a - c * a -> (b - c) * a
         a * b - c * a -> (b - c) * a
         a * b - a * c -> (b - c) * a
         b * a - a * c -> (b - c) * a
         */
        if (inst.getOperand_1() instanceof Instruction.FMul fmul1 && inst.getOperand_2() instanceof Instruction.FMul fmul2) {
            if (fmul1.getUsers().size() == 1 || fmul2.getUsers().size() == 1) {
                Value a = null, b = null, c = null;
                if (fmul1.getOperand_1().equals(fmul2.getOperand_1())) {
                    a = fmul1.getOperand_1();
                    b = fmul1.getOperand_2();
                    c = fmul2.getOperand_2();
                }
                else if (fmul1.getOperand_1().equals(fmul2.getOperand_2())) {
                    a = fmul1.getOperand_1();
                    b = fmul1.getOperand_2();
                    c = fmul2.getOperand_1();
                }
                else if (fmul1.getOperand_2().equals(fmul2.getOperand_1())) {
                    a = fmul1.getOperand_2();
                    b = fmul1.getOperand_1();
                    c = fmul2.getOperand_2();
                }
                else if (fmul1.getOperand_2().equals(fmul2.getOperand_2())) {
                    a = fmul1.getOperand_2();
                    b = fmul1.getOperand_1();
                    c = fmul2.getOperand_1();
                }
                if (a != null) {
                    Instruction.FSub fsub = new Instruction.FSub(inst.getParentBlock(), inst.getType(), b, c);
//                sub.remove();
                    Instruction.FMul newFMul = new Instruction.FMul(inst.getParentBlock(), inst.getType(), a, fsub);
//                newMul.remove();
                    inst.replaceAllUsesWith(newFMul);
                    delList.add(inst);
                    snap.add(idx + 1, fsub);
                    snap.add(idx + 2, newFMul);
                    return;
                }
            }
        }
        reducedList.add(inst);
    }

    private static void reduceFMUL(Instruction.FMul inst) {
        if (inst.getOperand_1() instanceof Constant c) {
            // 0 * v -> 0
            if (c.isZero()) {
                inst.replaceAllUsesWith(new Constant.ConstantFloat(0));
                delList.add(inst);
                return;
            }
            // 1 * v -> v
            if (c.getConstValue().equals(1)) {
                inst.replaceAllUsesWith(inst.getOperand_2());
                delList.add(inst);
                return;
            }
            // -1 * v -> 0 - v
            if (c.getConstValue().equals(-1)) {
                Instruction.FSub fsub = new Instruction.FSub(inst.getParentBlock(), inst.getType(),
                        new Constant.ConstantFloat(0), inst.getOperand_2());
//                sub.remove();
                inst.replaceAllUsesWith(fsub);
                delList.add(inst);
                snap.add(idx + 1, fsub);
                return;
            }
            //c * (0 - x) -> -c * x
            if (inst.getOperand_2() instanceof Instruction.FSub fsub) {
                if (fsub.getOperand_1() instanceof Constant c1 && c1.isZero()) {
                    Instruction.FMul newFMul = new Instruction.FMul(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(-1 * (float) c.getConstValue()), fsub.getOperand_2());
                    inst.replaceAllUsesWith(newFMul);
                    delList.add(inst);
                    snap.add(idx + 1, newFMul);
                    return;
                }
            }
        }
        reducedList.add(inst);
    }

    private static void reduceFDIV(Instruction.FDiv inst) {
        if (inst.getOperand_1() instanceof Constant constant) {
            // 0 / v -> 0
            if (constant.isZero()) {
                inst.replaceAllUsesWith(new Constant.ConstantFloat(0));
                delList.add(inst);
                return;
            }
        }
        if (inst.getOperand_2() instanceof Constant constant) {
            // v / 1 -> v
            if (constant.getConstValue().equals(1)) {
                inst.replaceAllUsesWith(inst.getOperand_1());
                delList.add(inst);
                return;
            }
            // v / -1 -> 0 - v
            if (constant.getConstValue().equals(-1)) {
                Instruction.FSub fsub = new Instruction.FSub(inst.getParentBlock(), inst.getType(),
                        new Constant.ConstantFloat(0), inst.getOperand_1());
//                sub.remove();
                inst.replaceAllUsesWith(fsub);
                delList.add(inst);
                snap.add(idx + 1, fsub);
                return;
            }
            // (c1 * x) / c2 -> 0 if c1 % c2 == 0
            if (inst.getOperand_1() instanceof Instruction.FMul mul) {
                if (mul.getUsers().size() == 1) {
                    if (mul.getOperand_1() instanceof Constant c1) {
                        if (((float) c1.getConstValue()) % ((float) constant.getConstValue()) == 0) {
                            inst.replaceAllUsesWith(new Constant.ConstantFloat(0));
                            delList.add(inst);
                            return;
                        }
                    }
                }
            }
            // (0 - x) / c -> x / -c
            if (inst.getOperand_1() instanceof Instruction.FSub fsub) {
                if (fsub.getOperand_1() instanceof Constant c && c.isZero()) {
                    Instruction.FDiv newFDiv = new Instruction.FDiv(inst.getParentBlock(), inst.getType(),
                            fsub.getOperand_2(), new Constant.ConstantFloat(-1 * (float) constant.getConstValue()));
                    inst.replaceAllUsesWith(newFDiv);
                    delList.add(inst);
                    snap.add(idx + 1, newFDiv);
                    return;
                }
            }
        }
        //v / v -> 1
        if (inst.getOperand_1().equals(inst.getOperand_2())) {
            inst.replaceAllUsesWith(new Constant.ConstantFloat(1));
            delList.add(inst);
            return;
        }
        //v / (0 - v) -> -1
        if (inst.getOperand_2() instanceof Instruction.FSub fsub) {
            if (fsub.getOperand_1() instanceof Constant c && c.isZero() && fsub.getOperand_2().equals(inst.getOperand_1())) {
                inst.replaceAllUsesWith(new Constant.ConstantFloat(-1));
                delList.add(inst);
                return;
            }
        }
        //( 0 - v ) / v -> -1
        if (inst.getOperand_1() instanceof Instruction.FSub fsub) {
            if (fsub.getOperand_1() instanceof Constant c && c.isZero() && fsub.getOperand_2().equals(inst.getOperand_2())) {
                inst.replaceAllUsesWith(new Constant.ConstantFloat(-1));
                delList.add(inst);
                return;
            }
        }
        //v / (v * a) or (a * v) -> 1 / a 确保 v * a 只有一个作用点
        if (inst.getOperand_2() instanceof Instruction.FMul mul) {
            if (mul.getUsers().size() == 1) {
                if (mul.getOperand_1().equals(inst.getOperand_1())) {
                    Instruction.FDiv newFDiv = new Instruction.FDiv(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(1), mul.getOperand_2());
                    inst.replaceAllUsesWith(newFDiv);
                    delList.add(inst);
                    snap.add(idx + 1, newFDiv);
                    return;
                }
                else if (mul.getOperand_2().equals(inst.getOperand_1())) {
                    Instruction.FDiv newFDiv = new Instruction.FDiv(inst.getParentBlock(), inst.getType(),
                            new Constant.ConstantFloat(1), mul.getOperand_1());
                    inst.replaceAllUsesWith(newFDiv);
                    delList.add(inst);
                    snap.add(idx + 1, newFDiv);
                    return;
                }
            }
        }
        //v / a / b -> v / (a * b) 确保 a / b 只有一个作用点
        if (inst.getOperand_1() instanceof Instruction.FDiv div) {
            if (div.getUsers().size() == 1) {
                Instruction.FMul mul = new Instruction.FMul(inst.getParentBlock(), inst.getType(),
                        div.getOperand_2(), inst.getOperand_2());
                Instruction.FDiv newFDiv = new Instruction.FDiv(inst.getParentBlock(), inst.getType(),
                        div.getOperand_1(), mul);
                inst.replaceAllUsesWith(newFDiv);
                delList.add(inst);
                snap.add(idx + 1, mul);
                snap.add(idx + 2, newFDiv);
                return;
            }
        }
        reducedList.add(inst);
    }
}
