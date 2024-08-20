package manager;

import backend.Ir2RiscV.AfterRA;
import backend.Ir2RiscV.CodeGen;
import backend.Ir2RiscV.RemoveCRH;
import backend.Opt.BackLoop.LoopConstLift;
import backend.Opt.CalculateOpt;
import backend.Opt.CfgOpt.BlockInline;
import backend.Opt.CfgOpt.BlockReSort;
import backend.Opt.CfgOpt.SimplifyCFG;
import backend.Opt.GPpooling.GlobalFloat2roPool;
import backend.Opt.MemoryOpt.KnownBaseLSOpt;
import backend.Opt.MemoryOpt.RegAftExternCallLoadOpt;
import backend.Opt.MemoryOpt.UnknownBaseLSOpt;
import backend.allocator.Allocator;
import backend.riscv.RiscvModule;
import frontend.Visitor;
import frontend.exception.SemanticError;
import frontend.exception.SyntaxError;
import frontend.lexer.Lexer;
import frontend.lexer.TokenArray;
import frontend.syntaxChecker.Ast;
import frontend.syntaxChecker.Parser;
import midend.Analysis.AlignmentAnalysis;
import midend.Analysis.AnalysisManager;
import midend.Analysis.FuncAnalysis;
import midend.Transform.*;
import midend.Transform.Array.*;
import midend.Transform.DCE.*;
import midend.Transform.Function.CountArgCache;
import midend.Transform.Function.FuncCache;
import midend.Transform.Function.FunctionInline;
import midend.Transform.Function.TailCall2Loop;
import midend.Transform.Loop.*;
import midend.Util.FuncInfo;
import midend.Util.Print;
import mir.Function;
import mir.GlobalVariable;
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
        SCCP();
        Branch2MinMax.run(module);
        FuncPasses();
        GepFold.run(module);
        GlobalVarLocalize.run(module);
        FuncAnalysis.run(module);
        DeadCodeEliminate();
        ConstrainReduce.run(module);
        Cond2MinMax.run(module);
        LoopBuildAndNormalize();
        GlobalCodeMotion.run(module);
