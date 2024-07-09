package midend.Transform;

import mir.*;
import mir.Module;

import java.util.ArrayList;

public class RemovePhi {
    private static int idx = 0;

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) {
                continue;
            }
            removePhiAddPhiCopy(function);
            PhiCopy2move(function);
        }
    }

    public static void removePhiAddPhiCopy(Function function) {
        for (BasicBlock bb : function.getBlocks()) {
            if (!(bb.getFirstInst() instanceof Instruction.Phi)) {
                continue;
            }
            ArrayList<BasicBlock> pres = new ArrayList<>(bb.getPreBlocks());
            for (BasicBlock pre : pres) {
                Instruction.PhiCopy phiCopy = new Instruction.PhiCopy(pre, new ArrayList<>(), new ArrayList<>());
                phiCopy.remove();
                if (pre.getSucBlocks().size() > 1) {
                    addMidBB(pre, phiCopy, bb);
                }
                else {
                    Instruction term = pre.getLastInst();
                    pre.getInstructions().insertBefore(phiCopy, term);
                }
            }
            for (Instruction phi : bb.getInstructions()) {
                if (phi instanceof Instruction.Phi) {
                    for (BasicBlock pre : bb.getPreBlocks()) {
                        Value v = ((Instruction.Phi) phi).getOptionalValue(pre);
                        //如果value为空，说明value是关键边，phi中值映射来自pre前一个基本块
                        if (v == null) {
                            v = ((Instruction.Phi) phi).getOptionalValue(pre.getPreBlocks().get(0));
                        }
                        Instruction.PhiCopy pc = (Instruction.PhiCopy) pre.getLastInst().getPrev();
                        pc.add(phi, v);
                    }
                    phi.delete();
                }
                else {
                    break;
                }
            }
        }
    }

    /**
     * 添加关键边：在pre和bb之间插入一个新的基本块，将phiCopy插入到新的基本块中
     *
     * @param pre
     * @param phiCopy
     * @param bb
     */
    public static void addMidBB(BasicBlock pre, Instruction.PhiCopy phiCopy, BasicBlock bb) {
        BasicBlock mid = new BasicBlock(pre.getParentFunction().getBBName(), pre.getParentFunction());
        phiCopy.remove();
        mid.addInstFirst(phiCopy);
        Instruction term = pre.getLastInst();
        term.replaceUseOfWith(bb, mid);
        new Instruction.Jump(mid, bb);
        pre.getSucBlocks().remove(bb);
        pre.getSucBlocks().add(mid);
        mid.getPreBlocks().add(pre);
        mid.getSucBlocks().add(bb);
        bb.getPreBlocks().remove(pre);
        bb.getPreBlocks().add(mid);
    }

    public static void PhiCopy2move(Function function) {
        for (BasicBlock bb : function.getBlocks()) {
            Instruction end = bb.getLastInst();
            ArrayList<Instruction.Move> seq = new ArrayList<>();
            ArrayList<Instruction.PhiCopy> phiCopies = new ArrayList<>();
            while (end.getPrev() instanceof Instruction.PhiCopy) {
                Instruction.PhiCopy phiCopy = (Instruction.PhiCopy) end.getPrev();
                phiCopies.add(phiCopy);
                ArrayList<Value> LHS = new ArrayList<>(phiCopy.getLHS());
                ArrayList<Value> RHS = new ArrayList<>(phiCopy.getRHS());
                while (!LHS.isEmpty()) {
                    for (int i = 0; i < LHS.size(); i++) {
                        Value phi = LHS.get(i);
                        if (RHS.contains(phi)) {
                            continue;
                        }
                        Instruction.Move move = new Instruction.Move(bb, phi.getType(), RHS.get(i), phi);
                        move.remove();
                        seq.add(move);
                        phiCopy.Delete(LHS.get(i), RHS.get(i));
                    }
                    if (!phiCopy.getLHS().isEmpty()) {
                        Value src = phiCopy.getRHS().get(0);
                        Value temp = new Value("temp" + idx++, src.getType());
                        Instruction.Move move = new Instruction.Move(bb, src.getType(), src, temp);
                        move.remove();
                        seq.add(move);
                        phiCopy.changeRS(0, temp);
                    }
                    LHS = new ArrayList<>(phiCopy.getLHS());
                    RHS = new ArrayList<>(phiCopy.getRHS());
                }
                end = (Instruction) end.getPrev();
            }
            for (Instruction.PhiCopy pc : phiCopies) {
                pc.remove();
            }
            end = bb.getLastInst();
            for (Instruction.Move move : seq) {
                bb.getInstructions().insertBefore(move, end);
            }
        }
    }
}