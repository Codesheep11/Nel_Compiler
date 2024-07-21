package backend.operand;

import backend.StackManager;

public class Address extends Operand {

    private int offset;
    private boolean hasFilled;

    private final String regName;

    private final int byteSize;

    public Address(String regName, int byteSize,String func) {
        this.regName = regName;
        this.byteSize = byteSize;
        this.offset = 0;
        this.hasFilled = false;
        StackManager.arrangeAddress(func,this);
    }

    public Address(String regName, int offset, int byteSize,String func) {
        this.regName = regName;
        this.byteSize = byteSize;
        this.offset = offset;
        this.hasFilled = true;
        StackManager.arrangeAddress(func,this);
    }

    // 此方法是专门为了LS设置的，因此绑定的寄存器选择绑定t0，byteSize 64
    public Address(int offset,String func) {
        this.regName = "t0";
        this.byteSize = 64;
        this.offset = offset;
        hasFilled = true;
        StackManager.arrangeAddress(func,this);
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
