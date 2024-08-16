package midend.Util;

import mir.Function;
import mir.GlobalVariable;
import mir.Type;

import java.util.HashMap;
import java.util.HashSet;

public class FuncInfo {

    public final Function function;
    public boolean isRecursive = false;
    public boolean isLeaf = false;
    /**
     * 对内存进行了读写，这里的内存只包括全局变量
     */
    public boolean hasMemoryRead = false;
    public boolean hasMemoryWrite = false;
    public boolean hasMemoryAlloc = false;
    /**
     * IO操作
     */
    public boolean hasReadIn = false;
    public boolean hasPutOut = false;
    public boolean hasReturn = false;
    /**
     * 表示该函数有副作用，对传入的数组参数进行了写操作
     */
    public boolean hasSideEffect = false;
    /**
     * 表示该函数是无状态的，输出与内存无关
     */
    public boolean isStateless = true;

    public final HashSet<GlobalVariable> usedGlobalVariables = new HashSet<>();

    public FuncInfo(Function function) {
        this.function = function;
    }


    public static class ExternFunc {
        public static final Function MEMSET = new Function(Type.VoidType.VOID_TYPE, "memset",
                new Type.PointerType(Type.BasicType.I32_TYPE), Type.BasicType.I32_TYPE, Type.BasicType.I32_TYPE);
        public static final Function GETINT = new Function(Type.BasicType.I32_TYPE, "getint");
        public static final Function PUTINT = new Function(Type.VoidType.VOID_TYPE, "putint", Type.BasicType.I32_TYPE);
        public static final Function GETCH = new Function(Type.BasicType.I32_TYPE, "getch");
        public static final Function GETFLOAT = new Function(Type.BasicType.F32_TYPE, "getfloat");
        public static final Function PUTCH = new Function(Type.VoidType.VOID_TYPE, "putch", Type.BasicType.I32_TYPE);
        public static final Function PUTFLOAT = new Function(Type.VoidType.VOID_TYPE, "putfloat", Type.BasicType.F32_TYPE);
        public static final Function STARTTIME = new Function(Type.VoidType.VOID_TYPE, "_sysy_starttime", Type.BasicType.I32_TYPE);
        public static final Function STOPTIME = new Function(Type.VoidType.VOID_TYPE, "_sysy_stoptime", Type.BasicType.I32_TYPE);
        public static final Function GETARRAY = new Function(Type.BasicType.I32_TYPE, "getarray", new Type.PointerType(Type.BasicType.I32_TYPE));
        public static final Function GETFARRAY = new Function(Type.BasicType.I32_TYPE, "getfarray", new Type.PointerType(Type.BasicType.F32_TYPE));
        public static final Function PUTARRAY = new Function(Type.VoidType.VOID_TYPE, "putarray", Type.BasicType.I32_TYPE, new Type.PointerType(Type.BasicType.I32_TYPE));
        public static final Function PUTFARRAY = new Function(Type.VoidType.VOID_TYPE, "putfarray", Type.BasicType.I32_TYPE, new Type.PointerType(Type.BasicType.F32_TYPE));
        public static final Function PUTF = new Function(Type.VoidType.VOID_TYPE, "putf");
//        public static final Function PARALLEL = new Function(Type.VoidType.VOID_TYPE, "nel_parallel", Type.BasicType.I32_TYPE, Type.BasicType.I32_TYPE, Type.FunctionType.FUNC_TYPE);
        //        public static final Function MIN = new Function(Type.BasicType.I32_TYPE, "llvm.smin.i32", Type.BasicType.I32_TYPE, Type.BasicType.I32_TYPE);
//        public static final Function MAX = new Function(Type.BasicType.I32_TYPE, "llvm.smax.i32", Type.BasicType.I32_TYPE, Type.BasicType.I32_TYPE);
        public static final HashMap<String, Function> externFunctions = new HashMap<>() {
            {
                put(MEMSET.getName(), MEMSET);
                put(GETINT.getName(), GETINT);
                put(PUTINT.getName(), PUTINT);
                put(GETCH.getName(), GETCH);
                put(GETFLOAT.getName(), GETFLOAT);
                put(PUTCH.getName(), PUTCH);
                put(PUTFLOAT.getName(), PUTFLOAT);
                put("starttime", STARTTIME);
                put("stoptime", STOPTIME);
                put(GETARRAY.getName(), GETARRAY);
                put(GETFARRAY.getName(), GETFARRAY);
                put(PUTARRAY.getName(), PUTARRAY);
                put(PUTFARRAY.getName(), PUTFARRAY);
                put(PUTF.getName(), PUTF);
//                put(PARALLEL.getName(), PARALLEL);
//            put(MIN.getName(), MIN);
//            put(MAX.getName(), MAX);
            }
        };

    }
}
