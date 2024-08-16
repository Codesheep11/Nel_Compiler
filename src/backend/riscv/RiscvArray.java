package backend.riscv;

import mir.Constant;

import java.util.ArrayList;

public class RiscvArray extends RiscvGlobalVar {
    //全局数组来自LLVM的全局数组
    // type指的是元素个数
    // 强制压平了,且元素只有int或者float两种
    // 浮点数也会被强制转成int的版本
    //数组的元素个数
    public int size;

    //压缩后的数组值
    //如果是浮点数则就将其强制
    public final ArrayList<Integer> values;

    //没有初始化的数组构造方法
    public RiscvArray(String name, int size, GlobType type) {
        super(name, type);
        this.size = size;
        this.values = new ArrayList<>();
    }

    //初始化的数据构造方法
    //保证init的长度大于0
    public RiscvArray(String name, int size, GlobType type, ArrayList<Constant> init) {
        super(name, type);
        this.values = new ArrayList<>();
        if (init.isEmpty()) {
            throw new RuntimeException("wrong init method");
        }
        for (int i = 0; i < size; i++) {
            if (type == GlobType.FLOAT) {
                values.add(Float.floatToIntBits((Float) (init.get(i)).getConstValue()));
            } else {
                values.add((Integer) (init.get(i)).getConstValue());
            }
        }
        this.size = size;
    }

    // 问题,是否能传递的时候精确的和0比较?
    // 最后一个0的位置
    public int indexOfLastNotZero() {
        if (values.isEmpty()) {
            return -1;
        }
//        System.out.println(values.size());
        for (int i = size - 1; i >= 0; i--) {
            if (values.get(i) != 0) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(".align 3\n");
        sb.append(name).append(":");
        int index = indexOfLastNotZero();
        for (int i = 0; i <= index; i++) {
            sb.append("\n\t" + ".word ");
            sb.append(values.get(i));
        }
        if (index != size - 1) {
            sb.append("\n\t.zero ");
            sb.append(4 * (size - index - 1));
        }
        return sb + "\n";
    }

    @Override
    public boolean hasInit() {
        int index = indexOfLastNotZero();
        return index != -1;
    }

    @Override
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        int index = indexOfLastNotZero();
        for (int i = 0; i <= index; i++) {
            sb.append("\n\t" + ".word ");
            sb.append(values.get(i));
        }
        if (index != size - 1) {
            sb.append("\n\t.zero ");
            sb.append(4 * (size - index - 1));
        }
        return sb.append("\n").toString();
    }

    @Override
    public int size() {
        return size;
    }
}