//        LoopUnSwitching.run(module);
        LCSSA.remove(module);
        LocalValueNumbering.run(module);
        SCCP();
        DeadCodeEliminate();
        ConstLoopUnRoll.run(module);
        DeadCodeEliminate();
        LCSSA.remove(module);
        ArrayPasses();
        DeadCodeEliminate();
        SCCP();
        Reassociate.run(module);
        SCCP();
        Branch2MinMax.run(module);
        DeadCodeEliminate();
        StillLoopMotion.run(module);
        CertainLoopExtract.run(module);
        LoopBuildAndNormalize();
        LICMMemory.run(module);
        LoopBuildAndNormalize();
        FinalReplacement.run(module);
        IntegerSumToMul.run(module);
        LoopBuildAndNormalize();
        LoopInterchange.run(module);
        LCSSA.remove(module);
        SCCP();
        LoopBuildAndNormalize();
        LoopNestTemp.run(module);
        LCSSA.run(module);
        SCCP();
        ConstrainReduce.run(module);
        DeadCodeEliminate();
        LoopBuildAndNormalize();
        LoopParallel.run(module);
        LCSSA.remove(module);
        FuncAnalysis.run(module);
        DeadCodeEliminate();
        LoopBuildAndNormalize();
        FinalReplacement.run(module);
        LocalValueNumbering.run(module);
        LoopUnroll.run(module);
        LCSSA.remove(module);
        ArrayPasses();
        ConstLoopUnRoll.run(module);
        SCCP();
        DeadCodeEliminate();
        FuncCache.run(module);
        FuncAnalysis.run(module);
        LoopBuildAndNormalize();
        GepLift.run(module);
        AlignmentAnalysis.run(module);
        LoopInfo.run(module);
        GlobalCodeMotion.run(module);
        LCSSA.remove(module);
        BrPredction.run(module);
        PhiMerge.run(module);
        /*--------------------------------------------------------------------------*/
        SCCP();
        DeadCodeEliminate();
        AggressivePass();
        SCCP();
        DeadCodeEliminate();
        FuncAnalysis.run(module);
        LCSSA.remove(module);
        Scheduler.run(module);
        if (arg.LLVM) {
            outputLLVM(arg.outPath, module);
            return;
        }
        RemovePhi.run(module);
        LoopInfo.run(module);
        BrPredction.run(module);
        /*--------------------------------------------------------------------------*/
        CodeGen codeGen = new CodeGen();
        RiscvModule riscvmodule = codeGen.genCode(module);
        GlobalFloat2roPool.run(riscvmodule);
        LoopConstLift.run(riscvmodule);
        CalculateOpt.runBeforeRA(riscvmodule);
        Allocator.run(riscvmodule);
        AfterRA.run(riscvmodule);
        BlockInline.run(riscvmodule);
        KnownBaseLSOpt.run(riscvmodule);
        UnknownBaseLSOpt.run(riscvmodule);
        RegAftExternCallLoadOpt.run(riscvmodule);
        CalculateOpt.runAftBin(riscvmodule);
        BlockReSort.blockSort(riscvmodule);
        SimplifyCFG.run(riscvmodule);
        RemoveCRH.run(riscvmodule);
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
        boolean modified;
        do {
            modified = false;
            modified |= DeadLoopEliminate.run(module);
            modified |= SimplifyCFGPass.run(module);
            modified |= RemoveBlocks.run(module);
            modified |= DeadCondEliminate.run(module);
            modified |= GlobalValueNumbering.run(module);
            ArithReduce.run(module);
            modified |= DeadArgEliminate.run();
            modified |= DeadRetEliminate.run(module);
            modified |= UseLessInstELiminate.run(module);
            modified |= DeadCodeEliminate.run(module);
        } while (modified);
    }

    /**
     * 稀疏条件常量传播
     * Sparse Conditional Constant Propagation
     */
    private void SCCP() {
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            boolean modified;
            do {
                modified = false;
                modified |= ConstantFolding.runOnFunc(func);
                modified |= SimplifyCFGPass.runOnFunc(func);
                if (modified) {
                    RemoveBlocks.runOnFunc(func);
                }
                modified |= RangeFolding.runOnFunc(func);
            } while (modified);
        }
        DeadCodeEliminate.run(module);
    }

    private void FuncPasses() {
        FuncAnalysis.run(module);
        Multiply.run(module);
        FuncAnalysis.run(module);
        CountArgCache.run(module);
        FuncAnalysis.run(module);
        DeadArgEliminate.run();
        TailCall2Loop.run(module);
        FunctionInline.run(module);
        FuncAnalysis.run(module);
        DeadArgEliminate.run();
        FuncAnalysis.run(module);
    }

    private void ArrayPasses() {
        FuncAnalysis.run(module);
        GepFold.run(module);
        LoadEliminate.run(module);
        StoreEliminate.run(module);
        SCCP();
        SroaPass.run(module);
        LocalArrayLift.run(module);
        ConstIdx2Value.run(module);
    }

    /**
     * 非常激进的优化，可能会导致误差错误
     */
    private void AggressivePass() {
        Branch2FMinMax.run(module);
        FABSPass.run(module);
        FMAddSubPass.run(module);
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
        outputList.add("""
                declare i32 @llvm.smax.i32(i32, i32)
                declare i32 @llvm.smin.i32(i32, i32)
                declare float @llvm.fmuladd.f32(float, float, float)
                define float @fmulsub(float %a, float %b, float %c) {
                entry:
                    %mul = fmul float %a, %b
                    %sub = fsub float %mul, %c
                    ret float %sub
                }
                define float @fnmadd(float %a, float %b, float %c) {
                entry:
                    %mul = fmul float %a, %b
                    %add = fadd float %mul, %c
                    %neg = fneg float %add
                    ret float %neg
                }
                define float @fnmsub(float %a, float %b, float %c) {
                entry:
                    %mul = fmul float %a, %b
                    %sub = fsub float %mul, %c
                    %neg = fneg float %sub
                    ret float %neg
                }"""
        );
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
        Allocator.run(riscvmodule);
        AfterRA.run(riscvmodule);
        BlockReSort.blockSort(riscvmodule);
        SimplifyCFG.run(riscvmodule);
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
