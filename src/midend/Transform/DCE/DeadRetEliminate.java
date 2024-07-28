package midend.Transform.DCE;

import mir.*;
import mir.Module;


/**
 * 返回值无用的函数，将其ret消除，并修改成void
 */
public class DeadRetEliminate {
    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        if (function.getRetType().equals(Type.VoidType.VOID_TYPE)) return;
        if (function.getName().equals("main")) return;
        // 如果函数的返回值没有用到，那么将其ret消除
        boolean hasUse = false;
        for (Instruction call : function.getUsers()) {
            if (call.getUsers().size() != 0) {
                hasUse = true;
                break;
            }
        }
        if (hasUse) return;
        // 没有用到，那么将其ret消除
        for (BasicBlock block : function.getBlocks()) {
            Instruction.Terminator terminator = block.getTerminator();
            if (terminator instanceof Instruction.Return ret) {
                ret.remove();
                new Instruction.Return(block);
            }
        }
        function.setRetType(Type.VoidType.VOID_TYPE);
        for (Instruction call : function.getUsers()) {
            call.setType(Type.VoidType.VOID_TYPE);
        }
    }
}
