package backend.Opt.GPpooling;

import backend.riscv.RiscvFloat;
import backend.riscv.RiscvGlobalVar;
import backend.riscv.RiscvModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

public class GPpool extends RiscvGlobalVar {
    private final HashMap<RiscvFloat, Integer> gv2offset = new HashMap<>();

    private final ArrayList<RiscvFloat> glos = new ArrayList<>();
    private int size = 0;

    public GPpool() {
        super("pool", GlobType.FLOAT);

    }

    public void add(RiscvFloat globalVar) {
        glos.add(globalVar);
    }

    public void init() {
        RiscvModule.gPpool = this;
        glos.sort(Comparator.comparing(RiscvFloat::getData));
        for (RiscvFloat rb : glos) {
            gv2offset.put(rb, size);
            size += rb.size();
        }
    }


    public int queryOffset(RiscvFloat globalVar) {
        if (gv2offset.containsKey(globalVar)) return gv2offset.get(globalVar);
        return -1;
    }

    @Override
    public String toString() {
        if (glos.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(".section .rodata\n.p2align 3\npool:\n");
        for (RiscvFloat rb : glos) {
            sb.append(rb.getContent());
        }
        return sb.toString();
    }
}
