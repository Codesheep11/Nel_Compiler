package midend.Transform.Loop;

import midend.Analysis.AliasAnalysis;
import midend.Analysis.AnalysisManager;
import midend.Util.FuncInfo;
import mir.Module;
import mir.*;

import java.util.HashSet;

public class LICMMemory {
    // 非load和store有关的东西都在gcm里实现了，现在只需要看load store就行了

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    private static void runOnFunc(Function function) {
        AliasAnalysis.runOnFunc(function);
        for (Loop loop : function.loopInfo.TopLevelLoops) {
            while (true) {
                if (!runOnLoop(loop)) break;
            }
        }
    }
    // 提取load指令的前提:

    /**
     * 基本原则：指令的指针不随循环改变,也就是说addr的parent不在循环内
     * store:没有其他读指令读这个地方
     **/
    private static boolean runOnLoop(Loop loop) {
        boolean modify = false;
        for (Loop subloop : loop.children) {
            modify |= runOnLoop(subloop);
        }
        HashSet<Instruction.Load> loads = new HashSet<>();
        HashSet<Instruction.Store> stores = new HashSet<>();
        HashSet<Instruction> after = new HashSet<>();
        HashSet<Instruction> before = new HashSet<>();
        // 存储所有可能修改的指针
        HashSet<Value> changedPointers = new HashSet<>();
        // 存储所有可能用到的指针
        HashSet<Value> usedPointers = new HashSet<>();
        // nowLevel是本层的,all才是所有在其中的
        HashSet<BasicBlock> allBlocks = loop.getAllBlocks();
        for (BasicBlock block : allBlocks) {
            // 遍历收集所有load和store,如果遇到对地址可能造成修改或者读取的call那么就直接退出
            for (Instruction instr : block.getInstructions()) {
                if (instr instanceof Instruction.Call call) {
                    FuncInfo funcInfo = AnalysisManager.getFuncInfo(call.getDestFunction());
                    if (funcInfo.hasSideEffect || funcInfo.hasMemoryWrite || funcInfo.hasMemoryRead) return false;
                } else if (instr instanceof Instruction.Store store) {
                    stores.add(store);
                    changedPointers.add(store.getAddr());
                } else if (instr instanceof Instruction.Load load) {
                    loads.add(load);
                    usedPointers.add(load.getAddr());
                }
            }
        }
        for (Instruction.Store store : stores) {
            if (store.getAddr() instanceof Instruction i && allBlocks.contains(i.getParentBlock())) continue;
            if (loop.defValue(store.getValue())) return false;
            // 如果没有人和他可能冲突的话
            boolean notConflict = !maybeConflict(store.getAddr(), usedPointers);
            for (Instruction.Store other : stores) {
                if (store == other) continue;
                if (AliasAnalysis.isDistinct(store.getAddr(), other.getAddr())) {
                    notConflict = false;
                    break;
                }
            }
            if (notConflict) {
                before.add(store);
            }
        }
        for (Instruction.Load load : loads) {
            // 如果地址随循环改变那么不可以提出去
            if (load.getAddr() instanceof Instruction i && allBlocks.contains(i.getParentBlock())) continue;
            // 如果它这个地址对应的值可能会被store修改那么就不可以提出去
            if (maybeConflict(load.getAddr(), changedPointers)) continue;
            // 走到这里load出来的值必定不会被循环改变了
            boolean usedInLoop = false;
            for (Instruction user : load.getUsers()) {
                // 判断load的值是否在循环中被使用
                usedInLoop |= loop.getAllBlocks().contains(user.getParentBlock());
            }
            if (!usedInLoop) {
                after.add(load);
            } else {
                before.add(load);
            }
        }
        modify |= !before.isEmpty() || !after.isEmpty();
        for (Instruction instruction : before) {
            insert(loop, instruction, true);
        }
        for (Instruction instruction : after) {
            insert(loop, instruction, false);
        }
        return modify;
    }

    // 每有必要新开一个，就移动到preHeader或者Exit即可
    // 如果是用了但是没有被修改，就放到循环前,如果没有用,那么就放到循环后的exit的最后一条phi后
    private static void insert(Loop loop, Instruction instr, boolean placeHeader) {
        instr.remove();
        if (placeHeader) {
            instr.setParentBlock(loop.getPreHeader());
            loop.getPreHeader().insertInstBefore(instr, loop.getPreHeader().getInstructions().getLast());
        } else {
            instr.setParentBlock(loop.getExit());
            Instruction inst = loop.getExit().getInstructions().getFirst();
            while (inst instanceof Instruction.Phi) {
                inst = (Instruction) inst.getNext();
            }
            inst.addPrev(instr);
        }
    }

    private static boolean maybeConflict(Value value, HashSet<Value> changedPointers) {
        for (Value pointer : changedPointers) {
            if (!AliasAnalysis.isDistinct(value, pointer)) return true;
        }
        return false;
    }
}
