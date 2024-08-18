package midend.Transform.Loop;

import midend.Analysis.PointerBaseAnalysis;
import midend.Analysis.result.SCEVinfo;
import midend.Transform.DCE.DeadLoopEliminate;
import midend.Transform.DCE.SimplifyCFGPass;
import midend.Transform.LocalValueNumbering;
import mir.*;
import midend.Analysis.AnalysisManager;
import mir.Module;
import utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;

public class LoopInterchange {

    private static SCEVinfo scevInfo;
    private static int outer_init;
    @SuppressWarnings("FieldCanBeLocal")
    private static int outer_step;
    private static Instruction.Phi outer_indvar;
    private static Instruction.Icmp outer_indvar_cmp;
    private static Instruction outer_inc;
    private static int inner_init;
    @SuppressWarnings("FieldCanBeLocal")
    private static int inner_step;
    private static Instruction.Phi inner_indvar;
    private static Instruction.Icmp inner_indvar_cmp;
    private static Instruction inner_inc;

    private static boolean isComplex;
    private static int outer_cost;
    private static int inner_cost;

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        PointerBaseAnalysis.runOnFunc(function);
        boolean modified;
        do {
            modified = false;
            AnalysisManager.refreshCFG(function);
            LoopReBuildAndNormalize(function);
            IndVars.runOnFunc(function);
            scevInfo = AnalysisManager.getSCEV(function);
            for (var loop : function.loopInfo.TopLevelLoops) {
                modified |= tryInterchangeLoop(loop);
            }
            DeadLoopEliminate.runOnFunc(function);
            SimplifyCFGPass.runOnFunc(function);
            LocalValueNumbering.runOnFunc(function);
            SimplifyCFGPass.runOnFunc(function);
        } while (modified);
    }

    private static boolean canTransform(Loop loop) {
        if (loop.children.size() != 1) return false;
        Loop child = loop.children.iterator().next();
        // 退出块不唯一
        if (loop.exits.size() != 1) return false;
        if (child.exits.size() != 1) return false;
        // 退出条件复杂
        ArrayList<BasicBlock> _pre = loop.getExit().getPreBlocks();
        if (_pre.size() > 1 || _pre.get(0) != loop.header) return false;
        _pre = child.getExit().getPreBlocks();
        if (_pre.size() > 1 || _pre.get(0) != child.header) return false;

        Instruction outerTer = loop.header.getTerminator();
        if (!(outerTer instanceof Instruction.Branch br)) return false;
        Value outerCond = br.getCond();
        if (!(outerCond instanceof Instruction.Icmp icmp)) return false;
        if (!(icmp.getSrc2() instanceof Constant.ConstantInt) && scevInfo.contains(icmp.getSrc2()))
            icmp.swap();
        if (!scevInfo.contains(icmp.getSrc1()))
            return false;
        if (!scevInfo.query(icmp.getSrc1()).isNotNegative())
            return false;
        if (!(icmp.getSrc1() instanceof Instruction))
            return false;
        outer_indvar_cmp = icmp;
        if (!(icmp.getSrc1() instanceof Instruction.Phi)) return false;
        outer_indvar = (Instruction.Phi) icmp.getSrc1();
        outer_init = scevInfo.query(icmp.getSrc1()).getInit();
        outer_step = scevInfo.query(icmp.getSrc1()).getStep();
        outer_inc = (Instruction) outer_indvar.getOptionalValue(loop.getLatch());

        Instruction innerTer = child.header.getTerminator();
        if (!(innerTer instanceof Instruction.Branch innerBr)) return false;
        Value innerBrCond = innerBr.getCond();
        if (!(innerBrCond instanceof Instruction.Icmp innerIcmp)) return false;
        if (!(innerIcmp.getSrc2() instanceof Constant.ConstantInt) && scevInfo.contains(innerIcmp.getSrc2()))
            innerIcmp.swap();
        if (!scevInfo.contains(innerIcmp.getSrc1()))
            return false;
        if (!scevInfo.query(innerIcmp.getSrc1()).isNotNegative())
            return false;
        if (!(innerIcmp.getSrc1() instanceof Instruction))
            return false;
        inner_indvar_cmp = innerIcmp;
        if (!(innerIcmp.getSrc1() instanceof Instruction.Phi)) return false;
        inner_indvar = (Instruction.Phi) innerIcmp.getSrc1();
        inner_init = scevInfo.query(innerIcmp.getSrc1()).getInit();
        inner_step = scevInfo.query(innerIcmp.getSrc1()).getStep();
        inner_inc = (Instruction) inner_indvar.getOptionalValue(child.getLatch());

        BasicBlock innerExit = child.getExit();
        if (innerExit != loop.getLatch()) return false;
        // 判断当前循环以及内循环没有迭代依赖
        isComplex = false;
        outer_cost = 0;
        inner_cost = 0;
        HashMap<Value, Integer> loadStoreMap = new HashMap<>();
        for (BasicBlock block : loop.getAllBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof Instruction.Terminator) continue;
                if (inst instanceof Instruction.Load load) {
                    Value ptr = load.getAddr();
                    Value baseAddr = PointerBaseAnalysis.getBaseOrNull(ptr);
                    loadStoreMap.put(baseAddr, loadStoreMap.getOrDefault(baseAddr, 0) | 1);
                    if (ptr instanceof Instruction.GetElementPtr gep) {
                        dfsEval(gep.getIdx(), 0, loop);
                    }
                    if (isComplex) return false;
                } else if (inst instanceof Instruction.Store store) {
                    Value ptr = store.getAddr();
                    Value baseAddr = PointerBaseAnalysis.getBaseOrNull(ptr);
                    loadStoreMap.put(baseAddr, loadStoreMap.getOrDefault(baseAddr, 0) | 2);
                    if (ptr instanceof Instruction.GetElementPtr gep) {
                        dfsEval(gep.getIdx(), 0, loop);
                    }
                    if (isComplex) return false;
                } else if (!inst.isNoSideEffect()) {
                    return false;
                }
            }
        }
