//package backend.Opt;
//
//import backend.operand.Operand;
//import backend.riscv.RiscvBlock;
//import backend.riscv.RiscvFunction;
//import backend.riscv.RiscvInstruction.RiscvInstruction;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.LinkedList;
//import java.util.Queue;
//
//public class Peephole {
//    public static boolean genericPeepholeOpt(RiscvFunction function) {
//        return false;
//    }
//
//    public static boolean removeUnusedInsts(RiscvFunction func) {
//        HashMap<Operand, ArrayList<RiscvInstruction>> writers = new HashMap<>();
//        Queue<RiscvInstruction> q = new LinkedList<>();
//
//        Predicate<OperandType> isAllocableType = type -> type.ordinal() <= OperandType.Float32.ordinal();
//
//        for (RiscvBlock block : func.blocks) {
//            for (RiscvInstruction inst : block.riscvInstructions) {
//                InstructionInfo instInfo = ctx.instInfo.getInstInfo(inst);
//                boolean special = false;
//
//                if (requireOneFlag(instInfo.getInstFlag(), InstFlagSideEffect)) {
//                    special = true;
//                }
//
//                for (int idx = 0; idx < instInfo.getOperandNum(); ++idx) {
//                    if ((instInfo.getOperandFlag(idx) & OperandFlagDef) != 0) {
//                        MIROperand op = inst.getOperand(idx);
//                        writers.computeIfAbsent(op, k -> new LinkedList<>()).add(inst);
//                        if (op.isReg() && isISAReg(op.getReg()) && isAllocableType.test(op.getType())) {
//                            special = true;
//                        }
//                    }
//                }
//
//                if (special) {
//                    q.add(inst);
//                }
//            }
//        }
//
//        while (!q.isEmpty()) {
//            RiscvInstruction inst = q.poll();
//            InstructionInfo instInfo = ctx.instInfo.getInstInfo(inst);
//
//            for (int idx = 0; idx < instInfo.getOperandNum(); ++idx) {
//                if ((instInfo.getOperandFlag(idx) & OperandFlagUse) != 0) {
//                    MIROperand operand = inst.getOperand(idx);
//                    List<RiscvInstruction> writerList = writers.get(operand);
//                    if (writerList != null) {
//                        q.addAll(writerList);
//                        writers.remove(operand);
//                    }
//                }
//            }
//        }
//
//        Set<RiscvInstruction> remove = new HashSet<>();
//        for (Map.Entry<MIROperand, List<RiscvInstruction>> entry : writers.entrySet()) {
//            MIROperand op = entry.getKey();
//            List<RiscvInstruction> writerList = entry.getValue();
//
//            if (isISAReg(op.getReg()) && isAllocableType.test(op.getType())) {
//                continue;
//            }
//
//            for (RiscvInstruction writer : writerList) {
//                InstructionInfo instInfo = ctx.instInfo.getInstInfo(writer);
//                if (requireOneFlag(instInfo.getInstFlag(), InstFlagSideEffect | InstFlagMultiDef)) {
//                    continue;
//                }
//                remove.add(writer);
//            }
//        }
//
//        for (RiscvBlock block : func.blocks) {
//            block.riscvInstructions.removeIf(inst -> remove.contains(inst));
//        }
//
//        return !remove.isEmpty();
//    }
//}
