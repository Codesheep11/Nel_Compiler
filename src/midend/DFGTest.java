//package midend;
//
//import mir.BasicBlock;
//import mir.Function;
//import mir.Instruction;
//import mir.Module;
//import mir.Type;
//import mir.Value;
//
//import java.util.ArrayList;
//
//public class DFGTest {
//
//    private static void buildCFG() {
//        Module module = new Module(new ArrayList<>(), new ArrayList<>());
//        Function func = new Function(Type.FunctionType.FUNC_TYPE, "main");
//        BasicBlock A = new BasicBlock("A", func);
//        BasicBlock B = new BasicBlock("B", func);
//        BasicBlock C = new BasicBlock("C", func);
//        BasicBlock D = new BasicBlock("D", func);
//        BasicBlock E = new BasicBlock("E", func);
//        BasicBlock F = new BasicBlock("F", func);
//        BasicBlock G = new BasicBlock("G", func);
//        Value cond = new Value(Type.BasicType.I1_TYPE);
//        Instruction branch_A = new Instruction.Branch(A,cond, B, G);
//        Instruction branch_B = new Instruction.Branch(B,cond, C, E);
//        Instruction jump_C = new Instruction.Jump(C, D);
//        Instruction jump_D = new Instruction.Jump(D, E);
//        Instruction jump_E = new Instruction.Jump(E, F);
//        Instruction jump_F = new Instruction.Jump(F, G);
//        Instruction ret_G = new Instruction.Return(G);
//        module.addFunction(func);
//        // 遍历指令
//        for (Function function : module.getFuncSet()) {
//            for (BasicBlock block : function.getBlocks()) {
//                for (Instruction instruction : block.getInstructions()) {
//                    System.out.println(instruction.getDescriptor());
//                }
//            }
//        }
//        System.out.println("entry is:" + func.getEntry().getLabel());
//        func.buildControlFlowGraph();
//        System.out.println("entry is:" + func.getEntry().getLabel());
//        func.buildDominanceGraph();
//    }
//
//    public static void main(String[] args) {
//        System.out.println("Hello World!");
//        buildCFG();
//
//    }
//}
