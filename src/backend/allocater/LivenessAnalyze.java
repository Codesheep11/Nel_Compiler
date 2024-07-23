package backend.allocater;

import backend.StackManager;
import backend.operand.Address;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.LS;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import mir.Ir2RiscV.CodeGen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static backend.operand.Reg.PhyReg.*;
import static backend.operand.Reg.PhyReg.sp;

public class LivenessAnalyze {

    private static HashMap<RiscvBlock, HashSet<Reg>> BlockIn = new HashMap<>();
    private static HashMap<RiscvBlock, HashSet<Reg>> BlockOut = new HashMap<>();
    private static HashMap<RiscvBlock, HashSet<Reg>> BlockUse = new HashMap<>();
    private static HashMap<RiscvBlock, HashSet<Reg>> BlockDef = new HashMap<>();

    public static HashMap<RiscvInstruction, HashSet<Reg>> In = new HashMap<>();
    public static HashMap<RiscvInstruction, HashSet<Reg>> Out = new HashMap<>();
    public static HashMap<RiscvInstruction, HashSet<Reg>> Use = new HashMap<>();
    public static HashMap<RiscvInstruction, HashSet<Reg>> Def = new HashMap<>();

    public static HashMap<Reg, HashSet<RiscvInstruction>> RegUse = new HashMap<>();

    public static HashSet<Reg> callSaved = new HashSet<>();


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

    public static void RunOnFunc(RiscvFunction function) {
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
        BlockDef.get(function.getEntry()).addAll(function.defs);
        for (RiscvBlock block : function.blocks) {
            for (RiscvInstruction ins : block.riscvInstructions) {
                HashSet<Reg> tmp = new HashSet<>(Use.get(ins));
                tmp.removeAll(BlockDef.get(block));
                BlockUse.get(block).addAll(tmp);
                BlockDef.get(block).addAll(Def.get(ins));
            }
        }
        ArrayList<RiscvBlock> topoSort = function.getTopoSort();
        int time = 0;
        boolean changed = true;
        while (changed) {
//            System.out.println("cycle times: " + time++);
            changed = false;
            for (RiscvBlock block : topoSort) {
                HashSet<Reg> inSet = new HashSet();
                HashSet<Reg> outSet = new HashSet();
                // out = U(succ)in
                for (RiscvBlock succ : block.succBlock) {
                    outSet.addAll(BlockIn.get(succ));
                }
                if (!outSet.equals(BlockOut.get(block))) {
                    changed = true;
                    BlockOut.put(block, outSet);
                }
                // in = use ∪ (out - def)
                inSet.addAll(BlockOut.get(block));
                inSet.removeAll(BlockDef.get(block));
                inSet.addAll(BlockUse.get(block));
                if (!inSet.equals(BlockIn.get(block))) {
                    changed = true;
                    BlockIn.put(block, inSet);
                }
            }
        }
        for (RiscvBlock block : topoSort) {
            if (block.riscvInstructions.size() == 0) continue;
            In.put(block.getFirst(), BlockIn.get(block));
            Out.put(block.getLast(), BlockOut.get(block));
            int size = block.riscvInstructions.size();
            for (int i = size - 1; i >= 0; i--) {
//                System.out.println("ins num: " + i + " size: " + size);
                RiscvInstruction ins = block.riscvInstructions.get(i);
                if (i != size - 1) {
                    //out[i] = in[i+1]
                    Out.put(ins, In.get(block.riscvInstructions.get(i + 1)));
                }
                if (i != 0) {
                    //in[i] = use[i] U (out[i] - def[i])
                    HashSet<Reg> inSet = new HashSet(Out.get(ins));
                    inSet.removeAll(Def.get(ins));
                    inSet.addAll(Use.get(ins));
                    In.put(ins, inSet);
                }
            }
        }
        for (J call : function.calls) {
            if (CodeGen.ansRis.getFunction(call.funcName).isExternal) {
                //此处只考虑VirtualReg，不考虑预着色寄存器,因为这里只为着色服务
                for (Reg reg : Out.get(call)) {
                    if (reg.preColored) continue;
                    if (!In.get(call).contains(reg)) continue;
                    if (reg.phyReg == zero || reg.phyReg == sp) continue;
                    callSaved.add(reg);
                }
            }
        }
    }
}
