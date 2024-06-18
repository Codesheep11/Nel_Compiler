//package midend;
//
//import backend.riscv.riscvInstruction.B;
//import mir.BasicBlock;
//import mir.Function;
//import mir.Instruction;
//import mir.Module;
//import mir.Type;
//import mir.Value;
//
//import java.util.ArrayList;
//
//public class LoopTest {
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
//        BasicBlock H = new BasicBlock("H", func);
//        BasicBlock I = new BasicBlock("I", func);
//        BasicBlock J = new BasicBlock("J", func);
//        BasicBlock K = new BasicBlock("K", func);
//        Value cond = new Value(Type.BasicType.I1_TYPE);
//        //A-B
//        Instruction jump_1 = new Instruction.Jump(A, B);
//        //B-C D
//        Instruction branch_2 = new Instruction.Branch(B, cond, C, D);
//        //C-B
//        Instruction jump_3 = new Instruction.Jump(C, B);
//        //D-B E
//        Instruction branch_4 = new Instruction.Branch(D, cond, B, E);
//        //E-F I
//        Instruction branch_5 = new Instruction.Branch(E, cond, F, I);
//        //F-G
//        Instruction jump_6 = new Instruction.Jump(F, G);
//        //G-H F
//        Instruction branch_7 = new Instruction.Branch(G, cond, H, F);
//        //H-E K
//        Instruction branch_8 = new Instruction.Branch(H, cond, E, K);
//        //I-J
//        Instruction jump_9 = new Instruction.Jump(I, J);
//        //J-K
//        Instruction jump_10 = new Instruction.Jump(J, K);
//
//        Instruction ret_ = new Instruction.Return(K);
//        module.addFunction(func);
//        // 遍历指令
//        for (Function function : module.getFuncSet()) {
//            for (BasicBlock block : function.getBlocks()) {
//                System.out.println(block.getLabel() + ":");
//                for (Instruction instruction : block.getInstructions()) {
//                    System.out.println(instruction);
//                }
//            }
//        }
//////        System.out.println("entry is:" + func.getEntry().getLabel());
////        func.buildControlFlowGraph();
//////        System.out.println("entry is:" + func.getEntry().getLabel());
////        func.buildDominanceGraph();
////        Loop.buildLoopNestTree(A);
////        for (Loop loop : func.loops) {
////            System.out.println("header:" + loop.header.getLabel());
////            for (BasicBlock block : loop.blocks) {
////                System.out.println(block.getLabel());
////            }
////        }
////        System.out.println("Hello World!");
//        for (BasicBlock block : func.getBlocks()) {
//            System.out.println(block.getLabel());
//            block.remove();
//        }
//    }
//
//    public static void main(String[] args) {
//        System.out.println("Hello World!");
//        buildCFG();
//    }
//}
