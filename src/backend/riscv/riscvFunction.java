package backend.riscv;

import backend.operand.Reg;
import backend.riscv.riscvInstruction.J;
import mir.Function;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class riscvFunction {
    public String name;

    public boolean isMain;

    public boolean isExternal;

    public ArrayList<riscvBlock> riscvBlocks = new ArrayList<>();

    public HashSet<Reg.PhyReg> usedRegs = new HashSet<>();

    public HashSet<J> calls = new HashSet<>();

    public HashSet<riscvBlock> exits = new HashSet<>();

    public riscvFunction(Function irFunction) {
        this.name = irFunction.getName();
        if (irFunction.isExternal()) {
            isExternal = true;
        } else {
            isExternal = false;
        }
    }

    public riscvBlock getEntry() {
        return riscvBlocks.get(0);
    }

    //得到所有出口riscvBlock
    public HashSet<riscvBlock> getExits() {
        return exits;
    }

    public void addBB(riscvBlock rb) {
        riscvBlocks.add(rb);
    }

//    private ArrayList<riscvBlock> topoSort = new ArrayList<>();

    public ArrayList<riscvBlock> getTopoSort() {
        ArrayList<riscvBlock> res = new ArrayList<>();
        HashSet<riscvBlock> vis = new HashSet<>();
        for (riscvBlock exit : getExits()) {
            dfs(exit, res, vis);
        }
        Collections.reverse(res);
        return res;
    }

    private void dfs(riscvBlock rb, ArrayList<riscvBlock> res, HashSet<riscvBlock> vis) {
        if (vis.contains(rb)) return;
        vis.add(rb);
        for (riscvBlock prev : rb.preBlock) {
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
        for (riscvBlock rb : riscvBlocks) {
            sb.append(rb + "\n");
        }
        return sb.toString();
    }
}
