package mir.Ir2RiscV;


import backend.StackManager;
import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.*;
import backend.riscv.RiscvInstruction.*;
import midend.Util.FuncInfo;
import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;

/*by hlq*/


public class CodeGen {
    private RiscvFunction nowFunc = null;
    public static RiscvBlock nowBlock = null;
    /**
     * 全局和对应的riscv全局变量的映射,可以方便在后面使用的时候取到
     * 注意不能按照对象本身来定位，因为对象不一样但是可能对应的事一个东西
     */
  public static final HashMap<String, RiscvGlobalVar> gloMap = new HashMap<>();

    public static final RiscvModule ansRis = new RiscvModule();

    // 为了给branch 和 jump指令进行block的存放
    // 因为branch和jump需要存的属性是riscvBlock,所以需要提前将所有llvm块和其翻译后的riscv块对应好
    private final HashMap<BasicBlock, RiscvBlock> blockMap = new HashMap<>();

    public RiscvModule genCode(Module module) {
        Reg.initPreColoredRegs();
        visitModule(module);
        return ansRis;
    }

    /**
     * 此方法需要全局变量初始化
     * 格式:string : .str_i
     * global : name:
     * float 字面量:专门放一起,叫做.LC
     */
    private void visitModule(Module module) {
        ArrayList<GlobalVariable> globalVariables = module.getGlobalValues();
        // 所有全局变量都认为是一个指针,且不包含string
        // 只能是数组或者float或者int32
        for (GlobalVariable globalVariable : globalVariables) {
            RiscvGlobalVar rb = RiscvGlobalVar.genGlobalVariable(globalVariable);
            ansRis.addGlobalVar(rb);
            gloMap.put(globalVariable.label, rb);
        }
        for (String s : module.getGlobalStrings()) {
            RiscvString rs = new RiscvString(s);
            ansRis.addGlobalVar(rs);
        }
        for (Function function : module.getFuncSet()) {
            VirRegMap.VRM.clean(function);
            visitFunction(function);
        }
        FuncInfo.getFuncTopoSort().forEach(func -> ansRis.TopoSort.add(ansRis.getFunction(func.getName())));
        for (RiscvFunction rf : ansRis.funcList) {
            if (RiscvModule.isMain(rf)) {
                rf.isMain = true;
                ansRis.mainFunc = rf;
            }
        }
    }

    /**
     * 假定函数的参数是这样的:前8个int参数存到a0-a7里,前8个float参数存到fa0-fa7里
     * 设置一个count,来计算两类参数的值
     * 对于在本函数的所有参数,我会假定一个虚拟寄存器来存它
     * 这里假定参数按照逆序存储
     * 以地址小的一段为开头,例如第一个放到地址的参数是sp+0
     * 同时参数有三种:i32,地址,float
     */
    private void visitFunction(Function function) {
        nowFunc = new RiscvFunction(function);
        ansRis.addFunction(nowFunc);
        for (BasicBlock block : function.getBlocks()) {
            RiscvBlock riscvBlock = new RiscvBlock(nowFunc, block);
            nowFunc.addBB(riscvBlock);
            blockMap.put(block, riscvBlock);
        }
        Address offset = null;
        if (!function.isExternal() && FuncInfo.callGraph.get(function).size() != 0) {
            offset = StackManager.getInstance().getRegOffset(nowFunc.name, "ra", 8);
            blockMap.get(function.getEntry()).addInstrucion(new LS(blockMap.get(function.getEntry()),
                    Reg.getPreColoredReg(Reg.PhyReg.ra, 64),
                    Reg.getPreColoredReg(Reg.PhyReg.sp, 64),
                    offset,
                    LS.LSType.sd));
        }
        nowBlock = function.isExternal() ? null : blockMap.get(function.getEntry());
        // 非浮点参数的个数
        int count_int = 0;
        // 浮点参数的个数
        int count_flot = 0;
        ArrayList<RiscvInstruction> paramPassing = new ArrayList<>();
        for (Function.Argument argument : function.getFuncRArguments()) {
            // 获取该参数对应的虚拟寄存器
            // 如果参数是浮点数
            if (argument.getType().isFloatTy()) {
                // 在浮点寄存器里面装着
                if (count_flot < 8) {
                    Reg reg = VirRegMap.VRM.ensureRegForValue(argument);
                    int fa0order = Reg.PhyReg.getOrder(Reg.PhyReg.fa0);
                    // 从浮点参数寄存器到目标虚拟寄存器
                    paramPassing.add(new R2(nowBlock, reg,
                            Reg.getPreColoredReg(Reg.PhyReg.getPhyRegByOrder(fa0order + count_flot), 32),
                            R2.R2Type.fmv));
                    nowFunc.defs.add(Reg.getPreColoredReg(Reg.PhyReg.getPhyRegByOrder(fa0order + count_flot), 32));
                    count_flot++;
                }
            } else {
                if (argument.getType().isPointerTy() || argument.getType().isInt64Ty()) {
                    if (count_int < 8) {
                        Reg reg = VirRegMap.VRM.ensureRegForValue(argument);
                        int a0order = Reg.PhyReg.getOrder(Reg.PhyReg.a0);
                        paramPassing.add(new R2(nowBlock,
                                reg,
                                Reg.getPreColoredReg(Reg.PhyReg.getPhyRegByOrder(a0order + count_int), 64),
                                R2.R2Type.mv
                        ));
                        nowFunc.defs.add(Reg.getPreColoredReg(Reg.PhyReg.getPhyRegByOrder(a0order + count_int), 64));
                        count_int++;
                    }
                } else {
                    if (count_int < 8) {
                        Reg reg = VirRegMap.VRM.ensureRegForValue(argument);
                        int a0order = Reg.PhyReg.getOrder(Reg.PhyReg.a0);
                        paramPassing.add(new R2(nowBlock,
                                reg,
                                Reg.getPreColoredReg(Reg.PhyReg.getPhyRegByOrder(a0order + count_int), 32),
                                R2.R2Type.mv
                        ));
                        nowFunc.defs.add(Reg.getPreColoredReg(Reg.PhyReg.getPhyRegByOrder(a0order + count_int), 32));
                        count_int++;
                    }
                }
            }
        }
        if (function.isExternal()) return;
        for (Loop loop : function.loopInfo.TopLevelLoops) setBlockLoopDepth(loop);
        for (RiscvInstruction riscvInstruction : paramPassing) {
            nowBlock.riscvInstructions.addLast(riscvInstruction);
        }
        for (BasicBlock block : function.getDomTreeLayerSort()) {
            visitBlock(block);
        }
        if (offset != null) {
            for (RiscvBlock rb : nowFunc.exits) {
                LS ld = new LS(rb,
                        Reg.getPreColoredReg(Reg.PhyReg.ra, 64),
                        Reg.getPreColoredReg(Reg.PhyReg.sp, 64),
                        offset,
                        LS.LSType.ld);
                rb.riscvInstructions.insertBefore(ld, rb.riscvInstructions.getLast());
            }
        }
    }

