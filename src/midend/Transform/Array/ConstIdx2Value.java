package midend.Transform.Array;

import mir.GlobalVariable;
import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.ListIterator;

public class ConstIdx2Value {
    public static void run(Module module) {
        for (GlobalVariable gv : module.getGlobalValues()) {
            if (isOnlyReadGlobalArray(gv)) TransLoad2Value(gv);
        }
    }

    private static boolean isOnlyReadGlobalArray(GlobalVariable gv) {
        if (gv.getInnerType().isArrayTy()) {
            ArrayList<Instruction> UseInst = new ArrayList<>();
            UseInst.addAll(gv.getUsers());
            ListIterator it = UseInst.listIterator();
            while (it.hasNext()) {
                Instruction inst = (Instruction) it.next();
                if (inst instanceof Instruction.Store) return false;
                else if (inst instanceof Instruction.Call) return false;
                else if (inst instanceof Instruction.GetElementPtr) {
                    Instruction.GetElementPtr gep = (Instruction.GetElementPtr) inst;
                    UseInst.addAll(gep.getUsers());
                }
                else if (inst instanceof Instruction.BitCast) {
                    Instruction.BitCast bitCast = (Instruction.BitCast) inst;
                    UseInst.addAll(bitCast.getUsers());
                }
            }
        }
        else return false;
        return true;
    }

    private static void TransLoad2Value(GlobalVariable gv) {
        ArrayList<Instruction> UseInst = new ArrayList<>();
        UseInst.addAll(gv.getUsers());
        ListIterator it = UseInst.listIterator();
        ArrayList<Instruction.Load> delList = new ArrayList<>();
        while (it.hasNext()) {
            Instruction inst = (Instruction) it.next();
            if (inst instanceof Instruction.Load) {
                Instruction.Load load = (Instruction.Load) inst;
                Instruction.GetElementPtr address = (Instruction.GetElementPtr) load.getAddr();
                if (address.getOffsets().get(address.getOffsets().size() - 1) instanceof Constant c) {
                    Constant constValue = ((Constant.ConstantArray) gv.getConstValue()).getIdxEle((Constant.ConstantInt) c);
                    load.replaceAllUsesWith(constValue);
                    delList.add(load);
                }
            }
            else if (inst instanceof Instruction.GetElementPtr) {
                Instruction.GetElementPtr gep = (Instruction.GetElementPtr) inst;
                UseInst.addAll(gep.getUsers());
            }
            else if (inst instanceof Instruction.BitCast) {
                Instruction.BitCast bitCast = (Instruction.BitCast) inst;
                UseInst.addAll(bitCast.getUsers());
            }
        }
        delList.forEach(Value::delete);
    }
}
