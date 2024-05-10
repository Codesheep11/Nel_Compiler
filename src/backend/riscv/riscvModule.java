package backend.riscv;

import java.util.ArrayList;

public class riscvModule {
    public ArrayList<riscvFunction> funcList = new ArrayList<>();
    public ArrayList<riscvGlobalVar> globList = new ArrayList<>();
    public riscvFunction mainFunc;

    public riscvModule() {

    }

    public riscvFunction getFunction(String name) {
        for (riscvFunction func : funcList) {
            if (func.name.equals(name)) {
                return func;
            }
        }
        return null;
    }

    public static boolean isMain(riscvFunction func) {
        return func.name.equals("main");
    }

    public void addFunction(riscvFunction function) {
        funcList.add(function);
    }

    public void addGlobalVar(riscvGlobalVar gv) {
        globList.add(gv);
    }

    @Override
    public String toString() {
        StringBuilder head = new StringBuilder(".global main\n");
        StringBuilder sb = new StringBuilder("");
        sb.append(".data\n");
        for (riscvGlobalVar glob : globList) {
            sb.append(glob.toString());
        }
        head.append(".type ").append("main").append(", @function\n");
        sb.append("\n");
        sb.append(".text\n");
        sb.append(mainFunc.toString());
        for (riscvFunction func : funcList) {
            if (func.isExternal) {
                continue;
            }
            if (func == mainFunc) {
                continue;
            }
            head.append(".type ").append(riscvFunction.funcNameWrap(func.name)).append(", @function\n");
            sb.append(func);
            sb.append("\n");
        }
        sb.append("\n");
        head.append(sb);
        return head.toString();
    }
}
