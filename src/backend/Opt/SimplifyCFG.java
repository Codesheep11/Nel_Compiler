package backend.Opt;

import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.B;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;
import utils.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.ListIterator;

public class SimplifyCFG {


    // 此方法屏蔽所有一个仅有一个非条件跳转指令的块或者空块
    // 然后仅有一个非条件的和空块就可以被remove啥的给删掉
    public static boolean redirectGoto(RiscvFunction func) {
        HashMap<RiscvBlock, RiscvBlock> redirect = new HashMap<>();
        for (RiscvBlock block : func.blocks) {
            if (block.riscvInstructions.size() > 1) continue;
            if (block.riscvInstructions.isEmpty()) {
                if (func.blocks.indexOf(block) == func.blocks.size() - 1) continue;
                // 是最后一个块的话就跳过
                RiscvBlock next = func.blocks.get(func.blocks.indexOf(block) + 1);
                // 否则所有到他的都可以认为是到下一个块
                redirect.put(block, next);
            } else {
                RiscvInstruction inst = block.riscvInstructions.getFirst();
                if (inst instanceof J && ((J) inst).type == J.JType.j) {
                    RiscvBlock targetBlock = ((J) inst).targetBlock;
                    redirect.put(block, targetBlock);
                }
            }
        }

        if (redirect.isEmpty()) return false;
        boolean modified = false;
        // 确定最后的重定向方向
        for (RiscvBlock block : redirect.keySet()) {
            RiscvBlock des = redirect.get(block);
            while (redirect.containsKey(des)) {
                redirect.put(block, redirect.get(des));
                des = redirect.get(des);
            }
        }
        for (RiscvBlock block : func.blocks) {
            for (RiscvInstruction inst : block.riscvInstructions) {
                // 如果要修改的目标块里有这个块
                if (inst instanceof J && ((J) inst).type == J.JType.j) {
                    if (redirect.containsKey(((J) inst).targetBlock)) {
                        ((J) inst).targetBlock = redirect.get(((J) inst).targetBlock);
                        modified = true;
                    }
                } else if (inst instanceof B) {
                    if (redirect.containsKey(((B) inst).targetBlock)) {
                        ((B) inst).targetBlock = redirect.get(((B) inst).targetBlock);
                        modified = true;
                    }
                }
            }
        }
        // 删除所有空的块,仅有一个J的后面会删
        func.blocks.removeIf(block -> block.riscvInstructions.isEmpty());
        return modified;
    }

