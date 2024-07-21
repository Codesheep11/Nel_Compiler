package midend.Util;

import mir.Function;
import mir.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class FuncInfo {

    public static boolean FuncAnalysisOpen = false;

    public static Function main;

    public static HashMap<Function, HashSet<Function>> callGraph = new HashMap<>();

    public static HashMap<Function, Boolean> isRecursive = new HashMap<>();
    public static HashMap<Function, Boolean> isLeaf = new HashMap<>();
    /**
     * 对内存进行了读写，这里的内存只包括全局变量
     */
    public static HashMap<Function, Boolean> hasMemoryRead = new HashMap<>();
    public static HashMap<Function, Boolean> hasMemoryWrite = new HashMap<>();
    public static HashMap<Function, Boolean> hasMemoryAlloc = new HashMap<>();
    /**
     * IO操作
     */
    public static HashMap<Function, Boolean> hasReadIn = new HashMap<>();
    public static HashMap<Function, Boolean> hasPutOut = new HashMap<>();
    public static HashMap<Function, Boolean> hasReturn = new HashMap<>();
    /**
     * 表示该函数有副作用，对传入的数组参数进行了写操作
     */
    public static HashMap<Function, Boolean> hasSideEffect = new HashMap<>();
    /**
     * 表示该函数是无状态的,不使用/修改全局变量，传入的数组
     */
    public static HashMap<Function, Boolean> isStateless = new HashMap<>();
    public static HashMap<Function, Boolean> isRecurse = new HashMap<>();

    private static ArrayList<Function> funcTopoSort = new ArrayList<>();
    private static boolean topoSortFlag = false;

    public static void addCall(Function caller, Function callee) {
        if (!callGraph.containsKey(caller)) {
            callGraph.put(caller, new HashSet<>());
        }
        callGraph.get(caller).add(callee);
    }

    public static void clear() {
        callGraph.clear();
        isRecursive.clear();
        isLeaf.clear();
        hasMemoryRead.clear();
        hasMemoryWrite.clear();
        hasMemoryAlloc.clear();
        hasReadIn.clear();
        hasPutOut.clear();
        hasReturn.clear();
        hasSideEffect.clear();
        isStateless.clear();
        isRecurse.clear();
        funcTopoSort.clear();
        topoSortFlag = false;
    }

    public static ArrayList<Function> getFuncTopoSort() {
        if (!topoSortFlag) {
            // 拓扑排序
            HashSet<Function> vis = new HashSet<>();
            dfs(main, vis, funcTopoSort);
            topoSortFlag = true;
        }
        return funcTopoSort;
    }

    private static void dfs(Function func, HashSet<Function> vis, ArrayList<Function> res) {
        vis.add(func);
        if (callGraph.containsKey(func)) {
            for (Function callee : callGraph.get(func)) {
                if (!vis.contains(callee)) {
                    dfs(callee, vis, res);
                }
            }
        }
        res.add(func);
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

        public static final HashMap<String, Function> externFunctions = new HashMap<>() {{
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
        }};

    }
}
