package midend;

import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class ArithReduce {

    private static ArrayList<Instruction> reducedList;

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            ArrayList<BasicBlock> blocks = function.getDomTreeLayerSort();
            for (BasicBlock block : blocks) {
                runOnBlock(block);
            }
        }
    }

    private static void runOnBlock(BasicBlock block) {
        reducedList = new ArrayList<>();
        Iterator<Instruction> iterator = block.getInstructions().iterator();
        while (iterator.hasNext()) {
            Instruction instruction = iterator.next();
            if (instruction instanceof Instruction.BinaryOperation) {
                runOnInst(instruction);
            }
            iterator.remove();
        }
        for (Instruction instruction : reducedList) {
            block.addInstLast(instruction);
        }
    }

    private static void runOnInst(Instruction inst) {
        //默认指令参与常量折叠，二元操作数只可能有一个常量
        switch (inst.getInstType()) {
            case ADD -> reduceAdd((Instruction.Add) inst);
            case SUB -> reduceSub((Instruction.Sub) inst);
            case MUL -> reduceMul((Instruction.Mul) inst);
            case DIV -> reduceDiv((Instruction.Div) inst);
            case REM -> reduceRem((Instruction.Rem) inst);
            case Icmp -> reduceIcmp((Instruction.Icmp) inst);
            default -> reducedList.add(inst);
        }
    }

    private static void reduceAdd(Instruction.Add inst) {
        //0 + v -> v
        if (inst.getOperand_1() instanceof Constant) {
            if (((Constant) inst.getOperand_1()).isZero()) {
                inst.replaceAllUsesWith(inst.getOperand_2());
                return;
            }
        }
        // a + (0 - b) -> a - b
        if (inst.getOperand_2() instanceof Instruction.Sub) {
            Instruction.Sub sub = (Instruction.Sub) inst.getOperand_2();
            if (sub.getOperand_1() instanceof Constant && ((Constant) sub.getOperand_1()).isZero()) {
                Instruction.Sub newSub = new Instruction.Sub(inst.getParentBlock(), inst.getType(),
                        inst.getOperand_1(), sub.getOperand_2());
                newSub.remove();
                inst.replaceAllUsesWith(newSub);
                reducedList.add(newSub);
                return;
            }
        }
        /*
         b * a + c * a -> (b + c) * a
         a * b + c * a -> (b + c) * a
         a * b + a * c -> (b + c) * a
         b * a + a * c -> (b + c) * a
         */
        if (inst.getOperand_1() instanceof Instruction.Mul && inst.getOperand_2() instanceof Instruction.Mul) {
            Instruction.Mul mul1 = (Instruction.Mul) inst.getOperand_1();
            Instruction.Mul mul2 = (Instruction.Mul) inst.getOperand_2();
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
                add.remove();
                Instruction.Mul newMul = new Instruction.Mul(inst.getParentBlock(), inst.getType(), a, add);
                newMul.remove();
                inst.replaceAllUsesWith(newMul);
                reducedList.add(add);
                reducedList.add(newMul);
                return;
            }
            reducedList.add(inst);
        }
    }

    private static void reduceSub(Instruction.Sub inst) {
        if (inst.getOperand_2() instanceof Constant) {
            //a - 0 -> a
            if (((Constant) inst.getOperand_2()).isZero()) {
                inst.replaceAllUsesWith(inst.getOperand_1());
            }
            //a -c ->  (-c) + a
            else {
                Instruction.Add add = new Instruction.Add(inst.getParentBlock(), inst.getType(),
                        new Constant.ConstantInt(-1 * (int) ((Constant) inst.getOperand_2()).getConstValue()), inst.getOperand_1());
                add.remove();
                inst.replaceAllUsesWith(add);
                reducedList.add(add);
            }
            return;
        }
        //a - (0 - b) -> a + b
        if (inst.getOperand_2() instanceof Instruction.Sub) {
            Instruction.Sub sub = (Instruction.Sub) inst.getOperand_2();
            if (sub.getOperand_1() instanceof Constant && ((Constant) sub.getOperand_1()).isZero()) {
                Instruction.Add add = new Instruction.Add(inst.getParentBlock(), inst.getType(),
                        inst.getOperand_1(), sub.getOperand_2());
                add.remove();
                inst.replaceAllUsesWith(add);
                reducedList.add(add);
                return;
            }
        }
        /*
         b * a - c * a -> (b - c) * a
         a * b - c * a -> (b - c) * a
         a * b - a * c -> (b - c) * a
         b * a - a * c -> (b - c) * a
         */
        if (inst.getOperand_1() instanceof Instruction.Mul && inst.getOperand_2() instanceof Instruction.Mul) {
            Instruction.Mul mul1 = (Instruction.Mul) inst.getOperand_1();
            Instruction.Mul mul2 = (Instruction.Mul) inst.getOperand_2();
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
                sub.remove();
                Instruction.Mul newMul = new Instruction.Mul(inst.getParentBlock(), inst.getType(), a, sub);
                newMul.remove();
                inst.replaceAllUsesWith(newMul);
                reducedList.add(sub);
                reducedList.add(newMul);
                return;
            }
        }
        reducedList.add(inst);
    }

    private static void reduceMul(Instruction.Mul inst) {
        if (inst.getOperand_1() instanceof Constant) {
            // 0 * v -> 0
            if (((Constant) inst.getOperand_1()).isZero()) {
                inst.replaceAllUsesWith(new Constant.ConstantInt(0));
                return;
            }
            // 1 * v -> v
            if (((Constant) inst.getOperand_1()).getConstValue().equals(1)) {
                inst.replaceAllUsesWith(inst.getOperand_2());
                return;
            }
        }
        reducedList.add(inst);
    }

    private static void reduceDiv(Instruction.Div inst) {
        if (inst.getOperand_1() instanceof Constant) {
            // 0 / v -> 0
            if (((Constant) inst.getOperand_1()).isZero()) {
                inst.replaceAllUsesWith(new Constant.ConstantInt(0));
                return;
            }
            // v / 1 -> v
            if (((Constant) inst.getOperand_2()).getConstValue().equals(1)) {
                inst.replaceAllUsesWith(inst.getOperand_1());
                return;
            }
            // v / -1 -> 0 - v
            if (((Constant) inst.getOperand_2()).getConstValue().equals(-1)) {
                Instruction.Sub sub = new Instruction.Sub(inst.getParentBlock(), inst.getType(),
                        new Constant.ConstantInt(0), inst.getOperand_1());
                sub.remove();
                inst.replaceAllUsesWith(sub);
                reducedList.add(sub);
                return;
            }
            //v / v -> 1
            if (inst.getOperand_1().equals(inst.getOperand_2())) {
                inst.replaceAllUsesWith(new Constant.ConstantInt(1));
                return;
            }
            //v / a / b -> v / (a * b)
            if (inst.getOperand_1() instanceof Instruction.Div) {
                Instruction.Div div = (Instruction.Div) inst.getOperand_1();
                Instruction.Mul mul = new Instruction.Mul(inst.getParentBlock(), inst.getType(),
                        div.getOperand_2(), inst.getOperand_2());
                mul.remove();
                Instruction.Div newDiv = new Instruction.Div(inst.getParentBlock(), inst.getType(),
                        div.getOperand_1(), mul);
                newDiv.remove();
                inst.replaceAllUsesWith(newDiv);
                reducedList.add(mul);
                reducedList.add(newDiv);
                return;
            }
        }
        reducedList.add(inst);
    }

    private static void reduceRem(Instruction.Rem inst) {
        if (inst.getOperand_1() instanceof Constant) {
            // 0 % v -> 0
            if (((Constant) inst.getOperand_1()).isZero()) {
                inst.replaceAllUsesWith(new Constant.ConstantInt(0));
                return;
            }
            // v % 1 -> 0
            if (((Constant) inst.getOperand_2()).getConstValue().equals(1)) {
                inst.replaceAllUsesWith(new Constant.ConstantInt(0));
                return;
            }
            // v % -1 -> 0
            if (((Constant) inst.getOperand_2()).getConstValue().equals(-1)) {
                inst.replaceAllUsesWith(new Constant.ConstantInt(0));
                return;
            }
            // v % v -> 0
            if (inst.getOperand_1().equals(inst.getOperand_2())) {
                inst.replaceAllUsesWith(new Constant.ConstantInt(0));
                return;
            }
        }
        reducedList.add(inst);
    }

    private static void reduceIcmp(Instruction.Icmp inst) {
        /*
         x*x > 0 -> 1
         x*x >= 0 -> 1
         x*x < 0 -> 0
         */

    }

}
