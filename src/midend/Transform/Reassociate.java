//package midend.Transform;
//
//import mir.Function;
//import mir.Module;
//import mir.*;
//import utils.Pair;
//
//import java.util.*;
//
//public class ReAssociate {
//    private static HashMap<Value, ArrayList<Pair<Integer, Value>>> map = new HashMap<>();
//    private static HashMap<Value, Integer> number = new HashMap<>();
//
//    public static void run(Module module) {
//        for (Function function : module.getFuncSet()) {
//            if (function.isExternal()) continue;
//            for (BasicBlock block : function.getBlocks())
//                runOnBlock(block);
//        }
//    }
//
//    private static void runOnBlock(BasicBlock block) {
//        map.clear();
//        number.clear();
//        int numberCount = 0;
//        for (Instruction inst : block.getInstructions()) {
//            number.put(inst, numberCount++);
//        }
//        for (Instruction inst : block.getInstructions()) {
//            runOnInst(inst);
//        }
//    }
//
//    private static void runOnInst(Instruction inst) {
//        if (!inst.isAssociative()) return;
//        BasicBlock block = inst.getParentBlock();
//        ArrayList<Pair<Integer, Value>> args = map.get(inst);
//        if (args == null) args = new ArrayList<>();
//        for (Value operand : inst.getOperands()) {
//            if (operand instanceof Instruction instOperand &&
//                    instOperand.getInstType().equals(inst.getInstType()) &&
//                    instOperand.getParentBlock().equals(block))
//            {
//                ArrayList<Pair<Integer, Value>> sub = map.get(operand);
//                if (sub.size() < 512) {
//                    args.addAll(sub);
//                    continue;
//                }
//            }
//            args.add(new Pair<>(1, operand));
//        }
//        args.sort((lhs, rhs) -> {
//            Value lv = lhs.getValue();
//            Value rv = rhs.getValue();
//            //操作数排序的主要目的是将常量放在前面，非常量按其编号从小到大排序
//            if (lv instanceof Constant && rv instanceof Constant) {
//                return Integer.compare((Integer) ((Constant) lv).getConstValue(),
//                        (Integer) ((Constant) rv).getConstValue());
//            }
//            if (lv instanceof Constant) return -1;
//            if (rv instanceof Constant) return 1;
//            return Integer.compare(getNumber(lv), getNumber(rv));
//        });
////        args.forEach(System.out::println);
////        System.out.println();
//        // 对 args 向量中的元素进行去重和合并
//        for (int i = 0; i < args.size(); i++) {
//            Pair<Integer, Value> cur = args.get(i);
//            if (cur.getKey() == 0) continue;
//            for (int j = i + 1; j < args.size(); j++) {
//                Pair<Integer, Value> next = args.get(j);
//                if (next.getValue() != cur.getValue()) break;
//                cur.setKey(cur.getKey() + next.getKey());
//                next.setKey(0);
//            }
//        }
//        Iterator<Pair<Integer, Value>> it = args.iterator();
//        while (it.hasNext()) {
//            if (it.next().getKey() == 0) it.remove();
//        }
//        map.put(inst, args);
//        //合并结束，开始进行重组
//        ArrayList<Value> reductionStorage = new ArrayList<>();
//        switch (inst.getInstType()) {
//            case ADD: {
//                for (Pair<Integer, Value> pair : args) {
//                    if (pair.getKey() == 1) reductionStorage.add(pair.getValue());
//                    else {
//                        Instruction fmul = new Instruction.FMul(block, pair.getValue().getType(),
//                                pair.getValue(), new Constant.ConstantFloat(((float) pair.getKey())));
//                        fmul.remove();
//                        block.getInstructions().insertBefore(fmul, inst);
//                        reductionStorage.add(fmul);
//                    }
//                }
//                break;
//            }
//            case FMUL: {
//                for (Pair<Integer, Value> pair : args) {
//                    if (pair.getKey() == 1) reductionStorage.add(pair.getValue());
//                    else {
//                        //多次乘法转换为幂运算
//                        int c = pair.getKey();
//                        Value v = pair.getValue();
//                        while (c != 0) {
//                            if ((c & 1) != 0) reductionStorage.add(v);
//                            c >>= 1;
//                            if (c != 0) {
//                                Instruction fmul = new Instruction.FMul(block, v.getType(), v, v);
//                                fmul.remove();
//                                block.getInstructions().insertBefore(fmul, inst);
//                                v = fmul;
//                            }
//                            else break;
//                        }
//                    }
//                }
//                break;
//            }