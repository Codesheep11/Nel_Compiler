package midend.Transform.Array;

import mir.Module;
import mir.*;

import java.util.*;

public class GepLift {
    // 在多个gep连续的情况下,实现复用
    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            for (BasicBlock block : function.getBlocks()) {
                runOnBlock(block);
            }
        }
    }

    static class OffsetPair {
        private final Instruction.GetElementPtr gepBase;
        private final int offset;

        public OffsetPair(Instruction.GetElementPtr gepBase, int offset) {
            this.gepBase = gepBase;
            this.offset = offset;
        }
    }

    static class IndexCal {
        private final ArrayList<Value> sum = new ArrayList<>();

        private final ArrayList<Instruction> store = new ArrayList<>();

        public IndexCal() {
        }

        public void standard() {
            ArrayList<Constant.ConstantInt> ints = new ArrayList<>();
            Iterator<Value> iterator = sum.iterator();
            while (iterator.hasNext()) {
                Value value = iterator.next();
                if (value instanceof Constant.ConstantInt i) {
                    iterator.remove();
                    ints.add(i);
                }
            }
            sum.addAll(ints);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Value s : sum) {
                sb.append(s).append(" ");
            }
            return sb.toString();
        }
    }

    // 使用llvm就不怎么需要考虑寄存器的被定义了,直接考虑将base和index计算好即可
    private static final HashMap<Instruction.GetElementPtr, OffsetPair> myBaseOffset = new HashMap<>();

    private static final HashMap<Instruction.GetElementPtr, IndexCal> indexCalMap = new HashMap<>();

    private static boolean containsAndIsAdd(BasicBlock block, Value value) {
        if (!(value instanceof Instruction)) return false;
        if (!(value instanceof Instruction.Add)) return false;
        for (Instruction instruction : block.getInstructions()) {
            if (value == instruction) return true;
        }
        return false;
    }

    private static int callOffset(IndexCal c1, IndexCal c2) {
        if (c1.sum.size() != c2.sum.size() && c1.sum.size() + 1 != c2.sum.size() && c1.sum.size() != c2.sum.size() + 1) {
            return Integer.MAX_VALUE;//设置最大值代表不可能计算差值
        }
        int i = 0;
        int range = 0;
        if (c1.sum.size() != c2.sum.size()) {
            range = Integer.min(c1.sum.size(), c2.sum.size());
        }
        else if (c2.sum.get(c2.sum.size() - 1) instanceof Constant.ConstantInt &&
                c1.sum.get(c1.sum.size() - 1) instanceof Constant.ConstantInt)
        {
            range = c1.sum.size() - 1;
        }
        else {
            range = c1.sum.size();
        }
        for (; i < range; i++) {
            if (c1.sum.get(i) != c2.sum.get(i)) {
                return Integer.MAX_VALUE;
            }
        }
        if (c2.sum.size() > c1.sum.size()) {
            if (c2.sum.get(c2.sum.size() - 1) instanceof Constant.ConstantInt ci)
                return ci.getIntValue();
            else return Integer.MAX_VALUE;
        }
        else if (c1.sum.size() > c2.sum.size()) {
            if (c1.sum.get(c1.sum.size() - 1) instanceof Constant.ConstantInt ci)
                return -ci.getIntValue();
            else return Integer.MAX_VALUE;
        }
        else {
            if (c2.sum.get(c2.sum.size() - 1) instanceof Constant.ConstantInt cs2
                    && c1.sum.get(c1.sum.size() - 1) instanceof Constant.ConstantInt cs1)
                return cs2.getIntValue() - cs1.getIntValue();
            else return Integer.MAX_VALUE;
        }
    }

    private static void runOnBlock(BasicBlock block) {
        myBaseOffset.clear();
        indexCalMap.clear();
        HashMap<Value, ArrayList<Instruction.GetElementPtr>> base2GEPs = new HashMap<>();
        for (Instruction instruction : block.getInstructions()) {
            if (instruction instanceof Instruction.GetElementPtr gep) {
                if (!base2GEPs.containsKey(gep.getBase())) {
                    base2GEPs.put(gep.getBase(), new ArrayList<>());
                }
                base2GEPs.get(gep.getBase()).add(gep);
                Value offset = gep.getOffsets().get(gep.getOffsets().size() - 1);// 获取最后一个,也就是offset
                IndexCal indexCal = new IndexCal();
                Queue<Value> q = new LinkedList<>();
                q.add(offset);
                while (!q.isEmpty()) {
                    //取得当前的第一个
                    Value value = q.poll();
                    if (containsAndIsAdd(block, value)) {
                        q.addAll(((Instruction) value).getOperands());
                        //需要判断这些计算指令是否仅仅被这个gep使用,否则就不能删indexCal.store.add((Instruction) value);
                        if (value.getUsers().size() == 1) {
                            indexCal.store.add((Instruction) value);
                        }
                    }
                    else {
                        // 不包含,代表可以直接放入
                        indexCal.sum.add(value);
                    }
                }
                // 将所有计算得到的存入map
                indexCal.standard();
                indexCalMap.put(gep, indexCal);
            }
        }
//        for (Instruction.GetElementPtr key : indexCalMap.keySet()) {
//            System.out.println(indexCalMap.get(key));
//        }
        // 整理完之后,对于同一base的继续计算
        for (Value base : base2GEPs.keySet()) {
            ArrayList<Instruction.GetElementPtr> q = base2GEPs.get(base);
            while (q.size() > 1) {
                Instruction.GetElementPtr head = q.get(0);
                Instruction.GetElementPtr next = q.get(1);
                int ans = callOffset(indexCalMap.get(head), indexCalMap.get(next));
                if (ans != Integer.MAX_VALUE) {
                    // 代表可以约减
                    myBaseOffset.put(next, new OffsetPair(head, ans));
                    q.remove(1);
                }
                else {
                    q.remove(0);
                }
            }
        }
        // 建立了所有的偏移的映射表之后,可以简单的计算了
        HashSet<Instruction> toRemove = new HashSet<>();
        for (Instruction.GetElementPtr gep : myBaseOffset.keySet()) {
            OffsetPair offsetPair = myBaseOffset.get(gep);
            if (!gep.getType().equals(offsetPair.gepBase.getType())) continue;
            ArrayList<Value> offset = new ArrayList<>();
            offset.add(Constant.ConstantInt.get(offsetPair.offset));
            Instruction.GetElementPtr add = new Instruction.GetElementPtr(block, offsetPair.gepBase, offsetPair.gepBase.getEleType(), offset);
            add.remove();
            block.insertInstBefore(add, gep);
            // 将使用gep的替换为add
            gep.replaceAllUsesWith(add);
            gep.delete();
            IndexCal indexCal = indexCalMap.get(gep);
            toRemove.addAll(indexCal.store);
        }
        for (Instruction instruction : toRemove) {
            instruction.delete();
        }
    }

}
