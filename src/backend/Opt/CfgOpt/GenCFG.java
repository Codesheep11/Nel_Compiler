package backend.Opt.CfgOpt;

import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.B;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.RiscvInstruction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class GenCFG {

    private static final double EPS = 1e-8;

    // 该方法是生成控制流图，图中包含转移的概率
    public static HashMap<RiscvBlock, BackCFGNode> calcCFG(RiscvFunction func) {
        HashMap<RiscvBlock, BackCFGNode> result = new HashMap<>();
        ArrayList<RiscvBlock> blocks = func.blocks;
        for (RiscvBlock block : blocks) {
            result.put(block, new BackCFGNode());
        }
        for (RiscvBlock block : blocks) {
            // 首先看看是否有前面的b，有的话需要单独建立一个表
            double prob = 1.0;
            for (RiscvInstruction instruction : block.riscvInstructions) {
                if (instruction instanceof B b) {
                    RiscvBlock target = b.targetBlock;
                    BackCFGNode.connect(result, block, target, b.getYesProb());
                    prob *= (1 - b.getYesProb());
                }
            }
            if (!(block.getLast() instanceof J myj)) throw new RuntimeException("wrong!");
            if (myj.type == J.JType.ret) {
                continue;
            }
            RiscvBlock target = myj.targetBlock;
            BackCFGNode.connect(result, block, target, prob);
        }
        //debug(result);
        return result;
    }

    public static void debug(HashMap<RiscvBlock, BackCFGNode> cfg) {
        for (RiscvBlock block : cfg.keySet()) {
            System.out.println(block.name);
            for (Map.Entry<RiscvBlock, Double> pair : cfg.get(block).suc.entrySet()) {
                System.out.println(pair.getKey().name + " -- " + pair.getValue());
            }
        }
    }

    private static ArrayList<Double> solve(int n, double[] mat) {
        int[] p = IntStream.range(0, n).toArray();

        for (int i = 0; i < n; ++i) {
            int x = Integer.MAX_VALUE;
            double maxv = EPS;
            for (int j = i; j < n; ++j) {
                double pivot = Math.abs(mat[i * n + j]);
                if (pivot > maxv) {
                    maxv = pivot;
                    x = j;
                    //break;//fixme 是否保留?
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
            for (Map.Entry<RiscvBlock, Double> pair : cfg.get(block).suc.entrySet()) {
                double prob = pair.getValue();
                a[nodeMap.get(pair.getKey()) * n + u] -= prob;
            }
        }

        ArrayList<Double> d = solve(n, a);
        if (d.isEmpty()) {
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