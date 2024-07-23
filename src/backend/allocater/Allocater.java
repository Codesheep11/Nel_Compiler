package backend.allocater;

import backend.Opt.LivelessDCE;
import backend.StackManager;
import backend.operand.Address;
import backend.operand.Reg;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.LS;
import backend.riscv.RiscvInstruction.R3;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;

import java.util.HashMap;
import java.util.HashSet;

import static backend.allocater.LivenessAnalyze.In;
import static backend.allocater.LivenessAnalyze.Out;
import static backend.operand.Reg.PhyReg.*;

/**
 * 寄存器分配器功能：
 * 1.分配寄存器
 * 2.跨函数寄存器保护
 * 3.栈帧回填
 */
public class Allocater {
    private static RiscvModule module;

    public static HashMap<String, HashSet<Reg.PhyReg>> UsedRegs = new HashMap<>();

    public static void run(RiscvModule riscvModule) {
        module = riscvModule;
        HashSet<Reg.PhyReg> allRegs = new HashSet<Reg.PhyReg>() {{
            for (int i = 3; i <= 7; i++) add(getPhyRegByOrder(i));
            for (int i = 10; i <= 17; i++) add(getPhyRegByOrder(i));
            for (int i = 28; i <= 39; i++) add(getPhyRegByOrder(i));
            for (int i = 42; i <= 49; i++) add(getPhyRegByOrder(i));
            for (int i = 60; i <= 63; i++) add(getPhyRegByOrder(i));
        }};
        for (RiscvFunction func : module.TopoSort) {
//            System.out.println(func.name);
            if (func.isExternal) {
                HashSet<Reg.PhyReg> used = new HashSet<>();
                used.addAll(allRegs);
                UsedRegs.put(func.name, used);
                continue;
            }
            UsedRegs.put(func.name, new HashSet<>());
            LivelessDCE.runOnFunc(func);
            GPRallocater.runOnFunc(func);
            FPRallocater.runOnFunc(func);
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
                        reg.regType == Reg.RegType.FPR ? LS.LSType.fsw : (reg.bits == 32 ? LS.LSType.sw : LS.LSType.sd));
                call.block.riscvInstructions.insertBefore(store, call);
                load = new LS(call.block, reg, Reg.getPreColoredReg(sp, 64), offset,
                        reg.regType == Reg.RegType.FPR ? LS.LSType.flw : (reg.bits == 32 ? LS.LSType.lw : LS.LSType.ld));
                call.block.riscvInstructions.insertAfter(load, call);
            }
            if (UsedRegs.containsKey(call.funcName))
                UsedRegs.get(func.name).addAll(UsedRegs.get(call.funcName));
        }
        //保护sp
        StackManager.getInstance().refill(func.name);
    }


}
