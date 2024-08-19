package midend.Transform.Loop;

import midend.Analysis.PointerBaseAnalysis;
import midend.Analysis.result.SCEVinfo;
import midend.Transform.DCE.DeadLoopEliminate;
import midend.Transform.DCE.SimplifyCFGPass;
import midend.Transform.LocalValueNumbering;
import midend.Util.Print;
import mir.*;
import midend.Analysis.AnalysisManager;
import mir.Module;
import utils.Pair;

import java.util.ArrayList;
import java.util.Collections;
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
        if (!embedOuterInst(loop, child)) return false;
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
        } else if (val instanceof Instruction.Mul mul) {
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

    /**
     * 将外循环的latch指令嵌入到内循环中
     * assert child.getExit() == father.getLatch()
     *
     * @param father 外循环
     * @param child  内循环
     * @return 是否成功
     */
    private static boolean embedOuterInst(Loop father, Loop child) {
        BasicBlock block = child.getExit();
        for (Instruction.Phi phi : block.getPhiInstructions()) {
            if (phi.isLCSSA) {
                if (!isOnlySum(phi.getOptionalValue(child.header), child))
                    return false;
                if (phi.getUsers().size() > 1) return false;
                User user = phi.getUsers().iterator().next();
                if (user instanceof Instruction.Store store) {
                    if (store.getValue() != phi) {
                        return false;
                    }
                } else return false;
                ArrayList<Instruction> list = new ArrayList<>();
                if (!collectOperands(father, block, store.getAddr(), list)) return false;
                Collections.reverse(list);
                for (Instruction inst : list) {
                    inst.remove();
                    onlySum_add.addPrev(inst);
                }
                Instruction.Load ld = new Instruction.Load(onlySum_add.getParentBlock(), store.getAddr());
                ld.remove();
                onlySum_add.addPrev(ld);
                onlySum_add.replaceUseOfWith(phi.getOptionalValue(child.header), ld);
                store.remove();
                onlySum_add.addNext(store);
                store.replaceUseOfWith(phi, onlySum_add);
                phi.delete();
            }
            else return false;
        }
        return true;
    }


    private static Instruction onlySum_add;
    /**
     * 判断phi是否为一条简单的累加指令
     */
    private static boolean isOnlySum(Value inst, Loop loop) {
        if (!(inst instanceof Instruction.Phi phi)) return false;
        // TODO: 可以加强 不必非得是0
        if (!phi.getOptionalValue(loop.getPreHeader()).equals(Constant.ConstantInt.get(0))) return false;
        if (phi.getUsers().size() > 2) return false;
        Value val = phi.getOptionalValue(loop.getLatch());
        if (val instanceof Instruction.Add add) {
            if (!(add.getOperand_1() == phi) && !(add.getOperand_2() == phi)) return false;
            onlySum_add = add;
        } else return false;
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean collectOperands(Loop loop, BasicBlock block, Value val, ArrayList<Instruction> ret) {
        if (!loop.defValue(val)) return true;
        if (val instanceof Instruction inst) {
            if (inst.getParentBlock() == block) {
                if (inst instanceof Instruction.Phi phi){
                    return phi.isLCSSA;
                }
                ret.add(inst);
                for (Value operand : inst.getOperands()) {
                    if (!collectOperands(loop, block, operand, ret)) return false;
                }
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
//        System.out.println("Interchange: " + loop.header.getLabel() + " " + child.header.getLabel());
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
/*
main_BB18:
	%phi_7 = phi i32 [ 0, %main_BB16 ], [ %add_44, %main_BB19 ]
	%icmp_6 = icmp slt i32 %phi_7, 1000
	br i1 %icmp_6, label %main_BB19, label %main_BB20 ;1.000000


main_BB19:
	%mul_19 = mul i32 1000, %phi_7
	%add_15 = add i32 %mul_19, %phi_16
	%add_13 = add i32 %mul_17, %phi_7
	%gep_6 = getelementptr [1000 x [1000 x i32]], [1000 x [1000 x i32]]* @a, i32 0, i32 0, i32 %add_13
	%load_21 = load i32, i32* %gep_4
	%load_24 = load i32, i32* %gep_6
	%gep_7 = getelementptr [1000 x [1000 x i32]], [1000 x [1000 x i32]]* @b, i32 0, i32 0, i32 %add_15
	%load_27 = load i32, i32* %gep_7
	%mul_6 = mul i32 %load_24, %load_27
	%add_16 = add i32 %load_21, %mul_6
	store i32 %add_16, i32* %gep_4
	%add_44 = add i32 1, %phi_7
	br label %main_BB18


main_BB20:
	%add_45 = add i32 1, %phi_16
	br label %main_BB15
 */
/*
main_BB18:
	%phi_10 = phi i32 [ 0, %main_BB16 ], [ %add_42, %main_BB19 ]
	%phi_5 = phi i32 [ 0, %main_BB16 ], [ %add_12, %main_BB19 ]
	%icmp_6 = icmp slt i32 %phi_10, 1000
	br i1 %icmp_6, label %main_BB19, label %main_BB20 ;0.500000


main_BB19:
	%add_9 = add i32 %mul_16, %phi_10
	%gep_4 = getelementptr [1000 x [1000 x i32]], [1000 x [1000 x i32]]* @a, i32 0, i32 0, i32 %add_9
	%load_20 = load i32, i32* %gep_4
	%mul_18 = mul i32 1000, %phi_10
	%add_11 = add i32 %mul_18, %phi_19
	%gep_5 = getelementptr [1000 x [1000 x i32]], [1000 x [1000 x i32]]* @b, i32 0, i32 0, i32 %add_11
	%load_23 = load i32, i32* %gep_5
	%mul_4 = mul i32 %load_20, %load_23
	%add_12 = add i32 %phi_5, %mul_4
	%add_42 = add i32 1, %phi_10
	br label %main_BB18


main_BB20:
	%phi_62 = phi i32 [ %phi_5, %main_BB18 ] ; [isLCSSA]
	%add_15 = add i32 %mul_16, %phi_19
	%gep_6 = getelementptr [1000 x [1000 x i32]], [1000 x [1000 x i32]]* @c, i32 0, i32 0, i32 %add_15
	store i32 %phi_62, i32* %gep_6
	%add_43 = add i32 1, %phi_19
	br label %main_BB15
 */