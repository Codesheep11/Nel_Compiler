package mir.Ir2RiscV;

import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvInstruction.Li;
import backend.riscv.RiscvInstruction.R2;
import backend.riscv.RiscvInstruction.R3;
import backend.riscv.RiscvInstruction.RiscvInstruction;

import java.util.ArrayList;


public class MulPlaner {
    private static final int MUL_COST = 5;

    private static boolean mulEval(int x, boolean isNeg) {
        return 2 * Integer.bitCount(x) < MUL_COST;
    }

    public static void MulConst(Reg ans, Reg src, int mulr) {
        if (!DivRemByConstant.isO1) return;
        RiscvBlock block = CodeGen.nowBlock;
        if (mulr == 0) {
            block.riscvInstructions.addLast(new R2(block, ans, Reg.getPreColoredReg(Reg.PhyReg.zero, 32), R2.R2Type.mv));
            return;
        } else if (mulr == 1) {
            block.riscvInstructions.addLast(new R2(block, ans, src, R2.R2Type.mv));
            return;
        } else if (mulr == -1) {
            block.riscvInstructions.addLast(new R3(block, ans, Reg.getPreColoredReg(Reg.PhyReg.zero, 32), src, R3.R3Type.subw));
            return;
        }
        boolean isNeg = mulr < 0;
        mulr = isNeg ? -mulr : mulr;
        if (DivRemByConstant.isPowerOf2(mulr)) {
            int x = DivRemByConstant.log2(mulr);
            block.riscvInstructions.addLast(new R3(block, ans, src, new Imm(x), R3.R3Type.slliw));
        } else {
            if (mulr == 3) {
                block.riscvInstructions.addLast(new R3(block, ans, src, src, R3.R3Type.sh1add));
            } else if (mulr == 5) {
                block.riscvInstructions.addLast(new R3(block, ans, src, src, R3.R3Type.sh2add));
            } else if (mulr == 9) {
                block.riscvInstructions.addLast(new R3(block, ans, src, src, R3.R3Type.sh3add));
            } else {
                // 如果足以完成优化
                block.riscvInstructions.addLast(new Li(block, ans, new Imm(isNeg ? -mulr : mulr)));
                block.riscvInstructions.addLast(new R3(block, ans, src, ans, R3.R3Type.mulw));
                return;
            }
        }
        if (isNeg) {
            block.riscvInstructions.addLast(new R3(block, ans, Reg.getPreColoredReg(Reg.PhyReg.zero, 32), ans, R3.R3Type.subw));
        }
    }

    // 这里保证value大于0
    private static ArrayList<RiscvInstruction> recurMul(int value) {
        return null;
    }
}
