package manager;

import java.io.*;

public class Arg {
    public final String srcFileName;
    public final FileInputStream srcStream;
    public final String outPath;
    public boolean opt;//是否开启优化
    public final boolean LLVM;//是否输出LLVM代码

    private Arg(String src, String outPath, boolean opt, boolean LLVM) throws FileNotFoundException {
        this.srcFileName = src;
        this.srcStream = new FileInputStream(srcFileName);
        this.outPath = outPath;
        this.opt = opt;
        this.LLVM = LLVM;
    }

    public static Arg parse(String[] args) {
        String src = "", out = "";
        boolean opt = false, LLVM = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o" -> out = args[++i];
                case "-O0" -> opt = false;
                case "-O1", "-O2" -> opt = true;
                case "-S" -> LLVM = false;
                case "-emit-llvm" -> LLVM = true;
                default -> src = args[i];
            }
        }
        try {
            return new Arg(src, out, opt, LLVM);
        } catch (FileNotFoundException e) {
            printHelp();
            throw new RuntimeException(e);
        }

    }

    public static void printHelp() {
        System.err.println("Usage: compiler {(-S|-emit-llvm) -o filename} filename -On [options...]");
        System.err.println("optimize level: 0, 1 (default), 2");
    }
}
