package backend.Ir2RiscV;

import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvInstruction.Li;
import backend.riscv.RiscvInstruction.R2;
import backend.riscv.RiscvInstruction.R3;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import manager.Manager;
import utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class MulPlaner {
    private static final int MUL_COST = 3;
    private static final ArrayList<RiscvInstruction> steps = new ArrayList<>();

    private static RiscvBlock block;

    private static final boolean doMulOpt = true;


    public static void MulConst(Reg ans, Reg src, int mulr) {
        steps.clear();
        block = CodeGen.nowBlock;
        MulOp mulOp = makePlan(mulr);
        if (mulOp instanceof MulFinal || !Manager.isO1 || !doMulOpt) {
            Reg tmp = Reg.getVirtualReg(Reg.RegType.GPR, 32);
            steps.add(new Li(block, tmp, new Imm(mulr)));
            steps.add(new R3(block, ans, src, tmp, R3.R3Type.mulw));
        } else if (mulOp instanceof VariableMulOp) {
            // 说明这个就是它本身,就是*1
            steps.add(new R2(block, ans, src, R2.R2Type.mv));
        } else if (mulOp instanceof ConstantMulOp) {
            // 在这一层直接返回说明是0
            steps.add(new R2(block, ans, Reg.getPreColoredReg(Reg.PhyReg.zero, 32), R2.R2Type.mv));
        } else if (mulOp instanceof Action action) {
            Reg reg = readPlan(src, action);
            steps.add(new R2(block, ans, reg, R2.R2Type.mv));
        } else {
            throw new RuntimeException("wrong type!");
        }
        optStepsByShadd();
        for (RiscvInstruction instr : steps) {
            block.addInstLast(instr);
        }
    }

    private static void optStepsByShadd() {
        // 使用shadd来优化操作
        for (int i = 0; i < steps.size() - 1; i++) {
            RiscvInstruction ri = steps.get(i);
            if (ri instanceof R3 sri && sri.type == R3.R3Type.slliw) {
                if (sri.rs2 instanceof Imm imm && imm.getVal() <= 3) {
                    Reg srcS = (Reg) sri.rs1;
                    Reg dstS = (Reg) sri.rd;
                    RiscvInstruction next = steps.get(i + 1);
                    if (next instanceof R3 add && add.type == R3.R3Type.addw) {
                        Reg srcA1 = (Reg) add.rs1;
                        Reg srcA2 = (Reg) add.rs2;
                        if ((srcS.equals(srcA1) && dstS.equals(srcA2)) ||
                                srcS.equals(srcA2) || dstS.equals(srcA1)) {
                            R3.R3Type type = imm.getVal() == 3 ?
                                    R3.R3Type.sh3add : imm.getVal() == 2 ?
                                    R3.R3Type.sh2add : R3.R3Type.sh1add;
                            R3 r3 = new R3(block, add.rd, srcS, srcS, type);
                            steps.set(i, r3);
                            steps.remove(i + 1);
                        }
                    }
                }
            }
        }

    }

    private static Reg readPlan(Reg src, Action action) {
        MulOp left = action.mulOp1;
        MulOp right = action.mulOp2;
        Reg reg = Reg.getVirtualReg(Reg.RegType.GPR, 32);
        if (action.type == Action.Type.SHL) {
            if (!(right instanceof ConstantMulOp c)) {
                throw new RuntimeException("wrong type");
            }
            if (left instanceof VariableMulOp) {
                steps.add(new R3(block, reg, src, new Imm(c.value), R3.R3Type.slliw));
            } else {
                if (!(left instanceof Action a)) {
                    throw new RuntimeException("wrong type");
                }
                Reg other = readPlan(src, a);
                steps.add(new R3(block, reg, other, new Imm(c.value), R3.R3Type.slliw));
            }
        } else if (action.type == Action.Type.SUB || action.type == Action.Type.ADD) {
            //如果是SUB或者是ADD,那么两边的操作必定都是寄存器,不可能是数字
            if (left instanceof ConstantMulOp c && c.value != 0 || right instanceof ConstantMulOp m && m.value != 0) {
                throw new RuntimeException("wrong type");
            }
            Reg l, r;
            l = left instanceof ConstantMulOp ? Reg.getPreColoredReg(Reg.PhyReg.zero, 32) :
                    left instanceof Action a ? readPlan(src, a) : src;
            r = right instanceof ConstantMulOp ? Reg.getPreColoredReg(Reg.PhyReg.zero, 32) :
                    right instanceof Action a ? readPlan(src, a) : src;
            R3.R3Type type = action.type == Action.Type.ADD ? R3.R3Type.addw : R3.R3Type.subw;
            steps.add(new R3(block, reg, l, r, type));
        }
        return reg;
    }


    /**
     * 构建乘法值的操作数
     */
    private static class MulOp {
        /**
         * 操作的等级，每进行一次算数运算加一级
         */
        private final int level;

        protected MulOp(int level) {
            this.level = level;
        }

    }

    /**
     * 常数
     */
    public static class ConstantMulOp extends MulOp {
        private final int value;

        public ConstantMulOp(int value) {
            super(0);
            this.value = value;
        }

    }

    /**
     * 乘法的原始值
     */
    public static class VariableMulOp extends MulOp {
        private VariableMulOp() {
            super(0);
        }

        private static final VariableMulOp instance = new VariableMulOp();

        public static VariableMulOp getInstance() {
            return instance;
        }

    }

    /**
     * 一个无法在合理cost内计算的值
     */
    public static class MulFinal extends MulOp {
        private MulFinal() {
            super(0x3fffffff); // 使用该值以避免两个值相加产生溢出
        }

        private static final MulFinal instance = new MulFinal();

        public static MulFinal getInstance() {
            return instance;
        }

    }

    /**
     * 一个操作的结果作为一个操作数
     * 类似一个操作数的树
     */
    public static class Action extends MulOp {
        public enum Type {
            ADD, SUB, SHL
        }

        public final Type type;
        public final MulOp mulOp1, mulOp2;

        public Action(Type type, MulOp mulOp1, MulOp mulOp2) {
            super(mulOp1.level + mulOp2.level + 1);
            this.type = type;
            this.mulOp1 = mulOp1;
            this.mulOp2 = mulOp2;
        }

    }


    private static final Map<Long, MulOp> operandMap = new HashMap<>();

    private static boolean isInitialized = false;

    private static void tryAddOp(ArrayList<Pair<Long, MulOp>> levelLdOperations, long idAdd, MulOp odAdd) {
        if (!operandMap.containsKey(idAdd)) {
            operandMap.put(idAdd, odAdd);
            levelLdOperations.add(new Pair<>(idAdd, odAdd));
        }
    }

    private static void initialize() {
        ArrayList<ArrayList<Pair<Long, MulOp>>> operations = new ArrayList<>();
        ArrayList<Pair<Long, MulOp>> level0 = new ArrayList<>();
        tryAddOp(level0, 0, new ConstantMulOp(0));
        tryAddOp(level0, 1, VariableMulOp.getInstance());
        operations.add(level0);
        // ld -> level dest
        // id -> integer dest
        // i1 -> integer 1
        // o1 -> operand 1
        for (int ld = 1; ld <= MUL_COST; ld++) {
            ArrayList<Pair<Long, MulOp>> levelLdOperations = new ArrayList<>();
            for (int l1 = 0; l1 < ld; l1++) {
                // SHL
                for (Pair<Long, MulOp> pair1 : operations.get(l1)) {
                    long i1 = pair1.first;
                    MulOp o1 = pair1.second;
                    if (i1 == 0) continue;
                    for (int i = 0; ; i++) {
                        long idShl = i1 << i;
                        if (i != 0) {
                            Action odShl = new Action(Action.Type.SHL, o1, new ConstantMulOp(i));
                            tryAddOp(levelLdOperations, idShl, odShl);
                        }
                        if ((idShl & (1L << 63)) != 0) break;
                    }
                }
                for (int l2 = 0; l1 + l2 < ld; l2++) {
                    // ADD, SUB
                    for (Pair<Long, MulOp> pair1 : operations.get(l1)) {
                        long i1 = pair1.first;
                        MulOp o1 = pair1.second;
                        for (Pair<Long, MulOp> pair2 : operations.get(l2)) {
                            long i2 = pair2.first;
                            MulOp o2 = pair2.second;
                            long idAdd = i1 + i2;
                            Action odAdd = new Action(Action.Type.ADD, o1, o2);
                            tryAddOp(levelLdOperations, idAdd, odAdd);
                            long idSub = i1 - i2;
                            Action odSub = new Action(Action.Type.SUB, o1, o2);
                            tryAddOp(levelLdOperations, idSub, odSub);
                        }
                    }
                }
            }
            operations.add(levelLdOperations);
        }
        for (ArrayList<Pair<Long, MulOp>> operationList : operations) {
            for (Pair<Long, MulOp> pair : operationList) {
                operandMap.put(pair.first, pair.second);
            }
        }
        isInitialized = true;
    }

    private static MulOp makePlan(long multipliedConstant) {
        if (!isInitialized) initialize();
        return operandMap.getOrDefault(multipliedConstant, MulFinal.getInstance());
    }
}
