package backend.Opt;

import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.R3;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;

import java.util.ArrayList;
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
        private R3 addiw;


        public MatrixPointCaler(R3 addw, R3 shadd) {
            this.addw = addw;
            this.shadd = shadd;
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

    private static final ArrayList<MatrixPointCaler> pointShadds = new ArrayList<>();

    // 当遇到重新定义时，应当直接删掉原来的,而不是将新的返回一个null
    private static void removeByDef(Reg reg) {
        pointShadds.removeIf(m -> m.shadd.rd.equals(reg) ||
                m.shadd.rs2.equals(reg) ||
                m.addw.rs1.equals(reg) ||
                m.addw.rs2.equals(reg) ||
                (m.addiw != null && m.addiw.rs1.equals(reg)));
    }

    // 后面还需要用,就是那个sh2add的out,就没办法了,就不能删那些了


    private static boolean remove(RiscvBlock block) {
        // 现在获得了所有的指针计算组合,可以酌情消去
        if (pointShadds.size() == 0) return false;
        MatrixPointCaler last = pointShadds.get(pointShadds.size() - 1);
        for (int i = pointShadds.size() - 2; i >= 0; i--) {
            MatrixPointCaler m = pointShadds.get(i);
            //如果满足二者的addw和sh2add都一样,那么可以删掉，然后根据addi来判断index的增加值
            //当然,要注意这个块后面和out是否还需要使用这个
            HashSet<Reg> outs = LivenessAftBin.Out.get(m.shadd);
            System.out.println(m.shadd);
            System.out.println(outs);
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
                        pointShadds.remove(pointShadds.size() - 1);//删除最后一个
                        block.riscvInstructions.insertBefore(
                                new R3(block, last.shadd.rd, last.shadd.rd, new Imm(4 * imm.getVal()), R3.R3Type.addi),
                                last.shadd
                        );
                        if (!outs.contains((Reg) last.addw.rd)) {
                            last.addw.remove();
                        }
                        last.shadd.remove();
                        return true;
                        // 额外增模式
                    } else if (!last.addiw.rd.equals(last.addiw.rs1) && last.addiw.rs2 instanceof Imm limm) {
                        if (m.addiw != null && !m.addiw.rd.equals(last.addiw.rd) &&
                                m.addiw.rs1.equals(last.addiw.rs1) && m.addiw.rs2 instanceof Imm mimm) {
                            pointShadds.remove(pointShadds.size() - 1);//删除最后一个
                            block.riscvInstructions.insertBefore(
                                    new R3(block, last.shadd.rd, last.shadd.rd, new Imm(4 * (mimm.getVal() - limm.getVal())), R3.R3Type.addi),
                                    last.shadd
                            );
                            if (!outs.contains((Reg) last.addiw.rd)) {
                                last.addiw.remove();
                            }
                            if (!outs.contains((Reg) last.addw.rd)) {
                                last.addw.remove();
                            }
                            last.shadd.remove();
                            return true;
                        }
                    }
                }// 一开始的情况 add
            } else if (m.shadd.rd.equals(last.shadd.rd) &&
                    m.shadd.rs1.equals(last.shadd.rs1) &&
                    m.shadd.rs2.equals(last.shadd.rs2) &&
                    m.addw.rd.equals(last.addw.rd) &&
                    m.addiw == null && last.addiw != null &&
                    m.addw.rs2.equals(last.addw.rs2) &&
                    m.addw.rs1.equals(last.addiw.rs1) && last.addiw.rs2 instanceof Imm imm) {
                pointShadds.remove(pointShadds.size() - 1);//删除最后一个
                block.riscvInstructions.insertBefore(
                        new R3(block, last.shadd.rd, last.shadd.rd, new Imm(4 * imm.getVal()), R3.R3Type.addi),
                        last.shadd
                );
                if (!outs.contains((Reg) last.addiw.rd)) {
                    last.addiw.remove();
                }
                if (!outs.contains((Reg) last.addw.rd)) {
                    last.addw.remove();
                }
                last.shadd.remove();
                return true;
            }
        }
        return false;
    }


    private static void calAllAdder(RiscvBlock block) {
        //搜索所有的sh2add,并将能模式匹配的单独拿出来
        pointShadds.clear();
        ArrayList<Integer> bards = new ArrayList<>();
        bards.add(0);
        for (int i = 0; i < block.riscvInstructions.size(); i++) {
            RiscvInstruction ri = block.riscvInstructions.get(i);
            if (ri instanceof R3 r3 && r3.type == R3.R3Type.sh2add) {
                bards.add(i);
            }
        }
        for (int i = 0; i < bards.size() - 1; i++) {
            MatrixPointCaler m = rangeFind(block, bards.get(i + 1), bards.get(i));
            if (m != null) {
                pointShadds.add(m);
            }
        }
    }

    // 在range里面找
    private static MatrixPointCaler rangeFind(RiscvBlock block, int i, int last_index) {
        RiscvInstruction ri = block.riscvInstructions.get(i);
        if (ri instanceof R3 sh2add && sh2add.type == R3.R3Type.sh2add) {
            // 找addw
            int j;
            MatrixPointCaler m = null;
            HashSet<Reg> inRegs = new HashSet<>();
            inRegs.add((Reg) sh2add.rs1);
            inRegs.add((Reg) sh2add.rs2);
            inRegs.add((Reg) sh2add.rd);
            for (j = i - 1; j > last_index; j--) {
                RiscvInstruction tmp = block.riscvInstructions.get(j);
                if (tmp instanceof R3 addw && addw.type == R3.R3Type.addw
                        && addw.rd.equals(sh2add.rs1)) {
                    m = new MatrixPointCaler(addw, sh2add);
                    break;
                } else {
                    for (int k = 0; k < tmp.getOperandNum(); k++) {
                        if (tmp.isDef(k) && inRegs.contains(tmp.getRegByIdx(k))) {
                            removeByDef(tmp.getRegByIdx(k));
                        }
                    }
                }
            }
            if (j <= last_index) return null;
            inRegs.add((Reg) m.addw.rs1);
            inRegs.add((Reg) m.addw.rs2);
            boolean finish = false;
            for (j = j - 1; j > last_index; j--) {
                RiscvInstruction tmp = block.riscvInstructions.get(j);
                if (!finish && tmp instanceof R3 addiw && addiw.type == R3.R3Type.addiw
                        && (addiw.rd.equals(m.addw.rs1) || addiw.rd.equals(m.addw.rs2))) {
                    m.addiw = addiw;
                    finish = true;
                } else {
                    for (int k = 0; k < tmp.getOperandNum(); k++) {
                        if (tmp.isDef(k) && inRegs.contains(tmp.getRegByIdx(k))) {
                            removeByDef(tmp.getRegByIdx(k));
                        }
                    }
                }
            }
            return m;
        }
        return null;
    }

    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            LivenessAftBin.runOnFunc(function);
            for (RiscvBlock block : function.blocks) {
                calAllAdder(block);
                while (true) {
                    if (!remove(block)) break;
                }
            }
        }
    }
}
