package backend.allocator;

import backend.Opt.Liveness.LivelessDCE;
import backend.StackManager;
import backend.operand.Address;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.LS;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;
import midend.Analysis.AlignmentAnalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static backend.allocator.LivenessAnalyze.In;
import static backend.allocator.LivenessAnalyze.Out;
import static backend.operand.Reg.PhyReg.*;

/**
 * 寄存器分配器功能：
 * 1.分配寄存器
 * 2.跨函数寄存器保护
 * 3.栈帧回填
 */
public class Allocator {
    private static RiscvModule module;

    public static final HashMap<String, HashSet<Reg.PhyReg>> UsedRegs = new HashMap<>();

    public static void run(RiscvModule riscvModule) {
        module = riscvModule;
        HashSet<Reg.PhyReg> allRegs = new HashSet<>() {{
            for (int i = 3; i <= 7; i++) add(getPhyRegByOrder(i));
            for (int i = 10; i <= 17; i++) add(getPhyRegByOrder(i));
            for (int i = 28; i <= 39; i++) add(getPhyRegByOrder(i));
            for (int i = 42; i <= 49; i++) add(getPhyRegByOrder(i));
            for (int i = 60; i <= 63; i++) add(getPhyRegByOrder(i));
        }};
        for (RiscvFunction func : module.TopoSort) {
            if (func.isExternal) {
                func.isSaveOut = true;
                HashSet<Reg.PhyReg> used = new HashSet<>(allRegs);
                UsedRegs.put(func.name, used);
                continue;
            }
            //判断函数是否存在隐含的外部函数调用
            for (J call : func.calls) {
                if (module.getFunction(call.funcName).isSaveOut) {
                    func.isSaveOut = true;
                    break;
                }
            }
            UsedRegs.put(func.name, new HashSet<>());
            LivelessDCE.runOnFunc(func);
            GPRallocator.runOnFunc(func);
            FPRallocator.runOnFunc(func);
            LivenessAnalyze.RunOnFunc(func);
            SaveReg4Call(func);
        }
    }

    /**
     * 为函数调用保存寄存器，保护结束后移动栈帧<br/>
     * 保存的寄存器满足call指令出口活跃并且在所调用函数中被使用
     * 如果callee=null，说明是外部函数调用，保存所有寄存器
     *
     * @param func 当前函数
     */
    public static void SaveReg4Call(RiscvFunction func) {
        for (J call : func.calls) {
            for (Reg reg : Out.get(call)) {
                if (!In.get(call).contains(reg)) continue;
                //a0若来自于函数返回值，一定不需要保存
                //如果a0是用于分配的寄存器，则会在call的out中与返回值预着色寄存器a0产生冲突，不被分配a0
                if (reg.phyReg == a0 || reg.phyReg == fa0) {
                    if (reg.preColored) {
                        int retCode = module.getFunction(call.funcName).retTypeCode;
                        if (retCode == 1 && reg.phyReg == a0) continue;
                        if (retCode == -1 && reg.phyReg == fa0) continue;
                    }
                }
                if (reg.phyReg == zero || reg.phyReg == sp) continue;
                if (!UsedRegs.get(call.funcName).contains(reg.phyReg)) continue;
                RiscvInstruction store, load;
                Address offset = StackManager.getInstance().getRegOffset(func.name, reg.toString(), reg.bits / 8);
                store = new LS(call.block, reg, Reg.getPreColoredReg(sp, 64), offset,
                        reg.regType == Reg.RegType.FPR ? LS.LSType.fsw : (reg.bits == 32 ? LS.LSType.sw : LS.LSType.sd), AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                call.block.insertInstBefore(store, call);
                load = new LS(call.block, reg, Reg.getPreColoredReg(sp, 64), offset,
                        reg.regType == Reg.RegType.FPR ? LS.LSType.flw : (reg.bits == 32 ? LS.LSType.lw : LS.LSType.ld), AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                call.block.insertInstAfter(load, call);
            }
            if (UsedRegs.containsKey(call.funcName))
                UsedRegs.get(func.name).addAll(UsedRegs.get(call.funcName));
        }
        //如果是并行循环体，则需要保护所有用到的全局寄存器
        if (func.isParallelLoopBody) {
            ArrayList<Reg> saved = new ArrayList<>();
            for (Reg.PhyReg use : UsedRegs.get(func.name)) {
                int idx = use.ordinal();
                if (idx == 3 || idx == 4 || (idx >= 8 && idx <= 9) || (idx >= 18 && idx <= 27))
                    saved.add(Reg.getPreColoredReg(use, 64));
                else if ((idx >= 40 && idx <= 41) || (idx >= 50 && idx <= 59)) {
                    saved.add(Reg.getPreColoredReg(use, 32));
                }
            }
            Reg sp = Reg.getPreColoredReg(Reg.PhyReg.sp, 64);
            for (Reg reg : saved) {
                Address offset = StackManager.getInstance().getRegOffset(func.name, reg.toString(), reg.bits / 8);
                RiscvInstruction store = new LS(func.getEntry(), reg, sp, offset,
                        reg.regType == Reg.RegType.FPR ? LS.LSType.fsw : LS.LSType.sd,
                        AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                func.getEntry().insertInstAfter(store, func.getEntry().riscvInstructions.getFirst());
                for (RiscvBlock exit : func.exits) {
                    RiscvInstruction load = new LS(exit, reg, sp, offset,
                            reg.regType == Reg.RegType.FPR ? LS.LSType.flw : LS.LSType.ld,
                            AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                    exit.insertInstBefore(load, exit.riscvInstructions.getLast());
                }
            }
        }
        StackManager.getInstance().refill(func.name);
    }


}
