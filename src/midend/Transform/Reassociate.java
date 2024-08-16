package midend.Transform;

import mir.Module;
import mir.*;
import utils.Pair;

import java.util.*;

/**
 * v + v + v + v + v -> 5 * v
 * v * v * v * v * v -> v * (v*v) * (v*v)
 */
public class Reassociate {
    private static final HashMap<Value, ArrayList<Pair<Integer, Value>>> map = new HashMap<>();
    private static final HashMap<Value, Integer> number = new HashMap<>();

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        for (BasicBlock block : function.getDomTreeLayerSort())
            runOnBlock(block);
        map.clear();
        number.clear();
    }

    public static void runOnBlock(BasicBlock block) {
        int numberCount = 1;
        for (Instruction inst : block.getInstructions()) {
            number.put(inst, numberCount++);
        }
        for (Instruction inst : block.getInstructionsSnap()) {
            if (inst.isAssociative()) runOnInst((Instruction.BinaryOperation) inst);
        }
    }

    private static void runOnInst(Instruction.BinaryOperation inst) {
        if (inst instanceof Instruction.FMul || inst instanceof Instruction.FAdd) return;
        BasicBlock block = inst.getParentBlock();
        Instruction.InstType instType = inst.getInstType();
        ArrayList<Pair<Integer, Value>> args = new ArrayList<>();
        Value op1 = inst.getOperand_1();
        Value op2 = inst.getOperand_2();
        if (op1 instanceof Instruction opInstr && opInstr.getInstType().equals(instType)) {
            ArrayList<Pair<Integer, Value>> sub = map.get(op1);
            ArrayList<Pair<Integer, Value>> sub_clone = new ArrayList<>();
            for (Pair<Integer, Value> pair : sub) sub_clone.add(new Pair<>(pair.getKey(), pair.getValue()));
            args.addAll(sub_clone);
        }
        else args.add(new Pair<>(1, op1));
        if (op2 instanceof Instruction opInstr && opInstr.getInstType().equals(instType)) {
            ArrayList<Pair<Integer, Value>> sub = map.get(op2);
            ArrayList<Pair<Integer, Value>> sub_clone = new ArrayList<>();
            for (Pair<Integer, Value> pair : sub) sub_clone.add(new Pair<>(pair.getKey(), pair.getValue()));
            args.addAll(sub_clone);
        }
        else args.add(new Pair<>(1, op2));
        args.sort((lhs, rhs) -> {
            Value lv = lhs.getValue();
            Value rv = rhs.getValue();
            //操作数排序的主要目的是将常量放在前面，非常量按其编号从小到大排序
            if (lv instanceof Constant && rv instanceof Constant) {
                return Integer.compare((Integer) ((Constant) lv).getConstValue(),
                        (Integer) ((Constant) rv).getConstValue());
            }
            if (lv instanceof Constant) return -1;
            if (rv instanceof Constant) return 1;
            return Integer.compare(getNumber(lv), getNumber(rv));
        });
//        args.forEach(System.out::println);
//        System.out.println();
        // 对 args 向量中的元素进行去重和合并
        for (int i = 0; i < args.size(); i++) {
            Pair<Integer, Value> cur = args.get(i);
            if (cur.getKey() == 0) continue;
            for (int j = i + 1; j < args.size(); j++) {
                Pair<Integer, Value> next = args.get(j);
                if (next.getValue() != cur.getValue()) break;
                cur.setKey(cur.getKey() + next.getKey());
                next.setKey(0);
            }
        }
        args.removeIf(integerValuePair -> integerValuePair.getKey() == 0);
        Instruction firstUser = inst.getUsers().get(0);
        if (inst.getUsers().size() == 1 && firstUser.getInstType().equals(instType)) {
            map.put(inst, args);
            return;
        }
        map.put(inst, new ArrayList<>(List.of(new Pair<>(1, inst))));
        boolean needRebuild = false;
        int cnt = 0;
        for (Pair<Integer, Value> pair : args) {
            if (pair.getValue() instanceof Constant) cnt++;
            if (pair.getKey() != 1) {
                needRebuild = true;
                break;
            }
        }
        if (!needRebuild && cnt < 1) return;
        //合并结束，开始进行重组
        ArrayList<Value> reductionStorage = new ArrayList<>();
        switch (inst.getInstType()) {
            case ADD: {
                for (Pair<Integer, Value> pair : args) {
                    if (pair.getKey() == 1) reductionStorage.add(pair.getValue());
                    else {
                        Instruction mul = new Instruction.Mul(block, pair.getValue().getType(),
                                pair.getValue(), Constant.ConstantInt.get(pair.getKey()));
                        mul.remove();
                        block.insertInstBefore(mul, inst);
                        reductionStorage.add(mul);
                    }
                }
                break;
            }
            case MUL: {
                for (Pair<Integer, Value> pair : args) {
                    if (pair.getKey() == 1) reductionStorage.add(pair.getValue());
                    else {
                        //多次乘法转换为幂运算
                        int c = pair.getKey();
                        Value v = pair.getValue();
                        while (c != 0) {
                            if ((c & 1) != 0) reductionStorage.add(v);
                            c >>= 1;
                            if (c != 0) {
                                Instruction mul = new Instruction.Mul(block, v.getType(), v, v);
                                mul.remove();
                                block.insertInstBefore(mul, inst);
                                v = mul;
                            }
                            else break;
                        }
                    }
                }
                break;
            }
//            case FADD: {
//                for (Pair<Integer, Value> pair : args) {
//                    if (pair.getKey() == 1) reductionStorage.add(pair.getValue());
//                    else {
//                        Instruction mul = new Instruction.FMul(block, pair.getValue().getType(),
//                                pair.getValue(), new Constant.ConstantFloat(pair.getKey()));
//                        mul.remove();
//                        block.insertInstBefore(mul, inst);
//                        reductionStorage.add(mul);
//                    }
//                }
//                break;
//            }
            default: {
                throw new RuntimeException("Unsupported instruction type");
            }
        }
        Value reducedStorage = null;
        for (Value v : reductionStorage) {
            if (reducedStorage == null) {
                reducedStorage = v;
            }
            else {
                if (inst instanceof Instruction.Add) {
                    Instruction add = new Instruction.Add(block, v.getType(), reducedStorage, v);
                    add.remove();
                    block.insertInstBefore(add, inst);
                    reducedStorage = add;
                }
                else if (inst instanceof Instruction.Mul) {
                    Instruction mul = new Instruction.Mul(block, v.getType(), reducedStorage, v);
                    mul.remove();
                    block.insertInstBefore(mul, inst);
                    reducedStorage = mul;
                }
//                else if (inst instanceof Instruction.FAdd) {
//                    Instruction fadd = new Instruction.FAdd(block, v.getType(), reducedStorage, v);
//                    fadd.remove();
//                    block.insertInstBefore(fadd, inst);
//                    reducedStorage = fadd;
//                }
                else {
                    throw new RuntimeException("Unsupported instruction type");
                }
            }
        }
        if (reducedStorage instanceof Instruction && !map.containsKey(reducedStorage)) {
            ArrayList<Pair<Integer, Value>> array = map.remove(inst);
            if (array.size() == 1 && array.get(0).getValue().equals(inst)) array.get(0).setValue(reducedStorage);
            map.put(reducedStorage, array);
            inst.replaceAllUsesWith(reducedStorage);
            inst.delete();
        }
    }

    private static int getNumber(Value v) {
        if (v instanceof Constant) return Integer.MAX_VALUE;
        if (number.containsKey(v)) return number.get(v);
        return 0;
    }
}

