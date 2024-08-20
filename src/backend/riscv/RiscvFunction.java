package backend.riscv;

import backend.Opt.BackLoop.RiscLoop;
import backend.operand.Reg;
import backend.riscv.RiscvInstruction.J;
import mir.Function;

import java.util.*;

import static mir.Type.VoidType.VOID_TYPE;

public class RiscvFunction {
    public final String name;

    public boolean isMain;

    public final boolean isExternal;

    public boolean isSaveOut = false;

    public boolean isParallelLoopBody = false;
    /**
     * 0 表示没有返回值<br/>
     * 1 表示返回int<br/>
     * -1 表示返回float
     */
    public final int retTypeCode;


    public final ArrayList<RiscvBlock> blocks = new ArrayList<>();

    public final HashSet<Reg> defs = new HashSet<>();

    public final HashSet<J> calls = new HashSet<>();

    public final HashSet<RiscvBlock> exits = new HashSet<>();

    public final ArrayList<RiscLoop> loops = new ArrayList<>();

    public RiscvFunction(Function irFunction) {
        this.name = irFunction.getName();
        isExternal = irFunction.isExternal();
        if (irFunction.getRetType().equals(VOID_TYPE)) {
            retTypeCode = 0;
        } else if (irFunction.getRetType().isInt32Ty()) {
            retTypeCode = 1;
        } else if (irFunction.getRetType().isFloatTy()) {
            retTypeCode = -1;
        } else {
            retTypeCode = 1;
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

    public ArrayList<RiscvBlock> getTopoSort() {
        ArrayList<RiscvBlock> res = new ArrayList<>();
        HashSet<RiscvBlock> vis = new HashSet<>();
        for (RiscvBlock exit : getExits()) {
            dfs4topo(exit, res, vis);
        }
        Collections.reverse(res);
        return res;
    }

    private void dfs4topo(RiscvBlock rb, ArrayList<RiscvBlock> res, HashSet<RiscvBlock> vis) {
        if (vis.contains(rb)) return;
        vis.add(rb);
        for (RiscvBlock prev : rb.preBlock) {
            dfs4topo(prev, res, vis);
        }
        res.add(rb);
    }

    public ArrayList<RiscvBlock> getReversePostOrder() {
        ArrayList<RiscvBlock> res = new ArrayList<>();
        HashSet<RiscvBlock> vis = new HashSet<>();
        dfs(getEntry(), res, vis);
        Collections.reverse(res);
        return res;
    }

    private void dfs(RiscvBlock now, ArrayList<RiscvBlock> res, HashSet<RiscvBlock> vis) {
        if (vis.contains(now)) return;
        vis.add(now);
        for (RiscvBlock succ : now.succBlock) {
            dfs(succ, res, vis);
        }
        res.add(now);
    }

    // 如果是外部定义的话就不加f_,否则为了防止鬼畜函数名出现，需要加一个f_前缀
    public static String funcNameWrap(String str) {
        return switch (str) {
            case "memset", "getint", "putint", "getch",
                    "getfloat", "putch", "putfloat", "_sysy_starttime", "getfarray",
                    "_sysy_stoptime", "getarray", "putarray", "putfarray", "putf", "main"
                    , "NELCacheLookup", "NELParallelFor", "NELReduceAddF32" -> str;
            default -> "f_" + str;
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(funcNameWrap(name) + ":\n");
        for (RiscvBlock rb : blocks) {
            sb.append(rb);
        }
        return sb.toString();
    }
}
