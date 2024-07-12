package midend.Transform.Array;

import mir.*;
import mir.Module;

import java.util.*;

//将所有访问值的GEP指令展平为最后一维的GEP指令
public class GepFold {
    public static void run(Module module) {
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            func.buildDominanceGraph();
            ArrayList<Instruction.GetElementPtr> geps = new ArrayList<>();
            for (BasicBlock block : func.getDomTreeLayerSort()) {
                for (Instruction inst : block.getInstructions()) {
                    if (inst instanceof Instruction.GetElementPtr) {
                        Instruction.GetElementPtr gep = (Instruction.GetElementPtr) inst;
//                        System.out.println("gep: " + gep);
                        if (isFoldGep(gep)) {
//                            System.out.println("geps add: " + gep);
                            geps.add(gep);
                        }
                    }
                }
            }
            Collections.reverse(geps);
            for (Instruction.GetElementPtr gep : geps) {
//                System.out.println("GepFold: " + gep);
                if (gep.getUses().size() == 0) continue;
                BasicBlock block = gep.getParentBlock();
                ArrayList<Instruction.GetElementPtr> gepChain = new ArrayList<>();
                Value curGep = gep;
                while (curGep instanceof Instruction.GetElementPtr) {
                    gepChain.add((Instruction.GetElementPtr) curGep);
                    curGep = ((Instruction.GetElementPtr) curGep).getBase();
                }
                Collections.reverse(gepChain);
                ArrayList<Value> offsets = new ArrayList<>();
                offsets.addAll(gepChain.get(0).getOffsets());
                for (int i = 1; i < gepChain.size(); i++) {
                    Instruction.GetElementPtr cur = gepChain.get(i);
                    Value lastIdx = offsets.get(offsets.size() - 1);
                    if (!(cur.getOffsets().get(0) instanceof Constant.ConstantInt c && c.isZero())) {
                        if (!(lastIdx instanceof Constant.ConstantInt c && c.isZero())) {
                            Instruction add = new Instruction.Add(block, lastIdx.getType(), lastIdx, cur.getOffsets().get(0));
                            add.remove();
                            block.getInstructions().insertBefore(add, gep);
                            offsets.set(offsets.size() - 1, add);
                        }
                        else {
                            offsets.set(offsets.size() - 1, cur.getOffsets().get(0));
                        }
                    }
                    for (int j = 1; j < cur.getOffsets().size(); j++) {
                        offsets.add(cur.getOffsets().get(j));
                    }
                }
                Value address = curGep;
                Type baseType = ((Type.PointerType) address.getType()).getInnerType();
                Value sum = null;
                for (int i = 0; i < offsets.size(); i++) {
                    Value offset = offsets.get(i);
                    if (!(offset instanceof Constant.ConstantInt c && c.isZero())) {
                        Value mul = null;
                        if (baseType.isArrayTy()) {
                            mul = new Instruction.Mul(block, offset.getType(),
                                    new Constant.ConstantInt(((Type.ArrayType) baseType).getFlattenSize()), offset);
                            mul.remove();
                            block.getInstructions().insertBefore((Instruction) mul, gep);
                            offsets.set(i, new Constant.ConstantInt(0));
                        }
                        else mul = offset;
                        if (sum == null) sum = mul;
                        else {
                            Instruction add = new Instruction.Add(block, sum.getType(), sum, mul);
                            add.remove();
                            block.getInstructions().insertBefore(add, gep);
                            sum = add;
                        }
                    }
                    if (baseType.isArrayTy()) baseType = ((Type.ArrayType) baseType).getEleType();
                }
                if (sum != null) offsets.set(offsets.size() - 1, sum);
                Instruction.GetElementPtr newGep = new Instruction.GetElementPtr(block, address, baseType, offsets);
                newGep.remove();
                block.getInstructions().insertBefore(newGep, gep);
                gep.replaceAllUsesWith(newGep);
                gep.delete();
            }
        }
    }

    private static boolean isFoldGep(Instruction.GetElementPtr gep) {
        if (gep.getBase() instanceof Instruction.GetElementPtr) return true;
        return false;
    }
}
