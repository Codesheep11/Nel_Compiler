package backend.allocator;

import backend.StackManager;
import backend.operand.Address;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.*;
import midend.Analysis.AlignmentAnalysis;

import java.util.*;

import static backend.allocator.LivenessAnalyze.*;

/**
 * 基于图着色算法改进
 *
 * @see <a href="https://en.wikipedia.org/wiki/Graph_coloring">Graph coloring</a>
 * @see <a href="https://www.sciencedirect.com/science/article/abs/pii/0096055181900485">Register allocation via coloring</a>
 */
public class GPRallocator {

    public static RiscvFunction curFunc; //当前分配的函数

    public static final LinkedHashMap<Reg, LinkedHashSet<Reg>> conflictGraph = new LinkedHashMap<>();
    public static LinkedHashMap<Reg, LinkedHashSet<Reg>> curCG = new LinkedHashMap<>();

    public static final ArrayList<Reg> outNodes = new ArrayList<>();
    public static final LinkedHashSet<Reg> spillNodes = new LinkedHashSet<>();

    public static final LinkedHashSet<R2> moveList = new LinkedHashSet<>();

    public static final LinkedHashSet<Reg> moveNodes = new LinkedHashSet<>();

    public static int pass;

    /*
    x5 - x31均可分配
     */
    private static final int K = 26;
    //t0作为临时寄存器，不参与图着色寄存器分配

    private static final ArrayList<Reg.PhyReg> Regs = new ArrayList<>(
            Arrays.asList(Reg.PhyReg.t1, Reg.PhyReg.t2, Reg.PhyReg.t3,
                    Reg.PhyReg.t4, Reg.PhyReg.t5, Reg.PhyReg.t6,
                    Reg.PhyReg.a0, Reg.PhyReg.a1, Reg.PhyReg.a2, Reg.PhyReg.a3, Reg.PhyReg.a4,
                    Reg.PhyReg.a5, Reg.PhyReg.a6, Reg.PhyReg.a7,
                    Reg.PhyReg.s0, Reg.PhyReg.s1, Reg.PhyReg.s2, Reg.PhyReg.s3, Reg.PhyReg.s4,
                    Reg.PhyReg.s5, Reg.PhyReg.s6, Reg.PhyReg.s7, Reg.PhyReg.s8, Reg.PhyReg.s9,
                    Reg.PhyReg.s10, Reg.PhyReg.s11)
    );

    private static final ArrayList<Reg.PhyReg> Regs4CallOut = new ArrayList<>(
            Arrays.asList(Reg.PhyReg.s0, Reg.PhyReg.s1, Reg.PhyReg.s2, Reg.PhyReg.s3, Reg.PhyReg.s4,
                    Reg.PhyReg.s5, Reg.PhyReg.s6, Reg.PhyReg.s7, Reg.PhyReg.s8, Reg.PhyReg.s9,
                    Reg.PhyReg.s10, Reg.PhyReg.s11,
                    Reg.PhyReg.a0, Reg.PhyReg.a1, Reg.PhyReg.a2, Reg.PhyReg.a3, Reg.PhyReg.a4,
                    Reg.PhyReg.a5, Reg.PhyReg.a6, Reg.PhyReg.a7,
                    Reg.PhyReg.t1, Reg.PhyReg.t2, Reg.PhyReg.t3, Reg.PhyReg.t4, Reg.PhyReg.t5,
                    Reg.PhyReg.t6)
    );

    private static final HashSet<Reg.PhyReg> unAllocateRegs = new HashSet<>(
            Arrays.asList(Reg.PhyReg.zero, Reg.PhyReg.ra, Reg.PhyReg.sp, Reg.PhyReg.gp, Reg.PhyReg.tp, Reg.PhyReg.t0
            )
    );

    private static final HashSet<Reg.PhyReg> curUsedRegs = new HashSet<>();

    private static class RegNode {
        public final Reg reg;
        public final int degree;

