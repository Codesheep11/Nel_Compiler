package backend.riscv;

import backend.Opt.GPpooling.GPpool;
import backend.parallel.ExternLib;

import java.util.ArrayList;

public class RiscvModule {
    public final ArrayList<RiscvFunction> funcList = new ArrayList<>();
    public final ArrayList<RiscvGlobalVar> globList = new ArrayList<>();
    public RiscvFunction mainFunc;
    public static GPpool gPpool = null;

    public final ArrayList<RiscvFunction> TopoSort = new ArrayList<>();

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

    public RiscvFloat getSameFloat(Float floatx) {
        for (RiscvGlobalVar rg : globList) {
            if (rg instanceof RiscvFloat && ((RiscvFloat) rg).equalFloat(floatx)) {
                return (RiscvFloat) rg;
            }
        }
        RiscvFloat riscvFloat = new RiscvFloat(floatx);
        globList.add(riscvFloat);
        return riscvFloat;
    }

    @Override
    public String toString() {
        StringBuilder head = new StringBuilder(".option nopic\n" +
                ".attribute arch, \"rv64i2p1_m2p0_a2p1_f2p2_d2p2_c2p0_zicsr2p0_zifencei2p0_zba1p0_zbb1p0\"\n" +
                ".attribute unaligned_access, 0\n" +
                (ExternLib.need(this) ? ExternLib.model : "") +
                ".attribute stack_align, 16\n.global main\n");
        StringBuilder sb = new StringBuilder("");
        sb.append(".bss\n");
        for (RiscvGlobalVar glob : globList) {
            if (!glob.hasInit()) {
                sb.append(glob);
            }
        }
        sb.append(".data\n");
        for (RiscvGlobalVar glob : globList) {
            if (glob.hasInit()) {
                sb.append(glob);
            }
        }
        if (gPpool != null) {
            sb.append(gPpool);
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
