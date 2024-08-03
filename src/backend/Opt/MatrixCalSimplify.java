package backend.Opt;

import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.R3;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class MatrixCalSimplify {
    // 充分利用指针的局部性进行的优化
    // 因为其他指令,除了乘除，都是一条解决战斗,但是gep会是多条,而且计算内容基本相同
    // 为了减轻思考的难度,暂时进行一个类似模式匹配的操作:找sh2add，找addw，找addi


    static class MatrixPointCaler {
        // 仅仅匹配
        /*
          形如
          addiw   s1,s1,1
          addw	t1, s3, s1
          sh2add	s5, t1, s4(base)
         */

        /*
         * 形如
         * addiw	t1, s3, 2
         * addw	t1, t1, s1
         * sh2add	s5, t1, s4
         */


        /**
         * 总结:base不能变,变了全部清空
         * 前面的指令如果是addi/addiw之外的操作,那么就要把前面保留的指针信息修改掉
         **/

        private final R3 shadd;

        private final R3 addw;
        private R3 addiw;// indexCtl

        private final int line;


        public MatrixPointCaler(R3 addw, R3 shadd, int line) {
            this.addw = addw;
            this.shadd = shadd;
            this.line = line;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (addiw != null) sb.append(addiw);
            sb.append(addw);
            sb.append(shadd);
            return sb.toString();
        }
    }


    private static final HashMap<Reg, ArrayList<MatrixPointCaler>> p2m = new HashMap<>();

    private static boolean checkRange(MatrixPointCaler m1, MatrixPointCaler m2, RiscvBlock block) {
        HashSet<Reg> needCheck = new HashSet<>();
        needCheck.add((Reg) m2.shadd.rd);
        needCheck.add((Reg) m2.shadd.rs2);
        needCheck.add((Reg) m2.addw.rs1);
        needCheck.add((Reg) m2.addw.rs2);
        for (int i = m1.line + 2; i < m2.line; i++) {
            // 如果有不是m2的addi且定义了m2的任何一个寄存器的,都需要返回false
            RiscvInstruction ri = block.riscvInstructions.get(i);
            if (ri instanceof J j && j.type == J.JType.call && j.isExternel()) {
                return false;
            }
            for (int j = 0; j <= ri.getOperandNum(); j++) {
                if (ri.isDef(j) && needCheck.contains(ri.getRegByIdx(j))) {
                    if (ri != m2.addiw) return false;
                }
            }
        }
        return true;
    }

    // 后面还需要用,就是那个sh2add的out,就没办法了,就不能删那些了

    private static final HashSet<R3> addi2Remove = new HashSet<>();


    private static boolean remove(Reg reg, RiscvBlock block) {
        //上面的reg是指针的ans
        // 现在获得了所有的指针计算组合,可以酌情消去
        ArrayList<MatrixPointCaler> calers = p2m.get(reg);
        if (calers.size() == 0) return false;
        MatrixPointCaler last = calers.get(calers.size() - 1);
        for (int i = calers.size() - 2; i >= 0; i--) {
            MatrixPointCaler m = calers.get(i);
            //如果满足二者的addw和sh2add都一样,那么可以删掉，然后根据addi来判断index的增加值
            //当然,要注意这个块后面和out是否还需要使用这个
            HashSet<Reg> outs = LivenessAftBin.Out.get(last.shadd);
            if (m.addw.rd.equals(last.addw.rd) &&
                    m.addw.rs1.equals(last.addw.rs1) &&
                    m.addw.rs2.equals(last.addw.rs2) &&
                    m.shadd.rd.equals(last.shadd.rd) &&
                    m.shadd.rs1.equals(last.shadd.rs1) &&
                    m.shadd.rs2.equals(last.shadd.rs2)) {
                // 判断是addi s1,s1,1还是addi s1,t1,2这种
                if (last.addiw != null) {
                    // 自增模式
                    if (last.addiw.rd.equals(last.addiw.rs1) && last.addiw.rs2 instanceof Imm imm) {
                        // 先检查是否会出现覆写的情况,覆写了直接删除这最后一个
                        if (checkRange(m, last, block)) {
                            block.riscvInstructions.insertBefore(
                                    new R3(block, last.shadd.rd, last.shadd.rd, new Imm(4 * imm.getVal()), R3.R3Type.addi),
                                    last.shadd
                            );
                            if (!outs.contains((Reg) last.addw.rd) || last.shadd.rd.equals(last.addw.rd)) {
                                last.addw.remove();
                            }
                            last.shadd.remove();
                        }
                        calers.remove(calers.size() - 1);
                        return true;
                        // 额外增模式
                    } else if (!last.addiw.rd.equals(last.addiw.rs1) && last.addiw.rs2 instanceof Imm limm) {
                        if (m.addiw != null && m.addiw.rd.equals(last.addiw.rd) &&
                                m.addiw.rs1.equals(last.addiw.rs1) && m.addiw.rs2 instanceof Imm mimm) {
                            if (checkRange(m, last, block)) {
                                block.riscvInstructions.insertBefore(
                                        new R3(block, last.shadd.rd, last.shadd.rd, new Imm(4 * (limm.getVal() - mimm.getVal())), R3.R3Type.addi),
                                        last.shadd
                                );
                                if (!outs.contains((Reg) last.addiw.rd) || last.shadd.rd.equals(last.addiw.rd)) {
                                    addi2Remove.add(last.addiw);
                                }
                                if (!outs.contains((Reg) last.addw.rd) || last.shadd.rd.equals(last.addw.rd)) {
                                    last.addw.remove();
                                }
                                last.shadd.remove();
                            }
                            calers.remove(calers.size() - 1);
                            return true;
                        }
                    }
                }// 一开始的情况 add
            } else if (m.shadd.rd.equals(last.shadd.rd) &&
                    m.shadd.rs1.equals(last.shadd.rs1) &&
                    m.shadd.rs2.equals(last.shadd.rs2) &&
                    m.addw.rd.equals(last.addw.rd) &&
                    m.addiw == null && last.addiw != null &&
                    m.addw.rs1.equals(last.addw.rs1) &&
                    m.addw.rs2.equals(last.addiw.rs1) && last.addiw.rs2 instanceof Imm imm) {
                if (checkRange(m, last, block)) {
                    block.riscvInstructions.insertBefore(
                            new R3(block, last.shadd.rd, last.shadd.rd, new Imm(4 * imm.getVal()), R3.R3Type.addi),
                            last.shadd
                    );
                    if (!outs.contains((Reg) last.addiw.rd) || last.shadd.rd.equals(last.addiw.rd)) {
                        addi2Remove.add(last.addiw);
                    }
                    if (!outs.contains((Reg) last.addw.rd) || last.shadd.rd.equals(last.addw.rd)) {
                        last.addw.remove();
                    }
                    last.shadd.remove();
                }
                calers.remove(calers.size() - 1);
                return true;
            }
        }
        return false;
    }


    private static void calAllAdder(RiscvBlock block) {
        //搜索所有的sh2add,并将能模式匹配的单独拿出来
        p2m.clear();
        for (int i = 0; i < block.riscvInstructions.size() - 1; i++) {
            RiscvInstruction now = block.riscvInstructions.get(i);
            RiscvInstruction next = block.riscvInstructions.get(i + 1);
            if (now instanceof R3 addw && addw.type == R3.R3Type.addw &&
                    next instanceof R3 sh2add && sh2add.type == R3.R3Type.sh2add) {
                MatrixPointCaler m = new MatrixPointCaler(addw, sh2add, i);
                if (!p2m.containsKey((Reg) m.shadd.rd)) p2m.put((Reg) m.shadd.rd, new ArrayList<>());
                p2m.get((Reg) m.shadd.rd).add(m);
            }
        }
        for (Reg reg : p2m.keySet()) {
            for (MatrixPointCaler m : p2m.get(reg)) {
                //倒着寻找它的idx
                for (int i = m.line - 1; i >= 0; i--) {
                    RiscvInstruction ri = block.riscvInstructions.get(i);
                    if (ri instanceof R3 addi && addi.type == R3.R3Type.addiw) {
                        if (addi.rd.equals(m.addw.rs1) || addi.rd.equals(m.addw.rs2)) {
                            m.addiw = addi;
                            break;
                        }
                    }
                }
            }
        }
    }


    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            LivenessAftBin.runOnFunc(function);
            for (RiscvBlock block : function.blocks) {
                calAllAdder(block);
                addi2Remove.clear();
                for (Reg reg : p2m.keySet()) {
                    while (true) {
                        if (!remove(reg, block)) break;
                    }
                }
                for (R3 r3 : addi2Remove) {
                    r3.remove();
                }
            }
        }
    }
}