    private void setBlockLoopDepth(Loop loop) {
        for (BasicBlock block : loop.nowLevelBB) {
            blockMap.get(block).loopDepth = loop.getDepth();
        }
        for (Loop child : loop.children) setBlockLoopDepth(child);
    }

    /**
     * 约定：如果是float那么返回值放到fa0里面，否则放到a0里
     */
    private void solveReturn(Instruction.Return rtInstr) {
        Type type = rtInstr.getType();
        J ret = new J(nowBlock, J.JType.ret);
        if (type.isFloatTy()) {
            Reg fa0 = Reg.getPreColoredReg(Reg.PhyReg.fa0, 32);
            Reg src = VirRegMap.VRM.ensureRegForValue(rtInstr.getRetValue());
            nowBlock.riscvInstructions.addLast(new R2(nowBlock, fa0, src, R2.R2Type.fmv));
        } else if (type.isInt32Ty()) {
            Reg a0 = Reg.getPreColoredReg(Reg.PhyReg.a0, 32);
            Reg src = VirRegMap.VRM.ensureRegForValue(rtInstr.getRetValue());
            nowBlock.riscvInstructions.addLast(new R2(nowBlock, a0, src, R2.R2Type.mv));
        }
        nowBlock.riscvInstructions.addLast(ret);
        nowFunc.exits.add(nowBlock);
    }

