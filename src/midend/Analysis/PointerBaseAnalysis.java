package midend.Analysis;


import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.BiConsumer;

/**
 * 参考了 CMMC 的基指针分析
 *
 * @see <a href="https://gitlab.eduxiji.net/educg-group-17291-1894922/202314325201374-1031/-/blob/riscv_fix/src/cmmc/Analysis/PointerBaseAnalysis.cpp"> </a>
 */
public class PointerBaseAnalysis {

    public static Value getBaseOrNull(Value value) {
        return pointBaseInfo.getOrDefault(value, null);
    }


    private static final HashMap<Value, Value> pointBaseInfo = new HashMap<>();

    private static final int max_depth = 7;

    /**
     * 输入:函数,参数
     * 如果这几个的来源都是共同的话,那么会返回一个value,否则是null
     **/
    private static Value traceInterProceduralArg(Function function, Function.Argument argument, int depth) {
        int idx = function.getFuncRArguments().indexOf(argument);
        if (!(idx >= 0)) throw new RuntimeException("wrong argument");
        return traceArgRecur(function, argument, idx, depth);
    }

    private static Value traceArgRecur(Function function, Function.Argument argument, int idx, int depth) {
        if (depth >= max_depth) return null;
        Value commonSrc = null;
        for (var users : function.getUsers()) {
            if (!(users instanceof Instruction.Call call)) throw new RuntimeException("wrong type");
            if (call.getParams().get(idx).equals(argument)) continue;// 如果是自递归调用那么就继续
            Value src = traceInterProceduralVal(function, argument, depth + 1);
            if (src == null) return null;
            if (commonSrc == null) commonSrc = src;
            else if (commonSrc != src) return null;
        }
        return commonSrc;
    }

    private static Value traceInterProceduralVal(Function function, Value val, int depth) {
        if (val instanceof GlobalVariable || val instanceof Instruction.Alloc) {
            return val;
        }
        if (val instanceof Instruction instruction) {
            return traceInterProceduralInst(function, instruction, depth);
        }
        if (val instanceof Function.Argument) {
            int idx = 0;
            for (Function.Argument arg : function.getFuncRArguments()) {
                if (arg.equals(val)) {
                    return traceInterProceduralArg(function, arg, depth);
                }
                idx++;
            }
        }
        return null;
    }

    private static Value traceInterProceduralInst(Function function, Instruction instruction, int depth) {
        if (depth >= max_depth) return null;
        if (instruction instanceof Instruction.GetElementPtr gep) {
            Value parm = gep.getOffsets().get(gep.getOffsets().size() - 1);
            traceInterProceduralVal(function, parm, depth + 1);
        }
        else if (instruction instanceof Instruction.BitCast bitCast) {
            traceInterProceduralVal(function, bitCast.getSrc(), depth + 1);
        }
        else if (instruction instanceof Instruction.Phi phi) {
            Value commonSrc = null;
            for (Value entry : phi.getIncomingValues()) {
                if (entry.equals(instruction)) continue;
                Value src = traceInterProceduralVal(function, entry, depth + 1);
                if (src == null) return null;
                if (commonSrc == null) commonSrc = src;
                else if (commonSrc != src) return null;
            }
            return commonSrc;
        }
        return null;
    }

    private static void graphAdd(HashMap<Value, ArrayList<Instruction>> graph, Value key, Instruction instruction) {
        if (!graph.containsKey(key)) {
            graph.put(key, new ArrayList<>());
        }
        graph.get(key).add(instruction);
    }


    public static void runOnFunc(Function function) {
        pointBaseInfo.clear();
        HashMap<Value, ArrayList<Instruction>> graph = new HashMap<>();
        HashMap<Value, Integer> degree = new HashMap<>();
        for (BasicBlock block : function.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (!instruction.getType().isPointerTy()) continue;
                if (instruction instanceof Instruction.GetElementPtr gep) {
                    graphAdd(graph, gep.getBase(), gep);
                    degree.put(gep, 1);
                }
                else if (instruction instanceof Instruction.BitCast bitcast) {
                    graphAdd(graph, bitcast.getSrc(), bitcast);
                    degree.put(bitcast, 1);
                }
                else if (instruction instanceof Instruction.Phi phi) {
                    degree.put(phi, phi.getSize());
                    for (var entry : phi.getIncomingValues()) {
                        graphAdd(graph, entry, phi);
                    }
                }
            }
        }
        Queue<Instruction> q = new LinkedList<>();
        BiConsumer<Value, Value> setStorage = (dst, src) -> {
            if (pointBaseInfo.containsKey(dst) && pointBaseInfo.get(dst) != src) {
                throw new AssertionError("Storage already contains key: " +
                        dst + " src:" + src + " bef " + pointBaseInfo.get(dst));
            }
            pointBaseInfo.put(dst, src);
            for (Instruction child : graph.getOrDefault(dst, new ArrayList<>())) {
                if (degree.get(child) <= 0) {
                    throw new AssertionError("Degree should be greater than 0 for child: " + child);
                }
                degree.put(child, degree.get(child) - 1);
                if (degree.get(child) == 0) {
                    q.add(child);
                }
            }
        };
        for (GlobalVariable g : function.module.getGlobalValues()) {
            setStorage.accept(g, g);
        }
        boolean directlyUseGlobal = !q.isEmpty();
        Value uniquePointerArg = null;
        for (Function.Argument argument : function.getFuncRArguments()) {
            if (argument.getType().isPointerTy()) {
                Value base = traceInterProceduralArg(function, argument, 0);
                if (base != null) setStorage.accept(argument, base);
                uniquePointerArg = argument;
            }
        }
        if (!directlyUseGlobal && function.getFuncRArguments().size() == 1) {
            setStorage.accept(uniquePointerArg, uniquePointerArg);
        }
        for (BasicBlock block : function.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction.getType().isPointerTy() && instruction instanceof Instruction.Alloc) {
                    setStorage.accept(instruction, instruction);
                }
            }
        }
        while (!q.isEmpty()) {
            Instruction instruction = q.poll();
            if (instruction instanceof Instruction.GetElementPtr gep) {
                setStorage.accept(gep, gep.getBase());
            }
            else if (instruction instanceof Instruction.BitCast bitCast) {
                setStorage.accept(bitCast, bitCast.getSrc());
            }
            else if (instruction instanceof Instruction.Phi phi) {
                Value src = null;
                boolean same = true;
                for (Value entry : phi.getIncomingValues()) {
                    if (entry.equals(instruction)) continue;
                    Value from = pointBaseInfo.get(entry);
                    if (src == null || from == src) src = from;
                    else {
                        same = false;
                        break;
                    }
                }
                if (same && src != null) setStorage.accept(instruction, src);
            }
        }

    }


}
