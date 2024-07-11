package midend.Transform;

import mir.GlobalVariable;
import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ConstArray2Value {
    public static void run(Module module) {
        for (GlobalVariable gv : module.getGlobalValues()) {
            if (isOnlyReadGlobalArray(gv)) TransLoad2Value(gv);
        }
    }

    private static boolean isOnlyReadGlobalArray(GlobalVariable gv) {
        if (gv.getInnerType().isArrayTy()) {
            for (Use use : gv.getUses()) {
                Instruction inst = (Instruction) use.getUser();
                if (inst instanceof Instruction.GetElementPtr) {
                    Instruction.GetElementPtr gep = (Instruction.GetElementPtr) inst;
                    for (Use use1 : gep.getUses()) {
                        Instruction inst1 = (Instruction) use1.getUser();
                        if (inst1 instanceof Instruction.Store) return false;
                    }
                }
                else if (inst instanceof Instruction.BitCast) {
                    Instruction.BitCast bitCast = (Instruction.BitCast) inst;
                    for (Use use1 : bitCast.getUses()) {
                        Instruction inst1 = (Instruction) use1.getUser();
                        if (inst1 instanceof Instruction.Call) return false;
                    }
                }
            }
        }
        else return false;
        return true;
    }

    private static void TransLoad2Value(GlobalVariable gv) {
        HashMap<Instruction.GetElementPtr, HashSet<Instruction.Load>> gepMap = new HashMap<>();
        for (Use use : gv.getUses()) {
            Instruction inst = (Instruction) use.getUser();
            Instruction.GetElementPtr gep = (Instruction.GetElementPtr) inst;
            if (!gep.isConstOffset()) continue;
            gepMap.putIfAbsent(gep, new HashSet<>());
            for (Use use1 : inst.getUses()) {
                gepMap.get(inst).add((Instruction.Load) use1.getUser());
            }
        }
        for (Instruction.GetElementPtr gep : gepMap.keySet()) {
            ArrayList<Value> idxs = new ArrayList<>();

            for (Value idx : gep.getOffsets()) {
                idxs.add(idx);
            }
//            Constant value = ((Constant.ConstantArray)gv.getConstValue()).getIdxEle()
        }
    }
}
