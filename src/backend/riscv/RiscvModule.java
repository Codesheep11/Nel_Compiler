package backend.riscv;

import java.util.ArrayList;

public class RiscvModule {
    public ArrayList<RiscvFunction> funcList = new ArrayList<>();
    public ArrayList<RiscvGlobalVar> globList = new ArrayList<>();
    public RiscvFunction mainFunc;

    public RiscvModule() {

    }

    public RiscvFunction getFunction(String name) {
        for (RiscvFunction func : funcList) {
            if (func.name.equals(name)) {
                return func;
            }
        }
        return null;
    }

    public static boolean isMain(RiscvFunction func) {
        return func.name.equals("main");
    }

    public void addFunction(RiscvFunction function) {
        funcList.add(function);
    }

    public void addGlobalVar(RiscvGlobalVar gv) {
        globList.add(gv);
    }

    @Override
    public String toString() {
        StringBuilder head = new StringBuilder(".global main\n");
        StringBuilder sb = new StringBuilder("");
        sb.append(".data\n");
        for (RiscvGlobalVar glob : globList) {
            sb.append(glob.toString());
        }
        head.append(".type ").append("main").append(", @function\n");
        sb.append("\n");
        sb.append(".text\n");
        sb.append(mainFunc.toString());
        for (RiscvFunction func : funcList) {
            if (func.isExternal) {
                continue;
            }
            if (func == mainFunc) {
                continue;
            }
            head.append(".type ").append(RiscvFunction.funcNameWrap(func.name)).append(", @function\n");
            sb.append(func);
            sb.append("\n");
        }
        sb.append("\n");
        head.append(sb);
        return head.toString();
    }
}
