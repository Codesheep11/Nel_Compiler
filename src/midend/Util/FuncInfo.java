package midend.Util;

import mir.Function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class FuncInfo {

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
}
