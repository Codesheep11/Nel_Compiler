package midend.Transform.Array;

import midend.Transform.Mem2Reg;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashMap;

public class SroaPass {
    //alloc-> index -> use
    private static final HashMap<Instruction.Alloc, HashMap<Integer, ArrayList<Instruction.GetElementPtr>>> Alloc2Idx2Gep = new HashMap<>();

    private static HashMap<Integer, ArrayList<Instruction.GetElementPtr>> Idx2Use = new HashMap<>();

    private static final ArrayList<Instruction> delList = new ArrayList<>();

    public static void run(Module module) {

        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            RunOnFunction(func);
        }

    }

    private static void clear() {
        Alloc2Idx2Gep.clear();
        delList.clear();
    }

    private static void RunOnFunction(Function function) {
        clear();
        for (BasicBlock block : function.getBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof Instruction.Alloc alloc) {
                    Idx2Use = new HashMap<>();
                    if (canBeSpilt(alloc)) {
                        Alloc2Idx2Gep.put(alloc, Idx2Use);
                        delList.add(alloc);
                    }
                }
            }
        }
        for (Instruction.Alloc alloc : Alloc2Idx2Gep.keySet()) {
            HashMap<Integer, ArrayList<Instruction.GetElementPtr>> idx2Use = Alloc2Idx2Gep.get(alloc);
            for (int idx : idx2Use.keySet()) {
                ArrayList<Instruction.GetElementPtr> geps = idx2Use.get(idx);
                Instruction.Alloc newAlloc = new Instruction.Alloc(alloc.getParentBlock(), ((Type.ArrayType) alloc.getContentType()).getBasicEleType());
                newAlloc.remove();
                alloc.getParentBlock().insertInstAfter(newAlloc, alloc);
                for (Instruction.GetElementPtr gep : geps) {
                    gep.replaceAllUsesWith(newAlloc);
                    delList.add(gep);
                }
            }
        }
        delList.forEach(Instruction::delete);
        if (!delList.isEmpty()) {
//            System.out.println("SROA run on " + function.getName());
            Mem2Reg.runOnFunc(function);
        }
    }

    private static boolean canBeSpilt(Instruction.Alloc alloc) {
        if (!alloc.getContentType().isArrayTy()) return false;
        return onlyConstantIndex(alloc);
    }

    private static boolean onlyConstantIndex(Value alloc) {
        ArrayList<Instruction> curDelList = new ArrayList<>();
        int idx = 0;
        ArrayList<Instruction> users = new ArrayList<>(alloc.getUsers());
        while (idx < users.size()) {
            Instruction inst = users.get(idx);
            if (inst instanceof Instruction.GetElementPtr gep) {
                if (!(gep.getIdx() instanceof Constant)) return false;
                users.addAll(gep.getUsers());
                if (((Type.PointerType) gep.getType()).getInnerType().isValueType()) {
                    int index = ((Constant.ConstantInt) gep.getIdx()).getIntValue();
                    Idx2Use.putIfAbsent(index, new ArrayList<>());
                    Idx2Use.get(index).add(gep);
                }
            }
            else if (inst instanceof Instruction.BitCast) {
                users.addAll(inst.getUsers());
                curDelList.add(inst);
            }
            else if (inst instanceof Instruction.Call call) {
                if (!call.getDestFunction().getName().equals("memset")) return false;
                curDelList.add(inst);
            }
            idx++;
        }
        SroaPass.delList.addAll(curDelList);
        return true;
    }
}
