//package midend;
//
//
//import mir.BasicBlock;
//import mir.Instruction;
//import mir.Module;
//import mir.Function;
//
//import java.util.HashMap;
//import java.util.Iterator;
//
///**
// * 尾递归优化
// * 函数分析之后做
// */
//public class TailCall2Loop {
//
//    public static HashMap<Function, Instruction.Call> tailCallFunc = new HashMap<>();
//
//    public static void run(Module module) {
//        for (Function func : module.getFuncSet()) {
//            if (!func.hasMemoryAlloc && func.isRecurse) RunOnFunc(func);
//        }
//    }
//
//    private static void RunOnFunc(Function func) {
//        if (func.hasMemoryAlloc) return;
//        for (BasicBlock block : func.getBlocks()) {
//            if (block == func.getEntry()) {
//                Instruction firstRecursiveCall = null;
//                for (Instruction instr : block.getInstructions()) {
//                    if (isRecurseCall((Instruction.Call) instr, func)) {
//                        firstRecursiveCall = instr;
//                        break;
//                    }
//                }
//                if (firstRecursiveCall != null) {
//                    Iterator<Instruction> iter = block.getInstructions().iterator();
//                    while (iter.hasNext()) {
//                        Instruction inst = iter.next();
//                        if (inst.canbeOperand()) {
//                            inst.replaceWith(new UndefinedValue(inst.getType()));
//                        }
//                    }
//                    firstRecursiveCall.remove();
//                    Instruction unreachable = new UnreachableInst();
//                    block.getInstructions().add(unreachable);
//                    return;
//                }
//                continue;
//            }
//            handleBlock(block, func);
//        }
//    }
//
//
//    private static void handleBlock(BasicBlock block, Function self) {
//        Instruction term = block.getInstructions().getLast();
//        if (term instanceof Instruction.Return) {
//            Instruction.Call lastRecursiveCall = getLastRecursiveCall(block, self);
//            if (lastRecursiveCall != null) {
//                tailCallFunc.put(self, lastRecursiveCall);
//                return;
//            }
//        }
//        else if (term instanceof Instruction.Jump) {
//            BasicBlock target = ((Instruction.Jump) term).getTargetBlock();
//
//        }
//    }
//
//    private static void TransCall2Loop(Instruction.Call call) {
//
//    }
//
//    public static boolean isRecurseCall(Instruction.Call call, Function func) {
//        return call.getDestFunction().equals(func);
//    }
//
//    public static Instruction.Call getLastRecursiveCall(BasicBlock block, Function func) {
//        int size = block.getInstructions().getSize();
//        for (int i = size - 1; i >= 0; i--) {
//            if (block.getInstructions().get(i) instanceof Instruction.Call) {
//                Instruction.Call call = (Instruction.Call) block.getInstructions().get(i);
//                if (isRecurseCall(call, func)) return call;
//            }
//        }
//        return null;
//    }
//}
