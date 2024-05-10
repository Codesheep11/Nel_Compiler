package frontend.semantic;

import frontend.syntaxChecker.Ast;

import java.util.HashMap;

public class SymTable {
    private final HashMap<String, Symbol> symbolMap;

    private final SymTable parent;

    public SymTable() {
        symbolMap = new HashMap<>();
        this.parent = null;
    }

    public SymTable(SymTable parent) {
        symbolMap = new HashMap<>();
        this.parent = parent;
    }

    public SymTable getParent() {
        return parent;
    }

    public void addSymbol(Symbol symbol) {
        assert !symbolMap.containsKey(symbol.getName());
        symbolMap.putIfAbsent(symbol.getName(), symbol);
    }

    public Symbol getSymbol(String name, boolean checkParent) {
        Symbol symbol = symbolMap.get(name);
        if(symbol == null && parent != null && checkParent) {
            return parent.getSymbol(name, true);
        }
        return symbol;
    }

    public Symbol getSymbol(Ast.Ident ident, boolean checkParent) {
        Symbol symbol = symbolMap.get(ident.identifier.content);
        if(symbol == null && parent != null && checkParent) {
            return parent.getSymbol(ident.identifier.content, true);
        }
        return symbol;
    }

    public HashMap<String, Symbol> getSymbolMap() {
        return symbolMap;
    }

    public boolean hasSymbol(String name, boolean checkParent) {
        return getSymbol(name, checkParent) != null;
    }

    public boolean hasSymbol(Ast.Ident ident, boolean checkParent) { return getSymbol(ident.identifier.content, checkParent) != null; }
}
