package mir;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

public final class Module {
    private final HashMap<String, Function> functions = new HashMap<>();
    private final ArrayList<String> globalStrings;
    private final ArrayList<GlobalVariable> globalVariables;

    public Module(ArrayList<String> globalStrings, ArrayList<GlobalVariable> globalVariables) {
        this.globalStrings = globalStrings;
        this.globalVariables = globalVariables;
    }

    public HashMap<String, Function> getFunctions() {
        return functions;
    }

    public ArrayList<GlobalVariable> getGlobalValues() {
        return globalVariables;
    }

    public ArrayList<String> getGlobalStrings() {
        return globalStrings;
    }

    public void addFunction(Function function) {
        function.module = this;
        functions.putIfAbsent(function.getName(), function);
    }

    public void addGlobalValue(GlobalVariable gv) {
        globalVariables.add(gv);
    }

    public Collection<Function> getFuncSet() {
        return functions.values();
    }

    public void removeFunction(Function function) {
        functions.remove(function.getName());
        Iterator<BasicBlock> iterator = function.getBlocks().iterator();
        while (iterator.hasNext()) {
            BasicBlock basicBlock = iterator.next();
            Iterator<Instruction> instructionIterator = basicBlock.getInstructions().iterator();
            while (instructionIterator.hasNext()) {
                Instruction instruction = instructionIterator.next();
//                System.out.println("remove instruction: " + instruction.getDescriptor());
                instruction.use_clear();
                instructionIterator.remove();
            }
            basicBlock.use_clear();
            iterator.remove();
        }
        function.use_clear();
    }
}
