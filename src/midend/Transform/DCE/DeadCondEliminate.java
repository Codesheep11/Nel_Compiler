package midend.Transform.DCE;

import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DeadCondEliminate {
    private static final HashSet<BasicBlock> visited = new HashSet<>();

    private static final HashMap<Instruction, Instruction> instMap = new HashMap<>();

    public static boolean run(Module module) {
        boolean modified = false;
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            modified |= runOnFunc(function);
        }
        return modified;
    }

    public static boolean runOnFunc(Function function) {
        boolean modified = false;
        visited.clear();
        for (BasicBlock block : function.getBlocks()) {
            if (visited.contains(block)) continue;
            modified |= runOnBlock(block);
        }
        return modified;
    }

    public static boolean runOnBlock(BasicBlock block) {
        visited.add(block);
        Instruction.Terminator terminator = block.getTerminator();
        if (!(terminator instanceof Instruction.Branch br)) return false;
        BasicBlock thenBlock = br.getThenBlock();
        BasicBlock elseBlock = br.getElseBlock();
        if (thenBlock.getPreBlocks().size() == 1 && elseBlock.getPreBlocks().size() == 1) {
            if (thenBlock.getTerminator() instanceof Instruction.Jump thenJump &&
                    elseBlock.getTerminator() instanceof Instruction.Jump elseJump)
            {
                if (thenJump.getTargetBlock() == elseJump.getTargetBlock()) {
                    visited.add(thenBlock);
                    visited.add(elseBlock);
                    BasicBlock endBlock = thenJump.getTargetBlock();
                    for (Instruction.Phi phi : endBlock.getPhiInstructions()) {
                        Value v1 = phi.getOptionalValue(thenBlock);
                        Value v2 = phi.getOptionalValue(elseBlock);
                        if (!v1.equals(v2)) return false;
                    }
                    ArrayList<Instruction> thenSnaps = thenBlock.getInstructionsSnap();
                    ArrayList<Instruction> elseSnaps = elseBlock.getInstructionsSnap();
                    if (thenSnaps.size() != elseSnaps.size()) return false;
                    instMap.clear();
                    for (int i = 0; i < thenSnaps.size(); i++) {
                        Instruction thenInst = thenSnaps.get(i);
                        Instruction elseInst = elseSnaps.get(i);
                        if (!similarInst(thenInst, elseInst)) return false;
                    }
                    block.getLastInst().delete();
                    new Instruction.Jump(block, thenBlock);
//                    System.out.println("DeadCondEliminate");
                    return true;
                }
            }
        }
        else if (thenBlock.getPreBlocks().size() == 2 || elseBlock.getPreBlocks().size() == 2) {
            BasicBlock endBlock = thenBlock.getPreBlocks().size() == 2 ? thenBlock : elseBlock;
            BasicBlock passBlock = thenBlock.getPreBlocks().size() == 2 ? elseBlock : thenBlock;
            if (!passBlock.getSucBlocks().contains(endBlock)) return false;
            visited.add(passBlock);
            if (!(passBlock.getFirstInst() instanceof Instruction.Jump)) return false;
            for (Instruction.Phi phi : endBlock.getPhiInstructions()) {
                Value v1 = phi.getOptionalValue(thenBlock);
                Value v2 = phi.getOptionalValue(elseBlock);
                if (!v1.equals(v2)) return false;
            }
            block.getLastInst().delete();
            new Instruction.Jump(block, endBlock);
//            System.out.println("DeadCondEliminate");
            return true;
        }
        return false;
    }

    private static boolean similarInst(Instruction a, Instruction b) {
        if (a.getInstType() != b.getInstType()) return false;
        if (a.equals(b)) return true;
        if (instMap.containsKey(a) && instMap.get(a).equals(b)) return true;
        for (int i = 0; i < a.getOperands().size(); i++) {
            Value v1 = a.getOperands().get(i);
            Value v2 = b.getOperands().get(i);
            if (v1 instanceof Instruction i1 && v2 instanceof Instruction i2) {
                if (!similarInst(i1, i2)) return false;
            }
            else if (!v1.equals(v2)) return false;
        }
        instMap.put(a, b);
        return true;
    }

}
