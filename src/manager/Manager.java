package manager;

import backend.Opt.*;
import backend.Opt.BackLoop.LoopConstLift;
import backend.Opt.GPpooling.GlobalFloat2roPool;
import backend.allocater.Allocater;
import backend.riscv.RiscvModule;
import frontend.Visitor;
import frontend.exception.SemanticError;
import frontend.exception.SyntaxError;
import frontend.lexer.Lexer;
import frontend.lexer.TokenArray;
import frontend.syntaxChecker.Ast;
import frontend.syntaxChecker.Parser;
import midend.Analysis.AnalysisManager;
import midend.Analysis.FuncAnalysis;
import midend.Transform.*;
import midend.Transform.Array.ConstIdx2Value;
import midend.Transform.Array.GepFold;
import midend.Transform.Array.LocalArrayLift;
import midend.Transform.Array.SroaPass;
import midend.Transform.DCE.*;
import midend.Transform.Function.FunctionInline;
import midend.Transform.Function.TailCall2Loop;
import midend.Transform.Loop.*;
import midend.Util.FuncInfo;
import midend.Util.Print;
import mir.Function;
import mir.GlobalVariable;
import mir.Ir2RiscV.AfterRA;
import mir.Ir2RiscV.CodeGen;
import mir.Module;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Manager {

    public static Arg arg;

    private final ArrayList<String> outputList = new ArrayList<>();

    public static boolean afterRegAssign = false;

    public static boolean isO1 = false;

    public Manager(Arg arg) {
        Manager.arg = arg;
    }

    public static Module module;

    public void run() {
        try {
            FrontEnd();
            if (arg.opt) O1();
            else O0();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(e.getClass().getSimpleName().length());
        }
    }

    private void O1() throws IOException {
        isO1 = true;
        AnalysisManager.buildCFG(module);
        DeadCodeEliminate.run(module);
        Mem2Reg.run(module);
        FuncAnalysis.run(module);
        DeadCodeEliminate();
        ConstEliminate();
        Branch2MinMax.run(module);
        FuncPasses();
        GlobalVarLocalize.run(module);
        FuncAnalysis.run(module);
        GlobalValueNumbering.run(module);
        DeadCodeEliminate();
        LoopBuildAndNormalize();
        GlobalCodeMotion.run(module);
        LoopUnSwitching.run(module);
        DeadCodeEliminate();
        ConstLoopUnRoll.run(module);
        LoopUnroll.run(module);
        DeadCodeEliminate();
        LCSSA.remove(module);
        ArrayPasses();
        DeadCodeEliminate();
        ArrayPasses();
        Reassociate.run(module);
        ConstEliminate();
        Branch2MinMax.run(module);
        GlobalValueNumbering.run(module);
        RangeFolding.run(module);
        DeadCodeEliminate();
        GlobalValueNumbering.run(module);
        FuncAnalysis.run(module);
        LoopInfo.run(module);
        Scheduler.run(module);
        if (arg.LLVM) {
            outputLLVM(arg.outPath, module);
            return;
        }
        RemovePhi.run(module);
        LoopInfo.run(module);
        BrPredction.run(module);
        CodeGen codeGen = new CodeGen();
        RiscvModule riscvmodule = codeGen.genCode(module);
        GlobalFloat2roPool.run(riscvmodule);
        LoopConstLift.run(riscvmodule);
        CalculateOpt.runBeforeRA(riscvmodule);
        Allocater.run(riscvmodule);
        AfterRA.run(riscvmodule);
        BlockInline.run(riscvmodule);
        MemoryOpt.run(riscvmodule);
        CalculateOpt.runAftBin(riscvmodule);
        BlockReSort.blockSort(riscvmodule);
        SimplifyCFG.run(riscvmodule);
//        ShortInstrConvert.run(riscvmodule);
//        AfterRAScheduler.postRASchedule(riscvmodule);
        outputRiscv(arg.outPath, riscvmodule);
    }

    private void FrontEnd() throws IOException, SyntaxError, SemanticError {
        BufferedInputStream src = new BufferedInputStream(arg.srcStream);
        TokenArray tokenArray = new TokenArray();
        Lexer lexer = new Lexer(src, tokenArray);
        lexer.lex();
        Parser parser = new Parser(tokenArray);
        Ast ast = parser.parseAst();
        Visitor visitor = new Visitor();
        visitor.visitAst(ast);
        module = visitor.module;
    }

    private void DeadCodeEliminate() {
        DeadLoopEliminate.run(module);
        SimplifyCFGPass.run(module);
        GlobalValueNumbering.run(module);
        SimplifyCFGPass.run(module);
        ArithReduce.run(module);
        DeadRetEliminate.run(module);
        DeadCodeEliminate.run(module);
        SimplifyCFGPass.run(module);
    }

    private void ConstEliminate() {
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            boolean modified;
            do {
                modified = false;
                modified |= ConstantFolding.runOnFunc(func);
//                System.out.println("ConstFoling "+modified);
                modified |= SimplifyCFGPass.runOnFunc(func);
//                System.out.println("SimplifyCFGPass "+modified);
                AnalysisManager.refreshI32Range(func);
                modified |= RangeFolding.runOnFunc(func);
//                System.out.println("RangeFolding "+modified);
//                System.out.println();
            } while (modified);
        }
        DeadCodeEliminate.run(module);
    }

    private void FuncPasses() {
        FuncAnalysis.run(module);
        TailCall2Loop.run(module);
        FunctionInline.run(module);
        FuncAnalysis.run(module);
        DeadArgEliminate.run();
        FuncAnalysis.run(module);
    }

    private void ArrayPasses() {
        GepFold.run(module);
        LoadEliminate.run(module);
        StoreEliminate.run(module);
        GlobalValueNumbering.run(module);
        SroaPass.run(module);
        LocalArrayLift.run(module);
        ConstIdx2Value.run(module);
    }

    private void LoopBuildAndNormalize() {
        LCSSA.remove(module);
        LoopInfo.run(module);
        LoopSimplifyForm.run(module);
        LoopInfo.run(module);
        LCSSA.run(module);
    }


    public void outputRiscv(String outpath, RiscvModule module) throws FileNotFoundException {
        OutputStream out = new FileOutputStream(outpath);
        outputList.clear();
        outputList.add(module.toString());
        streamOutput(out, outputList);
    }

    public void outputLLVM(String name, Module module) throws FileNotFoundException {
        OutputStream out = new FileOutputStream(name);
        outputList.clear();
        HashMap<String, Function> functions = module.getFunctions();
        ArrayList<String> globalStrings = module.getGlobalStrings();
        ArrayList<GlobalVariable> globalVariables = module.getGlobalValues();
        for (int i = 0; i < globalStrings.size(); i++) {
            outputList.add("@.str_" + (i + 1) + " = constant [" + str2llvmIR(globalStrings.get(i)));
        }

        //全局变量
        for (GlobalVariable gv : globalVariables) {
            outputList.add(gv.toString());
        }
        outputList.add("declare i32 @llvm.smax.i32(i32, i32)\n" +
                "declare i32 @llvm.smin.i32(i32, i32)");
        //函数声明
        for (Map.Entry<String, Function> functionEntry : functions.entrySet()) {
            if (functionEntry.getValue().isExternal()) {
                Function function = functionEntry.getValue();
                if (functionEntry.getKey().equals(FuncInfo.ExternFunc.PUTF.getName())) {
                    outputList.add("declare void @" + FuncInfo.ExternFunc.PUTF.getName() + "(ptr, ...)");
                }
                else {
                    outputList.add(String.format("declare %s @%s(%s)", function.getRetType().toString(), functionEntry.getKey(), function.FArgsToString()));
                }
            }
        }

        //函数定义
        for (Map.Entry<String, Function> functionEntry : functions.entrySet()) {
            Function function = functionEntry.getValue();
            if (function.isExternal()) {
                continue;
            }
            outputList.addAll(function.output());
        }
        streamOutput(out, outputList);
    }

    private void O0() throws IOException {
        AnalysisManager.buildCFG(module);
        DeadCodeEliminate.run(module);
        Mem2Reg.run(module);
        FuncAnalysis.run(module);
        DeadCodeEliminate();
        FuncPasses();
        GlobalVarLocalize.run(module);
        GlobalValueNumbering.run(module);
        DeadCodeEliminate.run(module);
        LoopInfo.run(module);
        LoopSimplifyForm.run(module);
        GlobalCodeMotion.run(module);
        LCSSA.run(module);
        DeadCodeEliminate();
        LoopInfo.run(module);
        LoopSimplifyForm.run(module);
        IndVars.run(module);
        DeadCodeEliminate();
        LoopInfo.run(module);
        LCSSA.remove(module);
        ArrayPasses();
        DeadCodeEliminate();
        GlobalValueNumbering.run(module);
        FuncAnalysis.run(module);
        if (arg.LLVM) {
            outputLLVM(arg.outPath, module);
            return;
        }
        RemovePhi.run(module);
        CodeGen codeGen = new CodeGen();
        RiscvModule riscvmodule = codeGen.genCode(module);
        CalculateOpt.runBeforeRA(riscvmodule);
        Allocater.run(riscvmodule);
        AfterRA.run(riscvmodule);
        BlockReSort.blockSort(riscvmodule);
        SimplifyCFG.run(riscvmodule);
//        AfterRAScheduler.postRASchedule(riscvmodule);
        outputRiscv(arg.outPath, riscvmodule);
    }

    //region outputLLVM IR
    private int countOfSubStr(String str, String sub) {
        int count = 0;
        int index = str.indexOf(sub);
        while (index != -1) {
            count++;
            index = str.indexOf(sub, index + sub.length());
        }
        return count;
    }

    private String str2llvmIR(String str) {
        str = str.substring(0, str.length() - 1).replace("\\n", "\\0A");
        str += "\\00\"";
        int length = str.length() - 2;
        length -= (countOfSubStr(str, "\\0A") + countOfSubStr(str, "\\00")) * 2;
        StringBuilder sb = new StringBuilder();
        sb.append(length).append(" x i8] c");
        sb.append(str);
        return sb.toString();
    }

    private static void streamOutput(OutputStream fop, ArrayList<String> outputStringList) {
        OutputStreamWriter writer;
        writer = new OutputStreamWriter(fop, StandardCharsets.UTF_8);
        for (String t : outputStringList) {
            try {
                writer.append(t).append("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fop.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


//end region

}
