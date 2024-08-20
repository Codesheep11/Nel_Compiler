package backend.Ir2RiscV;

import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvInstruction.ConstRemHelper;
import backend.riscv.RiscvInstruction.Li;
import backend.riscv.RiscvInstruction.R2;
import backend.riscv.RiscvInstruction.R3;
import manager.Manager;
import midend.Analysis.AnalysisManager;
import midend.Analysis.I32RangeAnalysis;
import mir.BasicBlock;
import mir.Value;

import java.math.BigInteger;

/**
 * 用于处理除法和取余的优化
 *
 * @see <a href="https://dl.acm.org/doi/abs/10.1145/178243.178249">Division by Invariant Integers using Multiplication</a>
 */
public class DivRemByConstant {


    private static RiscvBlock block;

    private static final boolean doOpt = true;


    private static final boolean Branch_Rem = true;

    /**
     * 计算log2(x) 向下取整
     *
     * @param x 正整数
     * @return log2(x)
     */
    public static int log2(int x) {
        return 31 - Integer.numberOfLeadingZeros(x);
    }

    public static boolean isPowerOf2(int x) {
        return x > 0 && ((x & (x - 1)) == 0);
    }

    public static boolean Div(Reg reg, Reg me, int val, Value value, BasicBlock par) {
        if (!Manager.isO1 || !doOpt) return false;
        I32RangeAnalysis.I32Range ir = AnalysisManager.getValueRange(value, par);
        block = CodeGen.nowBlock;
        if (ir.getMinValue() >= 0 && val >= 0) {
            return UnSignDiv(reg, me, val);
        } else {
            return SignDiv(reg, me, val);
        }
    }

    public static boolean Rem(Reg reg, Reg me, int val, Value value, BasicBlock par) {
        if (!Manager.isO1 || !doOpt) return false;
        block = CodeGen.nowBlock;
        SignRem(reg, me, val, value, par);
        return true;
    }


    private static boolean UnSignDiv(Reg ans, Reg src, int divisor) {
        if (isPowerOf2(divisor)) {
            int x = log2(divisor);
            if (x == 0) {
                // 如果是/1的话直接一个mv解决
                block.addInstLast(new R2(block, ans, src, R2.R2Type.mv));
            } else if (x >= 1 && x <= 30) {
                // 否则直接逻辑位移
                block.addInstLast(new R3(block, ans, src, new Imm(x), R3.R3Type.srliw));
            } else {
                return false;
            }
        } else {
            int l = log2(divisor);
            int sh = l;
            BigInteger temp = new BigInteger("1");
            long low = temp.shiftLeft(32 + l).divide(BigInteger.valueOf(divisor)).longValue();
            long high = temp.shiftLeft(32 + l).add(temp.shiftLeft(l + 1)).divide(BigInteger.valueOf(divisor)).longValue();
            while (((low / 2) < (high / 2)) && sh > 0) {
                low /= 2;
                high /= 2;
                sh--;
            }
            if (high < (1L << 31)) {
                // %1 = mul %src, #high
                // %2 = srai %1, #(32+sh)
                // %3 = sraiw %src, #31
                // %ans = subw %2, %3
                Reg op1 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op2 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op3 = Reg.getVirtualReg(Reg.RegType.GPR, 32);
                Reg tmp = Reg.getVirtualReg(Reg.RegType.GPR, 64);
                block.addInstLast(new Li(block, tmp, new Imm(high)));
                block.addInstLast(new R3(block, op1, src, tmp, R3.R3Type.mul));
                block.addInstLast(new R3(block, op2, op1, new Imm(32 + sh), R3.R3Type.srai));
                block.addInstLast(new R3(block, op3, op2, new Imm(31), R3.R3Type.sraiw));
                block.addInstLast(new R3(block, ans, op2, op3, R3.R3Type.subw));
            } else {
                high = high - (1L << 32);
                // %1 = mul %src, #high
                // %2 = srai %1, #32
                // %3 = addw %2, %src
                // %4 = sariw %3, #sh
                // %5 = sariw %src, #31
                // %ans = subw %4, %5
                Reg op1 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op2 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op3 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op4 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op5 = Reg.getVirtualReg(Reg.RegType.GPR, 32);
                Reg tmp = Reg.getVirtualReg(Reg.RegType.GPR, 64);
                block.addInstLast(new Li(block, tmp, new Imm(high)));
                block.addInstLast(new R3(block, op1, src, tmp, R3.R3Type.mul));
                block.addInstLast(new R3(block, op2, op1, new Imm(32), R3.R3Type.srai));
                block.addInstLast(new R3(block, op3, op2, src, R3.R3Type.addw));
                block.addInstLast(new R3(block, op4, op3, new Imm(sh), R3.R3Type.sraiw));
                block.addInstLast(new R3(block, op5, src, new Imm(31), R3.R3Type.sraiw));
                block.addInstLast(new R3(block, ans, op4, op5, R3.R3Type.subw));
            }
        }
        return true;
    }


