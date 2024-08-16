package backend.riscv;

import java.util.ArrayList;

/**
 * 字符串常量
 * 置于.data段
 */
public class RiscvString extends RiscvGlobalVar {
    private final String data;

    public static int count = 0;

    public RiscvString(String data) {
        super(".str_" + (count + 1), GlobType.STRING);
        this.data = data;
        count++;
        RS.add(this);
    }

    public static final ArrayList<RiscvString> RS = new ArrayList<>();

    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        return ".align 3\n"+name + ":\n\t" +
                ".string " + data + "\n";
    }

    @Override
    public boolean hasInit() {
        return super.hasInit();
    }
}
