package backend.riscv;

import java.util.ArrayList;
import java.util.Arrays;

public class RiscvFloat extends RiscvGlobalVar {
    // 浮点数,初值按照bits存储
    private final int floatBits;

    // 字面量计数器,用来计算其中的字面量的数目
    public static int literalCount = 0;

    public RiscvFloat(String name, float data) {
        super(name, GlobType.FLOAT);
        this.floatBits = Float.floatToIntBits(data);
    }

    public boolean equalFloat(Float floatx) {
        return (floatBits == Float.floatToIntBits(floatx));
    }

    public RiscvFloat(String name) {
        super(name, GlobType.FLOAT);
        this.floatBits = 0;
    }

    public RiscvFloat(float data) {
        super(".LC" + literalCount, GlobType.FLOAT);
        literalCount++;
        this.floatBits = Float.floatToIntBits(data);
    }

    public Integer getData() {
        return floatBits;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(".align 2\n");
        sb.append(name).append(":\n");
        if (floatBits == 0) {
            sb.append("\t" + ".zero 4");
        } else {
            sb.append("\t.word ").append(floatBits);
        }
        return sb + "\n";
    }

    @Override
    public boolean hasInit() {
        return true;// 浮点数必须是已经初始化的，否则不会存起来
    }

    @Override
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        if (floatBits == 0) {
            sb.append("\t" + ".zero 4");
        } else {
            sb.append("\t.word ").append(floatBits);
        }
        return sb.append("\n").toString();
    }

    @Override
    public int size() {
        return 4;
    }

    public static void main(String[] args) {
        ArrayList<Integer> integers = new ArrayList<>(Arrays.asList(
                1083808154,
                1066192077,
                1089680179,
                1070386381,
                1088212173,
                1008981770,
                1015222895,
                1033610723,
                1032671199,
                1090519040
        ));
        for (int i : integers) {
            System.out.println(i + " " + Float.intBitsToFloat(i));
        }


    }
}
