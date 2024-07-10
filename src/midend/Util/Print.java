package midend.Util;

import manager.Manager;
import mir.Function;
import mir.GlobalVariable;
import mir.Module;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Print {

    public static void outputLLVM(Function func, String filepath) {
        try {
            OutputStream out = new FileOutputStream(filepath);
            ArrayList<String> outputList = new ArrayList<>(func.output());
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
}
