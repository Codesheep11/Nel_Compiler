package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.*;
import mir.Module;

import java.util.HashSet;
import java.util.Iterator;

/**
 * 全局变量分析
 * 只支持标量的替换
 */
public class GlobalVarLocalize {

    private static final HashSet<GlobalVariable> GlobalReplace = new HashSet<>();

    public static void run(Module module) {
        //对于只读的全局变量，将其替换为常数
        GlobalReplace.clear();
        FindOnlyRead(module);
        Replace2Const(module);
        //对于只出现在一个非递归函数中的全局变量，将其替换为对应的局部变量
        GlobalReplace.clear();
        GlobalLocalized(module);
    }

    private static void FindOnlyRead(Module module) {
        for (GlobalVariable gv : module.getGlobalValues()) {
            if (!((Type.PointerType) gv.getType()).getInnerType().isArrayTy()) GlobalReplace.add(gv);
        }
        for (Function func : module.getFuncSet()) {
            for (BasicBlock bb : func.getBlocks()) {
                for (var inst : bb.getInstructions()) {
                    if (inst instanceof Instruction.Store) {
                        Value addr = isGlobalAddr(((Instruction.Store) inst).getAddr());
                        if (addr != null && !((Type.PointerType) addr.getType()).getInnerType().isArrayTy()) {
                            GlobalReplace.remove(addr);
                        }
                    }
                }
            }
        }
    }


    private static void Replace2Const(Module module) {
//        for (GlobalVariable gv : GlobalReplace) {
//            System.out.println("Replace2Const " + gv);
//        }
        for (GlobalVariable gv : GlobalReplace) {
            Iterator<Use> useIterator = gv.getUses().iterator();
            HashSet<Instruction.Load> loads = new HashSet<>();
            while (useIterator.hasNext()) {
                Use use = useIterator.next();
                Instruction inst = (Instruction) use.getUser();
                if (inst instanceof Instruction.Load) {
                    Constant constant = gv.getConstValue();
                    inst.replaceAllUsesWith(constant);
                    loads.add((Instruction.Load) inst);
                }
                else throw new RuntimeException("GlobalVarAnalysis: ReplaceUse: unknown instruction");
            }
            for (Instruction.Load load : loads) load.delete();
        }
        for (GlobalVariable gv : GlobalReplace) {
            module.getGlobalValues().remove(gv);
        }
    }

    private static void GlobalLocalized(Module module) {
        for (GlobalVariable gv : module.getGlobalValues()) {
            if (((Type.PointerType) gv.getType()).getInnerType().isArrayTy()) continue;
            HashSet<Function> useFunctions = new HashSet<>();
            for (Use use : gv.getUses()) {
                useFunctions.add(((Instruction) use.getUser()).getParentBlock().getParentFunction());
            }
            if (useFunctions.size() > 1) continue;
            else if (useFunctions.isEmpty()) module.getGlobalValues().remove(gv);
            else if (useFunctions.iterator().next().getName().equals("main")) {
                //todo 考虑非main的情况下将全局变量作为参数进行传递
                GlobalReplace.add(gv);
                FuncReplace(useFunctions.iterator().next(), gv);
            }
        }
        if (!GlobalReplace.isEmpty()) Mem2Reg.run(module);
        for (GlobalVariable gv : GlobalReplace) {
            module.getGlobalValues().remove(gv);
        }
    }

    private static void FuncReplace(Function func, GlobalVariable gv) {
//        System.out.println("FuncReplace " + gv);
        FuncInfo funcInfo = AnalysisManager.getFuncInfo(func);
        if (funcInfo.isRecursive) return;
        //将全局变量转换成局部变量再mem2reg
        BasicBlock entry = func.getEntry();
        Instruction alloca = new Instruction.Alloc(entry, ((Type.PointerType) gv.getType()).getInnerType());
        alloca.remove();
        Instruction store = new Instruction.Store(entry, gv.getConstValue(), alloca);
        store.remove();
        entry.insertInstBefore(alloca, entry.getFirstInst());
        entry.insertInstAfter(store, alloca);
        gv.replaceAllUsesWith(alloca);
//        LinkedList<Use> uses = new LinkedList<>(gv.getUses());
//        Iterator<Use> useIterator = uses.iterator();
//        while (useIterator.hasNext()) {
//            Use use = useIterator.next();
//            Instruction inst = (Instruction) use.getUser();
//            inst.replaceUseOfWith(gv, alloca);
//        }
    }

    /**
     * 判断是否是全局变量
     *
     */

    public static Value isGlobalAddr(Value addr) {
        if (addr instanceof Instruction.GetElementPtr gep) {
            addr = gep.getBase();
        }
        if (addr instanceof GlobalVariable) return addr;
        return null;
    }
}
