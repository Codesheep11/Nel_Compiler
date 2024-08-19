package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import midend.Analysis.PointerBaseAnalysis;
import midend.Analysis.result.SCEVinfo;
import midend.Transform.Mem2Reg;
import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 1. 对于子循环出口的LCSSA，如果在子循环中全为计算操作，并在父循环中store，将其直接在子循环中store
 * 2. 对于子循环中的load + 计算 + store，而该地址是循环不变量，则设置一个temp，并将store操作移出循环
 */
public class LoopNestTemp {
    private static SCEVinfo scevInfo;

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }


    public static void runOnFunc(Function function) {
        scevInfo = AnalysisManager.getSCEV(function);
        PointerBaseAnalysis.runOnFunc(function);
        for (Loop loop : function.loopInfo.TopLevelLoops) {
            runMem2Temp4Loop(loop);
        }
        Mem2Reg.runOnFunc(function);
    }

    private static void runOnLoop(Loop loop) {
        for (Loop child : loop.children) {
            runOnLoop(child);
        }
        if (loop.children.isEmpty()) return;
        if (loop.exits.size() != 1) return;
        ArrayList<BasicBlock> _pre = loop.getExit().getPreBlocks();
        if (_pre.size() > 1 || _pre.get(0) != loop.header) return;
        Instruction terminator = loop.header.getTerminator();
        if (!(terminator instanceof Instruction.Branch br)) return;
        Value cond = br.getCond();
        if (!(cond instanceof Instruction.Icmp icmp)) return;
    }

    public static void runTemp2Mem4Loop(Loop loop) {
        for (Loop child : loop.children) {
            runTemp2Mem4Loop(child);
        }
    }

    public static void runMem2Temp4Loop(Loop loop) {
        for (Loop child : loop.children) {
            runMem2Temp4Loop(child);
        }
        //似乎只要循环的出口只有一个，就可以进行这个优化
        if (loop.exits.size() != 1) return;
        boolean hasCallOrRetInLoop = false;
        for (BasicBlock block : loop.getAllBlocks()) {
            for (Instruction inst : block.getMainInstructions()) {
                if (inst instanceof Instruction.Call || inst instanceof Instruction.Return) {
                    hasCallOrRetInLoop = true;
                    break;
                }
            }
        }
        if (hasCallOrRetInLoop) return;
        HashMap<Value, Integer> loadStoreMap = new HashMap<>();
        HashMap<Value, Value> baseMap = new HashMap<>();
        boolean hasStore = false;
        for (BasicBlock block : loop.getAllBlocks()) {
            for (Instruction inst : block.getMainInstructions()) {
                if (inst instanceof Instruction.Load load) {
                    Value ptr = load.getAddr();
                    //获得指针的基地址
                    Value base = PointerBaseAnalysis.getBaseOrNull(ptr);
                    if (base == null) continue;
                    if (baseMap.containsKey(base) && baseMap.get(base) != ptr) {
                        loadStoreMap.remove(baseMap.get(base));
                        baseMap.put(base, null);
                        continue;
                    }
                    baseMap.put(base, ptr);
                    loadStoreMap.put(ptr, loadStoreMap.getOrDefault(ptr, 0) | 1);
                }
                else if (inst instanceof Instruction.Store store) {
                    hasStore = true;
                    Value ptr = store.getAddr();
                    Value base = PointerBaseAnalysis.getBaseOrNull(ptr);
                    if (base == null) continue;
                    if (baseMap.containsKey(base) && baseMap.get(base) != ptr) {
                        loadStoreMap.remove(baseMap.get(base));
                        baseMap.put(base, null);
                        continue;
                    }
                    loadStoreMap.put(ptr, loadStoreMap.getOrDefault(ptr, 0) | 2);
                }
            }
        }
        if (!hasStore) return;
        for (Value ptr : loadStoreMap.keySet()) {
            //指针是循环不变量
            if (!(ptr instanceof Instruction inst) || !loop.LoopContains(inst.getParentBlock())) {
                if (loadStoreMap.get(ptr) == 3) {
                    //preHeader中插入alloc，load，store
                    BasicBlock preHeader = loop.getPreHeader();
                    Instruction.Alloc alloc = new Instruction.Alloc(preHeader, ((Type.PointerType) ptr.getType()).getInnerType());
                    alloc.remove();
                    preHeader.insertInstBefore(alloc, preHeader.getTerminator());
                    Instruction.Load load = new Instruction.Load(preHeader, ptr);
                    load.remove();
                    preHeader.insertInstAfter(load, alloc);
                    Instruction.Store store = new Instruction.Store(preHeader, load, alloc);
                    store.remove();
                    preHeader.insertInstAfter(store, load);
                    //Exit中插入load store
                    BasicBlock exit = loop.getExit();
                    Instruction.Load load1 = new Instruction.Load(exit, alloc);
                    load1.remove();
                    Instruction insertPoint = exit.getFirstInst();
                    while (insertPoint instanceof Instruction.Phi) {
                        insertPoint = (Instruction) insertPoint.getNext();
                    }
                    exit.insertInstBefore(load1, insertPoint);
                    Instruction.Store store1 = new Instruction.Store(exit, load1, ptr);
                    store1.remove();
                    exit.insertInstAfter(store1, load1);
                    for (BasicBlock block : loop.getAllBlocks()) {
                        for (Instruction inst1 : block.getMainInstructions()) {
                            if (inst1 instanceof Instruction.Load load2 && load2.getAddr() == ptr) {
                                load2.replaceUseOfWith(ptr, alloc);
                            }
                            if (inst1 instanceof Instruction.Store store2 && store2.getAddr() == ptr) {
                                store2.replaceUseOfWith(ptr, alloc);
                            }
                        }
                    }
                }
            }
        }
    }
}
