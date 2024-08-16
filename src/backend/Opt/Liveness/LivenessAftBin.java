package backend.Opt.Liveness;

import backend.Opt.CfgOpt.BackCFGNode;
import backend.Opt.CfgOpt.GenCFG;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.RiscvInstruction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public class LivenessAftBin {
    // 块内联后的活跃变量分析
    public static HashMap<RiscvBlock, BackCFGNode> cfg;

    private static ArrayList<RiscvBlock> callTopoSortAft(RiscvFunction function) {
        cfg = GenCFG.calcCFG(function);
        ArrayList<RiscvBlock> res = new ArrayList<>();
        HashSet<RiscvBlock> vis = new HashSet<>();
        HashSet<RiscvBlock> exits = callRetBlock(function);
        for (RiscvBlock block : exits) {
            dfs(block, res, vis);
        }
        Collections.reverse(res);
        return res;
    }

    private static void dfs(RiscvBlock rb, ArrayList<RiscvBlock> res, HashSet<RiscvBlock> vis) {
        if (vis.contains(rb)) return;
        vis.add(rb);
        for (RiscvBlock prev : cfg.get(rb).pre.keySet()) {
            dfs(prev, res, vis);
        }
        res.add(rb);
    }

    private static HashSet<RiscvBlock> callRetBlock(RiscvFunction function) {
        HashSet<RiscvBlock> ans = new HashSet<>();
        for (RiscvBlock block : function.blocks) {
            if (block.riscvInstructions.getLast() instanceof J j && j.type == J.JType.ret) {
                ans.add(block);
            }
        }
        return ans;
    }

    public static final HashMap<RiscvBlock, HashSet<Reg>> BlockIn = new HashMap<>();
    public static final HashMap<RiscvBlock, HashSet<Reg>> BlockOut = new HashMap<>();
    public static final HashMap<RiscvBlock, HashSet<Reg>> BlockUse = new HashMap<>();
    public static final HashMap<RiscvBlock, HashSet<Reg>> BlockDef = new HashMap<>();

    private static final HashMap<RiscvInstruction, HashSet<Reg>> In = new HashMap<>();
    public static final HashMap<RiscvInstruction, HashSet<Reg>> Out = new HashMap<>();
    private static final HashMap<RiscvInstruction, HashSet<Reg>> Use = new HashMap<>();
    private static final HashMap<RiscvInstruction, HashSet<Reg>> Def = new HashMap<>();

    private static final HashMap<Reg, HashSet<RiscvInstruction>> RegUse = new HashMap<>();

    private static void clear() {
        BlockIn.clear();
        BlockOut.clear();
        BlockUse.clear();
        BlockDef.clear();
        In.clear();
        Out.clear();
        Use.clear();
        Def.clear();
        RegUse.clear();
    }

    public static void runOnFunc(RiscvFunction function) {
        clear();
        for (RiscvBlock block : function.blocks) {
            for (RiscvInstruction ins : block.riscvInstructions) {
                In.put(ins, new HashSet<>());
                Out.put(ins, new HashSet<>());
                Use.put(ins, ins.getUse());
                Def.put(ins, ins.getDef());
                for (Reg reg : ins.getReg()) {
                    RegUse.putIfAbsent(reg, new HashSet<>());
                    RegUse.get(reg).add(ins);
                }
            }
        }
        for (RiscvBlock block : function.blocks) {
            BlockIn.put(block, new HashSet<>());
            BlockOut.put(block, new HashSet<>());
            BlockUse.put(block, new HashSet<>());
            BlockDef.put(block, new HashSet<>());
        }
        // 由于即使再内联,函数的def也不会变化,所以可以直接用
        BlockDef.get(function.getEntry()).addAll(function.defs);
        for (RiscvBlock block : function.blocks) {
            for (RiscvInstruction ins : block.riscvInstructions) {
                HashSet<Reg> tmp = new HashSet<>(Use.get(ins));
                tmp.removeAll(BlockDef.get(block));
                BlockUse.get(block).addAll(tmp);
                BlockDef.get(block).addAll(Def.get(ins));
            }
        }
        ArrayList<RiscvBlock> topoSort = callTopoSortAft(function);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (RiscvBlock block : topoSort) {
                HashSet<Reg> outSet = new HashSet<>();
                // out = U(succ)in
                for (RiscvBlock succ : cfg.get(block).suc.keySet()) {
                    outSet.addAll(BlockIn.get(succ));
                }
                if (!outSet.equals(BlockOut.get(block))) {
                    changed = true;
                    BlockOut.put(block, outSet);
                }
                // in = use ∪ (out - def)
                HashSet<Reg> inSet = new HashSet<>(BlockOut.get(block));
                inSet.removeAll(BlockDef.get(block));
                inSet.addAll(BlockUse.get(block));
                if (!inSet.equals(BlockIn.get(block))) {
                    changed = true;
                    BlockIn.put(block, inSet);
                }
            }
        }
        for (RiscvBlock block : topoSort) {
            if (block.riscvInstructions.isEmpty()) continue;
            In.put(block.getFirst(), BlockIn.get(block));
            Out.put(block.getLast(), BlockOut.get(block));
            int size = block.riscvInstructions.size();
            for (int i = size - 1; i >= 0; i--) {
                RiscvInstruction ins = block.riscvInstructions.get(i);
                if (i != size - 1) {
                    //out[i] = in[i+1]
                    Out.put(ins, In.get(block.riscvInstructions.get(i + 1)));
                }
                if (i != 0) {
                    //in[i] = use[i] U (out[i] - def[i])
                    HashSet<Reg> inSet = new HashSet<>(Out.get(ins));
                    inSet.removeAll(Def.get(ins));
                    inSet.addAll(Use.get(ins));
                    In.put(ins, inSet);
                }
            }
        }
    }
}
