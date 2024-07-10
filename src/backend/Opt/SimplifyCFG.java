package backend.Opt;

import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.B;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;

import java.util.*;
import java.util.function.Consumer;

public class SimplifyCFG {


    // 此方法屏蔽所有一个仅有一个跳转指令的块
    public static boolean redirectGoto(RiscvFunction func) {
        HashMap<RiscvBlock, RiscvBlock> redirect = new HashMap<>();
        for (RiscvBlock block : func.blocks) {
            if (block.riscvInstructions.getSize() != 1) continue;
            RiscvInstruction inst = block.riscvInstructions.getFirst();
            if (inst instanceof J && ((J) inst).type == J.JType.j) {
                RiscvBlock targetBlock = ((J) inst).targetBlock;
                redirect.put(block, targetBlock);
            }
        }

        if (redirect.isEmpty())
            return false;
        boolean modified = false;
        for (RiscvBlock block : func.blocks) {
            for (RiscvInstruction inst : block.riscvInstructions) {
                // 如果要修改的目标块里有这个块
                if (inst instanceof J && ((J) inst).type == J.JType.j) {
                    if (redirect.containsKey(((J) inst).targetBlock)) {
                        ((J) inst).targetBlock = redirect.get(((J) inst).targetBlock);
                        modified = true;
                    }
                }
            }
        }
        return modified;
    }

    // 理论上经过前面的优化，不会出现不会访问到的块，因此不用写这个
    public static boolean removeUnusedLabels(RiscvFunction func) {
        Set<RiscvBlock> usedLabels = new HashSet<>();
        usedLabels.add(func.blocks.get(0));
        for (RiscvBlock block : func.blocks) {
            for (RiscvInstruction inst : block.riscvInstructions) {
                if (inst instanceof J && ((J) inst).type == J.JType.j) {
                    usedLabels.add(((J) inst).targetBlock);
                }
            }
        }
        if (usedLabels.size() == func.blocks.size()) {
            return false;
        }
        LinkedList<RiscvBlock>q = new LinkedList<>();
        q.add(func.blocks.get(0));
        while (q.size()!=0)
        {
            RiscvBlock block=q.poll();

        }
        func.blocks.removeIf(block -> !usedLabels.contains(block));
        return true;
    }


    public static boolean removeEmptyBlocks(RiscvFunction func) {
        HashMap<RiscvBlock, RiscvBlock> redirects = new HashMap<>();
        List<RiscvBlock> currentEmptySet = new ArrayList<>();
        // Lambda function equivalent in Java
        Consumer<RiscvBlock> commit = (target) -> {
            for (RiscvBlock block : currentEmptySet) {
                redirects.put(block, target);
            }
            currentEmptySet.clear();
        };
        for (RiscvBlock block : func.blocks) {
            if (block.riscvInstructions.isEmpty()) {
                currentEmptySet.add(block);
            } else {
                commit.accept(block);
            }
        }
        if (currentEmptySet.size() >= 2) {
            currentEmptySet.remove(currentEmptySet.size() - 1);
            commit.accept(func.blocks.get(func.blocks.size() - 1));
        }
        for (RiscvBlock block : func.blocks) {
            for (RiscvInstruction inst : block.riscvInstructions) {
                if (inst instanceof J && ((J) inst).type == J.JType.j) {
                    if (redirects.containsKey(((J) inst).targetBlock)) {
                        ((J) inst).targetBlock = redirects.get(((J) inst).targetBlock);
                    }
                }
            }
        }
        func.blocks.removeIf(redirects::containsKey);
        return !redirects.isEmpty();
    }

    public static boolean conditional2Unconditional(RiscvFunction func) {
        boolean modified = false;
        ListIterator<RiscvBlock> iter = func.blocks.listIterator();
        while (iter.hasNext()) {
            RiscvBlock block = iter.next();
            if (!iter.hasNext()) {
                break;
            }
            RiscvBlock nextBlock = iter.next();
            iter.previous(); // Move back the iterator to the correct position
            RiscvInstruction instr = block.riscvInstructions.getLast();
            if (instr instanceof B) {
                if (((B) instr).targetBlock == nextBlock) {
                    block.riscvInstructions.removeLast();
                    block.riscvInstructions.addLast(new J(block, J.JType.j, nextBlock));
                    modified = true;
                }
            }
        }
        return modified;
    }

    public static boolean reorderBranch(RiscvFunction func) {
        boolean modified = false;

        ListIterator<RiscvBlock> iter = func.blocks.listIterator();

        while (iter.hasNext()) {
            RiscvBlock block = iter.next();
            if (!iter.hasNext()) {
                break;
            }
            RiscvBlock nextBlock = iter.next();
            iter.previous(); // Move back the iterator to the correct position
            if (block.riscvInstructions.getSize() < 2) {
                continue;
            }
            RiscvInstruction jump = block.riscvInstructions.getLast();
            RiscvInstruction branch = block.riscvInstructions.get(block.riscvInstructions.getSize() - 2);
            if (branch instanceof B && jump instanceof J && ((J) jump).type == J.JType.j) {
                if (((B) branch).targetBlock == nextBlock) {
                    ((B) branch).inverse();
                    ((B) branch).targetBlock = ((J) jump).targetBlock;
                    block.riscvInstructions.removeLast();
                    modified = true;
                }
            }
        }
        return modified;
    }


    private static void simplifyCFG(RiscvFunction func) {
        while (true) {
            boolean modified = false;
            modified |= redirectGoto(func);
            modified |= removeEmptyBlocks(func);
            modified |= reorderBranch(func);
            modified |= Peephole.genericPeepholeOpt(func);
            modified |= conditional2Unconditional(func);
            modified |= redirectGoto(func);
            modified |= Peephole.genericPeepholeOpt(func);
            if (!modified) {
                return;
            }
        }
    }


    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            simplifyCFG(function);
        }
    }
}
