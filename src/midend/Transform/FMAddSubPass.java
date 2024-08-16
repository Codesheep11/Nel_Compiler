package midend.Transform;

import mir.*;
import mir.Module;

import java.util.ArrayList;

public class FMAddSubPass {

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc2FAS(function);
            runOnFunc2FNEG(function);
            runOnFunc2FNM(function);
        }
    }

    public static void runOnFunc2FAS(Function function) {
        for (BasicBlock block : function.getBlocks()) {
            runOnBlock2FAS(block);
        }
    }

    private static void runOnFunc2FNEG(Function function) {
        for (BasicBlock block : function.getBlocks()) {
            runOnBlock2FNEG(block);
        }
    }

    private static void runOnFunc2FNM(Function function) {
        for (BasicBlock block : function.getBlocks()) {
            runOnBlock2FNM(block);
        }
    }

    private static void runOnBlock2FAS(BasicBlock block) {
        ArrayList<Instruction> snaps = block.getInstructionsSnap();
        for (Instruction instr : snaps) {
            if (instr instanceof Instruction.FAdd fadd) {
                if (fadd.getOperand_1() instanceof Instruction.FMul fmul && fmul.getUsers().size() == 1) {
//                    System.out.println("FMADD");
                    Value fmulOp1 = fmul.getOperand_1();
                    Value fmulOp2 = fmul.getOperand_2();
                    Value faddOp2 = fadd.getOperand_2();
                    Instruction.Fmadd fmadd = new Instruction.Fmadd(block, fadd.getType(), fmulOp1, fmulOp2, faddOp2);
                    fmadd.remove();
                    block.insertInstBefore(fmadd, fadd);
                    fadd.replaceAllUsesWith(fmadd);
                }
                else if (fadd.getOperand_2() instanceof Instruction.FMul fmul && fmul.getUsers().size() == 1) {
//                    System.out.println("FMADD");
                    Value fmulOp1 = fmul.getOperand_1();
                    Value fmulOp2 = fmul.getOperand_2();
                    Value faddOp1 = fadd.getOperand_1();
                    Instruction.Fmadd fmadd = new Instruction.Fmadd(block, fadd.getType(), fmulOp1, fmulOp2, faddOp1);
                    fmadd.remove();
                    block.insertInstBefore(fmadd, fadd);
                    fadd.replaceAllUsesWith(fmadd);
                }
            }
            else if (instr instanceof Instruction.FSub fsub) {
                //f1*f2-f3
                if (fsub.getOperand_1() instanceof Instruction.FMul fmul && fmul.getUsers().size() == 1) {
//                    System.out.println("FMSUB");
                    Value fmulOp1 = fmul.getOperand_1();
                    Value fmulOp2 = fmul.getOperand_2();
                    Value fsubOp2 = fsub.getOperand_2();
                    Instruction.Fmsub fmsub = new Instruction.Fmsub(block, fsub.getType(), fmulOp1, fmulOp2, fsubOp2);
                    fmsub.remove();
                    block.insertInstBefore(fmsub, fsub);
                    fsub.replaceAllUsesWith(fmsub);
                }
            }
        }
    }

    private static void runOnBlock2FNEG(BasicBlock block) {
        ArrayList<Instruction> snaps = block.getInstructionsSnap();
        for (Instruction instr : snaps) {
            if (instr instanceof Instruction.FMul fmul) {
                if (fmul.getOperand_1().equals(new Constant.ConstantFloat(-1))) {
//                    System.out.println("FNEG");
                    Value fmulOp2 = fmul.getOperand_2();
                    Instruction.Fneg fneg = new Instruction.Fneg(block, fmul.getType(), fmulOp2);
                    fneg.remove();
                    block.insertInstBefore(fneg, fmul);
                    fmul.replaceAllUsesWith(fneg);
                }
                else if (fmul.getOperand_2().equals(new Constant.ConstantFloat(-1))) {
//                    System.out.println("FNEG");
                    Value fmulOp1 = fmul.getOperand_1();
                    Instruction.Fneg fneg = new Instruction.Fneg(block, fmul.getType(), fmulOp1);
                    fneg.remove();
                    block.insertInstBefore(fneg, fmul);
                    fmul.replaceAllUsesWith(fneg);
                }
            }
            else if (instr instanceof Instruction.FSub fsub) {
                if (fsub.getOperand_1().equals(new Constant.ConstantFloat(0))) {
//                    System.out.println("FNEG");
                    Value fsubOp2 = fsub.getOperand_2();
                    Instruction.Fneg fneg = new Instruction.Fneg(block, fsub.getType(), fsubOp2);
                    fneg.remove();
                    block.insertInstBefore(fneg, fsub);
                    fsub.replaceAllUsesWith(fneg);
                }
            }
        }
    }

    private static void runOnBlock2FNM(BasicBlock block) {
        ArrayList<Instruction> snaps = block.getInstructionsSnap();
        for (Instruction instr : snaps) {
            if (instr instanceof Instruction.Fneg fneg) {
                if (fneg.getOperand() instanceof Instruction.Fmadd fmadd && fmadd.getUsers().size() == 1) {
//                    System.out.println("FNMADD");
                    Value fmaddOp1 = fmadd.getOperand_1();
                    Value fmaddOp2 = fmadd.getOperand_2();
                    Value fmaddOp3 = fmadd.getOperand_3();
                    Instruction.Fnmadd fnmadd = new Instruction.Fnmadd(block, fmadd.getType(), fmaddOp1, fmaddOp2, fmaddOp3);
                    fnmadd.remove();
                    block.insertInstBefore(fnmadd, fneg);
                    fneg.replaceAllUsesWith(fnmadd);
                }
                else if (fneg.getOperand() instanceof Instruction.Fmsub fmsub && fmsub.getUsers().size() == 1) {
//                    System.out.println("FNMSUB");
                    Value fmsubOp1 = fmsub.getOperand_1();
                    Value fmsubOp2 = fmsub.getOperand_2();
                    Value fmsubOp3 = fmsub.getOperand_3();
                    Instruction.Fnmsub fnmsub = new Instruction.Fnmsub(block, fmsub.getType(), fmsubOp1, fmsubOp2, fmsubOp3);
                    fnmsub.remove();
                    block.insertInstBefore(fnmsub, fneg);
                    fneg.replaceAllUsesWith(fnmsub);
                }
            }
            if (instr instanceof Instruction.FSub fsub) {
                //f3-f1*f2
                if (fsub.getOperand_2() instanceof Instruction.FMul fmul && fmul.getUsers().size() == 1) {
//                    System.out.println("FNMSUB");
                    Value fmulOp1 = fmul.getOperand_1();
                    Value fmulOp2 = fmul.getOperand_2();
                    Value fsubOp1 = fsub.getOperand_1();
                    Instruction.Fnmsub fnmsub = new Instruction.Fnmsub(block, fsub.getType(), fmulOp1, fmulOp2, fsubOp1);
                    fnmsub.remove();
                    block.insertInstBefore(fnmsub, fsub);
                    fsub.replaceAllUsesWith(fnmsub);
                }
            }
        }
    }

}
