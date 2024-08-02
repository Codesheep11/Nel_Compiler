package backend.operand;

public class Imm extends Operand {

    private final long val;

    public Imm(long imm) {
        this.val = imm;
    }

    @Override
    public String toString() {
        return Long.toString(val);
    }

    public long getVal() {
        return val;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Imm) {
            return val == ((Imm) obj).val;
        }
        return false;
    }
}
