package midend.Transform.DCE;

import midend.Analysis.AnalysisManager;
import midend.Analysis.FuncAnalysis;
import mir.*;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * 死参数消除
 */
public class DeadArgEliminate {

    /**
     *
     */
    public static boolean run() {
        boolean modified = false;
        ArrayList<Function> funcs = FuncAnalysis.getFuncTopoSort();
        for (Function function : funcs) {
            if (function.isExternal()) continue;
            modified |= runOnFunc(function);
        }
        return modified;
    }

    private static boolean runOnFunc(Function function) {
        boolean modified = false;
        if (function.getFuncRArguments().isEmpty()) return false;
//        System.out.println("DeadArgEliminate: " + function.getDescriptor());
        LinkedList<Function.Argument> removeList = new LinkedList<>();
        for (int i = 0; i < function.getFuncRArguments().size(); i++) {
            Function.Argument arg = function.getFuncRArguments().get(i);
            if (arg.use_empty()) removeList.add(arg);
            else if (AnalysisManager.getFuncInfo(function).isRecursive) {
                boolean hasUse = false;
                for (Instruction user : arg.getUsers()) {
                    if (!isRecurseUser(user, i, arg)) {
                        hasUse = true;
                        break;
                    }
                }
                if (!hasUse) removeList.add(arg);
            }
        }
        for (Function.Argument arg : removeList) {
            int idx = function.getFuncRArguments().indexOf(arg);
            LinkedList<Instruction.Call> calls = new LinkedList<>();
            for (Use use : function.getUses()) {
                User user = use.getUser();
                if (!(user instanceof Instruction.Call call)) {
                    throw new RuntimeException("DeadArgEliminate: user is not Call");
                }
                calls.add(call);
            }
            for (Instruction.Call call : calls) {
//                System.out.println("DeadArgEliminate: " + call.getDescriptor());
                ArrayList<Value> args = new ArrayList<>(call.getParams());
                args.remove(idx);
                Instruction.Call newCall = new Instruction.Call(call.getParentBlock(), call.getDestFunction(), args);
                newCall.remove();
                call.getParentBlock().insertInstBefore(newCall, call);
                call.replaceAllUsesWith(newCall);
                call.delete();
            }
            function.getFuncRArguments().remove(idx);
        }
        for (int i = 0; i < function.getFuncRArguments().size(); i++) {
            function.getFuncRArguments().get(i).idx = i;
        }
        return !removeList.isEmpty();
    }

    private static boolean isRecurseUser(Instruction user, int idx, Value arg) {
        while (!(user instanceof Instruction.Call) && user.getUsers().size() == 1) {
            arg = user;
            user = user.getUsers().get(0);
        }
        return user instanceof Instruction.Call
                && ((Instruction.Call) user).getDestFunction() == user.getParentBlock().getParentFunction()
                && ((Instruction.Call) user).getParams().get(idx) == arg;
    }

}
