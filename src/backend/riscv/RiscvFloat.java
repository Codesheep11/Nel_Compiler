package backend.riscv;

public class RiscvFloat extends RiscvGlobalVar {
    // 浮点数,初值按照bits存储
    private final Integer floatBits;

    // 字面量计数器,用来计算其中的字面量的数目
    public static int literalCount = 0;

    public RiscvFloat(String name, float data) {
        super(name, GlobType.FLOAT);
        this.floatBits = Float.floatToIntBits(data);
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
        sb.append(name).append(":\n");
        if (floatBits == 0) {
            sb.append("\t" + ".zero 4");
        } else {
            sb.append("\t.word ").append(floatBits);
        }
        return sb.toString()+"\n";
    }
}