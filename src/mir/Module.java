package mir;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class Module {
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
        functions.putIfAbsent(function.getName(), function);
    }

    public Collection<Function> getFuncSet() {
        return functions.values();
    }

    public void removeFunction(Function function) {
        functions.remove(function.getName());
    }
}
