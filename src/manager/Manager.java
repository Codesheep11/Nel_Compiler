package manager;

import backend.allocater.Allocater;
import backend.riscv.riscvModule;
import frontend.Visitor;
import frontend.lexer.Lexer;
import frontend.lexer.TokenArray;
import frontend.syntaxChecker.Ast;
import frontend.syntaxChecker.Parser;
import midend.*;
import mir.Function;
import mir.GlobalVariable;
import mir.Ir2RiscV.CodeGen;
import mir.Module;
import mir.Type;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Manager {

    public final Arg arg;

    private final ArrayList<String> outputList = new ArrayList<>();

    public Manager(Arg arg) {
        this.arg = arg;
    }

    public void run() {
        try {
            // lex
            // System.err.println("Lexer here");
            BufferedInputStream src = new BufferedInputStream(arg.srcStream);
            TokenArray tokenArray = new TokenArray();
            Lexer lexer = new Lexer(src, tokenArray);
            lexer.lex();
            //System.err.println("Lexer work well, now it is parser");
            // parse
            //System.err.println("Parser here");
            Parser parser = new Parser(tokenArray);
            Ast ast = parser.parseAst();
            //System.err.println("Parser work well, now it is visitor");

            // visit
            //System.err.println("Visitor here");
            Visitor visitor = new Visitor();
            visitor.visitAst(ast);
            //visitor.getManager().outputLLVM(manager.arg.outPath);
            //System.err.println("Visitor work well, now it is midend");
            // midend
            //System.err.println("Midend here");
            Module module = visitor.module;
            if (arg.opt) {
                //mem2reg
                Mem2Reg.run(module);
                //inline
                FunctionInline.run(module);
                //LoopNestTreeBuilder
                LoopForsetBuild.build(module);
//                LoopInvariantCodeMotion
                LoopInVarLift.run(module);
//                LCSSA
                LCSSA.run(module);

                LCSSA.remove(module);

                //dead code delete
                for (Function function : module.getFuncSet()) {
                    if (function.isExternal()) {
                        continue;
                    }
                    function.buildControlFlowGraph();
                }
                DeadCodeDelete.run(module);

//                RemovePhi.run(module);
            }
            for (Function function : module.getFuncSet()) {
                if (function.isExternal()) {
                    continue;
                }
                function.buildControlFlowGraph();
            }
            if (arg.LLVM) outputLLVM(arg.outPath, module);
            else {
                CodeGen codeGen = new CodeGen();
                riscvModule riscvmodule = codeGen.genCode(module);
                Allocater.run(riscvmodule);
                outputRiscv(arg.outPath, riscvmodule);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(e.getClass().getSimpleName().length());
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


    public void outputRiscv(String outpath, riscvModule module) throws FileNotFoundException {
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
        for (Map.Entry<String, Function> functionEntry :
                functions.entrySet()) {
            Function function = functionEntry.getValue();
            if (function.isDeleted() || function.isExternal()) {
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
