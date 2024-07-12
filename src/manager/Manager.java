package manager;

import backend.Opt.SimplifyCFG;
import backend.allocater.Allocater;
import backend.riscv.RiscvModule;
import frontend.Visitor;
import frontend.lexer.Lexer;
import frontend.lexer.TokenArray;
import frontend.syntaxChecker.Ast;
import frontend.syntaxChecker.Parser;
import midend.Analysis.FuncAnalysis;
import midend.Analysis.GlobalVarAnalysis;
import midend.Transform.*;
import midend.Transform.Array.GepFold;
import midend.Transform.DCE.DeadArgEliminate;
import midend.Transform.DCE.DeadCodeDelete;
import midend.Transform.DCE.SimplfyCFG;
import midend.Transform.Function.FunctionInline;
import midend.Transform.Function.TailCall2Loop;
import midend.Transform.Loop.LCSSA;
import midend.Transform.Loop.LoopInfo;
import midend.Transform.Loop.LoopUnSwitching;
import mir.*;
import mir.Ir2RiscV.CodeGen;
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

    public void run() {
        try {
            BufferedInputStream src = new BufferedInputStream(arg.srcStream);
            TokenArray tokenArray = new TokenArray();
            Lexer lexer = new Lexer(src, tokenArray);
            lexer.lex();
            Parser parser = new Parser(tokenArray);
            Ast ast = parser.parseAst();
            Visitor visitor = new Visitor();
            visitor.visitAst(ast);
            Module module = visitor.module;
            if (arg.opt) {
                Mem2Reg.run(module);
                FunctionInline.run(module);
                FuncAnalysis.run(module);
                DeadArgEliminate.run();
                TailCall2Loop.run(module);
                FuncAnalysis.run(module);
                GlobalVarAnalysis.run(module);
//                ConstArray2Value.run(module);
                GlobalValueNumbering.run(module);
                DeadCodeDelete.run(module);
                LoopInfo.build(module);
                GlobalCodeMotion.run(module);
                LoopUnSwitching.run(module);
                LoopInfo.build(module);
                LCSSA.remove(module);
                GepFold.run(module);
                SimplfyCFG.run(module);
                DeadCodeDelete.run(module);
            }
            if (arg.LLVM) {
                outputLLVM(arg.outPath, module);
            }
            else {
                RemovePhi.run(module);
                outputLLVM("test.txt", module);
                CodeGen codeGen = new CodeGen();
                RiscvModule riscvmodule = codeGen.genCode(module);
//                Scheduler.preRASchedule(riscvmodule);
                outputRiscv("debug.txt", riscvmodule);
                Allocater.run(riscvmodule);
                afterRegAssign = true;
//                Scheduler.postRASchedule(riscvmodule);
                SimplifyCFG.run(riscvmodule);
                outputRiscv(arg.outPath, riscvmodule);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(e.getClass().getSimpleName().length());
        }
    }

    public void LoopTest(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            for (Loop loop : function.loopInfo.TopLevelLoops)
                loop.LoopInfoPrint();
        }
    }

    public static class ExternFunc {
        public static final Function MEMSET = new Function(Type.VoidType.VOID_TYPE, "memset",
                new Type.PointerType(Type.BasicType.I32_TYPE), Type.BasicType.I32_TYPE, Type.BasicType.I32_TYPE);
        public static final Function GETINT = new Function(Type.BasicType.I32_TYPE, "getint");
        public static final Function PUTINT = new Function(Type.VoidType.VOID_TYPE, "putint", Type.BasicType.I32_TYPE);
        public static final Function GETCH = new Function(Type.BasicType.I32_TYPE, "getch");
        public static final Function GETFLOAT = new Function(Type.BasicType.F32_TYPE, "getfloat");
        public static final Function PUTCH = new Function(Type.VoidType.VOID_TYPE, "putch", Type.BasicType.I32_TYPE);
        public static final Function PUTFLOAT = new Function(Type.VoidType.VOID_TYPE, "putfloat", Type.BasicType.F32_TYPE);
        public static final Function STARTTIME = new Function(Type.VoidType.VOID_TYPE, "_sysy_starttime");
        public static final Function STOPTIME = new Function(Type.VoidType.VOID_TYPE, "_sysy_stoptime");
        public static final Function GETARRAY = new Function(Type.BasicType.I32_TYPE, "getarray", new Type.PointerType(Type.BasicType.I32_TYPE));
        public static final Function GETFARRAY = new Function(Type.BasicType.I32_TYPE, "getfarray", new Type.PointerType(Type.BasicType.F32_TYPE));
        public static final Function PUTARRAY = new Function(Type.VoidType.VOID_TYPE, "putarray", Type.BasicType.I32_TYPE, new Type.PointerType(Type.BasicType.I32_TYPE));
        public static final Function PUTFARRAY = new Function(Type.VoidType.VOID_TYPE, "putfarray", Type.BasicType.I32_TYPE, new Type.PointerType(Type.BasicType.F32_TYPE));
        public static final Function PUTF = new Function(Type.VoidType.VOID_TYPE, "putf");

        public static final HashMap<String, Function> externFunctions = new HashMap<>() {{
            put(MEMSET.getName(), MEMSET);
            put(GETINT.getName(), GETINT);
            put(PUTINT.getName(), PUTINT);
            put(GETCH.getName(), GETCH);
            put(GETFLOAT.getName(), GETFLOAT);
            put(PUTCH.getName(), PUTCH);
            put(PUTFLOAT.getName(), PUTFLOAT);
            put("starttime", STARTTIME);
            put("stoptime", STOPTIME);
            put(GETARRAY.getName(), GETARRAY);
            put(GETFARRAY.getName(), GETFARRAY);
            put(PUTARRAY.getName(), PUTARRAY);
            put(PUTFARRAY.getName(), PUTFARRAY);
            put(PUTF.getName(), PUTF);
        }};

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
                if (functionEntry.getKey().equals(ExternFunc.PUTF.getName())) {
                    outputList.add("declare void @" + ExternFunc.PUTF.getName() + "(ptr, ...)");
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
