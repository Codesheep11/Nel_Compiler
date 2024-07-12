package midend.Transform.Array;

import mir.*;
import mir.Module;

public class LocalArrayLift {
    public static void run(Module module) {
        for (Function func : module.getFuncSet()) {
            for (BasicBlock block : func.getBlocks()) {
                for (Instruction instr : block.getInstructions()) {
                    if (instr instanceof Instruction.Alloc) {

                    }
                }
            }
        }

    }
}