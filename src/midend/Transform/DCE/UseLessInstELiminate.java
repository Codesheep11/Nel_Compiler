package midend.Transform.DCE;

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.ArrayList;

public class UseLessInstELiminate {
    public static boolean run(Module module) {
        boolean modified = false;
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            modified |= runOnFunc(function);
        }
        return modified;
    }

    public static boolean runOnFunc(Function function) {
        boolean modified = false;
        boolean changed = true;
        ArrayList<Instruction> uselessInst = new ArrayList<>();
        while (changed) {
            uselessInst.clear();
            for (BasicBlock block : function.getBlocks()) {
                for (Instruction inst : block.getInstructions()) {
                    switch (inst.getInstType()) {
                        case ALLOC, STORE, ATOMICADD, RETURN, JUMP, BRANCH -> {
                            continue;
                        }
                        case CALL -> {
                            Function func = ((Instruction.Call) inst).getDestFunction();
                            FuncInfo funcInfo = AnalysisManager.getFuncInfo(func);
                            if (func.isExternal() || funcInfo.hasReadIn || funcInfo.hasPutOut || funcInfo.hasSideEffect)
                                continue;
                        }
                        default -> {
                            if (inst.getUsers().isEmpty()) {
                                uselessInst.add(inst);
                            }
                        }
                    }
                }
            }
            for (Instruction inst : uselessInst) {
//                System.out.println("DCE: " + inst);
                inst.delete();
            }
            changed = !uselessInst.isEmpty();
            modified |= changed;
        }
        return modified;
    }
}
