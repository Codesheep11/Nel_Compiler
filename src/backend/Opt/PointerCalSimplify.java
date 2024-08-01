package backend.Opt;

import backend.operand.Imm;
import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.R3;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;

import java.util.ArrayList;

public class PointerCalSimplify {
    // 充分利用指针的局部性进行的优化
    // 因为其他指令,除了乘除，都是一条解决战斗,但是gep会是多条,而且计算内容基本相同
    // 为了减轻思考的难度,暂时进行一个类似模式匹配的操作:找sh2add，找addw，找addi
    static class Addr extends Operand {

        private final Reg me;
        private Operand left;
        private Operand right;

        public Addr(Reg me) {
            this.me = me;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Addr)) return false;
            return (left.equals(((Addr) obj).left) && right.equals(((Addr) obj).right))
                    || (left.equals(((Addr) obj).right) && right.equals(((Addr) obj).left));
        }

        // 展平计算，保证把数字放最后
        public ArrayList<Operand> floor() {
            ArrayList<Operand> tmp = new ArrayList<>();
            if (left instanceof Addr ad) tmp.addAll(ad.floor());
            else tmp.add(left);
            if (right instanceof Addr ad) tmp.addAll(ad.floor());
            else tmp.add(right);
            ArrayList<Imm> imms = new ArrayList<>();
            for (Operand o : tmp) {
                if (o instanceof Imm imm) {
                    imms.add(imm);
                }
            }
            int i = 0;
            for (Imm imm : imms) {
                tmp.remove(imm);
                i += imm.getVal();
            }
            tmp.add(new Imm(i));
            return tmp;
        }

        public boolean contains(Operand o) {
            return left.equals(o) || right.equals(o) ||
                    (left instanceof Addr ad && ad.contains(o))
                    || (right instanceof Addr da && da.contains(o));
        }

        public boolean inc() {
            return left instanceof Reg r && r.equals(me) && right instanceof Imm imm && imm.getVal() == 1;
        }
    }

    static class MatrixPointCaler {
        /*
          形如
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
        public Addr indexCon;
        // 局部的重复计算一般都是+得到的

        private final ArrayList<RiscvInstruction> ri2Remove = new ArrayList<>();

        public Reg index() {
            return (Reg) shadd.rs1;
        }

        public Reg base() {
            return (Reg) shadd.rs2;
        }


        public Reg pointer() {
            return (Reg) shadd.rd;
        }


        public MatrixPointCaler(R3 shadd) {
            this.shadd = shadd;
        }
    }

    private static final ArrayList<MatrixPointCaler> pointShadds = new ArrayList<>();

    private static boolean searchBase(Reg reg) {
        for (MatrixPointCaler ml : pointShadds) {
            if (ml.base().equals(reg)) return true;
        }
        return false;
    }

    private static void runOnBlock(RiscvBlock block) {
        //搜索所有的sh2add
        for (RiscvInstruction ri : block.riscvInstructions) {
            if (ri instanceof R3 r3 && r3.type == R3.R3Type.sh2add) {
                if (searchBase((Reg) r3.rs2)) {

                }
            }
        }
    }

    private static boolean isAddClass(R3 r3) {
        return r3.type == R3.R3Type.add || r3.type == R3.R3Type.addw ||
                r3.type == R3.R3Type.addi || r3.type == R3.R3Type.addiw;
    }

    private static void calAllAdder(RiscvBlock block) {
        //搜索所有的sh2add
        int last_index = 0;
        for (int i = 0; i < block.riscvInstructions.size(); i++) {
            RiscvInstruction ri = block.riscvInstructions.get(i);
            if (ri instanceof R3 r3 && r3.type == R3.R3Type.sh2add) {
                MatrixPointCaler ma = rangeFind(block, i, last_index);
                last_index = i;
            }
        }
    }

    private static MatrixPointCaler rangeFind(RiscvBlock block, int i, int last_index) {
        RiscvInstruction ri = block.riscvInstructions.get(i);
        assert ri instanceof R3 r3 && r3.type == R3.R3Type.sh2add;
        MatrixPointCaler ma = new MatrixPointCaler((R3) ri);
        // 从当前到上一个的结束倒着找
        Addr cons = new Addr(ma.index());
        ArrayList<R3> calCular = new ArrayList<>();
        for (int j = i - 1; j > last_index; j--) {
            RiscvInstruction find = block.riscvInstructions.get(j);
            if (find instanceof R3 f && isAddClass(f)) {
                if (cons.contains(f.rd)) {
                    cons.add(f.rs1);
                    cons.add(f.rs2);
                    calCular.add(f);
                }
            } else {
                //不以加的形式定义指针变量中的东西
                for (int k = 0; k < find.getOperandNum(); k++) {
                    if (find.isDef(k) && cons.contains(find.getRegByIdx(i))) {
                        return null;
                    }
                }
            }
        }
        // 求了所有这个index的构成
        ma.indexAdds.addAll(cons);
        ma.ri2Remove.addAll(calCular);
        return ma;
    }

    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                runOnBlock(block);
            }
        }
    }
}
