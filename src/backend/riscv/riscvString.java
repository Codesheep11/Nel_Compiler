package backend.riscv;

import mir.Type;

import java.util.ArrayList;

/**
 * 字符串常量
 * 置于.data段
 */
public class riscvString extends riscvGlobalVar {
    private final String data;

    public static int count = 0;

    public riscvString(String data) {
        super(".str_" + (count + 1), GlobType.STRING);
        this.data = data;
        count++;
        RS.add(this);
    }

    public static ArrayList<riscvString> RS = new ArrayList<>();

    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        return name + ":\n\t" +
                ".string " + data + "\n";
    }
}
