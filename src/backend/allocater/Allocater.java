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

import static backend.operand.Reg.PhyReg.*;

/**
 * 寄存器分配器功能：
 * 1.分配寄存器
 * 2.跨函数寄存器保护
 * 3.栈帧回填
 */
public class Allocater {
    private static RiscvModule module;

    public static void run(RiscvModule riscvModule) {
        module = riscvModule;
        GPRallocater gprallocater = new GPRallocater(module);
        FPRallocater fprallocater = new FPRallocater(module);
        for (RiscvFunction func : module.funcList) {
            if (func.isExternal) continue;
            new LivenessAnalyze(func).genInOutSet();
        }
        for (RiscvFunction func : module.funcList) {
            if (func.isExternal) continue;
            for (J call : func.calls) {
                if (call.type == J.JType.call) {
                    String funcName = call.funcName;
                    RiscvFunction callee = module.getFunction(funcName);
                    RegSaveForCall(func, call, callee);
                } else {
                    throw new RuntimeException("call type error");
                }
            }
            //如果存在函数调用，则需要保护ra
            Address offset = null;
            if (!func.calls.isEmpty()) {
                offset = StackManager.getInstance().getRegOffset(func.name, "ra", 8);
            }
            StackManager.getInstance().refill(func.name);
            if (offset != null) {
                func.getEntry().riscvInstructions.addFirst(
                        new LS(func.getEntry(), Reg.getPreColoredReg(ra, 64), Reg.getPreColoredReg(sp, 64), offset, LS.LSType.sd));
                for (RiscvBlock block : func.getExits()) {
                    RiscvInstruction ret = block.riscvInstructions.getLast();
                    block.riscvInstructions.insertBefore(
                            new LS(block, Reg.getPreColoredReg(ra, 64), Reg.getPreColoredReg(sp, 64), offset, LS.LSType.ld), ret);
                }
            }
            for (J call : func.calls) {
                if (call.type == J.JType.call) {
                    int funcSize = StackManager.getInstance().getFuncSize(func.name);
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
                    } else {
                        Imm offset1 = new Imm(-1 * funcSize);
                        Imm offset2 = new Imm(funcSize);
                        R3 beforeCall = new R3(call.block, Reg.getPreColoredReg(sp, 64), Reg.getPreColoredReg(sp, 64), offset1, R3.R3Type.addi);
                        call.block.riscvInstructions.insertBefore(beforeCall, call);
                        R3 afterCall = new R3(call.block, Reg.getPreColoredReg(sp, 64), Reg.getPreColoredReg(sp, 64), offset2, R3.R3Type.addi);
                        call.block.riscvInstructions.insertAfter(afterCall, call);
                    }
                } else {
                    throw new RuntimeException("call type error");
                }
            }
        }
        Manager.afterRegAssign = true;
        LS2LiAddLS(riscvModule);
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

    /**
     * 为函数调用保存寄存器，保护结束后移动栈帧<br/>
     * 保存的寄存器满足call指令出口活跃并且在所调用函数中被使用
     * 如果callee=null，说明是外部函数调用，不保存寄存器
     * TODO:现在的策略是保护所有call出口点活跃的寄存器，后续可以优化
     *
     * @param func   当前函数
     * @param call   函数调用指令
     * @param callee 被调用的函数
     */
    public static void RegSaveForCall(RiscvFunction func, J call, RiscvFunction callee) {
//        if (callee != null) {
//            for (Reg reg : call.out) {
//                //call的out如果是a0，不保存
//                if (reg.phyReg == a0) {
//                    continue;
//                }
//                if (callee.usedRegs.contains(reg.phyReg)) {
//                    RiscvInstruction store, load;
//                    Address offset = StackManager.getInstance().getRegOffset(callee.name, reg.toString(), reg.bits / 8);
//                    store = new LS(call.block, reg, new Reg(Reg.PhyReg.sp, 64), offset, reg.bits == 32 ? LS.LSType.sw : LS.LSType.sd);
//                    call.block.riscvInstructions.insertBefore(store, call);
//                    load = new LS(call.block, reg, new Reg(Reg.PhyReg.sp, 64), offset, reg.bits == 32 ? LS.LSType.lw : LS.LSType.ld);
//                    call.block.riscvInstructions.insertAfter(load, call);
//                }
//            }
//        }

        //修改成保护所有在当前函数被使用的寄存器：
        for (Reg reg : call.out) {
            if (reg.phyReg == a0 || reg.phyReg == fa0) {
                //a0若来自于函数返回值，一定不需要保存
                //如果a0是用于分配的寄存器，则会在call的out中与返回值预着色寄存器a0产生冲突，不被分配a0
                if (reg.preColored) {
                    continue;
                }
            }
            if (reg.phyReg == zero || reg.phyReg == sp) {
                continue;
            }
            RiscvInstruction store, load;
            Address offset = StackManager.getInstance().getRegOffset(func.name, reg.toString(), reg.bits / 8);
            store = new LS(call.block, reg, Reg.getPreColoredReg(sp, 64), offset,
                    reg.regType == Reg.RegType.FPR ? LS.LSType.fsw : (reg.bits == 32 ? LS.LSType.sw : LS.LSType.sd));
            call.block.riscvInstructions.insertBefore(store, call);
            load = new LS(call.block, reg, Reg.getPreColoredReg(sp, 64), offset,
                    reg.regType == Reg.RegType.FPR ? LS.LSType.flw : (reg.bits == 32 ? LS.LSType.lw : LS.LSType.ld));
            call.block.riscvInstructions.insertAfter(load, call);
        }
    }
}
