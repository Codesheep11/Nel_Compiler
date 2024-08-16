package midend.Util;

import midend.Analysis.AlignmentAnalysis;
import mir.Function;
import mir.GlobalVariable;
import mir.Module;
import mir.Value;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Print {

    public static void output(Module module, String filepath) {
        try {
            OutputStream out = new FileOutputStream(filepath);
            ArrayList<String> outputList = new ArrayList<>();
            HashMap<String, Function> functions = module.getFunctions();
            ArrayList<String> globalStrings = module.getGlobalStrings();
            ArrayList<GlobalVariable> globalVariables = module.getGlobalValues();
            for (int i = 0; i < globalStrings.size(); i++) {
                outputList.add("@.str_" + (i + 1) + " = constant [" + str2llvmIR(globalStrings.get(i)));
            }
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
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void output(Function func, String filepath) {
        try {
            OutputStream out = new FileOutputStream(filepath);
            ArrayList<String> outputList = new ArrayList<>(func.output());
            streamOutput(out, outputList);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void printAlignMap(AlignmentAnalysis.AlignMap alignMap, String filepath) {
        try {
            OutputStream out = new FileOutputStream(filepath);
            ArrayList<String> outputList = new ArrayList<>();
            for (Map.Entry<Value, AlignmentAnalysis.AlignType> entry : alignMap.entrySet()) {
                outputList.add(entry.getKey().toString() + " : " + entry.getValue());
            }
            streamOutput(out, outputList);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

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

    private static String str2llvmIR(String str) {
        str = str.substring(0, str.length() - 1).replace("\\n", "\\0A");
        str += "\\00\"";
        int length = str.length() - 2;
        length -= (countOfSubStr(str, "\\0A") + countOfSubStr(str, "\\00")) * 2;
        StringBuilder sb = new StringBuilder();
        sb.append(length).append(" x i8] c");
        sb.append(str);
        return sb.toString();
    }

    private static int countOfSubStr(String str, String sub) {
        int count = 0;
        int index = str.indexOf(sub);
        while (index != -1) {
            count++;
            index = str.indexOf(sub, index + sub.length());
        }
        return count;
    }
}
