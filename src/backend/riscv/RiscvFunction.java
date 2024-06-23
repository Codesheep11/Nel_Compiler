package backend.riscv;

import backend.operand.Reg;
import backend.riscv.RiscvInstruction.J;
import mir.Function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class RiscvFunction {
    public String name;

    public boolean isMain;

    public boolean isExternal;

    public ArrayList<RiscvBlock> blocks = new ArrayList<>();

    public HashSet<Reg.PhyReg> usedRegs = new HashSet<>();

    public HashSet<J> calls = new HashSet<>();

    public HashSet<RiscvBlock> exits = new HashSet<>();

    public RiscvFunction(Function irFunction) {
        this.name = irFunction.getName();
        if (irFunction.isExternal()) {
            isExternal = true;
        } else {
            isExternal = false;
        }
    }

    public RiscvBlock getEntry() {
        return blocks.get(0);
    }

    //得到所有出口riscvBlock
    public HashSet<RiscvBlock> getExits() {
        return exits;
    }

    public void addBB(RiscvBlock rb) {
        blocks.add(rb);
    }

//    private ArrayList<RiscvBlock> topoSort = new ArrayList<>();

    public ArrayList<RiscvBlock> getTopoSort() {
        ArrayList<RiscvBlock> res = new ArrayList<>();
        HashSet<RiscvBlock> vis = new HashSet<>();
        for (RiscvBlock exit : getExits()) {
            dfs(exit, res, vis);
        }
        Collections.reverse(res);
        return res;
    }

    private void dfs(RiscvBlock rb, ArrayList<RiscvBlock> res, HashSet<RiscvBlock> vis) {
        if (vis.contains(rb)) return;
        vis.add(rb);
        for (RiscvBlock prev : rb.preBlock) {
            dfs(prev, res, vis);
        }
        res.add(rb);
    }

    // 如果是外部定义的话就不加f_,否则为了防止鬼畜函数名出现，需要加一个f_前缀
    public static String funcNameWrap(String str) {
        return switch (str) {
            case "memset", "getint", "putint", "getch",
                    "getfloat", "putch", "putfloat", "_sysy_starttime","getfarray",
                    "_sysy_stoptime", "getarray", "putarray", "putfarray", "putf","main" -> str;
            default -> "f_" + str;
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(funcNameWrap(name) + ":\n");
        for (RiscvBlock rb : blocks) {
            sb.append(rb + "\n");
        }
        return sb.toString();
    }
}