        public RegNode(Reg reg) {
            this.reg = reg;
            this.degree = getDegree(curCG.get(reg));
        }
    }

    private static final PriorityQueue<RegNode> regQueue = new PriorityQueue<>(Comparator.comparingInt(o -> o.degree));


    private static void clear() {
        curUsedRegs.clear();
        curCG.clear();
//        conflictGraph.clear();
        outNodes.clear();
        spillNodes.clear();
    }

    public static void runOnFunc(RiscvFunction func) {
        curFunc = func;
        pass = 0;
        buildConflictGraph();
        MoveInit();
        while (true) {
            clear();
            buildCurCG();
            //标记第几轮循环
//            System.out.println(func.name + " GPR round: " + pass++);
//            System.out.println(func);
            //建立冲突图
            while (!curCG.isEmpty()) {
                SimplifyCoalesce();
                FreezeSpill();
            }
            if (Select()) break;
            ReWrite();
            for (Reg reg : conflictGraph.keySet()) {
                if (reg.preColored) continue;
                reg.phyReg = null;
            }
        }
        Allocator.UsedRegs.get(func.name).addAll(curUsedRegs);
    }

    /**
     * 重写代码：
     * 将所有spillNode中虚拟寄存器存入内存
     * 再在变量使用处从内存中取出
     */
    private static void ReWrite() {
        RegCost.buildSpillCost(spillNodes);
        ArrayList<Reg> spills = RegCost.getSpillArray();
        ArrayList<Reg> newNodes = new ArrayList<>();
        for (Reg reg : spills) {
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
            if (defs.size() == 1 &&
                    defs.get(0) instanceof Li ||
                    defs.get(0) instanceof La ||
                    defs.get(0) instanceof Lui)
            {
                RiscvInstruction def = defs.get(0);
                def.remove();
                for (RiscvInstruction use : uses) {
                    Reg tmp = Reg.getVirtualReg(reg.regType, reg.bits);
                    newNodes.add(tmp);
                    RiscvInstruction defCopy = def.myCopy(use.block);
                    defCopy.replaceUseReg(reg, tmp);
                    use.replaceUseReg(reg, tmp);
                    use.block.insertInstBefore(defCopy, use);
                }
            }
            else {
                for (RiscvInstruction ud : uds) {
                    Reg tmp = Reg.getVirtualReg(reg.regType, reg.bits);
                    newNodes.add(tmp);
                    Address offset = StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                    StackManager.getInstance().blingRegOffset(curFunc.name, tmp.toString(), reg.bits / 8, offset);
                    RiscvInstruction store = new LS(ud.block, tmp, sp, offset, reg.bits == 32 ? LS.LSType.sw : LS.LSType.sd, true, AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                    RiscvInstruction load = new LS(ud.block, tmp, sp, offset, reg.bits == 32 ? LS.LSType.lw : LS.LSType.ld, true, AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                    ud.replaceUseReg(reg, tmp);
                    ud.block.insertInstAfter(store, ud);
                    ud.block.insertInstBefore(load, ud);
                }
                for (RiscvInstruction def : defs) {
                    //如果定义点是lw或者ld指令，则不需要sw保护？
                    //其实应该是溢出点不能重复保护，之后需要对寄存器增加cost属性来进行限制
                    //错误的，定义点也可能会溢出，比如call多个load或者多个arg
                    //非 ssa 在使用点使用新的虚拟寄存器
//                System.out.println("def: " + def);
                    RiscvInstruction store;
                    Reg tmp = Reg.getVirtualReg(reg.regType, reg.bits);
                    newNodes.add(tmp);
                    Address offset = StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                    StackManager.getInstance().blingRegOffset(curFunc.name, tmp.toString(), reg.bits / 8, offset);
                    store = new LS(def.block, tmp, sp, offset, reg.bits == 32 ? LS.LSType.sw : LS.LSType.sd, true, AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                    def.replaceUseReg(reg, tmp);
                    def.block.insertInstAfter(store, def);
                }
                for (RiscvInstruction use : uses) {
                    //在使用点使用新的虚拟寄存器
                    RiscvInstruction load;
                    Reg tmp = Reg.getVirtualReg(reg.regType, reg.bits);
                    newNodes.add(tmp);
                    Address offset = StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                    StackManager.getInstance().blingRegOffset(curFunc.name, tmp.toString(), reg.bits / 8, offset);
                    load = new LS(use.block, tmp, sp, offset, reg.bits == 32 ? LS.LSType.lw : LS.LSType.ld, true, AlignmentAnalysis.AlignType.ALIGN_BYTE_8);
                    use.replaceUseReg(reg, tmp);
                    use.block.insertInstBefore(load, use);
                }
            }
            DeleteNode(reg, conflictGraph);
        }
        LivenessAnalyze.RunOnFunc(curFunc);
        for (Reg reg : newNodes) {
            conflictGraph.put(reg, new LinkedHashSet<>());
        }
        conflictGraph.putIfAbsent(Reg.getPreColoredReg(Reg.PhyReg.sp, 64), new LinkedHashSet<>());
        for (Reg reg : newNodes) {
            for (RiscvInstruction ins : RegUse.get(reg)) {
                for (Reg def : Def.get(ins)) {
                    if (def.regType == Reg.RegType.GPR) {
                        for (Reg out : Out.get(ins)) {
                            if (out.regType == Reg.RegType.GPR) {
                                addConflict(def, out);
                            }
                        }
                    }
                }
                for (Reg o1 : Out.get(ins)) {
                    if (o1.regType == Reg.RegType.GPR) {
                        for (Reg o2 : Out.get(ins)) {
                            if (o2.regType == Reg.RegType.GPR) {
                                addConflict(o1, o2);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 初始化两个队列，并将所有非预着色虚拟寄存器的物理寄存器置空
     **/
    private static void MoveInit() {
        moveList.clear();
        moveNodes.clear();
        //维护moveList
        for (RiscvBlock block : curFunc.blocks) {
            for (RiscvInstruction ins : block.riscvInstructions) {
                if (ins instanceof R2 && ((R2) ins).type == R2.R2Type.mv) {
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
        if (v.preColored) return true;
        ArrayList<Reg.PhyReg> regs2Assign;
        if (!callSaved.contains(v)) regs2Assign = new ArrayList<>(Regs);
        else regs2Assign = new ArrayList<>(Regs4CallOut);
        for (Reg u : curCG.get(v)) {
            regs2Assign.remove(u.phyReg);
        }
        if (!regs2Assign.isEmpty()) {
            v.phyReg = regs2Assign.get(0);
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
                DeleteNode(node, curCG);
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
        curCG.putIfAbsent(v, new LinkedHashSet<>());
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
                ArrayList<Reg> PreRegs = new ArrayList<>(curCG.keySet());
                for (Reg reg : PreRegs) {
                    DeleteNode(reg, curCG);
                }
            }
            return;
        }
//        System.out.println("spill: " + maxReg);
        DeleteNode(maxReg, curCG);
        spillNodes.add(maxReg);
        outNodes.add(maxReg);
    }

    private static void FreezeMoveNode(Reg node) {
//        System.out.println("freeze: " + node);
        LinkedHashSet<R2> freezeMoves = new LinkedHashSet<>();
        LinkedHashSet<Reg> TryFreezeReg = new LinkedHashSet<>();
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

    private static int getDegree(LinkedHashSet<Reg> nodes) {
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
            regQueue.clear();
            for (Reg node : curCG.keySet()) {
                if (!moveNodes.contains(node)) regQueue.add(new RegNode(node));
            }
            while (!regQueue.isEmpty()) {
                RegNode cur = regQueue.remove();
                if (!curCG.containsKey(cur.reg)) continue;
                if (cur.degree != getDegree(curCG.get(cur.reg))) continue;
                Reg node = cur.reg;
//                System.out.println("simplify: " + node + " " + getDegree(curCG.get(node)));
                if (getDegree(curCG.get(node)) < K) {
                    LinkedHashSet<Reg> neighbors = new LinkedHashSet<>(curCG.get(node));
                    DeleteNode(node, curCG);
                    for (Reg neighbor : neighbors) {
                        if (!moveNodes.contains(neighbor)) regQueue.add(new RegNode(neighbor));
                        if (neighbor.equals(node)) System.out.println("error");
                    }
                    if (!node.preColored) outNodes.add(node);
                }
                else break;
            }
            //如果没有可以删除的低度数传送无关节点，尝试删除一条move来合并一对move相关节点
            boolean merge = true;
            while (merge) {
                merge = false;
                for (R2 move : moveList) {
                    Reg r1 = (Reg) move.rs;
                    Reg r2 = (Reg) move.rd;
                    if (CanBeMerged(r1, r2)) {
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

        if (callSaved.contains(oldReg)) callSaved.add(newReg);
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
     */
    public static boolean CanBeMerged(Reg r1, Reg r2) {
        if (r1.equals(r2)) return true;
        if (r1.preColored && r2.preColored)
            if (r1.phyReg == r2.phyReg) return true;
            else return false;
        if (r1.phyReg == Reg.PhyReg.zero || r2.phyReg == Reg.PhyReg.zero) return false;
        if (curCG.get(r1).contains(r2)) return false;
        //合并策略1：如果两个节点的合并节点度数小于K，则可以合并
        LinkedHashSet<Reg> neighbors = new LinkedHashSet<>();
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
    private static void DeleteNode(Reg node, LinkedHashMap<Reg, LinkedHashSet<Reg>> graph) {
        for (Reg neighbor : graph.get(node)) {
            graph.get(neighbor).remove(node);
        }
        graph.remove(node);
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
        conflictGraph.clear();
        for (Reg reg : RegUse.keySet()) {
            if (reg.regType == Reg.RegType.GPR) {
                conflictGraph.put(reg, new LinkedHashSet<>());
            }
        }
        for (RiscvBlock block : curFunc.blocks) {
            for (RiscvInstruction ins : block.riscvInstructions) {
                for (Reg def : Def.get(ins)) {
                    if (def.regType == Reg.RegType.GPR) {
                        for (Reg out : Out.get(ins)) {
                            if (out.regType == Reg.RegType.GPR) {
                                addConflict(def, out);
                            }
                        }
                    }
                }
                for (Reg o1 : Out.get(ins)) {
                    if (o1.regType == Reg.RegType.GPR) {
                        for (Reg o2 : Out.get(ins)) {
                            if (o2.regType == Reg.RegType.GPR) {
                                addConflict(o1, o2);
                            }
                        }
                    }
                }
            }
        }
        //检查conflictGraph是否有自环
        for (Reg reg : conflictGraph.keySet()) {
            //                System.out.println("self conflict: " + reg);
            conflictGraph.get(reg).remove(reg);
        }
//        System.out.println(conflictGraph);
    }

    private static void buildCurCG() {
        //深拷贝删除图
        curCG = new LinkedHashMap<>();
        for (Reg reg : conflictGraph.keySet()) {
            curCG.put(reg, new LinkedHashSet<>(conflictGraph.get(reg)));
        }
    }

    /**
     * 向冲突图中添加冲突
     */
    private static void addConflict(Reg reg1, Reg reg2) {
        if (reg1.equals(reg2)) return;
        if ((reg1).regType != Reg.RegType.GPR || reg2.regType != Reg.RegType.GPR) return;
        conflictGraph.get(reg1).add(reg2);
        conflictGraph.get(reg2).add(reg1);
    }

}
