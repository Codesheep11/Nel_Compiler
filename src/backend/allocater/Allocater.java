package backend.allocater;

import backend.StackManager;
import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;
import manager.Manager;

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
        for (RiscvFunction func : module.TopoSort) {
            if (func.isExternal) continue;
//            System.out.println(func.name);
            UsedRegs.put(func.name, new HashSet<>());
            GPRallocater.runOnFunc(func);
            FPRallocater.runOnFunc(func);
            LivenessAnalyze.RunOnFunc(func);
            SaveReg4Call(func);
        }
        Manager.afterRegAssign = true;
        LS2LiAddLS(riscvModule);
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
                if (UsedRegs.get(call.funcName) != null && !UsedRegs.get(call.funcName).contains(reg.phyReg)) continue;
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
        int funcSize = StackManager.getInstance().getFuncSize(func.name);
        for (J call : func.calls) {
            // 移动栈指针
            if (funcSize >= 2048) {
                Reg tmp = Reg.getPreColoredReg(t0, 64);
                Reg sp = Reg.getPreColoredReg(Reg.PhyReg.sp, 64);
                Li beforeCall1 = new Li(call.block, tmp, -1 * funcSize);
                R3 beforeCall2 = new R3(call.block, sp, sp, tmp, R3.R3Type.add);
                call.block.riscvInstructions.insertBefore(beforeCall1, call);
                call.block.riscvInstructions.insertBefore(beforeCall2, call);
                Li afterCall1 = new Li(call.block, tmp, funcSize);
                R3 afterCall2 = new R3(call.block, sp, sp, tmp, R3.R3Type.add);
                call.block.riscvInstructions.insertAfter(afterCall2, call);
                call.block.riscvInstructions.insertAfter(afterCall1, call);
            }
            else {
                Imm offset1 = new Imm(-1 * funcSize);
                Imm offset2 = new Imm(funcSize);
                R3 beforeCall = new R3(call.block, Reg.getPreColoredReg(sp, 64), Reg.getPreColoredReg(sp, 64), offset1, R3.R3Type.addi);
                call.block.riscvInstructions.insertBefore(beforeCall, call);
                R3 afterCall = new R3(call.block, Reg.getPreColoredReg(sp, 64), Reg.getPreColoredReg(sp, 64), offset2, R3.R3Type.addi);
                call.block.riscvInstructions.insertAfter(afterCall, call);
            }
        }
    }


    public static void LS2LiAddLS(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock riscvBlock : function.blocks) {
                for (RiscvInstruction ri : riscvBlock.riscvInstructions) {
                    if (ri instanceof LS ls) {
                        ls.replaceMe(riscvBlock);
                    }
                }
            }
        }
    }
}
