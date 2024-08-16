package frontend.semantic;

import frontend.syntaxChecker.Ast;
import mir.Type;
import mir.Value;

public class Symbol {
    private final String name;
    private final Type type;
    private final InitValue value;
    private final boolean isConstant;
    private final Value allocInst;

    private InitValue curValue;

    public boolean isChanged = false;

    public Symbol(Ast.Ident ident, Type type, InitValue value, Boolean isConstant, Value allocInst) {
        this.name = ident.identifier.content;
        this.type = type;
        this.value = value;
        this.isConstant = isConstant;
        this.allocInst = allocInst;
        this.curValue = value;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public InitValue getValue() {
        return value;
    }

    public boolean isConstant() {
        return isConstant;
    }

    public Value getAllocInst() {
        return allocInst;
    }

    public void setCurValue(InitValue curValue) {
        this.curValue = curValue;
    }

    public InitValue getCurValue() {
        return curValue;
    }
}
