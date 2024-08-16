package mir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public abstract class Constant extends User {

    /***
     * llvm 中的常量
     * 抽象概念，包括整数常量，浮点数常量，数组常量，指针常量
     * 由静态内部类实现
     */
    public Constant(Type type) {
        super(type);
    }

    /***
     * 根据所属的类型返回不同值类型
     * @return java 下的常量值存储 例如 null, int, float, ArrayList
     */
    public abstract Object getConstValue();

    public abstract boolean isZero();

    @Override
    public String getDescriptor() {
        return toString();
    }

    /**
     * 布尔常量，仅I1
     * 用 java int 存储
     */
    public static class ConstantBool extends Constant {
        private static final ConstantBool _CONST_TRUE = new ConstantBool(1);
        private static final ConstantBool _CONST_FALSE = new ConstantBool(0);

        public static ConstantBool get(int val) {
            return val == 0 ? _CONST_FALSE : _CONST_TRUE;
        }

        public static ConstantBool get(boolean val) {
            return val ? _CONST_TRUE : _CONST_FALSE;
        }

        final int boolValue;//0 or 1

        private ConstantBool(int val) {
            super(Type.BasicType.I1_TYPE);
            boolValue = val;
        }

        @Override
        public Object getConstValue() {
            return boolValue;
        }

        @Override
        public boolean isZero() {
            return boolValue == 0;
        }

        @Override
        public String toString() {
            return String.valueOf(boolValue);
        }

        @Override
        public void delete() {
            throw new RuntimeException("Cannot delete constant bool");
        }

    }

    /**
     * 整数常量，仅I32
     * 用 java int 存储
     */
    public static class ConstantInt extends Constant {
        private static final HashMap<Integer, ConstantInt> intPool = new HashMap<>();

        public static ConstantInt get(int val) {
            return intPool.computeIfAbsent(val, ConstantInt::new);
        }

        private final int intValue;//当前int具体的值

        private ConstantInt(int intValue) {
            super(Type.BasicType.I32_TYPE);
            this.intValue = intValue;
        }

        public int getIntValue() {
            return intValue;
        }

        @Override
        public Object getConstValue() {
            return intValue;
        }

        @Override
        public String toString() {
            return String.valueOf(intValue);
        }

        @Override
        public boolean isZero() {
            return intValue == 0;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ConstantInt) {
                return ((ConstantInt) obj).intValue == intValue;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return intValue;
        }

        @Override
        public void delete() {
            throw new RuntimeException("Cannot delete constant int");
        }

    }

    /**
     * 浮点数常量，仅F32
     * 用 java float 存储
     */
    public static class ConstantFloat extends Constant {
        private final float floatValue;

        public ConstantFloat(float val) {
            super(Type.BasicType.F32_TYPE);
            floatValue = val;
        }

        @Override
        public Object getConstValue() {
            return floatValue;
        }

        @Override
        public String toString() {
            return String.format("0x%x", Double.doubleToRawLongBits((floatValue)));
        }

        @Override
        public boolean isZero() {
            return floatValue == 0;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ConstantFloat) {
                return ((ConstantFloat) obj).floatValue == floatValue;
            }
            return false;
        }

    }

    /**
     * 指针常量
     */
    public static class ConstantArray extends Constant {
        private final ArrayList<Constant> constArray;
        private final Type eleType;

        public ConstantArray(Type eleType, ArrayList<Constant> constArray) {
            super(new Type.ArrayType(constArray.size(), eleType));
            this.constArray = constArray;
            this.eleType = eleType;
        }

        public ConstantArray(Type.ArrayType arrayType) {
            super(arrayType);
            eleType = arrayType.getEleType();
            int size = arrayType.getSize();
            ArrayList<Constant> array = new ArrayList<>();
            if (eleType.isArrayTy())
                for (int i = 0; i < size; i++) array.add(new ConstantArray((Type.ArrayType) eleType));
            else if (eleType.isInt32Ty()) for (int i = 0; i < size; i++) array.add(ConstantInt.get(0));
            else if (eleType.isFloatTy()) for (int i = 0; i < size; i++) array.add(new ConstantFloat(0));
            else throw new RuntimeException("Type is illegal!");
            constArray = array;
        }

        @Override
        public Object getConstValue() {
            return constArray;
        }

        public Type getEleType() {
            return eleType;
        }

        @Override
        public boolean isZero() {
            for (Constant ele : constArray) {
                if (!ele.isZero())
                    return false;
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("[");
            Iterator<Constant> iter = constArray.iterator();
            while (iter.hasNext()) {
                Constant ele = iter.next();
                str.append(ele.getType()).append(" ");
                str.append(ele);
                if (iter.hasNext()) {
                    str.append(", ");
                }
            }
            str.append("]");
            return str.toString();
        }

        /***
         * 仅仅是展平， 对于展平后的每一个元素，类型为 Constant
         */
        public ArrayList<Constant> getFlattenedArray() {
            ArrayList<Constant> flatten = new ArrayList<>();
            for (Constant ele : constArray) {
                if (ele instanceof ConstantArray) {
                    flatten.addAll(((ConstantArray) ele).getFlattenedArray());
                }
                else if (ele instanceof ConstantZeroInitializer) {
                    //如果元素为未初始化数组，则直接返回展开的0
                    for (int i = 0; i < ((Type.ArrayType) eleType).getFlattenSize(); i++) {
                        flatten.add(ConstantInt.get(0));
                    }
                }
                else {
                    flatten.add(ele);
                }
            }
            return flatten;
        }

        /**
         * 根据展平后的索引找到元素
         *
         */
        public Constant getIdxEle(int idx) {
            int v = idx;
            if (v > ((Type.ArrayType) type).getFlattenSize()) {
                throw new RuntimeException("Index out of bound");
            }
            Constant ret = this;
            //idx是数组展平的第i个元素，所以要找到第i个元素
            Type eleType = ((Type.ArrayType) type).getEleType();
            while (eleType instanceof Type.ArrayType) {
                int len = ((Type.ArrayType) eleType).getFlattenSize();
                int i = v / len;
                v = v % len;
                ret = ((ConstantArray) ret).getEle(i);
                eleType = ((Type.ArrayType) eleType).getEleType();
            }
            ret = ((ConstantArray) ret).getEle(v);
            if (ret == null) {
                throw new RuntimeException("Index out of bound");
            }
            return ret;
        }

        /**
         * 根据展平后的索引设置元素
         *
         */
        public void setIdxEle(int idx, Constant value) {
            int v = idx;
            if (v > ((Type.ArrayType) type).getFlattenSize()) {
                throw new RuntimeException("Index out of bound");
            }
            Constant ret = this;
            //idx是数组展平的第i个元素，所以要找到第i个元素
            Type eleType = ((Type.ArrayType) type).getEleType();
            while (eleType instanceof Type.ArrayType) {
                int len = ((Type.ArrayType) eleType).getFlattenSize();
                int i = v / len;
                v = v % len;
                ret = ((ConstantArray) ret).getEle(i);
                eleType = ((Type.ArrayType) eleType).getEleType();
            }
            if (((ConstantArray) ret).getEle(v) == null) {
                throw new RuntimeException("Index out of bound");
            }
            ((ConstantArray) ret).setEle(v, value);
        }

        public Constant getEle(int index) {
            return constArray.get(index);
        }

        public void setEle(int index, Constant value) {
            constArray.set(index, value);
        }

    }

    /**
     * 空指针常量
     */
    public static class ConstantPointerNull extends Constant {

        public ConstantPointerNull(Type type) {
            super(type);
        }

        @Override
        public Object getConstValue() {
            return null;
        }

        @Override
        public boolean isZero() {
            return true;
        }
    }

    /**
     * 零初始化常量, 当且仅当全局数组初始化时使用
     * 后端可以通过类型系统 Type 判断维数
     */
    public static class ConstantZeroInitializer extends Constant {
        public ConstantZeroInitializer(Type type) {
            super(type);
        }

        @Override
        public Object getConstValue() {
            return null;
        }

        @Override
        public boolean isZero() {
            return true;
        }

        @Override
        public String toString() {
            return "zeroinitializer";
        }
    }
}
