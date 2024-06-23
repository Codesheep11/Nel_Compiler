package backend.Opt;

import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.B;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.IntStream;

class BackCFGNode {
    // pair后面跟的是概率
    public ArrayList<Pair<RiscvBlock, Double>> suc = new ArrayList<>();
    public ArrayList<RiscvBlock> pre = new ArrayList<>();
}

public class GenCFG {

    private static final int MAX_SUPPORTED_BLOCK_SIZE = 1000;
    private static final double EPS = 1e-8;

    // 该方法是生成控制流图，图中包含转移的概率
    public static HashMap<RiscvBlock, BackCFGNode> calcCFG(RiscvFunction func) {
        HashMap<RiscvBlock, BackCFGNode> result = new HashMap<>();
        ArrayList<RiscvBlock> blocks = func.blocks;
        for (RiscvBlock block : blocks) {
            result.put(block, new BackCFGNode());
        }
        for (int i = 0; i < blocks.size(); i++) {
            RiscvBlock block = blocks.get(i);
            BackCFGNode backCFGNode = result.get(block);
            // 首先看看是否有前面的b，有的话需要单独建立一个表
            double prob = 1.0;
            for (RiscvInstruction instruction : block.riscvInstructions) {
                if (instruction instanceof B) {
                    Pair<RiscvBlock, Double> tar = new Pair<>(
                            ((B) instruction).targetBlock, ((B) instruction).getYesProb() * prob);
                    backCFGNode.suc.add(tar);
                    BackCFGNode other = result.get(((B) instruction).targetBlock);
                    other.pre.add(block);
                    prob *= (1 - ((B) instruction).getYesProb());
                }
            }
            if (block.getLast() instanceof B) {
                // 由于不是ret，因此不可能是最后一个
                RiscvBlock next = blocks.get(i + 1);
                Pair<RiscvBlock, Double> lastTar = new Pair<>(next, prob);
                backCFGNode.suc.add(lastTar);
                BackCFGNode lastOther = result.get(next);
                lastOther.pre.add(block);
            } else if (block.getLast() instanceof J) {
                // J类，直接当成1
                // 由于没有jr，所以不考虑这个带来的问题
                if (((J) block.getLast()).type == J.JType.ret) {
                    continue;
                }
                Pair<RiscvBlock, Double> tar = new Pair<>(((J) block.getLast()).targetBlock, prob);
                backCFGNode.suc.add(tar);
                BackCFGNode other = result.get(((J) block.getLast()).targetBlock);
                other.pre.add(block);
            } else {
                //直接到下一个基本快的,否则是ret,那也不用考虑了，和正常指令一样
                RiscvBlock next = i + 1 < blocks.size() ? blocks.get(i + 1) : null;
                if (next != null) {
                    Pair<RiscvBlock, Double> tar = new Pair<>(next, prob);
                    backCFGNode.suc.add(tar);
                    BackCFGNode other = result.get(next);
                    other.pre.add(block);
                }
            }
        }
        //debug(result);
        return result;
    }

    public static void debug(HashMap<RiscvBlock, BackCFGNode> cfg) {
        for (RiscvBlock block : cfg.keySet()) {
            System.out.println(block.name);
            for (Pair<RiscvBlock, Double> pair : cfg.get(block).suc) {
                System.out.println(pair.first.name + " -- " + pair.second);
            }
        }
    }

    private static ArrayList<Double> solve(int n, double[] mat) {
        int[] p = IntStream.range(0, n).toArray();

        for (int i = 0; i < n; ++i) {
            int x = -1;
            double maxv = EPS;
            for (int j = i; j < n; ++j) {
                double pivot = Math.abs(mat[i * n + j]);
                if (pivot > maxv) {
                    maxv = pivot;
                    x = j;
                }
            }
            if (maxv == EPS) {
                return new ArrayList<>();
            }

            if (i != x) {
                int temp = p[i];
                p[i] = p[x];
                p[x] = temp;

                for (int j = 0; j < n; ++j) {
                    double tempVal = mat[i * n + j];
                    mat[i * n + j] = mat[x * n + j];
                    mat[x * n + j] = tempVal;
                }
            }

            double pivot = mat[i * n + i];
            for (int j = i + 1; j < n; ++j) {
                mat[j * n + i] /= pivot;
                double scale = mat[j * n + i];
                for (int k = i + 1; k < n; ++k) {
                    mat[j * n + k] -= scale * mat[i * n + k];
                }
            }
        }

        double[] c = new double[n];
        double[] d = new double[n];
        for (int i = 0; i < n; ++i) {
            double sum = (p[i] == 0) ? 1.0 : 0.0;
            for (int j = 0; j < i; ++j) {
                sum -= mat[i * n + j] * c[j];
            }
            c[i] = sum;
        }

        for (int i = n - 1; i >= 0; --i) {
            double sum = c[i];
            for (int j = i + 1; j < n; ++j) {
                sum -= mat[i * n + j] * d[j];
            }
            d[i] = Math.max(1e-4, sum / mat[i * n + i]);
        }
        ArrayList<Double> ans = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ans.add(d[i]);
        }
        return ans;
    }

    // 该方法会根据控制流图生成每一个块的执行概率
    public static HashMap<RiscvBlock, Double> callFreq(RiscvFunction func, HashMap<RiscvBlock, BackCFGNode> cfg) {
        HashMap<RiscvBlock, Double> res = new HashMap<>();

        int n = func.blocks.size();
        if (n > MAX_SUPPORTED_BLOCK_SIZE) {
            return res;
        }

        int allocateID = 0;
        HashMap<RiscvBlock, Integer> nodeMap = new HashMap<>();
        for (RiscvBlock block : func.blocks) {
            nodeMap.put(block, allocateID++);
        }

        double[] a = new double[n * n];

        for (int i = 0; i < n; ++i) {
            a[i * n + i] = 1.0;
        }

        for (RiscvBlock block : func.blocks) {
            int u = nodeMap.get(block);
            for (Pair<RiscvBlock, Double> pair : cfg.get(block).suc) {
                double prob = pair.second;
                a[nodeMap.get(pair.first) * n + u] -= prob;
            }
        }

        ArrayList<Double> d = solve(n, a);
        if (d.size() == 0) {
            for (RiscvBlock block : func.blocks) {
                res.put(block, 1.0);
            }
            return res;
        }
        for (RiscvBlock block : func.blocks) {
            res.put(block, d.get(nodeMap.get(block)));
        }
        return res;
    }
}