package backend.Opt.CfgOpt;

import backend.riscv.RiscvBlock;

import java.util.HashMap;

public class BackCFGNode {
    // pair后面跟的是概率
    public final HashMap<RiscvBlock, Double> suc = new HashMap<>();
    public final HashMap<RiscvBlock, Double> pre = new HashMap<>();

    public static void connect(HashMap<RiscvBlock, BackCFGNode> result, RiscvBlock src, RiscvBlock dst, double prob) {
        // 首先为了防止一个合并后的块有多个跳转,需要搜索一下
        if (result.get(src).suc.containsKey(dst)) {
            result.get(src).suc.put(dst, result.get(src).suc.get(dst) + prob);
        } else {
            result.get(src).suc.put(dst, prob);
        }
        if (result.get(dst).pre.containsKey(src)) {
            result.get(dst).pre.put(src, result.get(dst).pre.get(src) + prob);
        } else {
            result.get(dst).pre.put(src, prob);
        }
    }
}
