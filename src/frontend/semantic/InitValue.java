package frontend.semantic;

import mir.Constant;
import mir.Type;
import mir.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 变量初始化的值
 */
public abstract class InitValue {
    private final Type type;

    public InitValue(final Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public abstract String toString();

    public abstract Flatten flatten();

    public abstract Constant genConstant();

    public static class ArrayInit extends InitValue {
        private final ArrayList<InitValue> arrayValues;

        public ArrayInit(Type type) {
            super(type);
            arrayValues = new ArrayList<>();
        }

        public void addElement(InitValue newValue) {
            arrayValues.add(newValue);
        }

        public int getSize() {
            return arrayValues.size();
        }

        public InitValue getValue(int index) {
            return arrayValues.get(index);
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder(getType().toString() + " ");
            str.append("[");
            Iterator<InitValue> iter = arrayValues.iterator();
            while (iter.hasNext()) {
                str.append(iter.next().toString());
                if (iter.hasNext()) {
                    str.append(", ");
                }
            }
            str.append("]");
            return str.toString();
        }


        @Override
        public Flatten flatten() {
            Flatten flatten = new Flatten();
            for (InitValue initValue : arrayValues) {
                Flatten initFlattenValue = initValue.flatten();
                flatten.addAll(initFlattenValue);
                flatten.mergeAll();
            }
            return flatten;
        }

        @Override
        public Constant genConstant() {
            ArrayList<Constant> arrayList = new ArrayList<>();
            for (InitValue v : arrayValues) {
                arrayList.add(v.genConstant());
            }
            return new Constant.ConstantArray(((Type.ArrayType) getType()).getEleType(), arrayList);
        }
    }

    public static class ZeroArrayInit extends ArrayInit {
        public final ArrayList<InitValue> arrayValues;

        public int solve(Type ele)
        {
            if (ele instanceof Type.ArrayType)
                return ((Type.ArrayType) ele).getSize() * solve(((Type.ArrayType) ele).getEleType());
            else return 1;
        }

        public ZeroArrayInit(Type type) {
            super(type);
            arrayValues = new ArrayList<>();
//            int size = solve(type);
//            for (int i = 0;i < size;i++)
//            {
//                arrayValues.add(new ValueInit(new Constant.ConstantInt(0),Type.BasicType.I32_TYPE));
//            }
        }

        @Override
        public Flatten flatten() {
            Type type = getType();
            assert type.isArrayTy();
            int size = ((Type.ArrayType) type).getFlattenSize();
            Type.BasicType basicType = ((Type.ArrayType) type).getBasicEleType();
            Flatten flatten = new Flatten();
            if (basicType.isInt32Ty()) {
                flatten.add(new Flatten.Slice(Constant.ConstantInt.get(0), size));
            }
            else {
                flatten.add(new Flatten.Slice(new Constant.ConstantFloat(0), size));
            }
            return flatten;
        }

        public InitValue getValue(int index) {
            return arrayValues.get(index);
        }

        @Override
        public String toString() {
            return getType().toString() + " zeroinitializer";
        }

        @Override
        public Constant genConstant() {
            return new Constant.ConstantZeroInitializer(getType());
        }
    }


    public static class ValueInit extends InitValue {
        private final Value value;

        public ValueInit(Value value, Type type) {
            super(type);
            this.value = value;
        }

        public Value getValue() {
            return value;
        }

        @Override
        public String toString() {
            return getType().toString() + " " + value.getDescriptor();
        }

        @Override
        public Flatten flatten() {
            Flatten flatten = new Flatten();
            flatten.add(new Flatten.Slice(value));
            return flatten;
        }

        @Override
        public Constant genConstant() {
            if (!(value instanceof Constant))
                throw new RuntimeException("Err: non Constant ValueInit can't generate Constant");
            return (Constant) value;
        }
    }


    public static class ExpInit extends InitValue {
        private final Value result;

        public ExpInit(Value result, Type type) {
            super(type);
            this.result = result;
        }

        @Override
        public Flatten flatten() {
            Flatten flatten = new Flatten();
            flatten.add(new Flatten.Slice(result));
            return flatten;
        }

        @Override
        public String toString() {
            throw new RuntimeException("Err: Cannot be output as string");
        }

        public Value getResult() {
            return result;
        }

        @Override
        public Constant genConstant() {
            if (!(result instanceof Constant))
                throw new RuntimeException("Err: non Constant ExpInit can't generate Constant");
            return (Constant) result;
        }
    }


    public static class Flatten {
        private ArrayList<Slice> slices = new ArrayList<>();

        public Flatten() {

        }

        public static class Slice {
            public final Value value;
            public int count = 1;
            public Flatten flatten;

            public Slice(Value value, int count) {
                this.value = value;
                this.count = count;
            }

            public Slice(Value value) {
                this.value = value;
            }

            public boolean canbeMerged(Slice that) {
                if (that != flatten.slices.get(flatten.slices.indexOf(this) + 1)) {
                    return false;
                }
                return this.value.equals(that.value) && this.value.equals(Constant.ConstantInt.get(0));
            }

            public void merge(Slice that) {
                assert canbeMerged(that);
                this.count += that.count;
                flatten.slices.remove(that);
            }

            public boolean isZero() {
                assert value.getType() instanceof Type.BasicType;
                if (value instanceof Constant.ConstantInt) {
                    return (int) ((Constant.ConstantInt) value).getConstValue() == 0;
                }
                else if (value instanceof Constant.ConstantFloat) {
                    return (float) ((Constant.ConstantFloat) value).getConstValue() == 0;
                }
                else {
                    return false;
                }
            }


        }

        public void add(Slice slice) {
            slices.add(slice);
            slice.flatten = this;
        }

        public void addAll(Flatten flatten) {
            slices.addAll(flatten.slices);
            for (Slice slice : flatten.slices) {
                slice.flatten = this;
            }
        }

        public void mergeAll() {
            int sizeBeforeMerge = countOfWords();
            ArrayList<Slice> newSlices = new ArrayList<>();
            for (int i = 0; i < slices.size(); ) {
                Slice slice = slices.get(i);
                int j = i + 1;
                while (j < slices.size() && slice.canbeMerged(slices.get(j))) {
                    slice.merge(slices.get(j));
                    j++;
                }
                newSlices.add(slice);
                i = j;
            }
            slices = newSlices;
            assert sizeBeforeMerge == countOfWords();
        }

        public int countOfWords() {
            int size = 0;
            for (Slice slice : slices) {
                size += slice.count;
            }
            return size;
        }


        public Map<Integer, Value> IndexOfNZero() {
            int offset = 0;
            Map<Integer, Value> ret = new LinkedHashMap<>();
            for (Slice slice : slices) {
                if (slice.isZero()) {
                    offset += slice.count;
                }
                else {
                    ret.put(offset, slice.value);
                    offset += slice.count;
                }
            }
            return ret;
        }


    }
}
