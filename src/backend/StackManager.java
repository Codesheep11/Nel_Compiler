package backend;

import backend.operand.Address;
import mir.Function;
import mir.Instruction;
import mir.Value;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * 管理栈上内存 <br />
 * 采用单例模式设计 <br />
 * 使用 StackManager.getInstance()获取实例 <br />
 * 注意: 当前的栈帧管理没有给通过a0-a7寄存器传递的参数保留空间
 * @author Srchycz
 */
public class StackManager {

    private static final StackManager INSTANCE = new StackManager();

    private StackManager() {
        offsetMap = new HashMap<>();
        funcSizeMap = new HashMap<>();
        funcArgMap = new HashMap<>();
    }

    public static StackManager getInstance() {
        return INSTANCE;
    }

    private final HashMap<String, HashMap<String, Address>> offsetMap;

    private final HashMap<String, Integer> funcSizeMap;

    private final HashMap<String, HashMap<Instruction.Call, LinkedList<Address>>> funcArgMap;

    /**
     * 存储并获取栈上内存的偏移量 <br />
     *
     * @param funcName 函数名
     * @param regName  虚拟寄存器名
     * @param byteSize 字节大小
     * @return Address 偏移量的绝对值(>=0)
     */
    public Address getRegOffset(String funcName, String regName, int byteSize) {
        regName = regName + "_" + byteSize;
        if (!offsetMap.containsKey(funcName)) {
            offsetMap.put(funcName, new HashMap<>());
            if (!llvm2Offset.containsKey(funcName)) {
                llvm2Offset.put(funcName, new HashMap<>());
                funcSizeMap.put(funcName, 0);
            }
        }
        HashMap<String, Address> funcMap = offsetMap.get(funcName);
        int offset = funcSizeMap.get(funcName);
        if (!funcMap.containsKey(regName)) {
            offset += byteSize;
            funcMap.put(regName, new Address(regName, offset, byteSize));
            funcSizeMap.replace(funcName, offset);
        }
        return funcMap.get(regName);
    }

    private final HashMap<String, HashMap<Value, Integer>> llvm2Offset = new HashMap<>();


    /**
     * 分配并获取栈上的偏移
     * 仅在codegen阶段适用
     * size 是字节
     */
    public void allocOnStack(String funcName, Value pointer, int size) {
        if (!llvm2Offset.containsKey(funcName)) {
            llvm2Offset.put(funcName, new HashMap<>());
            if (!offsetMap.containsKey(funcName)) {
                offsetMap.put(funcName, new HashMap<>());
                funcSizeMap.put(funcName, 0);
            }
        }
        HashMap<Value, Integer> funcMap = llvm2Offset.get(funcName);
        int offset = funcSizeMap.get(funcName);
        funcMap.put(pointer, offset + size);
        funcSizeMap.replace(funcName, offset + size);
    }

    public Address getPointerAddress(String funcName, Value pointer) {
        if (!llvm2Offset.containsKey(funcName)) {
            llvm2Offset.put(funcName, new HashMap<>());
            if (!offsetMap.containsKey(funcName)) {
                offsetMap.put(funcName, new HashMap<>());
                funcSizeMap.put(funcName, 0);
            }
        }
        HashMap<Value, Integer> funcMap = llvm2Offset.get(funcName);
        int offset = funcMap.get(pointer);
        return new Address(offset);
    }

    public boolean hasPointerStored(String funcName, Value pointer) {
        if (!llvm2Offset.containsKey(funcName)) {
            llvm2Offset.put(funcName, new HashMap<>());
            if (!offsetMap.containsKey(funcName)) {
                offsetMap.put(funcName, new HashMap<>());
                funcSizeMap.put(funcName, 0);
            }
        }
        HashMap<Value, Integer> funcMap = llvm2Offset.get(funcName);
        return funcMap.containsKey(pointer);
    }

    /**
     * 获取某个函数栈帧的大小(单位：字节) <br />
     * 该方法请保证在refill后调用，否则不会计算栈顶的参数大小
     *
     * @param funcName 函数名
     * @return int 栈帧大小
     */
    public int getFuncSize(String funcName) {
        return funcSizeMap.get(funcName);
    }

    /**
     * 强制插入参数寄存器到函数栈顶并获取相应的 Address对象 <br />
     * 若插入过再次调用会返回上次插入的 Address对象 <br />
     * 调用该方法顺次插入 <br />
     *
     * @param funcName 函数名
     * @param call     对应的函数调用 IR 指令
     * @param regName  参数名
     * @param byteSize 参数字节大小
     * @return Address 内存的 Address对象
     */
    public Address getArgOffset(String funcName, Instruction.Call call, String regName, int byteSize) {
        if (!funcArgMap.containsKey(funcName)) {
            funcArgMap.put(funcName, new HashMap<>());
        }
        HashMap<Instruction.Call, LinkedList<Address>> CallMap = this.funcArgMap.get(funcName);
        if (!CallMap.containsKey(call)) {
            CallMap.put(call, new LinkedList<>());
        }
        LinkedList<Address> argList = CallMap.get(call);
        for (Address arg : argList) {
            if (arg.getRegName().equals(regName)) {
                return arg;
            }
        }
        Address ret = new Address(regName, byteSize);
        argList.addLast(ret);
        return ret;
    }

    /**
     * 回填参数偏移 重新计算函数栈帧大小 <br />
     * 该方法会回填对应函数的参数寄存器的偏移量 <br />
     * Note: 会 padding 一部分空字节保证栈帧可整除 16
     *
     * @param funcName 函数名
     */
    public void refill(String funcName) {
        if (!funcArgMap.containsKey(funcName)) {
            if (!funcSizeMap.containsKey(funcName)) {
                return;
            }
            int offset = funcSizeMap.get(funcName);
            funcSizeMap.replace(funcName, align(offset));
            return;
        }
        HashMap<Instruction.Call, LinkedList<Address>> CallMap = this.funcArgMap.get(funcName);
        int size = 0;
        for (LinkedList<Address> argList : CallMap.values()) {
            size = Math.max(size, sumList(argList));
        }
        int offset = funcSizeMap.get(funcName);
        offset = align(offset + size);
        for (LinkedList<Address> argList : CallMap.values()) {
            int _temp = offset;
            for (Address arg : argList) {
                arg.setOffset(_temp);
                _temp -= arg.getByteSize();
            }
        }
        funcSizeMap.replace(funcName, offset);
    }

    /**
     * 16 字节对齐
     *
     * @param size 原始大小
     * @return
     */
    private int align(int size) {
        return (size + 15) / 16 * 16;
    }

    private int sumList(LinkedList<Address> list) {
        int sum = 0;
        for (Address address : list) {
            sum += address.getByteSize();
        }
        return sum;
    }

}
