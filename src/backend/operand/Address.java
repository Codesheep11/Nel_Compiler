package backend.operand;

public class Address extends Operand {

    private int offset;
    private boolean hasFilled;

    private String regName;

    private int byteSize;

    public Address(String regName, int byteSize) {
        this.regName = regName;
        this.byteSize = byteSize;
        this.offset = 0;
        this.hasFilled = false;
    }

    public Address(String regName, int offset, int byteSize) {
        this.regName = regName;
        this.byteSize = byteSize;
        this.offset = offset;
        this.hasFilled = true;
    }

    // 此方法是专门为了LS设置的，因此绑定的寄存器选择绑定t0，byteSize 64
    public Address(int offset) {
        this.regName = "t0";
        this.byteSize = 64;
        this.offset = offset;
        hasFilled = true;
    }

    public boolean hasFilled() {
        return hasFilled;
    }

    public int getOffset() {
        return offset;
    }

    public String getRegName() {
        return regName;
    }

    public int getByteSize() {
        return byteSize;
    }

    /**
     * 用于回填偏移量 仅供 StackManager类使用
     *
     * @param offset 回填偏移量
     */
    public void setOffset(int offset) {
        this.offset = offset;
        this.hasFilled = true;
    }

    @Override
    public String toString() {
        if (hasFilled) {
            return Integer.toString(-1 * offset);
        }
        return "no refilled";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Address) {
            if (hasFilled && ((Address) obj).hasFilled) {
                return this.offset == ((Address) obj).offset;
            }
        }
        return false;
    }
}
