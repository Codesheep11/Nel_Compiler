package backend.Opt.GPpooling;

import backend.riscv.*;

import java.util.ArrayList;
import java.util.HashMap;

public class GPpool extends RiscvGlobalVar {
    private final HashMap<RiscvGlobalVar, Integer> gv2offset = new HashMap<>();

    private final ArrayList<RiscvGlobalVar> glos = new ArrayList<>();
    private int size = 0;

    public GPpool() {
        super("pool", GlobType.FLOAT);

    }

    public void add(RiscvGlobalVar globalVar) {
        if (globalVar instanceof RiscvArray || globalVar instanceof RiscvString
                || globalVar instanceof RiscvInt) {
            return;
        }
        glos.add(globalVar);
    }

    public void init() {
        RiscvModule.gPpool = this;
        for (RiscvGlobalVar rb : glos) {
            gv2offset.put(rb, size);
            size += rb.size();
        }
    }

    public boolean used() {
        return size != 0;
    }

    public int queryOffset(RiscvGlobalVar globalVar) {
        if (gv2offset.containsKey(globalVar)) return gv2offset.get(globalVar);
        return -1;
    }

    @Override
    public String toString() {
        if (glos.size() == 0) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(".section .rodata\n.p2align 2\npool:\n");
        for (RiscvGlobalVar rb : glos) {
            sb.append(rb.getContent());
        }
        return sb.toString();
    }
}