    private static boolean SignDiv(Reg regAns, Reg src, int divisor) {
        boolean isDivisorNeg = divisor < 0;
        divisor = isDivisorNeg ? -divisor : divisor;
        Reg ans = isDivisorNeg ? Reg.getVirtualReg(Reg.RegType.GPR, 32) : regAns;
        if (isPowerOf2(divisor)) {
            int x = log2(divisor);
            if (x == 0) {
                block.addInstLast(new R2(block, ans, src, R2.R2Type.mv));
            } else if (x >= 1 && x <= 30) {
                // %1 = srli %src, #(64-x)
                // %2 = addw %src, %1
                // %ans = sraiw %2, #x
                Reg op1 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op2 = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
                block.addInstLast(new R3(block, op1, src, new Imm(64 - x), R3.R3Type.srli));
                block.addInstLast(new R3(block, op2, src, op1, R3.R3Type.addw));
                block.addInstLast(new R3(block, ans, op2, new Imm(x), R3.R3Type.sraiw));
            } else {
                return false;
            }
        } else {
            int l = log2(divisor);
            int sh = l;
            BigInteger temp = new BigInteger("1");
            long low = temp.shiftLeft(32 + l).divide(BigInteger.valueOf(divisor)).longValue();
            long high = temp.shiftLeft(32 + l).add(temp.shiftLeft(l + 1)).divide(BigInteger.valueOf(divisor)).longValue();
            while (((low / 2) < (high / 2)) && sh > 0) {
                low /= 2;
                high /= 2;
                sh--;
            }
            if (high < (1L << 31)) {
                // %1 = mul %src, #high
                // %2 = srai %1, #(32+sh)
                // %3 = sraiw %src, #31
                // %ans = subw %2, %3
                Reg op1 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op2 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op3 = Reg.getVirtualReg(Reg.RegType.GPR, 32);
                Reg tmp = Reg.getVirtualReg(Reg.RegType.GPR, 64);
                block.addInstLast(new Li(block, tmp, new Imm(high)));
                block.addInstLast(new R3(block, op1, src, tmp, R3.R3Type.mul));
                block.addInstLast(new R3(block, op2, op1, new Imm(32 + sh), R3.R3Type.srai));
                block.addInstLast(new R3(block, op3, op2, new Imm(31), R3.R3Type.sraiw));
                block.addInstLast(new R3(block, ans, op2, op3, R3.R3Type.subw));
            } else {
                high = high - (1L << 32);
                // %1 = mul %src, #high
                // %2 = srai %1, #32
                // %3 = addw %2, %src
                // %4 = sariw %3, #sh
                // %5 = sariw %src, #31
                // %ans = subw %4, %5
                Reg op1 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op2 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op3 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op4 = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                Reg op5 = Reg.getVirtualReg(Reg.RegType.GPR, 32);
                Reg tmp = Reg.getVirtualReg(Reg.RegType.GPR, 64);
                block.addInstLast(new Li(block, tmp, new Imm(high)));
                block.addInstLast(new R3(block, op1, src, tmp, R3.R3Type.mul));
                block.addInstLast(new R3(block, op2, op1, new Imm(32), R3.R3Type.srai));
                block.addInstLast(new R3(block, op3, op2, src, R3.R3Type.addw));
                block.addInstLast(new R3(block, op4, op3, new Imm(sh), R3.R3Type.sraiw));
                block.addInstLast(new R3(block, op5, src, new Imm(31), R3.R3Type.sraiw));
                block.addInstLast(new R3(block, ans, op4, op5, R3.R3Type.subw));
            }
        }
        if (isDivisorNeg) {
            block.addInstLast(new R3(
                    block, regAns, Reg.getPreColoredReg
                    (Reg.PhyReg.zero, 64), ans, R3.R3Type.subw));
        }
        return true;
    }


    private static void SignRem(Reg ans, Reg src, int divisor, Value value, BasicBlock par) {
        I32RangeAnalysis.I32Range ir = AnalysisManager.getValueRange(value, par);
        if (isPowerOf2(divisor) && (ir.getMinValue() >= 0 || Branch_Rem)) {
            if (ir.getMinValue() >= 0) {
                int mask = divisor - 1;
                if (mask >= 2047) {
                    Reg tmp = Reg.getVirtualReg(Reg.RegType.GPR, 32);
                    block.addInstLast(new Li(block, tmp, new Imm(mask)));
                    block.addInstLast(new R3(block, ans, src, tmp, R3.R3Type.and));
                } else {
                    block.addInstLast(new R3(block, ans, src, new Imm(mask), R3.R3Type.andi));
                }
            } else {
                block.addInstLast(new ConstRemHelper(block, src, ans, divisor));
                if (divisor >= 2047) {
                    int x = log2(divisor);
                    block.addInstLast(new R3(block, ans, ans, new Imm(x), R3.R3Type.sraiw));
                    block.addInstLast(new R3(block, ans, ans, new Imm(x), R3.R3Type.slli));
                } else {
                    block.addInstLast(new R3(block, ans, ans, new Imm(-divisor), R3.R3Type.andi));
                }
                block.addInstLast(new R3(block, ans, src, ans, R3.R3Type.subw));
            }
        } else {
            // 当作一个除法+乘法+减法优化
            // 问题:如果被除数是负数?也是负数即可
            // 如果除数是负数?
            Reg tmp = Reg.getVirtualReg(Reg.RegType.GPR, 32);
            Reg store = Reg.getVirtualReg(Reg.RegType.GPR, 32);
            boolean ret = SignDiv(store, src, divisor);
            if (!ret) {// 如果失败,补偿一个divw
                block.addInstLast(new R3(block, store, src, tmp, R3.R3Type.divw));
            }
            MulPlaner.MulConst(tmp, store, divisor);
            block.addInstLast(new R3(block, ans, src, tmp, R3.R3Type.subw));
        }
    }
}