    /**
     * 有多种,一种是call自定义函数,另一种是call运行时函数
     * 参数有3种,指针,int32，float
     */
    private void solveCall(Instruction.Call callInstr) {
        String min = "llvm.smin.i32";
        String max = "llvm.smax.i32";
        // 开局拦截min和max
        String funcName = callInstr.getDestFunction().getName();
        if (funcName.equals(min) || funcName.equals(max)) {
            R3.R3Type type = funcName.equals(max) ? R3.R3Type.max : R3.R3Type.min;
            Reg r1 = VirRegMap.VRM.ensureRegForValue(callInstr.getParams().get(0));
            Reg r2 = VirRegMap.VRM.ensureRegForValue(callInstr.getParams().get(1));
            Reg ans = VirRegMap.VRM.ensureRegForValue(callInstr);
            nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, r1, r2, type));
            return;
        }
        J call = new J(nowBlock, J.JType.call, funcName);
        Type type = callInstr.getType();
        Reg reg = null;
        if (!(type instanceof Type.VoidType)) {
            reg = VirRegMap.VRM.ensureRegForValue(callInstr);
        }

        ArrayList<Value> paras = callInstr.getParams();
        int count_int = 0;
        int count_float = 0;
        // 如果是对字符串输出的话,需要将第一个参数设置成地址
        if (callInstr.strIdx != -1) {
            count_int++;
            nowBlock.riscvInstructions.addLast(new La(nowBlock, Reg.getPreColoredReg(Reg.PhyReg.a0, 64), RiscvString.RS.get(callInstr.strIdx - 1)));
        }
        for (Value para : paras) {
            Reg paraReg = VirRegMap.VRM.ensureRegForValue(para);
            // 如果是需要gep计算得到的，在这里再计算一遍
            if (StackManager.getInstance().canBeCalAsOffset(nowFunc.name, para)) {
                int byte_off = StackManager.getInstance().getSpOffset(nowFunc.name, para).getOffset();
                Reg sp = Reg.getPreColoredReg(Reg.PhyReg.sp, 64);
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, paraReg, sp, new Address(byte_off, nowFunc.name), R3.R3Type.addi));
            }
            // 如果是alloc得到的，
            if (para.getType().isFloatTy()) {
                if (count_float < 8) {
                    int fa0order = Reg.PhyReg.getOrder(Reg.PhyReg.fa0);
                    // 从浮点参数寄存器到目标虚拟寄存器
                    nowBlock.riscvInstructions.addLast(new R2(nowBlock,
                            Reg.getPreColoredReg(Reg.PhyReg.getPhyRegByOrder(fa0order + count_float), 32),
                            paraReg,
                            R2.R2Type.fmv));
                    count_float++;
                } else {
                    // 获取当前的偏移地址
                    Address address = StackManager.getInstance().
                            getArgOffset(nowFunc.name, callInstr, paraReg.toString(), 4);
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock,
                            paraReg,
                            Reg.getPreColoredReg(Reg.PhyReg.sp, 64),
                            address,
                            LS.LSType.fsw));
                }
            } else if (para.getType().isPointerTy()) {
                if (count_int < 8) {
                    int a0order = Reg.PhyReg.getOrder(Reg.PhyReg.a0);
                    nowBlock.riscvInstructions.addLast(new R2(nowBlock,
                            Reg.getPreColoredReg(Reg.PhyReg.getPhyRegByOrder(a0order + count_int), 64),
                            paraReg,
                            R2.R2Type.mv
                    ));
                    count_int++;
                } else {
                    Address address = StackManager.getInstance().
                            getArgOffset(nowFunc.name, callInstr, paraReg.toString(), 8);
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock,
                            paraReg,
                            Reg.getPreColoredReg(Reg.PhyReg.sp, 64),
                            address,
                            LS.LSType.sd));
                }
            } else if (para.getType().isInt32Ty()) {
                if (count_int < 8) {
                    int a0order = Reg.PhyReg.getOrder(Reg.PhyReg.a0);
                    nowBlock.riscvInstructions.addLast(new R2(nowBlock,
                            Reg.getPreColoredReg(Reg.PhyReg.getPhyRegByOrder(a0order + count_int), 32),
                            paraReg,
                            R2.R2Type.mv
                    ));
                    count_int++;
                } else {
                    Address address = StackManager.getInstance().
                            getArgOffset(nowFunc.name, callInstr, paraReg.toString(), 4);
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock,
                            paraReg,
                            Reg.getPreColoredReg(Reg.PhyReg.sp, 64),
                            address,
                            LS.LSType.sw));
                }
            }
        }
        // 起跳
        nowFunc.calls.add(call);
        nowBlock.riscvInstructions.addLast(call);
        // 将call返回值放到对应的寄存器里面
        if (reg != null) {
            if (type.isInt32Ty()) {
                nowBlock.riscvInstructions.addLast(new R2(nowBlock, reg, Reg.getPreColoredReg(Reg.PhyReg.a0, 32), R2.R2Type.mv));
            } else if (type.isFloatTy()) {
                nowBlock.riscvInstructions.addLast(new R2(nowBlock, reg, Reg.getPreColoredReg(Reg.PhyReg.fa0, 32), R2.R2Type.fmv));
            } else {
                throw new RuntimeException("....");
            }
        }
    }

    /**
     * alloc不需要真正的做出什么指令，只需要在栈上标记好即可，让内存分配器认为这一段被占用了就行
     * 32 32位按4存，64按8存,指针按64存，数组按n*4存，剩下的全按4存
     * 浮点数也是按的4存的
     */

    /*
     * 新更改:不再存储address，而是选择直接存个指针回来*/
    private void solveAlloc(Instruction.Alloc allocInstr) {
        // 所开内存的对象大小
        Type type = allocInstr.getContentType();
        int size = type.queryBytesSizeOfType();
        StackManager.getInstance().allocOnStack(nowFunc.name, allocInstr, size);
    }

    /**
     * 由于可以存在load 浮点数和load 正常的数,所以需要分别考虑使用指令
     * 需要保证load指针指向的东西的不是数组
     * 同时,我们需要两种,第一种是在栈上存取，另一种是全局的
     * 识别方式:如果get的addr是global的,那么就将addr的地址存到一个寄存器里面
     */
    private void solveLoad(Instruction.Load loadInstr) {
        Type type = loadInstr.getInnerType();
        if (type.isArrayTy()) {
            throw new RuntimeException("load an array!");
        } else {
            // 给load的值分配一个虚拟寄存器
            Reg reg = VirRegMap.VRM.ensureRegForValue(loadInstr);
            if (StackManager.getInstance().canBeCalAsOffset(nowFunc.name, loadInstr.getAddr())) {
                Address address = StackManager.getInstance().getSpOffset(nowFunc.name, loadInstr.getAddr());
                Reg sp = Reg.getPreColoredReg(Reg.PhyReg.sp, 64);
                LS.LSType lstype;
                if (type.isInt64Ty() || type.isPointerTy()) {
                    lstype = LS.LSType.ld;
                } else if (type.isInt32Ty() || type.isInt1Ty()) {
                    lstype = LS.LSType.lw;
                } else {
                    lstype = LS.LSType.flw;
                }
                nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, sp, address, lstype));
                return;
            }
            // 如果是对全局变量的访问
            if (loadInstr.getAddr() instanceof GlobalVariable) {
                Reg tmp = Reg.getVirtualReg(Reg.RegType.GPR, 64);
                RiscvGlobalVar label = gloMap.get(((GlobalVariable) loadInstr.getAddr()).label);
                nowBlock.riscvInstructions.addLast(new La(nowBlock, tmp, label));
                if (label.type == RiscvGlobalVar.GlobType.FLOAT) {
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, tmp, new Imm(0), LS.LSType.flw));
                } else {
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, tmp, new Imm(0), LS.LSType.lw));
                }
            } else {
                //否则我们获得了一个指针，这个指针可以通过查询得到
                Reg addr = VirRegMap.VRM.ensureRegForValue(loadInstr.getAddr());
                if (type.isInt64Ty() || type.isPointerTy()) {
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, addr, new Imm(0), LS.LSType.ld));
                } else if (type.isInt32Ty() || type.isInt1Ty()) {
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, addr, new Imm(0), LS.LSType.lw));
                } else {
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, addr, new Imm(0), LS.LSType.flw));
                }
            }
        }
    }

    /**
     *
     */
    private void solveStore(Instruction.Store storeInstr) {
        Type type = ((Type.PointerType) storeInstr.getAddr().getType()).getInnerType();
        if (type.isArrayTy()) {
            throw new RuntimeException("load an array!");
        } else {
            // 获取store的值的寄存器
            Reg reg = VirRegMap.VRM.ensureRegForValue(storeInstr.getValue());
            if (StackManager.getInstance().canBeCalAsOffset(nowFunc.name, storeInstr.getAddr())) {
                Address address = StackManager.getInstance().getSpOffset(nowFunc.name, storeInstr.getAddr());
                Reg sp = Reg.getPreColoredReg(Reg.PhyReg.sp, 64);
                LS.LSType lstype;
                if (type.isInt64Ty() || type.isPointerTy()) {
                    lstype = LS.LSType.sd;
                } else if (type.isInt32Ty() || type.isInt1Ty()) {
                    lstype = LS.LSType.sw;
                } else {
                    lstype = LS.LSType.fsw;
                }
                nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, sp, address, lstype));
                return;
            }
            // 如果是对全局变量的访问
            if (storeInstr.getAddr() instanceof GlobalVariable) {
                Reg tmp = Reg.getVirtualReg(Reg.RegType.GPR, 64);
                RiscvGlobalVar label = gloMap.get(((GlobalVariable) storeInstr.getAddr()).label);
                nowBlock.riscvInstructions.addLast(new La(nowBlock, tmp, label));
                if (label.type == RiscvGlobalVar.GlobType.FLOAT) {
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, tmp, new Imm(0), LS.LSType.fsw));
                } else {
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, tmp, new Imm(0), LS.LSType.sw));
                }
            } else {
                //否则我们获得了一个指针，这个指针可以通过查询得到
                Reg addr = VirRegMap.VRM.ensureRegForValue(storeInstr.getAddr());
                if (type.isInt64Ty() || type.isPointerTy()) {
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, addr, new Imm(0), LS.LSType.sd));
                } else if (type.isInt32Ty() || type.isInt1Ty()) {
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, addr, new Imm(0), LS.LSType.sw));
                } else {
                    nowBlock.riscvInstructions.addLast(new LS(nowBlock, reg, addr, new Imm(0), LS.LSType.fsw));
                }
            }
        }
    }

    /**
     * 需要:获取base,是一个指针
     * 获取最后一个offset,计算好了所在位置的数值(有多少个元素的偏移)
     * 只需要知道元素的大小即可
     * base:全局变量或者alloc的指针,若为全局变量则lw的offset写的是直接的偏移
     */
    private void solveGEP(Instruction.GetElementPtr getElementPtr) {
        int size = getElementPtr.getEleType().queryBytesSizeOfType();
        Value offset = getElementPtr.getIdx();
        Reg pointer = VirRegMap.VRM.ensureRegForValue(getElementPtr);
        Reg base;
        if (getElementPtr.getBase() instanceof GlobalVariable || getElementPtr.getBase() instanceof Function.Argument) {
            // 这两种情况是可以直接用指针的情况
            if (getElementPtr.getBase() instanceof GlobalVariable) {
                base = Reg.getVirtualReg(Reg.RegType.GPR, 64);
                RiscvGlobalVar globalVar = gloMap.get(((GlobalVariable) getElementPtr.getBase()).label);
                nowBlock.riscvInstructions.addLast(new La(nowBlock, base, globalVar));
            } else {
                base = VirRegMap.VRM.ensureRegForValue(getElementPtr.getBase());
            }
            if (offset instanceof Constant.ConstantInt) {
                int of = (Integer) ((Constant.ConstantInt) offset).getConstValue();
                int byte_off = of * size;
                if (byte_off >= -2047 && byte_off <= 2047) {
                    nowBlock.riscvInstructions.addLast(new R3(nowBlock, pointer, base, new Imm(byte_off), R3.R3Type.addi));
                } else {
                    Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 64);
                    nowBlock.riscvInstructions.addLast(new Li(nowBlock, tmp, new Imm(byte_off)));
                    nowBlock.riscvInstructions.addLast(new R3(nowBlock, pointer, base, tmp, R3.R3Type.add));
                }
                return;
            }
        } else {
            // 不是全局的或者是参数那就说明是一个局部变量指针,
            // 这种情况下仅仅记录了这个局部变量在栈上的偏移，需要通过计算来得到
            if (offset instanceof Constant.ConstantInt) {
                StackManager.getInstance().calAsOffset(nowFunc.name, getElementPtr.getBase(), offset, size, getElementPtr);
                return;
            } else {
                // 如果不是常数的话，则就需要计算出来指针了
                int baseOffset = StackManager.getInstance().getPointerAddress(nowFunc.name, getElementPtr.getBase());
                base = new Reg(Reg.RegType.GPR, 64);
                Reg sp = Reg.getPreColoredReg(Reg.PhyReg.sp, 64);
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, base, sp, new Address(baseOffset, nowFunc.name), R3.R3Type.addi));
            }
        }
        // 给偏移找一个寄存器,方便计算
        Reg reg_for_offset = VirRegMap.VRM.ensureRegForValue(offset);
        Reg tmp_offset = new Reg(Reg.RegType.GPR, 32);
        // 这个式子是判断size是否是2的幂次,如果是的化直接将size移位即可,不需要用乘法计算
        if ((size & (size - 1)) == 0) {
            int shift = Integer.toBinaryString(size).length() - 1;
            if (shift == 1) {
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, pointer, reg_for_offset, base, R3.R3Type.sh1add));
                return;
            } else if (shift == 2) {
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, pointer, reg_for_offset, base, R3.R3Type.sh2add));
                return;
            } else if (shift == 3) {
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, pointer, reg_for_offset, base, R3.R3Type.sh3add));
                return;
            }
            nowBlock.riscvInstructions.addLast(new R3(nowBlock, tmp_offset, reg_for_offset, new Imm(shift), R3.R3Type.slliw));
        } else {
            Reg reg_for_size = new Reg(Reg.RegType.GPR, 32);
            nowBlock.riscvInstructions.addLast(new Li(nowBlock, reg_for_size, new Imm(size)));
            nowBlock.riscvInstructions.addLast(new R3(nowBlock, tmp_offset, reg_for_size, reg_for_offset, R3.R3Type.mulw));
        }
        nowBlock.riscvInstructions.addLast(new R3(nowBlock, pointer, base, tmp_offset, R3.R3Type.add));
    }

    /**
     * branch有两个分支,但是riscv只有一个是否跳转的分支,
     * 假定:cond的种类是int1
     * 设计两个连续的跳转指令即可
     * br:等于1跳转前面的，否则后面的
     * 默认范围符合要求
     */
    private void solveBranch(Instruction.Branch branchInstr) {
        Reg reg = VirRegMap.VRM.ensureRegForValue(branchInstr.getCond());
        if (!VirRegMap.bUseReg.containsKey(reg)) {
            VirRegMap.bUseReg.put(reg, 0);
        }
        VirRegMap.bUseReg.put(reg, VirRegMap.bUseReg.get(reg) + 1);
        if (!branchInstr.getCond().getType().isInt1Ty()) {
            throw new RuntimeException("cond is not int1");
        }
        double prob = branchInstr.getProbability();
        // 如果概率跳转概率比50大，那么就应当反转，让j尽可能大
        if (prob >= 0.5) {
            nowBlock.riscvInstructions.addLast(new B(nowBlock, B.BType.beq, reg, Reg.getPreColoredReg(Reg.PhyReg.zero, 32), blockMap.get(branchInstr.getElseBlock()), 1 - prob));
            nowBlock.riscvInstructions.addLast(new J(nowBlock, J.JType.j, blockMap.get(branchInstr.getThenBlock())));
        } else {
            nowBlock.riscvInstructions.addLast(new B(nowBlock, B.BType.bne, reg, Reg.getPreColoredReg(Reg.PhyReg.zero, 32), blockMap.get(branchInstr.getThenBlock()), prob));
            nowBlock.riscvInstructions.addLast(new J(nowBlock, J.JType.j, blockMap.get(branchInstr.getElseBlock())));
        }
    }

    /**
     * 该指令能不能默认跳转的范围大小?
     * 这里假定跳转的范围一个j都能解决
     */
    private void solveJump(Instruction.Jump jumpInstr) {
        RiscvBlock rb = blockMap.get(jumpInstr.getTargetBlock());
        nowBlock.riscvInstructions.addLast(new J(nowBlock, J.JType.j, rb));
    }

    /**
     * int 转化为浮点数,默认是32位int或者1位int
     */
    private void solveSItofp(Instruction.SItofp sItofp) {
        if (!(sItofp.getSrc().getType().isInt32Ty() || sItofp.getSrc().getType().isInt1Ty())) {
            throw new RuntimeException("turn a float or 64-bits to float");
        }
        Reg src = VirRegMap.VRM.ensureRegForValue(sItofp.getSrc());
        Reg dst = VirRegMap.VRM.ensureRegForValue(sItofp);
        nowBlock.riscvInstructions.addLast(new R2(nowBlock, dst, src, R2.R2Type.fcvtsw));
    }

    /**
     * float 转化为整数,默认是32位
     */
    private void solveFPtosi(Instruction.FPtosi fPtosi) {
        if (!(fPtosi.getSrc().getType().isFloatTy())) {
            throw new RuntimeException("turn a not float to int");
        }
        Reg src = VirRegMap.VRM.ensureRegForValue(fPtosi.getSrc());
        Reg dst = VirRegMap.VRM.ensureRegForValue(fPtosi);
        nowBlock.riscvInstructions.addLast(new R2(nowBlock, dst, src, R2.R2Type.fcvtws));
    }

    /**
     * 将i1无符号扩展为i32
     * 考虑到我们实际上没有i1，因此只需要绑定即可
     */
    private void solveZext(Instruction.Zext zext) {
        if (!zext.getSrc().getType().isInt1Ty()) {
            throw new RuntimeException("zext not i1");
        }
        VirRegMap.VRM.binding(zext.getSrc(), zext);
    }

    /**
     * icmp中的均是满足则置位1
     * 遗憾的是riscv中没有大部分对应的指令,仅有slt
     * EQ:sub+seqz
     * NE:sub+snez
     * SGE:a>=b =!(a<b) = slt a
     * SGT:反slt
     * SLT:slt
     * SLE:a<=b = !(b<a)
     */
    private void solveIcmp(Instruction.Icmp icmp) {
        Instruction.Icmp.CondCode condCode = icmp.getCondCode();
        Reg reg1 = VirRegMap.VRM.ensureRegForValue(icmp.getSrc1());
        Reg reg2 = VirRegMap.VRM.ensureRegForValue(icmp.getSrc2());
        Reg ans = VirRegMap.VRM.ensureRegForValue(icmp);
        switch (condCode) {
            case EQ -> {
                Reg tmp = new Reg(Reg.RegType.GPR, 32);
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, tmp, reg1, reg2, R3.R3Type.subw));
                nowBlock.riscvInstructions.addLast(new R2(nowBlock, ans, tmp, R2.R2Type.seqz));
            }
            case NE -> {
                Reg tmp = new Reg(Reg.RegType.GPR, 32);
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, tmp, reg1, reg2, R3.R3Type.subw));
                nowBlock.riscvInstructions.addLast(new R2(nowBlock, ans, tmp, R2.R2Type.snez));
            }
            case SGE -> {
                Reg tmp = new Reg(Reg.RegType.GPR, 32);
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, tmp, reg1, reg2, R3.R3Type.slt));
                nowBlock.riscvInstructions.addLast(new R2(nowBlock, ans, tmp, R2.R2Type.seqz));
            }
            case SGT -> nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, reg2, reg1, R3.R3Type.slt));
            case SLE -> {
                Reg tmp = new Reg(Reg.RegType.GPR, 32);
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, tmp, reg2, reg1, R3.R3Type.slt));
                nowBlock.riscvInstructions.addLast(new R2(nowBlock, ans, tmp, R2.R2Type.seqz));
            }
            case SLT -> nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, reg1, reg2, R3.R3Type.slt));
            default -> throw new RuntimeException("wrong icmp code!");
        }
    }

    /**
     * 浮点数有的:flt,fle,feq
     * eq: feq
     * ne: !eq  eq=0(int)
     * lt: lt
     * le:le
     * ge:反le
     * gt:反lt
     */
    private void solveFcmp(Instruction.Fcmp fcmp) {
        Instruction.Fcmp.CondCode condCode = fcmp.getCondCode();
        Reg reg1 = VirRegMap.VRM.ensureRegForValue(fcmp.getSrc1());
        Reg reg2 = VirRegMap.VRM.ensureRegForValue(fcmp.getSrc2());
        Reg ans = VirRegMap.VRM.ensureRegForValue(fcmp);
        switch (condCode) {
            case EQ -> nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, reg1, reg2, R3.R3Type.feq));
            case NE -> {
                Reg tmp = new Reg(Reg.RegType.GPR, 32);
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, tmp, reg1, reg2, R3.R3Type.feq));
                nowBlock.riscvInstructions.addLast(new R2(nowBlock, ans, tmp, R2.R2Type.seqz));
            }
            case OGE -> nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, reg2, reg1, R3.R3Type.fle));
            case OGT -> nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, reg2, reg1, R3.R3Type.flt));
            case OLE -> nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, reg1, reg2, R3.R3Type.fle));
            case OLT -> nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, reg1, reg2, R3.R3Type.flt));
            default -> throw new RuntimeException("wrong fcmp code!");
        }
    }

    /**
     * 类型转换,需要注意的是这个并不改变其bit位,而仅仅改变其解释意义
     * 观察llvm处,可得实际上就是将一个指针转化为另一个种类的指针
     * 因此只需要绑定同一个虚拟寄存器即可
     * 真的可以吗？实际上会出bug的
     */

    private void solveBitCast(Instruction.BitCast bitCast) {
        //
        VirRegMap.VRM.binding(bitCast.getSrc(), bitCast);
    }

    /**
     * 默认两端都是整数,并且不需要考虑地址的大加法
     */

    private void solveAdd(Instruction.Add add) {
        if (!add.getOperand_1().getType().isInt32Ty() ||
                !add.getOperand_2().getType().isInt32Ty()) {
            throw new RuntimeException("not all oper of add is i32");
        }
        Reg ans = VirRegMap.VRM.ensureRegForValue(add);
        if (add.getOperand_1() instanceof Constant.ConstantInt
                || add.getOperand_2() instanceof Constant.ConstantInt) {
            // 有且仅有一个会是常数,否则会在中端消掉
            int value;
            Reg op;
            if (add.getOperand_1() instanceof Constant.ConstantInt) {
                op = VirRegMap.VRM.ensureRegForValue(add.getOperand_2());
                value = (int) ((Constant.ConstantInt) add.getOperand_1()).getConstValue();
            } else {
                op = VirRegMap.VRM.ensureRegForValue(add.getOperand_1());
                value = (int) ((Constant.ConstantInt) add.getOperand_2()).getConstValue();
            }
            if (value >= -2047 && value <= 2047) {
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, new Imm(value), R3.R3Type.addiw));
            } else {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
                nowBlock.riscvInstructions.addLast(new Li(nowBlock, tmp, new Imm(value)));
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, tmp, R3.R3Type.addw));
            }
        } else {
            Reg op1 = VirRegMap.VRM.ensureRegForValue(add.getOperand_1());
            Reg op2 = VirRegMap.VRM.ensureRegForValue(add.getOperand_2());
            nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op1, op2, R3.R3Type.addw));
        }
    }

    /**
     * 默认两端都是整数,并且不需要考虑地址的大减法
     */
    private void solveSub(Instruction.Sub sub) {
        if (!sub.getOperand_1().getType().isInt32Ty() ||
                !sub.getOperand_2().getType().isInt32Ty()) {
            throw new RuntimeException("not all oper of sub is i32");
        }
        Reg ans = VirRegMap.VRM.ensureRegForValue(sub);
        if (sub.getOperand_2() instanceof Constant.ConstantInt) {
            Reg op = VirRegMap.VRM.ensureRegForValue(sub.getOperand_1());
            int value = (int) ((Constant.ConstantInt) sub.getOperand_2()).getConstValue();
            if (value >= -2047 && value <= 2047) {
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, new Imm(-1 * value), R3.R3Type.addiw));
            } else {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
                nowBlock.riscvInstructions.addLast(new Li(nowBlock, tmp, new Imm(-1 * value)));
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, tmp, R3.R3Type.addw));
            }
        } else {
            Reg op1 = VirRegMap.VRM.ensureRegForValue(sub.getOperand_1());
            Reg op2 = VirRegMap.VRM.ensureRegForValue(sub.getOperand_2());
            nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op1, op2, R3.R3Type.subw));
        }
    }

    /**
     * 默认两端都是浮点数
     */
    private void solveFAdd(Instruction.FAdd fAdd) {
        if (!fAdd.getOperand_1().getType().isFloatTy() ||
                !fAdd.getOperand_2().getType().isFloatTy()) {
            throw new RuntimeException("not all oper of fAdd is float");
        }
        Reg op1 = VirRegMap.VRM.ensureRegForValue(fAdd.getOperand_1());
        Reg op2 = VirRegMap.VRM.ensureRegForValue(fAdd.getOperand_2());
        Reg ans = VirRegMap.VRM.ensureRegForValue(fAdd);
        nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op1, op2, R3.R3Type.fadd));
    }

    /**
     * 默认两端都是浮点数
     */
    private void solveFSub(Instruction.FSub fSub) {
        if (!fSub.getOperand_1().getType().isFloatTy() ||
                !fSub.getOperand_2().getType().isFloatTy()) {
            throw new RuntimeException("not all oper of fSub is float");
        }
        Reg op1 = VirRegMap.VRM.ensureRegForValue(fSub.getOperand_1());
        Reg op2 = VirRegMap.VRM.ensureRegForValue(fSub.getOperand_2());
        Reg ans = VirRegMap.VRM.ensureRegForValue(fSub);
        nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op1, op2, R3.R3Type.fsub));
    }
    //TODO 乘除优化

    private void solveMul(Instruction.Mul mul) {
        if (!mul.getOperand_1().getType().isInt32Ty() ||
                !mul.getOperand_2().getType().isInt32Ty()) {
            throw new RuntimeException("not all oper of mul is i32");
        }
        Reg op1 = VirRegMap.VRM.ensureRegForValue(mul.getOperand_1());
        Reg op2 = VirRegMap.VRM.ensureRegForValue(mul.getOperand_2());
        Reg ans = VirRegMap.VRM.ensureRegForValue(mul);
        nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op1, op2, R3.R3Type.mulw));
    }

    private void solveDiv(Instruction.Div div) {
        if (!div.getOperand_1().getType().isInt32Ty() ||
                !div.getOperand_2().getType().isInt32Ty()) {
            throw new RuntimeException("not all oper of div is i32");
        }
        Reg op1 = VirRegMap.VRM.ensureRegForValue(div.getOperand_1());
        Reg op2 = VirRegMap.VRM.ensureRegForValue(div.getOperand_2());
        Reg ans = VirRegMap.VRM.ensureRegForValue(div);
        nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op1, op2, R3.R3Type.divw));
    }

    private void solveFMul(Instruction.FMul fmul) {
        Reg op1 = VirRegMap.VRM.ensureRegForValue(fmul.getOperand_1());
        Reg op2 = VirRegMap.VRM.ensureRegForValue(fmul.getOperand_2());
        Reg ans = VirRegMap.VRM.ensureRegForValue(fmul);
        nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op1, op2, R3.R3Type.fmul));
    }

    private void solveFDiv(Instruction.FDiv fdiv) {
        Reg op1 = VirRegMap.VRM.ensureRegForValue(fdiv.getOperand_1());
        Reg op2 = VirRegMap.VRM.ensureRegForValue(fdiv.getOperand_2());
        Reg ans = VirRegMap.VRM.ensureRegForValue(fdiv);
        nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op1, op2, R3.R3Type.fdiv));
    }

    /**
     * 默认两端都是整数
     */
    private void solveRem(Instruction.Rem rem) {
        if (!rem.getOperand_1().getType().isInt32Ty() ||
                !rem.getOperand_2().getType().isInt32Ty()) {
            throw new RuntimeException("not all oper of rem is i32");
        }
        Reg op1 = VirRegMap.VRM.ensureRegForValue(rem.getOperand_1());
        Reg op2 = VirRegMap.VRM.ensureRegForValue(rem.getOperand_2());
        Reg ans = VirRegMap.VRM.ensureRegForValue(rem);
        nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op1, op2, R3.R3Type.remw));
    }

    /**
     * 将两端的浮点数化为整数之后再求余数
     * 约定op1和op2都是浮点数
     * reg1和reg2都是整数
     * 默认返回的是一个 WARNING int WARNING
     */
    private void solveFRem(Instruction.FRem fRem) {
        if (!fRem.getOperand_1().getType().isFloatTy() || !fRem.getOperand_2().getType().isFloatTy()) {
            throw new RuntimeException("not all operands in frem are float");
        }
        // 把第一个浮点数转化为整数并且加入转换指令
        Reg op1 = VirRegMap.VRM.ensureRegForValue(fRem.getOperand_1());
        Reg reg1 = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
        nowBlock.riscvInstructions.addLast(new R2(nowBlock, reg1, op1, R2.R2Type.fcvtsw));
        // 把第二个浮点数转化为整数并且加入转换指令
        Reg op2 = VirRegMap.VRM.ensureRegForValue(fRem.getOperand_2());
        Reg reg2 = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
        nowBlock.riscvInstructions.addLast(new R2(nowBlock, reg2, op2, R2.R2Type.fcvtsw));
        // 对于该指令的结果分配一个虚拟寄存器,并且将上述的操作存入虚拟寄存器中
        Reg ans = VirRegMap.VRM.ensureRegForValue(fRem);
        nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, reg1, reg2, R3.R3Type.remw));
    }

    /**
     * 这里假定两端要么都是浮点数，要么都是整数
     * 所以不用担心bit的意义不同
     */
    private void solveMove(Instruction.Move move) {
        Reg src = VirRegMap.VRM.ensureRegForValue(move.getSrc());
        Reg dst = VirRegMap.VRM.ensureRegForValue(move.getTarget());
        if (move.getSrc().getType().isFloatTy()) {
            nowBlock.riscvInstructions.addLast(new R2(nowBlock, dst, src, R2.R2Type.fmv));
        } else {
            nowBlock.riscvInstructions.addLast(new R2(nowBlock, dst, src, R2.R2Type.mv));
        }
    }

    private void solveShl(Instruction.Shl shl) {
        Value value1 = shl.getOperand_1();
        Value value2 = shl.getOperand_2();
        Reg ans = VirRegMap.VRM.ensureRegForValue(shl);
        Reg op = VirRegMap.VRM.ensureRegForValue(value1);
        if (value2 instanceof Constant.ConstantInt) {
            int val = ((Constant.ConstantInt) value2).getIntValue();
            if (val >= -2047 && val <= 2047) {
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, new Imm(val), R3.R3Type.slliw));
            } else {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
                nowBlock.riscvInstructions.addLast(new Li(nowBlock, tmp, new Imm(val)));
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, tmp, R3.R3Type.sllw));
            }
        }
    }

    private void solveAnd(Instruction.And and) {
        Value value1 = and.getOperand_1();
        Value value2 = and.getOperand_2();
        Reg ans = VirRegMap.VRM.ensureRegForValue(and);
        Reg op = VirRegMap.VRM.ensureRegForValue(value1);
        if (value2 instanceof Constant.ConstantInt) {
            int val = ((Constant.ConstantInt) value2).getIntValue();
            if (val >= -2047 && val <= 2047) {
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, new Imm(val), R3.R3Type.andi));
            } else {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
                nowBlock.riscvInstructions.addLast(new Li(nowBlock, tmp, new Imm(val)));
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, tmp, R3.R3Type.and));
            }
        }
    }

    private void solveLShr(Instruction.LShr lShr) {
        Value value1 = lShr.getOperand_1();
        Value value2 = lShr.getOperand_2();
        Reg ans = VirRegMap.VRM.ensureRegForValue(lShr);
        Reg op = VirRegMap.VRM.ensureRegForValue(value1);
        if (value2 instanceof Constant.ConstantInt) {
            int val = ((Constant.ConstantInt) value2).getIntValue();
            if (val >= -2047 && val <= 2047) {
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, new Imm(val), R3.R3Type.srliw));
            } else {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
                nowBlock.riscvInstructions.addLast(new Li(nowBlock, tmp, new Imm(val)));
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, tmp, R3.R3Type.srlw));
            }
        }
    }

    private void solveAShr(Instruction.AShr aShr) {
        Value value1 = aShr.getOperand_1();
        Value value2 = aShr.getOperand_2();
        Reg ans = VirRegMap.VRM.ensureRegForValue(aShr);
        Reg op = VirRegMap.VRM.ensureRegForValue(value1);
        if (value2 instanceof Constant.ConstantInt) {
            int val = ((Constant.ConstantInt) value2).getIntValue();
            if (val >= -2047 && val <= 2047) {
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, new Imm(val), R3.R3Type.sraiw));
            } else {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
                nowBlock.riscvInstructions.addLast(new Li(nowBlock, tmp, new Imm(val)));
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, tmp, R3.R3Type.sraw));
            }
        }
    }

    private void solveXor(Instruction.Xor xor) {
        Value value1 = xor.getOperand_1();
        Value value2 = xor.getOperand_2();
        Reg ans = VirRegMap.VRM.ensureRegForValue(xor);
        Reg op = VirRegMap.VRM.ensureRegForValue(value1);
        if (value2 instanceof Constant.ConstantInt) {
            int val = ((Constant.ConstantInt) value2).getIntValue();
            if (val >= -2047 && val <= 2047) {
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, new Imm(val), R3.R3Type.xoriw));
            } else {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
                nowBlock.riscvInstructions.addLast(new Li(nowBlock, tmp, new Imm(val)));
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, tmp, R3.R3Type.xorw));
            }
        }
    }

    private void solveOr(Instruction.Or or) {
        Value value1 = or.getOperand_1();
        Value value2 = or.getOperand_2();
        Reg ans = VirRegMap.VRM.ensureRegForValue(or);
        Reg op = VirRegMap.VRM.ensureRegForValue(value1);
        if (value2 instanceof Constant.ConstantInt) {
            int val = ((Constant.ConstantInt) value2).getIntValue();
            if (val >= -2047 && val <= 2047) {
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, new Imm(val), R3.R3Type.ori));
            } else {
                Reg tmp = Reg.getPreColoredReg(Reg.PhyReg.t0, 32);
                nowBlock.riscvInstructions.addLast(new Li(nowBlock, tmp, new Imm(val)));
                nowBlock.riscvInstructions.addLast(new R3(nowBlock, ans, op, tmp, R3.R3Type.or));
            }
        }
    }

    private void visitBlock(BasicBlock block) {
        nowBlock = blockMap.get(block);
        for (Instruction instruction : block.getInstructions()) {
            // 加入注释语句
            if (instruction instanceof Instruction.Return) {
                solveReturn((Instruction.Return) instruction);
            } else if (instruction instanceof Instruction.Call) {
                solveCall((Instruction.Call) instruction);
            } else if (instruction instanceof Instruction.Alloc) {
                solveAlloc((Instruction.Alloc) instruction);
            } else if (instruction instanceof Instruction.Load) {
                solveLoad((Instruction.Load) instruction);
            } else if (instruction instanceof Instruction.Store) {
                solveStore((Instruction.Store) instruction);
            } else if (instruction instanceof Instruction.Branch) {
                solveBranch((Instruction.Branch) instruction);
            } else if (instruction instanceof Instruction.Jump) {
                solveJump((Instruction.Jump) instruction);
            } else if (instruction instanceof Instruction.SItofp) {
                solveSItofp((Instruction.SItofp) instruction);
            } else if (instruction instanceof Instruction.FPtosi) {
                solveFPtosi((Instruction.FPtosi) instruction);
            } else if (instruction instanceof Instruction.Zext) {
                solveZext((Instruction.Zext) instruction);
            } else if (instruction instanceof Instruction.Icmp) {
                solveIcmp((Instruction.Icmp) instruction);
            } else if (instruction instanceof Instruction.Fcmp) {
                solveFcmp((Instruction.Fcmp) instruction);
            } else if (instruction instanceof Instruction.GetElementPtr) {
                solveGEP((Instruction.GetElementPtr) instruction);
            } else if (instruction instanceof Instruction.BitCast) {
                solveBitCast((Instruction.BitCast) instruction);
            } else if (instruction instanceof Instruction.Add) {
                solveAdd((Instruction.Add) instruction);
            } else if (instruction instanceof Instruction.Sub) {
                solveSub((Instruction.Sub) instruction);
            } else if (instruction instanceof Instruction.FAdd) {
                solveFAdd((Instruction.FAdd) instruction);
            } else if (instruction instanceof Instruction.FSub) {
                solveFSub((Instruction.FSub) instruction);
            } else if (instruction instanceof Instruction.Mul) {
                solveMul((Instruction.Mul) instruction);
            } else if (instruction instanceof Instruction.FMul) {
                solveFMul((Instruction.FMul) instruction);
            } else if (instruction instanceof Instruction.FDiv) {
                solveFDiv((Instruction.FDiv) instruction);
            } else if (instruction instanceof Instruction.Div) {
                solveDiv((Instruction.Div) instruction);
            } else if (instruction instanceof Instruction.Rem) {
                solveRem((Instruction.Rem) instruction);
            } else if (instruction instanceof Instruction.FRem) {
                solveFRem((Instruction.FRem) instruction);
            } else if (instruction instanceof Instruction.Move) {
                solveMove((Instruction.Move) instruction);
            } else if (instruction instanceof Instruction.And) {
                solveAnd((Instruction.And) instruction);
            } else if (instruction instanceof Instruction.Shl) {
                solveShl((Instruction.Shl) instruction);
            } else if (instruction instanceof Instruction.LShr) {
                solveLShr((Instruction.LShr) instruction);
            } else if (instruction instanceof Instruction.AShr) {
                solveAShr((Instruction.AShr) instruction);
            } else if (instruction instanceof Instruction.Or) {
                solveOr((Instruction.Or) instruction);
            } else if (instruction instanceof Instruction.Xor) {
                solveXor((Instruction.Xor) instruction);
            } else {
                throw new RuntimeException("wrong class " + instruction.getClass());
            }
        }
    }
}