    //
    public static boolean removeUnusedLabels(RiscvFunction func) {
        HashSet<RiscvBlock> usedLabels = new HashSet<>();
        LinkedList<RiscvBlock> q = new LinkedList<>();
        q.add(func.blocks.get(0));
        usedLabels.add(func.blocks.get(0));
        while (q.size() != 0) {
            RiscvBlock block = q.poll();
            if (block.riscvInstructions.size() == 0) {
                int idx = func.blocks.indexOf(block);
                if (idx >= func.blocks.size() - 1) continue;
                RiscvBlock next = func.blocks.get(idx + 1);
                if (!usedLabels.contains(next)) {
                    q.add(next);
                    usedLabels.add(next);
                }
            } else {
                RiscvInstruction jump = block.riscvInstructions.getLast();
                if (block.riscvInstructions.size() >= 2) {
                    RiscvInstruction branch = block.riscvInstructions.get(block.riscvInstructions.size() - 2);
                    if (branch instanceof B) {
                        if (!usedLabels.contains(((B) branch).targetBlock)) {
                            q.add(((B) branch).targetBlock);
                            usedLabels.add(((B) branch).targetBlock);
                        }
                    }
                }
                if (jump instanceof J) {
                    if (((J) jump).type == J.JType.j) {
                        if (!usedLabels.contains(((J) jump).targetBlock)) {
                            q.add(((J) jump).targetBlock);
                            usedLabels.add(((J) jump).targetBlock);
                        }
                        continue;
                    } else if (((J) jump).type == J.JType.ret) {
                        continue;
                    }
                }
                if (jump instanceof B) {
                    if (!usedLabels.contains(((B) jump).targetBlock)) {
                        q.add(((B) jump).targetBlock);
                        usedLabels.add(((B) jump).targetBlock);
                    }
                }
                //剩下的只需要找下面的就行
                int idx = func.blocks.indexOf(block);
                if (idx >= func.blocks.size() - 1) continue;
                RiscvBlock next = func.blocks.get(idx + 1);
                if (!usedLabels.contains(next)) {
                    q.add(next);
                    usedLabels.add(next);
                }
            }
        }
        int size = func.blocks.size();
        func.blocks.removeIf(block -> !usedLabels.contains(block));
        return size != func.blocks.size();
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

    public static boolean sfbOpt(RiscvFunction func) {
        boolean modified = false;
        ListIterator<RiscvBlock> iter = func.blocks.listIterator();
        while (iter.hasNext()) {
            RiscvBlock block = iter.next();
            if (!iter.hasNext()) {
                continue;
            }
            RiscvBlock nextBlock = iter.next();
            iter.previous(); // 将迭代器回退到正确位置
            RiscvInstruction terminator = block.riscvInstructions.getLast();
            RiscvBlock targetBlock = null;
            // 检查终止指令是否为条件跳转指令
            if (terminator instanceof B) {
                targetBlock = ((B) terminator).targetBlock;
                // 如果目标块的指令数量不等于2，继续
                if (targetBlock.riscvInstructions.size() != 2) {
                    continue;
                }
                RiscvInstruction back = targetBlock.riscvInstructions.getLast();
                RiscvBlock nextTargetBlock = null;
                // 检查最后一条指令是否为无条件跳转指令
                if (back instanceof J && ((J) back).type == J.JType.j) {
                    nextTargetBlock = ((J) back).targetBlock;
                } else {
                    continue;
                }
                // 如果无条件跳转的目标块不是下一个基本块，继续
                if (nextTargetBlock != nextBlock) {
                    continue;
                }
                // 反转条件跳转指令
                ((B) terminator).inverse();
                ((B) terminator).targetBlock = nextTargetBlock;
                RiscvInstruction needMove = targetBlock.riscvInstructions.getFirst();
                needMove.remove();
                block.riscvInstructions.addLast(needMove);
                modified = true;
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
            if (block.riscvInstructions.size() < 2) {
                continue;
            }
            RiscvInstruction jump = block.riscvInstructions.getLast();
            RiscvInstruction branch = block.riscvInstructions.get(block.riscvInstructions.size() - 2);
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
            modified |= reorderBranch(func);
            //modified |= Peephole.genericPeepholeOpt(func);
            modified |= conditional2Unconditional(func);
            modified |= redirectGoto(func);
            //modified |= Peephole.genericPeepholeOpt(func);
            modified |= removeUnusedLabels(func);
            modified |= sfbOpt(func);
            if (!modified) {
                return;
            }
        }
    }


    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            blockLinkOpt(function);
            simplifyCFG(function);
        }
    }

    public static void blockLinkOpt(RiscvFunction func) {
        // 下面就是在解决跳转问题
        // 解决策略:如果下一个块就是直接跳转的target,那么就去掉跳转指令
        // 如果下一个块不是默认的target,且最后一个不是跳往的指令，那么就应该加上
        HashMap<RiscvBlock, BackCFGNode> cfg = GenCFG.calcCFG(func);
        for (int i = 0; i < func.blocks.size(); i++) {
            RiscvBlock block = func.blocks.get(i);
            HashSet<RiscvBlock> targets = new HashSet<>();
            for (Pair<RiscvBlock, Double> pair : cfg.get(block).suc) {
                targets.add(pair.first);
                // 将所有目的加入其中
            }
            boolean removeLast = false;// 是否要删除最后一条指令
            for (RiscvInstruction ri : block.riscvInstructions) {
                // 检查B,将所有的可能的块收入其中
                if (ri instanceof B) {
                    targets.remove(((B) ri).targetBlock);
                    if (ri == block.getLast()) {
                        if (func.blocks.get(i + 1) == ((B) ri).targetBlock) {
                            removeLast = true;
                            break;
                        }
                    }
                }
            }
            // 如果是最后一个指令
            RiscvInstruction ri = block.getLast();
            if (ri instanceof J && ((J) block.getLast()).type != J.JType.ret) {
                // 如果最后一个是J,不是而J的目标是下一个block的话,
                targets.remove(((J) ri).targetBlock);
                if (func.blocks.size() > i + 1) {
                    if (func.blocks.get(i + 1) == ((J) ri).targetBlock) {
                        removeLast = true;
                    }
                }
            }
            if (targets.size() == 1) {
                // 如果最后一个指令还有剩余
                block.addInstrucion(new J(targets.iterator().next(), J.JType.j));
            } else if (targets.size() >= 1) {
                throw new RuntimeException("too more target block");
            }
            if (removeLast) {
                block.riscvInstructions.removeLast();
            }
        }
    }
}