//        System.out.println(loop.header.getLabel());
//        System.out.println("outer_cost: " + outer_cost + " inner_cost: " + inner_cost);
        if (inner_cost <= outer_cost) return false;

        for (Value key : loadStoreMap.keySet()) {
            if (loadStoreMap.get(key) == 3) {
                Instruction.Load load = null;
                for (BasicBlock block : loop.getAllBlocks()) {
                    for (Instruction inst : block.getInstructions()) {
                        if (inst instanceof Instruction.Load) {
                            Value base = PointerBaseAnalysis.getBaseOrNull(((Instruction.Load) inst).getAddr());
                            if (base == key) {
                                if (load != null) return false;
                                load = (Instruction.Load) inst;
                            }
                        } else if (inst instanceof Instruction.Store store) {
                            Value base = PointerBaseAnalysis.getBaseOrNull(store.getAddr());
                            if (base == key) {
                                if (load == null) return false;
                                load = null;
                            }
                        }
                    }
                }
                if (load != null) return false;
            }
        }
        if (!downOuterInst(loop, child)) return false;
        // 判断outer和inner为完美嵌套循环
        BasicBlock innerExitBlock = child.getExit();
        if (innerExitBlock != loop.getLatch()) return false;
        for (Instruction inst : innerExitBlock.getInstructions()) {
            if (inst instanceof Instruction.Add) {
                if (inst != outer_inc) return false;
                continue;
            }
            if (inst instanceof Instruction.Terminator) {
                break;
            }
            return false;
        }
        return true;
    }

    private static void dfsEval(Value val, int cost, Loop loop) {
        if (isComplex) return;
        if (!loop.defValue(val)) return;
        if (val instanceof Instruction.Add add) {
            dfsEval(add.getOperand_1(), cost, loop);
            dfsEval(add.getOperand_2(), cost, loop);
        } else if (val instanceof Instruction.Mul mul){
            dfsEval(mul.getOperand_1(), cost + 1, loop);
            dfsEval(mul.getOperand_2(), cost + 1, loop);
        } else if (val instanceof Instruction.Phi phi) {
            if (phi == outer_indvar) outer_cost += cost;
            else if (phi == inner_indvar) inner_cost += cost;
            else isComplex = true;
        } else {
            isComplex = true;
        }

    }

    private static boolean downOuterInst(Loop father, Loop child) {
        BasicBlock outerBodyEntry = father.getBodyEntry();
        BasicBlock innerBodyEntry = child.getBodyEntry();
        if (outerBodyEntry.getPreBlocks().size() != 1) return false;
        if (innerBodyEntry.getPreBlocks().size() != 1) return false;
        if (outerBodyEntry != child.header) {
            if (outerBodyEntry.getSucBlocks().size() != 1) return false;
            if (outerBodyEntry.getSucBlocks().get(0) != child.header) return false;
            Instruction firstInst = innerBodyEntry.getFirstInst();
            for (Instruction inst : outerBodyEntry.getInstructionsSnap()) {
                if (inst instanceof Instruction.Terminator) break;
                // 需要判断inst没有副作用？
                inst.remove();
                firstInst.addPrev(inst);
            }
        }
        return true;
    }

    public static boolean tryInterchangeLoop(Loop loop) {
        boolean modified = false;
        for (Loop child : loop.getChildrenSnap()) {
            modified |= tryInterchangeLoop(child);
        }
        if (!canTransform(loop)) return modified;

        // 循环不变量的补丁
        for (Instruction.Phi phi : loop.header.getPhiInstructions()) {
            if (phi.getOptionalValue(loop.getLatch()) == phi) {
                phi.replaceAllUsesWith(phi.getOptionalValue(loop.getPreHeader()));
                phi.delete();
            }
        }
        Loop child = loop.children.iterator().next();
        System.out.println("Interchange: " + loop.header.getLabel() + " " + child.header.getLabel());
        // 父循环视角
        outer_indvar.remove();
        child.header.addInstFirst(outer_indvar);
        outer_indvar.changePreBlock(loop.getPreHeader(), child.getPreHeader());
        outer_indvar.changePreBlock(loop.getLatch(), child.getLatch());

        inner_indvar.remove();
        loop.header.addInstFirst(inner_indvar);
        inner_indvar.changePreBlock(child.getPreHeader(), loop.getPreHeader());
        inner_indvar.changePreBlock(child.getLatch(), loop.getLatch());

        outer_inc.remove();
        child.getLatch().getTerminator().addPrev(outer_inc);
        inner_inc.remove();
        loop.getLatch().getTerminator().addPrev(inner_inc);

        outer_indvar_cmp.remove();
        child.header.getTerminator().addPrev(outer_indvar_cmp);
        child.header.getTerminator().replaceUseOfWith(inner_indvar_cmp, outer_indvar_cmp);
        inner_indvar_cmp.remove();
        loop.header.getTerminator().addPrev(inner_indvar_cmp);
        loop.header.getTerminator().replaceUseOfWith(outer_indvar_cmp, inner_indvar_cmp);

        return true;
    }

    private static void LoopReBuildAndNormalize(Function func) {
        LCSSA.removeOnFunc(func);
        LoopInfo.runOnFunc(func);
        LoopSimplifyForm.runOnFunc(func);
        LCSSA.runOnFunc(func);
    }
}
