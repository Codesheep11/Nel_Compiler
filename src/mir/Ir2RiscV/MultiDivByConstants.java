//package mir.Ir2RiscV;
//
//import backend.operand.Operand;
//import backend.operand.Reg;
//import backend.riscv.RiscvInstruction.R3;
//import backend.riscv.RiscvInstruction.RiscvInstruction;
//import mir.Constant;
//import mir.Instruction;
//import mir.Type;
//import mir.Value;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.ListIterator;
//import java.util.Map;
//
///**
// * 用于控制指令选择的类,主要用来控制乘法和除法
// **/
//public class MultiDivByConstants {
//
//    static class ShiftDivNode {
//
//        enum DivType {
//            DIV,
//            NOP
//        }
//
//        int cost;
//        int shamt;
//        DivType division;
//        ShiftDivNode parent;
//
//        ShiftDivNode(int shamtVal, DivType divisionVal, ShiftDivNode parentVal) {
//            this.shamt = shamtVal;
//            this.division = divisionVal;
//            this.parent = parentVal;
//            this.cost = (parent != null ? parent.cost : 0) + (division != DivType.NOP ? 1 : 0) + (shamt > 0 ? 1 : 0);
//        }
//
//        public boolean lessThan(ShiftDivNode other) {
//            return this.cost < other.cost;
//        }
//    }
//
//    static class DivEliminationOptimizer {
//        private static final Map<Integer, Map<Long, ShiftDivNode>> divMemo = new HashMap<>();
//
//        static {
//            for (int i = 0; i < 12; i++) {
//                divMemo.put(i, new HashMap<>());
//            }
//        }
//
//        private static ShiftDivNode minDivPlan(ShiftDivNode lhs, ShiftDivNode rhs) {
//            if (lhs == null) return rhs;
//            if (rhs == null) return lhs;
//            return lhs.lessThan(rhs) ? lhs : rhs;
//        }
//
//        private static ShiftDivNode findDividePlan(long n, int upper) {
//            assert upper < divMemo.size() : "Please increase the size of divMemo";
//
//            if (upper < 0) return null;
//            if (divMemo.get(upper).containsKey(n)) return divMemo.get(upper).get(n);
//            long m = n;
//            int shamt = 0;
//            while (m % 2 == 0) {
//                m >>= 1;
//                shamt++;
//            }
//            if (m == 1) {
//                ShiftDivNode node = new ShiftDivNode(shamt, ShiftDivNode.DivType.NOP, null);
//                divMemo.get(upper).put(n, node);
//                return node;
//            }
//            ShiftDivNode plan = findDividePlanOdd(m, upper - (shamt > 0 ? 1 : 0));
//            if (plan == null) return null;
//            if (shamt > 0) {
//                plan = new ShiftDivNode(shamt, ShiftDivNode.DivType.NOP, plan);
//            }
//            divMemo.get(upper).put(n, plan);
//            return plan;
//        }
//
//        private static ShiftDivNode findDividePlanOdd(long n, int upper) {
//            if (upper < 0) return null;
//            if (divMemo.get(upper).containsKey(n)) return divMemo.get(upper).get(n);
//
//            assert n > 1 && n % 2 == 1;
//
//            ShiftDivNode plan = null;
//
//            plan = minDivPlan(plan, new ShiftDivNode(0, ShiftDivNode.DivType.NOP, findDividePlan(n - 1, upper - 1)));
//            plan = minDivPlan(plan, new ShiftDivNode(0, ShiftDivNode.DivType.NOP, findDividePlan(n + 1, upper - 1)));
//
//            for (int i = 1; (1L << i) <= n; i++) {
//                long p1 = (1L << i) + 1;
//                long p2 = (1L << i) - 1;
//                if (n % p1 == 0) {
//                    plan = minDivPlan(plan, new ShiftDivNode(i, ShiftDivNode.DivType.DIV, findDividePlan(n / p1, upper - 2)));
//                }
//                if (n % p2 == 0) {
//                    plan = minDivPlan(plan, new ShiftDivNode(i, ShiftDivNode.DivType.DIV, findDividePlan(n / p2, upper - 2)));
//                }
//            }
//
//            if (plan != null) {
//                divMemo.get(upper).put(n, plan);
//            }
//
//            return plan;
//        }
//
//        private static boolean expandDivWithConstantImpl(ArrayList<RiscvInstruction> instructions, Instruction.Div instr, int maxCost) {
//
//            Value dst = instr;
//            Type type = dst.getType();
//            Value lhs = instr.getOperand_1();
//            Value rhs = instr.getOperand_2();
//
//            if (!(rhs instanceof Constant.ConstantInt) || ((Constant.ConstantInt) rhs).isZero()) return false;
//
//            int v2 = ((Constant.ConstantInt) rhs).getIntValue();
//            ShiftDivNode plan = findDividePlan(Math.abs(v2), maxCost);
//            if (plan == null) return false;
//
//            Reg ret = expand(plan, instructions, iter, lhs);
//            if (v2 < 0) {
//                instructions.add(new R3(CodeGen.nowBlock, ret, Reg.getPreColoredReg(Reg.PhyReg.zero, 32), ret), );
//                iter.set(new R(Opcode.SUB, dst, new Immediate(0, type), ret));
//            } else {
//                iter.set(new Instruction(Opcode.COPY, dst, ret));
//            }
//            return true;
//        }
//
//        private static Reg expand(ShiftDivNode plan, ArrayList<RiscvInstruction> instructions, Operand lhs) {
//            if (plan == null) return lhs;
//
//            Operand v = expand(plan.parent, instructions, codeGenCtx, iter, lhs);
//            Operand ret = v;
//            if (plan.shamt > 0) {
//                Operand res = new VirtualRegister(codeGenCtx.nextId(), lhs.getType());
//                instructions.add(iter.previousIndex(), new Instruction(Opcode.SHR, res, v, new Immediate(plan.shamt, lhs.getType())));
//                ret = res;
//            }
//
//            if (plan.division != ShiftDivNode.DivType.NOP) {
//                Operand res = new VirtualRegister(codeGenCtx.nextId(), lhs.getType());
//                instructions.add(iter.previousIndex(), new Instruction(plan.division == ShiftDivNode.DivType.DIV ? Opcode.DIV : Opcode.NOP, res, ret, lhs));
//                ret = res;
//            }
//
//            return ret;
//        }
//
//        public static boolean expandDivWithConstant(Instruction.Div instruction, int maxCost) {
//
//        }
//    }
//
//
//    static class ShiftMulNode {
//        enum MulType {
//            ADD,
//            SUB,
//            NOP
//        }
//
//        int cost;
//        int shamt;
//        MulType multiplication;
//        ShiftMulNode parent;
//
//        ShiftMulNode(int shamtVal, MulType multiplicationVal, ShiftMulNode parentVal) {
//            this.shamt = shamtVal;
//            this.multiplication = multiplicationVal;
//            this.parent = parentVal;
//            this.cost = (parent != null ? parent.cost : 0) + (multiplication != MulType.NOP ? 1 : 0) + (shamt > 0 ? 1 : 0);
//        }
//
//        public boolean lessThan(ShiftMulNode other) {
//            return this.cost < other.cost;
//        }
//    }
//
//    public class MulEliminationOptimizer {
//        private static final Map<Integer, Map<Long, ShiftMulNode>> mulMemo = new HashMap<>();
//
//        static {
//            for (int i = 0; i < 12; i++) {
//                mulMemo.put(i, new HashMap<>());
//            }
//        }
//
//        private static ShiftMulNode minMulPlan(ShiftMulNode lhs, ShiftMulNode rhs) {
//            if (lhs == null) return rhs;
//            if (rhs == null) return lhs;
//            return lhs.lessThan(rhs) ? lhs : rhs;
//        }
//
//        private static ShiftMulNode findMultiplyPlan(long n, int upper) {
//            assert upper < mulMemo.size() : "Please increase the size of mulMemo";
//
//            if (upper < 0) return null;
//            if (mulMemo.get(upper).containsKey(n)) return mulMemo.get(upper).get(n);
//
//            long m = n;
//            int shamt = 0;
//            while (m % 2 == 0) {
//                m >>= 1;
//                shamt++;
//            }
//
//            if (m == 1) {
//                ShiftMulNode node = new ShiftMulNode(shamt, ShiftMulNode.MulType.NOP, null);
//                mulMemo.get(upper).put(n, node);
//                return node;
//            }
//
//            ShiftMulNode plan = findMultiplyPlanOdd(m, upper - (shamt > 0 ? 1 : 0));
//            if (plan == null) return null;
//            if (shamt > 0) {
//                plan = new ShiftMulNode(shamt, ShiftMulNode.MulType.NOP, plan);
//            }
//
//            mulMemo.get(upper).put(n, plan);
//            return plan;
//        }
//
//        private static ShiftMulNode findMultiplyPlanOdd(long n, int upper) {
//            if (upper < 0) return null;
//            if (mulMemo.get(upper).containsKey(n)) return mulMemo.get(upper).get(n);
//
//            assert n > 1 && n % 2 == 1;
//
//            ShiftMulNode plan = null;
//
//            plan = minMulPlan(plan, new ShiftMulNode(0, ShiftMulNode.MulType.ADD, findMultiplyPlan(n - 1, upper - 1)));
//            plan = minMulPlan(plan, new ShiftMulNode(0, ShiftMulNode.MulType.SUB, findMultiplyPlan(n + 1, upper - 1)));
//
//            for (int i = 1; (1L << i) <= n; i++) {
//                long p1 = (1L << i) + 1;
//                long p2 = (1L << i) - 1;
//                if (n % p1 == 0) {
//                    plan = minMulPlan(plan, new ShiftMulNode(i, ShiftMulNode.MulType.ADD, findMultiplyPlan(n / p1, upper - 2)));
//                }
//                if (n % p2 == 0) {
//                    plan = minMulPlan(plan, new ShiftMulNode(i, ShiftMulNode.MulType.SUB, findMultiplyPlan(n / p2, upper - 2)));
//                }
//            }
//
//            if (plan != null) {
//                mulMemo.get(upper).put(n, plan);
//            }
//
//            return plan;
//        }
//
//        private static boolean expandMulWithConstantImpl(ArrayList<RiscvInstruction> instructions, Instruction.Mul inst, int maxCost) {
//
//
//            Value dst = inst;
//            Type type = dst.getType();
//            Value lhs = inst.getOperand_1();
//            Value rhs = inst.getOperand_2();
//            boolean change = lhs instanceof Constant.ConstantInt;
//            if (change) {
//                lhs = inst.getOperand_2();
//                rhs = inst.getOperand_1();
//            }
//            if (!(rhs instanceof Constant.ConstantInt) || ((Constant.ConstantInt) rhs).getIntValue() == 0) return false;
//
//            int v2 = ((Constant.ConstantInt) rhs).getIntValue());
//            ShiftMulNode plan = findMultiplyPlan(Math.abs(v2), maxCost);
//            if (plan == null) return false;
//
//            Operand ret = expand(plan, instructions, codeGenCtx, iter, lhs);
//            if (v2 < 0) {
//                iter.set(new Instruction(Opcode.SUB, dst, new Immediate(0, type), ret));
//            } else {
//                iter.set(new Instruction(Opcode.COPY, dst, ret));
//            }
//            return true;
//        }
//
//        private static Operand expand(ShiftMulNode plan, List<Instruction> instructions, CodeGenContext codeGenCtx, ListIterator<Instruction> iter, Operand lhs) {
//            if (plan == null) return lhs;
//
//            Operand v = expand(plan.parent, instructions, codeGenCtx, iter, lhs);
//            Operand ret = v;
//            if (plan.shamt > 0) {
//                Operand res = new VirtualRegister(codeGenCtx.nextId(), lhs.getType());
//                instructions.add(iter.previousIndex(), new Instruction(Opcode.SHL, res, v, new Immediate(plan.shamt, lhs.getType())));
//                ret = res;
//            }
//
//            if (plan.multiplication != ShiftMulNode.MulType.NOP) {
//                Operand res = new VirtualRegister(codeGenCtx.nextId(), lhs.getType());
//                instructions.add(iter.previousIndex(), new Instruction(plan.multiplication == ShiftMulNode.MulType.ADD ? Opcode.ADD : Opcode.SUB, res, ret, lhs));
//                ret = res;
//            }
//
//            return ret;
//        }
//
//        public static boolean expandMulWithConstant(Function func, CodeGenContext codeGenCtx, int maxCost) {
//
//
//        }
//    }
//
//}
