//package midend;
//
//import mir.*;
//import mir.Module;
//
///**
// * based on LCSSA /<br>
// */
//public class LoopUnRoll {
//    private static int UnRollLine = 3000;
//
//    public static void run(Module module) {
//        for (Function function : module.getFuncSet()) {
//            loopUnRollForFunc(function);
//        }
//    }
//
//    public static void loopUnRollForFunc(Function function) {
//        for (Loop loop : function.loops) {
//            loopUnRollForLoop(loop);
//        }
//    }
//
//    public static void loopUnRollForLoop(Loop loop) {
//        //先对子循环进行处理
//
//    }
//
//}
