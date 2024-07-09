package midend.Transform.DCE;

import midend.Util.FuncInfo;
import mir.*;

import java.util.ArrayList;
import java.util.LinkedList;

public class DeadArgEliminate {

    public static void run() {
        ArrayList<Function> funcs = FuncInfo.getFuncTopoSort();
        for (Function function : funcs) {
            if (function.isExternal()) continue;
            run(function);
        }
    }

    private static void run(Function function) {
        if (function.getFuncRArguments().isEmpty()) return;
//        System.out.println("DeadArgEliminate: " + function.getDescriptor());
        LinkedList<Function.Argument> removeList = new LinkedList<>();
        for (int i = 0; i < function.getFuncRArguments().size(); i++) {
            Function.Argument arg = function.getFuncRArguments().get(i);
            if (arg.use_empty()) removeList.add(arg);
            else if (FuncInfo.isRecurse.get(function)) {
                boolean hasUse = false;
                for (Use use : arg.getUses()) {
                    User user = use.getUser();
                    if (!isRecurseUser(user, i)) {
                        hasUse = true;
                        break;
                    }
                }
                if (!hasUse) removeList.add(arg);
            }
        }
        for (Function.Argument arg : removeList) {
            int idx = function.getFuncRArguments().indexOf(arg);
//            System.out.println("Arg: " + arg.getDescriptor() + " " + idx);
            LinkedList<Instruction.Call> calls = new LinkedList<>();
            for (Use use : function.getUses()) {
                User user = use.getUser();
                if (!(user instanceof Instruction.Call)) {
                    throw new RuntimeException("DeadArgEliminate: user is not Call");
                }
                Instruction.Call call = (Instruction.Call) user;
                calls.add(call);
            }
            for (Instruction.Call call : calls) {
//                System.out.println("DeadArgEliminate: " + call.getDescriptor());
                ArrayList<Value> args = new ArrayList<>();
                args.addAll(call.getParams());
                args.remove(idx);
                Instruction.Call newCall = new Instruction.Call(call.getParentBlock(), call.getDestFunction(), args);
                newCall.remove();
                call.getParentBlock().getInstructions().insertBefore(newCall, call);
                call.replaceAllUsesWith(newCall);
                call.delete();
            }
            function.getFuncRArguments().remove(idx);
            function.getArgumentsTP().remove(idx);
            function.getMyArguments().remove(idx);
        }
        for (int i = 0; i < function.getFuncRArguments().size(); i++) {
            function.getFuncRArguments().get(i).idx = i;
        }
    }

    private static boolean isRecurseUser(User user, int idx) {
        return user instanceof Instruction.Call
                && ((Instruction.Call) user).getDestFunction() == ((Instruction.Call) user).getParentBlock().getParentFunction()
                && ((Instruction.Call) user).getParams().get(idx) == ((Instruction.Call) user).getParentBlock().getParentFunction().getFuncRArguments().get(idx);
    }

}
