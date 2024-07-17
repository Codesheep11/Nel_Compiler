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

public class BlockInline {
    // 必须在simplify后使用

    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            makeMap(function);
            blockInline(function);
        }
    }

    // 事实上,光知道是不是能到其实没办法，需要知道的是是否会出现仅仅有b到的情况，那么还不能直接贴到后面
    private static final HashMap<RiscvBlock, HashSet<RiscvBlock>> myGoto = new HashMap<>();

    private static final HashMap<RiscvBlock, HashSet<Pair<RiscvBlock, Boolean>>> myFrom = new HashMap<>();

    private static final int MAX_LEN = 10;

    public static void getBlockTarget(RiscvBlock block, RiscvBlock next) {
        HashSet<RiscvBlock> ans = new HashSet<>();
        for (RiscvInstruction ri : block.riscvInstructions) {
            if (ri instanceof B) {
                ans.add(((B) ri).targetBlock);
                myFrom.get(((B) ri).targetBlock).add(new Pair<>(block, false));
            } else if (ri instanceof J && ((J) ri).type == J.JType.j) {
                ans.add(((J) ri).targetBlock);
                myFrom.get(((J) ri).targetBlock).add(new Pair<>(block, true));
            }
        }
        if (!(block.riscvInstructions.getLast() instanceof J)) {
            ans.add(next);
            myFrom.get(next).add(new Pair<>(block, true));
        }
        myGoto.put(block, ans);
    }

    public static void makeMap(RiscvFunction function) {
        myFrom.clear();
        for (RiscvBlock block : function.blocks) {
            myFrom.put(block, new HashSet<>());
        }
        myGoto.clear();
        for (int i = 0; i < function.blocks.size(); i++) {
            if (i == function.blocks.size() - 1) {
                getBlockTarget(function.blocks.get(i), null);
            } else {
                getBlockTarget(function.blocks.get(i), function.blocks.get(i + 1));
            }
        }
    }

    public static void blockInline(RiscvFunction function) {
        HashSet<RiscvBlock> remove = new HashSet<>();
        // 如果只有一个指向这个块且是确定要移动的话,那么直接将该块放到来自块后面
        // 如果来自块就在指向块之前，那么什么都不用动
        // 如果指向块的最后指令不是j，那么需要在最后加一个到它后面的j
        for (RiscvBlock block : function.blocks) {
            if (myFrom.get(block).size() == 1) {
                Pair<RiscvBlock, Boolean> from = myFrom.get(block).iterator().next();
                if (from.second) {
                    RiscvBlock fromBlock = from.first;
                    // 如果是跨越式到达，删除最后的J，然后粘贴所有指令，然后附加一个到原本block后面的块的指令
                    if (fromBlock.riscvInstructions.getLast() instanceof J) {
                        fromBlock.riscvInstructions.removeLast();
                        for (RiscvInstruction ri : block.riscvInstructions) {
                            fromBlock.riscvInstructions.addLast(ri.myCopy());
                        }
                        // 如果block是顺序到达后面的块，需要加一个j
                        if (!(fromBlock.riscvInstructions.getLast() instanceof J)) {
                            RiscvBlock blockNext = function.blocks.get(1 + function.blocks.indexOf(block));
                            fromBlock.riscvInstructions.addLast(new J(fromBlock, J.JType.j, blockNext));
                        }
                    } else {
                        // 如果是顺序到达，直接将指令粘贴即可
                        for (RiscvInstruction ri : block.riscvInstructions) {
                            fromBlock.riscvInstructions.addLast(ri.myCopy());
                        }
                    }
                    remove.add(block);
                    // 做完这些后，需要将block后面的目的块来自的块修改为fromBlock
                    for (RiscvBlock targetBlock : myGoto.get(block)) {
                        for (Pair<RiscvBlock, Boolean> pair : myFrom.get(targetBlock)) {
                            pair.first = fromBlock;
                        }
                    }
                }
            } else if (myFrom.get(block).size() >= 2) {
                // 有很多指向它的,这里就需要考虑最大块宽度问题了
                if (block.riscvInstructions.size() <= MAX_LEN) {
                    boolean canRemove = true;
                    for (Pair<RiscvBlock, Boolean> from : myFrom.get(block)) {
                        if (from.second) {
                            RiscvBlock fromBlock = from.first;
                            // 如果是跨越式到达，删除最后的J，然后粘贴所有指令，然后附加一个到原本block后面的块的指令
                            if (fromBlock.riscvInstructions.getLast() instanceof J) {
                                fromBlock.riscvInstructions.removeLast();
                                for (RiscvInstruction ri : block.riscvInstructions) {
                                    fromBlock.riscvInstructions.addLast(ri.myCopy());
                                }
                                // 如果block是顺序到达后面的块，需要加一个j
                                if (!(fromBlock.riscvInstructions.getLast() instanceof J)) {
                                    RiscvBlock blockNext = function.blocks.get(1 + function.blocks.indexOf(block));
                                    fromBlock.riscvInstructions.addLast(new J(fromBlock, J.JType.j, blockNext));
                                }
                            } else {
                                // 如果是顺序到达，直接将指令粘贴即可
                                for (RiscvInstruction ri : block.riscvInstructions) {
                                    fromBlock.riscvInstructions.addLast(ri.myCopy());
                                }
                            }
                            remove.add(block);
                            // 做完这些后，需要将block后面的目的块来自的块修改为fromBlock
                            for (RiscvBlock targetBlock : myGoto.get(block)) {
                                for (Pair<RiscvBlock, Boolean> pair : myFrom.get(targetBlock)) {
                                    pair.first = fromBlock;
                                }
                            }
                        } else {
                            canRemove = false;
                        }
                    }
                    if (canRemove) remove.add(block);
                }
            }
        }
        for (RiscvBlock need : remove) {
            function.blocks.remove(need);
        }
    }
}
