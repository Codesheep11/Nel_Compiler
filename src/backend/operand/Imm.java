package backend.operand;

public class Imm extends Operand {

    private int val;

    public Imm(int imm) {
        this.val = imm;
    }

    @Override
    public String toString() {
        return Integer.toString(val);
    }

    public int getVal() {
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
