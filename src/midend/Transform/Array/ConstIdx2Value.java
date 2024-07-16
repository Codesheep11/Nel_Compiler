package midend.Transform.Array;

import mir.GlobalVariable;
import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.ListIterator;

public class ConstIdx2Value {
    public static void run(Module module) {
        for (GlobalVariable gv : module.getGlobalValues()) {
            TransLoad2Value(gv);
        }
    }

    private static void TransLoad2Value(GlobalVariable gv) {
        if (!gv.getInnerType().isArrayTy()) return;
        ArrayList<Instruction> UseInst = new ArrayList<>();
        ArrayList<Instruction> LoadList = new ArrayList<>();
        UseInst.addAll(gv.getUsers());
        while (!UseInst.isEmpty()) {
            Instruction inst = UseInst.remove(0);
            if (inst instanceof Instruction.Store || inst instanceof Instruction.Call) return;
            else if (inst instanceof Instruction.Load) LoadList.add(inst);
            else if (inst instanceof Instruction.GetElementPtr || inst instanceof Instruction.BitCast)
                UseInst.addAll(inst.getUsers());
            else throw new RuntimeException("gv use inst not handled!");
        }
        ArrayList<Instruction.Load> delList = new ArrayList<>();
        for (Instruction load : LoadList) {
            Instruction.Load loadInst = (Instruction.Load) load;
            Instruction.GetElementPtr address = (Instruction.GetElementPtr) loadInst.getAddr();
            if (gv.getConstValue() instanceof Constant.ConstantZeroInitializer) {
                Constant constValue = new Constant.ConstantInt(0);
                loadInst.replaceAllUsesWith(constValue);
                delList.add(loadInst);
            }
            else {
                if (address.getOffsets().get(address.getOffsets().size() - 1) instanceof Constant c) {
                    Constant constValue = ((Constant.ConstantArray) gv.getConstValue()).getIdxEle((Constant.ConstantInt) c);
                    loadInst.replaceAllUsesWith(constValue);
                    delList.add(loadInst);
                }
            }
        }
        delList.forEach(Value::delete);
    }
}
