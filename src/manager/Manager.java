package manager;

import backend.Opt.BlockReSort;
import backend.Opt.CalculateOpt;
import backend.Opt.SimplifyCFG;
import backend.allocater.Allocater;
import backend.riscv.RiscvModule;
import frontend.Visitor;
import frontend.exception.SemanticError;
import frontend.exception.SyntaxError;
import frontend.lexer.Lexer;
import frontend.lexer.TokenArray;
import frontend.syntaxChecker.Ast;
import frontend.syntaxChecker.Parser;
import midend.Analysis.FuncAnalysis;
import midend.Analysis.GlobalVarAnalysis;
import midend.Transform.Array.GepFold;
import midend.Transform.DCE.DeadArgEliminate;
import midend.Transform.DCE.DeadCodeEliminate;
import midend.Transform.DCE.DeadLoopEliminate;
import midend.Transform.DCE.SimplfyCFG;
import midend.Transform.Function.FunctionInline;
import midend.Transform.Function.TailCall2Loop;
import midend.Transform.GlobalCodeMotion;
import midend.Transform.GlobalValueNumbering;
import midend.Transform.Loop.IndVars;
import midend.Transform.Loop.LCSSA;
import midend.Transform.Loop.LoopInfo;
import midend.Transform.Loop.LoopUnSwitching;
import midend.Transform.Mem2Reg;
import midend.Transform.RemovePhi;
import midend.Util.FuncInfo;
import midend.Util.Print;
import mir.*;
import mir.Ir2RiscV.CodeGen;
import mir.Loop;
import mir.Module;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Manager {

    public final Arg arg;

    private final ArrayList<String> outputList = new ArrayList<>();

    public static boolean afterRegAssign = false;

    public Manager(Arg arg) {
        this.arg = arg;
    }

    public static Module module;

    public void run() {
        try {
            FrontEnd();
            if (arg.opt) {
                Mem2Reg.run(module);
                DeadCodeEliminate();
                FuncPasses();
                GlobalVarAnalysis.run(module);
//                ConstArray2Value.run(module);
                GlobalValueNumbering.run(module);
                DeadCodeEliminate.run(module);
                LoopInfo.build(module);
                GlobalCodeMotion.run(module);
                LCSSA.Run(module);
                LoopUnSwitching.run(module);
                LoopInfo.build(module);
                IndVars.run(module);
                LoopInfo.build(module);
                LCSSA.remove(module);
                GepFold.run(module);
                DeadCodeEliminate();
            }
            if (arg.LLVM) {
                outputLLVM(arg.outPath, module);
                return;
            } else {
                RemovePhi.run(module);
//                outputLLVM("test.txt", module);
                CodeGen codeGen = new CodeGen();
                RiscvModule riscvmodule = codeGen.genCode(module);
                BlockReSort.blockSort(riscvmodule);
                CalculateOpt.run(riscvmodule);
//                Scheduler.preRASchedule(riscvmodule);
                outputRiscv("debug.txt", riscvmodule);
                Allocater.run(riscvmodule);
                afterRegAssign = true;
//                Scheduler.postRASchedule(riscvmodule);
                SimplifyCFG.run(riscvmodule);
                //BlockInline.run(riscvmodule);
                outputRiscv(arg.outPath, riscvmodule);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(e.getClass().getSimpleName().length());
        }
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
        SimplfyCFG.run(module);
        DeadCodeEliminate.run(module);
    }

    private void FuncPasses() {
        FunctionInline.run(module);
        FuncAnalysis.run(module);
        DeadArgEliminate.run();
        TailCall2Loop.run(module);
        FuncAnalysis.run(module);
    }

    public void LoopTest(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            for (Loop loop : function.loopInfo.TopLevelLoops)
                loop.LoopInfoPrint();
        }
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

        //函数声明
        for (Map.Entry<String, Function> functionEntry : functions.entrySet()) {
            if (functionEntry.getValue().isExternal()) {
                Function function = functionEntry.getValue();
                if (functionEntry.getKey().equals(FuncInfo.ExternFunc.PUTF.getName())) {
                    outputList.add("declare void @" + FuncInfo.ExternFunc.PUTF.getName() + "(ptr, ...)");
                } else {
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