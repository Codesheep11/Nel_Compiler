package midend.Transform;

import midend.Analysis.FuncAnalysis;
import mir.Module;
import mir.*;

import java.util.ArrayList;

import static midend.Util.FuncInfo.ExternFunc.MAX;
import static midend.Util.FuncInfo.ExternFunc.MIN;

public class Branch2MinMax {
    private static boolean addMAX = false;
    private static boolean addMIN = false;

    private static ArrayList<BasicBlock> visited = new ArrayList<>();

    public static void run(Module module) {
        addMAX = false;
        addMIN = false;
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            runOnFunction(func);
        }
        addMAX = addMAX && !module.getFuncSet().contains(MAX);
        addMIN = addMIN && !module.getFuncSet().contains(MIN);
        if (addMAX) module.addFunction(MAX);
        if (addMIN) module.addFunction(MIN);
        if (addMAX || addMIN) {
            System.out.println("Add MAX/MIN");
            FuncAnalysis.run(module);
        }
    }

    private static void runOnFunction(Function func) {
        visited.clear();
        for (BasicBlock block : func.getBlocks()) {
            if (visited.contains(block)) continue;
            runOnBlock(block);
        }
    }

    private static void runOnBlock(BasicBlock block) {
        Instruction.Terminator term = block.getTerminator();
        if (term instanceof Instruction.Branch branch) {
            Value cond = branch.getCond();
            if (cond instanceof Instruction.Icmp icmp) {
                Instruction.Icmp.CondCode condCode = icmp.getCondCode();
                if (condCode == Instruction.Icmp.CondCode.EQ || condCode == Instruction.Icmp.CondCode.NE) return;
                BasicBlock thenBlock = branch.getThenBlock();
                BasicBlock elseBlock = branch.getElseBlock();
                if (thenBlock.getPreBlocks().size() > 1 || elseBlock.getPreBlocks().size() > 1) return;
                if (thenBlock.getTerminator() instanceof Instruction.Jump thenJump &&
                        elseBlock.getTerminator() instanceof Instruction.Jump elseJump)
                {
                    if (thenJump.getTargetBlock() != elseJump.getTargetBlock()) return;
                    BasicBlock endBlock = thenJump.getTargetBlock();
                    if (endBlock.getPreBlocks().size() > 2) return;
                    Value LHS = icmp.getSrc1();
                    Value RHS = icmp.getSrc2();
                    ArrayList<Instruction.Phi> delPhiList = new ArrayList<>();
                    for (Instruction.Phi phi : endBlock.getPhiInstructions()) {
                        boolean isMinMaxPhi = true;
                        for (Value v : phi.getIncomingValues()) {
                            if (!v.equals(LHS) && !v.equals(RHS)) {
                                isMinMaxPhi = false;
                                break;
                            }
                        }
                        if (!isMinMaxPhi) continue;
                        delPhiList.add(phi);
                        visited.add(thenBlock);
                        visited.add(elseBlock);
                        Value thenValue = phi.getOptionalValue(thenBlock);
                        ArrayList<Value> args = new ArrayList<>();
                        args.add(LHS);
                        args.add(RHS);
                        Instruction.Call call = null;
                        switch (condCode) {
                            case SGE, SGT -> {
                                if (thenValue == LHS) {
                                    addMAX = true;
                                    call = new Instruction.Call(endBlock, MAX, args);
                                }
                                else {
                                    addMIN = true;
                                    call = new Instruction.Call(endBlock, MIN, args);
                                }
                            }
                            case SLE, SLT -> {
                                if (thenValue == LHS) {
                                    addMIN = true;
                                    call = new Instruction.Call(endBlock, MIN, args);
                                }
                                else {
                                    addMAX = true;
                                    call = new Instruction.Call(endBlock, MAX, args);

                                }
                            }
                            default -> throw new RuntimeException("Unexpected condCode: " + condCode);
                        }
                        call.remove();
                        phi.replaceAllUsesWith(call);
                        endBlock.addInstAfterPhi(call);
                    }
                    delPhiList.forEach(Instruction::delete);
                    if (endBlock.getPhiInstructions().size() == 0) {
                        if (thenBlock.getInstructions().size() == 1 && elseBlock.getInstructions().size() == 1) {
                            block.getLastInst().delete();
                            new Instruction.Jump(block, endBlock);
                        }
                    }
                }
            }
        }
    }
}
