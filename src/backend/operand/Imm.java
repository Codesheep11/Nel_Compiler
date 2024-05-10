package backend.operand;

import java.util.HashSet;

public class Imm extends Operand {

    private int val;

    public Imm(int imm) {
        //严格限制立即数的范围
        // FIXME: 似乎没有考虑浮点数
        // imm 不考虑浮点数
        if (!(imm < 2048 && imm >= -2048)) {
            throw new RuntimeException("imm not in range");
        }
        this.val = imm;
    }

    @Override
    public String toString() {
        return Integer.toString(val);
    }

    public int getVal() {
        return val;
    }
}
