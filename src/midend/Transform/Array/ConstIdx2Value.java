package midend.Transform.Array;

import mir.Module;
import mir.*;

import java.util.ArrayList;

public class ConstIdx2Value {
    public static void run(Module module) {
        for (GlobalVariable gv : module.getGlobalValues()) {
            TransLoad2Value(gv);
        }
    }

    private static void TransLoad2Value(GlobalVariable gv) {
        if (!gv.getInnerType().isArrayTy()) return;
        ArrayList<Instruction> LoadList = new ArrayList<>();
        ArrayList<Instruction> UseInst = new ArrayList<>(gv.getUsers());
        while (!UseInst.isEmpty()) {
            Instruction inst = UseInst.remove(0);
            if (inst instanceof Instruction.Store || inst instanceof Instruction.Call || inst instanceof Instruction.AtomicAdd)
                return;
            else if (inst instanceof Instruction.Load) LoadList.add(inst);
            else if (inst instanceof Instruction.GetElementPtr || inst instanceof Instruction.BitCast)
                UseInst.addAll(inst.getUsers());
            else
                throw new RuntimeException("gv use inst not handled!");
        }
        ArrayList<Instruction.Load> delList = new ArrayList<>();
        for (Instruction load : LoadList) {
            Instruction.Load loadInst = (Instruction.Load) load;
            Instruction.GetElementPtr address = (Instruction.GetElementPtr) loadInst.getAddr();
            if (gv.getConstValue() instanceof Constant.ConstantZeroInitializer) {
                Type.ArrayType arrayType = (Type.ArrayType) gv.getInnerType();
                Constant constValue = arrayType.getBasicEleType().isInt32Ty() ?
                        Constant.ConstantInt.get(0) : new Constant.ConstantFloat(0);
                loadInst.replaceAllUsesWith(constValue);
                delList.add(loadInst);
            }
            else {
                if (address.getIdx() instanceof Constant c) {
                    Constant constValue = ((Constant.ConstantArray) gv.getConstValue()).getIdxEle(((Constant.ConstantInt) c).getIntValue());
                    loadInst.replaceAllUsesWith(constValue);
                    delList.add(loadInst);
                }
            }
        }
        delList.forEach(Value::delete);
    }
}
