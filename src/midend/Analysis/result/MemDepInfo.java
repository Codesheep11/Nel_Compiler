package midend.Analysis.result;


import midend.Analysis.MemDepAnalysis;
import mir.Function;
import mir.Instruction;

import java.util.HashMap;

public class MemDepInfo {
    //Load 与其上次定值点
    private HashMap<Instruction.Load, Instruction> loadMap = new HashMap<>();
    //Store 与其可能的使用点
    private HashMap<Instruction.Store, Instruction> storeMap = new HashMap<>();

    private Function function;

    public MemDepInfo(Function function) {
        this.function = function;
    }

    public void addLoad(Instruction.Load load, Instruction inst) {
        loadMap.put(load, inst);
    }

    public void addStore(Instruction.Store store, Instruction inst) {
        storeMap.put(store, inst);
    }

    //判断两个load是否存在相同的定值点
    public boolean isGVNLoad(Instruction.Load load1, Instruction.Load load2) {
        if (loadMap.get(load1).equals(loadMap.get(load2))) return true;
        return false;
    }
}
