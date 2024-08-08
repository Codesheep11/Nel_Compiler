package backend.allocater;

import backend.StackManager;
import backend.operand.Address;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.LS;
import backend.riscv.RiscvInstruction.R2;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import midend.Analysis.AlignmentAnalysis;

import java.util.*;

import static backend.allocater.LivenessAnalyze.*;
import static backend.operand.Reg.PhyReg.zero;

public class FPRallocater {

    public static RiscvFunction curFunc; //当前分配的函数

    public static HashMap<Reg, HashSet<Reg>> conflictGraph = new HashMap<>();
    public static HashMap<Reg, HashSet<Reg>> curCG = new HashMap<>();

    public static ArrayList<Reg> outNodes = new ArrayList<>();
    public static LinkedHashSet<Reg> spillNodes = new LinkedHashSet<>();

    public static LinkedHashSet<R2> moveList = new LinkedHashSet<>();

    public static LinkedHashSet<Reg> moveNodes = new LinkedHashSet<>();

    public static int pass;

    private static int K = 32;

    private static final LinkedHashSet<Reg.PhyReg> Regs = new LinkedHashSet<>(
            Arrays.asList(Reg.PhyReg.ft0, Reg.PhyReg.ft1, Reg.PhyReg.ft2, Reg.PhyReg.ft3, Reg.PhyReg.ft4, Reg.PhyReg.ft5, Reg.PhyReg.ft6, Reg.PhyReg.ft7,
                    Reg.PhyReg.fs0, Reg.PhyReg.fs1,
                    Reg.PhyReg.fa0, Reg.PhyReg.fa1, Reg.PhyReg.fa2, Reg.PhyReg.fa3, Reg.PhyReg.fa4, Reg.PhyReg.fa5, Reg.PhyReg.fa6, Reg.PhyReg.fa7,
                    Reg.PhyReg.fs2, Reg.PhyReg.fs3, Reg.PhyReg.fs4, Reg.PhyReg.fs5, Reg.PhyReg.fs6, Reg.PhyReg.fs7, Reg.PhyReg.fs8, Reg.PhyReg.fs9, Reg.PhyReg.fs10, Reg.PhyReg.fs11,
                    Reg.PhyReg.ft8, Reg.PhyReg.ft9, Reg.PhyReg.ft10, Reg.PhyReg.ft11
            )
    );

    private static final HashSet<Reg.PhyReg> unAllocateRegs = new HashSet<>(

    );

    private static HashSet<Reg.PhyReg> curUsedRegs = new HashSet<>();

    private static void clear() {
        curUsedRegs.clear();
        curCG.clear();
        conflictGraph.clear();
        outNodes.clear();
        spillNodes.clear();
        moveList.clear();
        moveNodes.clear();
        pass = 0;
    }

    public static void runOnFunc(RiscvFunction func) {
        curFunc = func;
        while (true) {
            clear();
            //标记第几轮循环
//            System.out.println(func.name + " FPR round: " + pass++);
////            System.out.println(func);
            //建立冲突图
            buildConflictGraph();
            MoveInit();
            while (!curCG.isEmpty()) {
                SimplifyCoalesce();
                FreezeSpill();
            }
            if (Select()) break;
            ReWrite();
        }
        Allocater.UsedRegs.get(func.name).addAll(curUsedRegs);
    }

