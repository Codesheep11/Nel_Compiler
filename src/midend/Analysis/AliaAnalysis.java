package midend.Analysis;

import mir.Constant;
import mir.Instruction;
import mir.Type;
import mir.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class AliaAnalysis {
    public static ArrayList<Value> getGepIdx(Instruction.GetElementPtr gep) {
        HashMap<Integer, Integer> indexMap = new HashMap<>();
        Type type = ((Type.PointerType) gep.getType()).getInnerType();// 获取数组/普通元素的type
        int cnt = 0;
        while (type.isArrayTy()) {
            Type.ArrayType arrayType = (Type.ArrayType) type;
            int size = arrayType.getFlattenSize();
            indexMap.put(cnt, size);
            cnt++;
        }
        Value off = gep.getOffsets().get(gep.getOffsets().size() - 1);
        Queue<Value> queue = new LinkedList<>();
        queue.add(off);
        ArrayList<Value> ans = new ArrayList<>();
        for (int i = 0; i < gep.getOffsets().size(); i++) {
            ans.add(Constant.ConstantInt.get(0));//用0占位
        }
        while (!queue.isEmpty()) {
            Value first = queue.poll();
            // 判断是不是乘以index的数值
            if (first instanceof Instruction.Mul mul && mul.getOperand_1() instanceof Constant.ConstantInt c
                    && indexMap.containsKey(c.getIntValue())) {
                ans.set(indexMap.get(c.getIntValue()), mul.getOperand_2());
            } else if (first instanceof Instruction instr) {
                // 如果不是的话,则将其内容继续放进来
                queue.addAll(instr.getOperands());
            }
        }
        return ans;
    }
}
