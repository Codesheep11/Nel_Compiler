package backend.riscv;

public class RiscvInt extends RiscvGlobalVar {
    private final int data;

    public RiscvInt(String name, int data) {
        super(name, GlobType.INT);
        this.data = data;
    }

    public RiscvInt(String name) {
        super(name, GlobType.INT);
        this.data = 0;
    }

    public int getData() {
        return data;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(".align 2\n");
        sb.append(name).append(":\n");
        if (data == 0) {
            sb.append("\t" + ".zero 4");
        } else {
            sb.append("\t.word ").append(data);
        }
        return sb + "\n";
    }

    @Override
    public boolean hasInit() {
        return data != 0;
    }

    @Override
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        if (data == 0) {
            sb.append("\t" + ".zero 4");
        } else {
            sb.append("\t.word ").append(data);
        }
        return sb.append("\n").toString();
    }

    @Override
    public int size() {
        return 4;
    }
}