    /**
     * 重写代码：
     * 将所有spillNode中虚拟寄存器存入内存
     * 再在变量使用处从内存中取出
     */
    private static void ReWrite() {
        for (Reg reg : spillNodes) {
//            System.out.println("spill: " + reg);
            ArrayList<RiscvInstruction> contains = new ArrayList<>(RegUse.get(reg));
            ArrayList<RiscvInstruction> uses = new ArrayList<>();
            ArrayList<RiscvInstruction> defs = new ArrayList<>();
            ArrayList<RiscvInstruction> uds = new ArrayList<>();
            Reg sp = Reg.getPreColoredReg(Reg.PhyReg.sp, 64);
            for (RiscvInstruction ins : contains) {
                if (Def.get(ins).contains(reg) && Use.get(ins).contains(reg)) uds.add(ins);
                else if (Def.get(ins).contains(reg)) defs.add(ins);
                else if (Use.get(ins).contains(reg)) uses.add(ins);
            }
            for (RiscvInstruction ud : uds) {
                Reg tmp = Reg.getVirtualReg(reg.regType, reg.bits);
                Address offset = StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                StackManager.getInstance().blingRegOffset(curFunc.name, tmp.toString(), reg.bits / 8, offset);
                RiscvInstruction store = new LS(ud.block, tmp, sp, offset, LS.LSType.fsw, true, AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                RiscvInstruction load = new LS(ud.block, tmp, sp, offset, LS.LSType.flw, true,AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                ud.replaceUseReg(reg, tmp);
                ud.block.riscvInstructions.insertAfter(store, ud);
                ud.block.riscvInstructions.insertBefore(load, ud);
            }
            for (RiscvInstruction def : defs) {
                //如果定义点是lw或者ld指令，则不需要sw保护？
                //错误的，定义点也可能会溢出，比如call多个load或者多个arg
                //非 ssa 在使用点使用新的虚拟寄存器
                if (def instanceof LS && ((LS) def).isSpilled && ((LS) def).val.equals(reg)) {
                    LS.LSType type = ((LS) def).type;
                    if (type == LS.LSType.fsw) continue;
                }
                RiscvInstruction store;
                Reg tmp = Reg.getVirtualReg(reg.regType, reg.bits);
                Address offset = StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                StackManager.getInstance().blingRegOffset(curFunc.name, tmp.toString(), reg.bits / 8, offset);
                store = new LS(def.block, tmp, sp, offset, LS.LSType.fsw, true,AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                def.replaceUseReg(reg, tmp);
                def.block.riscvInstructions.insertAfter(store, def);
            }
            for (RiscvInstruction use : uses) {
                //在使用点使用新的虚拟寄存器
                if (use instanceof LS && ((LS) use).isSpilled && ((LS) use).val.equals(reg)) continue;
                RiscvInstruction load;
                Reg tmp = Reg.getVirtualReg(reg.regType, reg.bits);
                Address offset = StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                StackManager.getInstance().blingRegOffset(curFunc.name, tmp.toString(), reg.bits / 8, offset);
                load = new LS(use.block, tmp, sp, offset, LS.LSType.flw, true,AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                use.replaceUseReg(reg, tmp);
                use.block.riscvInstructions.insertBefore(load, use);
            }
        }
    }

    /**
     * 初始化两个队列，并将所有非预着色虚拟寄存器的物理寄存器置空
     **/
    public static void MoveInit() {
        for (Reg reg : conflictGraph.keySet()) {
            if (reg.preColored) continue;
            reg.phyReg = null;
        }
        //维护moveList
        for (RiscvBlock block : curFunc.blocks) {
            for (RiscvInstruction ins : block.riscvInstructions) {
                if (ins instanceof R2 && ((R2) ins).type == R2.R2Type.fmv) {
                    moveList.add((R2) ins);
                }
            }
        }
        //对于有冲突的mv取消冻结关系
        Iterator<R2> it = moveList.iterator();
        while (it.hasNext()) {
            R2 move = it.next();
            if (conflictGraph.get(move.rd).contains(move.rs)) {
                it.remove();
            }
            else {
                moveNodes.add((Reg) move.rd);
                moveNodes.add((Reg) move.rs);
            }
        }
    }


    /**
     * 试图向虚拟寄存器分配物理寄存器
     * 通过将图中冲突的已分配物理寄存器从寄存器池删除，再判断是否可以分配
     *
     * @param v 尝试分配物理寄存器的虚拟寄存器
     * @return 分配结果
     */
    private static boolean AssignPhy(Reg v) {
        if (v.preColored) {
            return true;
        }
        LinkedHashSet<Reg.PhyReg> regs2Assign = new LinkedHashSet<>(Regs);
        for (Reg u : curCG.get(v)) {
            regs2Assign.remove(u.phyReg);
        }
        if (regs2Assign.size() != 0) {
            v.phyReg = regs2Assign.iterator().next();
            return true;
        }
        return false;
    }


    /**
     * 尝试对curCG中的节点进行染色
     *
     * @return 返回染色结果
     */
    private static boolean Select() {
        curCG.clear();
        //先将所有预着色节点加入图中
        ArrayList<Reg> preColored = new ArrayList<>();
        for (Reg reg : conflictGraph.keySet()) {
            if (reg.preColored) {
                preColored.add(reg);
                outNodes.remove(reg);
            }
        }
        for (Reg reg : preColored) {
            AddNode(reg);
            curUsedRegs.add(reg.phyReg);
        }
        int outCnt = outNodes.size();
        for (int i = outCnt - 1; i >= 0; i--) {
            Reg node = outNodes.get(i);
            AddNode(node);
            if (AssignPhy(node)) {
                if (spillNodes.contains(node)) {
                    spillNodes.remove(node);
                }
                curUsedRegs.add(node.phyReg);
            }
            else {
                spillNodes.add(node);
                DeleteNode(node);
            }
        }
        if (spillNodes.isEmpty()) return true;
        else return false;
    }


    /**
     * 向curCG中加入节点node，保持ConflictGraph边存在
     *
     * @param v 加入的寄存器节点
     */
    private static void AddNode(Reg v) {
        curCG.putIfAbsent(v, new HashSet<>());
        for (Reg t : conflictGraph.get(v)) {
            if (curCG.containsKey(t)) {
                curCG.get(v).add(t);
                curCG.get(t).add(v);
            }
        }
    }

    /**
     * 选择一个高度数的节点删除
     */
    private static void FreezeSpill() {
        //先考虑冻结传送有关低度数节点，如果冻结成功则返回
        int min = Integer.MAX_VALUE;
        Reg minReg = null;
        for (Reg reg : moveNodes) {
            if (curCG.get(reg).size() < min) {
                min = curCG.get(reg).size();
                minReg = reg;
            }
        }
        if (minReg != null) {
            FreezeMoveNode(minReg);
            return;
        }
        //再考虑高度数结点的溢出
        int max = 0;
        Reg maxReg = null;
        for (Reg node : curCG.keySet()) {
            if (node.preColored) continue;
            if (curCG.get(node).size() > max) {
                max = curCG.get(node).size();
                maxReg = node;
            }
        }
        //如果找不到高度数的非预着色节点，说明图中剩余高度数节点全为预着色节点,则全部清空
        if (maxReg == null) {
            if (!curCG.isEmpty()) {
                HashSet<Reg> PreRegs = new HashSet<>(curCG.keySet());
                for (Reg reg : PreRegs) {
                    DeleteNode(reg);
                }
            }
            return;
        }
//        System.out.println("spill: " + maxReg);
        DeleteNode(maxReg);
        spillNodes.add(maxReg);
        outNodes.add(maxReg);
    }

    private static void FreezeMoveNode(Reg node) {
//        System.out.println("freeze: " + node);
        HashSet<R2> freezeMoves = new HashSet<>();
        HashSet<Reg> TryFreezeReg = new HashSet<>();
        for (R2 mv : moveList) {
            Reg rd = (Reg) mv.rd;
            Reg rs = (Reg) mv.rs;
            if (rs.equals(node) || rd.equals(node)) {
                freezeMoves.add(mv);
                TryFreezeReg.add(rd);
                TryFreezeReg.add(rs);
            }
        }
        moveList.removeAll(freezeMoves);
        for (Reg reg : TryFreezeReg) {
            TryThrowMoveNode(reg);
        }
    }

    private static int getDegree(HashSet<Reg> nodes) {
        int size = 0;
        HashSet<Reg.PhyReg> regs = new HashSet<>();
        for (Reg reg : nodes) {
            if (reg.preColored) {
                regs.add(reg.phyReg);
            }
            else size++;
        }
        regs.removeAll(unAllocateRegs);
        return regs.size() + size;
    }

    /**
     * 选择一个低度数的节点删除，直到图中剩余节点全为高度数
     */
    public static void SimplifyCoalesce() {
        boolean change = true;
        while (change) {
            change = false;
            //先进行传送无关节点的删除
            boolean simplify = true;
            while (simplify) {
                simplify = false;
                for (Reg node : curCG.keySet()) {
                    //mv相关指令不在这里处理
                    if (moveNodes.contains(node)) continue;
                    if (getDegree(curCG.get(node)) < K) {
                        simplify = true;
                        DeleteNode(node);
                        if (!node.preColored) outNodes.add(node);
                        break;
                    }
                }
            }
            //如果没有可以删除的低度数传送无关节点，尝试删除一条move来合并一对move相关节点
            boolean merge = true;
            while (merge) {
                merge = false;
                for (R2 move : moveList) {
                    Reg r1 = (Reg) move.rs;
                    Reg r2 = (Reg) move.rd;
                    if (CanBeMerged(r1, r2)) {
//                        System.out.println("merge: " + r1 + " " + r2);
                        Reg newReg;
                        if (!r1.equals(r2)) {
                            Reg oldReg;
                            if (r1.preColored) {
                                newReg = r1;
                                oldReg = r2;
                            }
                            else {
                                newReg = r2;
                                oldReg = r1;
                            }
                            MergeRegInCG(oldReg, newReg);
                            //合并节点
                            newReg.mergeReg(oldReg);
                            moveNodes.remove(oldReg);
                        }
                        else {
                            newReg = r1;
                        }
                        //删除move指令
                        deleteMove(move);
                        TryThrowMoveNode(newReg);
                        merge = true;
                        change = true;
                        break;
                    }
                }
            }
        }
    }

    private static void MergeRegInCG(Reg oldReg, Reg newReg) {
        //对于合并的节点，需要在冲突图中重新计算度数
        curCG.get(newReg).addAll(curCG.get(oldReg));
        for (Reg neighbor : curCG.get(oldReg)) {
            curCG.get(neighbor).remove(oldReg);
            curCG.get(neighbor).add(newReg);
        }
        curCG.remove(oldReg);
        conflictGraph.get(newReg).addAll(conflictGraph.get(oldReg));
        for (Reg neighbor : conflictGraph.get(oldReg)) {
            conflictGraph.get(neighbor).remove(oldReg);
            conflictGraph.get(neighbor).add(newReg);
        }
        conflictGraph.remove(oldReg);
    }

    private static void TryThrowMoveNode(Reg node) {
        moveNodes.remove(node);
        for (R2 mv : moveList) {
            Reg rd = (Reg) mv.rd;
            Reg rs = (Reg) mv.rs;
            if (rd.equals(node) || rs.equals(node)) {
                moveNodes.add(node);
                return;
            }
        }
    }

    private static void deleteMove(R2 move) {
        moveList.remove(move);
        for (Reg reg : move.getReg()) {
            RegUse.get(reg).remove(move);
        }
        move.remove();
    }

    /**
     * 判断两个节点是否可以合并
     *
     * @param r1
     * @param r2
     * @return
     */
    public static boolean CanBeMerged(Reg r1, Reg r2) {
        if (r1.equals(r2)) return true;
        if (r1.preColored && r2.preColored)
            if (r1.phyReg == r2.phyReg) return true;
            else return false;
        if (r1.phyReg == zero || r2.phyReg == zero) return false;
        if (curCG.get(r1).contains(r2)) return false;
        //合并策略1：如果两个节点的合并节点度数小于K，则可以合并
        HashSet<Reg> neighbors = new HashSet<>();
        neighbors.addAll(curCG.get(r1));
        neighbors.addAll(curCG.get(r2));
        if (getDegree(neighbors) < K) {
            return true;
        }
        //合并策略2：如果r1的每一个邻居t,t与r2已经存在冲突或者t是低度数节点，则可以合并
        for (Reg t : curCG.get(r1)) {
            if (!(getDegree(curCG.get(t)) < K || curCG.get(r2).contains(t))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从图中删除节点以及与其相连的边
     *
     * @param node 删除的节点
     */
    private static void DeleteNode(Reg node) {
        for (Reg neighbor : curCG.get(node)) {
            curCG.get(neighbor).remove(node);
        }
        curCG.remove(node);
    }


    /**
     * 建立冲突图
     * 指令生成时维护了所有的use和def
     * 只需要得到in，out
     * 指令冲突的条件是在变量定义处所有出口活跃的变量和定义的变量是互相冲突的；
     * 以及同一条指令的出口变量互相之间是冲突的;
     * 不同类型的寄存器不冲突
     */
    public static void buildConflictGraph() {
        LivenessAnalyze.RunOnFunc(curFunc);
        for (RiscvBlock block : curFunc.blocks) {
            for (RiscvInstruction ins : block.riscvInstructions) {
                for (Reg reg : ins.getReg()) {
                    if (reg.regType == Reg.RegType.FPR) conflictGraph.putIfAbsent(reg, new HashSet<>());
                }
                for (Reg def : Def.get(ins)) {
                    if (def.regType == Reg.RegType.FPR) {
                        for (Reg out : Out.get(ins)) {
                            if (out.regType == Reg.RegType.FPR) addConflict(def, out);
                        }
                    }
                }
                for (Reg o1 : Out.get(ins)) {
                    for (Reg o2 : Out.get(ins)) addConflict(o1, o2);

                }
            }
        }
        //检查conflictGraph是否有自环
        for (Reg reg : conflictGraph.keySet()) {
            if (conflictGraph.get(reg).contains(reg)) {
                System.out.println("self conflict: " + reg);
                conflictGraph.get(reg).remove(reg);
            }
        }

        //深拷贝删除图
        curCG = new HashMap<>();
        for (Reg reg : conflictGraph.keySet()) {
            curCG.put(reg, new HashSet<>(conflictGraph.get(reg)));
        }
//        System.out.println(conflictGraph);
    }

    /**
     * 向冲突图中添加冲突
     *
     * @param reg1
     * @param reg2
     */
    private static void addConflict(Reg reg1, Reg reg2) {
        if (reg1.equals(reg2)) return;
        if ((reg1).regType != Reg.RegType.FPR || reg2.regType != Reg.RegType.FPR) return;
        if (conflictGraph.containsKey(reg1) && conflictGraph.get(reg1).contains(reg2)) return;
        conflictGraph.putIfAbsent(reg1, new HashSet<>());
        conflictGraph.putIfAbsent(reg2, new HashSet<>());
        conflictGraph.get(reg1).add(reg2);
        conflictGraph.get(reg2).add(reg1);
    }

}
