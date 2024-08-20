package backend;

import backend.operand.Address;
import mir.Constant;
import mir.GlobalVariable;
import mir.Instruction;
import mir.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * 管理栈上内存 <br />
 * 采用单例模式设计 <br />
 * 使用 StackManager.getInstance()获取实例 <br />
 * 注意: 当前的栈帧管理没有给通过a0-a7寄存器传递的参数保留空间
 *
 */
public class StackManager {

    private static final StackManager INSTANCE = new StackManager();

    private static final HashMap<String, ArrayList<Address>> funcAddressManger = new HashMap<>();

    public static void changeAllAddress(String funcName, int offset) {
        if (!funcAddressManger.containsKey(funcName)) funcAddressManger.put(funcName, new ArrayList<>());
        for (Address address : funcAddressManger.get(funcName)) {
            address.setOffset(address.getOffset() - offset);
        }
    }

    public static void arrangeAddress(String funcName, Address address) {
        if (!funcAddressManger.containsKey(funcName)) funcAddressManger.put(funcName, new ArrayList<>());
        funcAddressManger.get(funcName).add(address);
    }

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

    private void prepareFunc(String funcName) {
        if (!offsetMap.containsKey(funcName)) {
            offsetMap.put(funcName, new HashMap<>());
            if (!llvm2Offset.containsKey(funcName)) {
                llvm2Offset.put(funcName, new HashMap<>());
                funcSizeMap.put(funcName, 0);
            }
        }
    }

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
        prepareFunc(funcName);
        HashMap<String, Address> funcMap = offsetMap.get(funcName);
        int offset = funcSizeMap.get(funcName);
        if (!funcMap.containsKey(regName)) {
            offset = byteSize == 8 ? ((offset + 7) / 8 * 8 + byteSize) : offset + byteSize;
            funcMap.put(regName, new Address(regName, offset, byteSize, funcName));
            funcSizeMap.replace(funcName, offset);
        }
        return funcMap.get(regName);
    }

    /**
     * 将虚拟寄存器与栈上偏移绑定 <br />
     */
    public void blingRegOffset(String funcName, String regName, int byteSize, Address address) {
        regName = regName + "_" + byteSize;
        prepareFunc(funcName);
        HashMap<String, Address> funcMap = offsetMap.get(funcName);
        if (funcMap.containsKey(regName)) {
            throw new RuntimeException("RegName has been binded");
        }
        funcMap.put(regName, address);
    }

    private final HashMap<String, HashMap<Value, Integer>> llvm2Offset = new HashMap<>();


    /**
     * 分配并获取栈上的偏移
     * 仅在codegen阶段适用
     * size 是字节
     */
    public void allocOnStack(String funcName, Value pointer, int size) {
        prepareFunc(funcName);
        HashMap<Value, Integer> funcMap = llvm2Offset.get(funcName);
        int offset = funcSizeMap.get(funcName);
        offset = offset % 8 == 0 ? (offset + size) : ((offset + 7) / 8 * 8 + size);
        funcMap.put(pointer, offset);
        funcSizeMap.replace(funcName, offset);
        llvm2Offset.get(funcName).put(pointer, offset);
    }

    public void bindingValue(String funcName, Value before, Value after) {
        prepareFunc(funcName);
        if (llvm2Offset.get(funcName).containsKey(before)) {
            llvm2Offset.get(funcName).put(after, llvm2Offset.get(funcName).get(before));
        }
    }

    public int getPointerAddress(String funcName, Value pointer) {
        prepareFunc(funcName);
        HashMap<Value, Integer> funcMap = llvm2Offset.get(funcName);
        return funcMap.get(pointer);
    }

    public void calAsOffset(String funcName, Value base, Value offset, int size, Value ans) {
        prepareFunc(funcName);
        if (base instanceof GlobalVariable) throw new RuntimeException("global wrong");
        if (!(offset instanceof Constant.ConstantInt)) throw new RuntimeException("not constant wrong");
        int off = ((Constant.ConstantInt) offset).getIntValue();
        int now_off = llvm2Offset.get(funcName).get(base);
        llvm2Offset.get(funcName).put(ans, -size * off + now_off);
    }

    public boolean valueHasOffset(String funcname, Value base) {
        prepareFunc(funcname);
        return llvm2Offset.get(funcname).containsKey(base);
    }

    public boolean canBeCalAsOffset(String funcName, Value pointer) {
        prepareFunc(funcName);
        return llvm2Offset.get(funcName).containsKey(pointer);
    }

    public Address getSpOffset(String funcName, Value pointer) {
        prepareFunc(funcName);
        if (!canBeCalAsOffset(funcName, pointer)) {
            throw new RuntimeException("wrong offset!");
        }
        return new Address(llvm2Offset.get(funcName).get(pointer), funcName);
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
        Address ret = new Address(regName, byteSize, funcName);
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
                funcSizeMap.put(funcName, 0);
                return;
            }
            int offset = align(funcSizeMap.get(funcName));
            funcSizeMap.replace(funcName, offset);
            changeAllAddress(funcName, offset);
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
        changeAllAddress(funcName, offset);
    }

    /**
     * 16 字节对齐
     *
     * @param size 原始大小
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
