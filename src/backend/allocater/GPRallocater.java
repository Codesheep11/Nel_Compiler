package backend.allocater;

import backend.StackManager;
import backend.operand.Address;
import backend.operand.Reg;
import backend.riscv.*;
import backend.riscv.RiscvInstruction.*;

import java.util.*;

public class GPRallocater {

    public RiscvModule module;

    public RiscvFunction curFunc; //当前分配的函数

    public HashMap<Reg, HashSet<Reg>> conflictGraph;
    public HashMap<Reg, HashSet<Reg>> curCG;

    public ArrayList<Reg> outNodes = new ArrayList<>();
    public HashSet<Reg> spillNodes = new HashSet<>();

    public HashSet<R2> moveList = new HashSet<>();

    public HashSet<Reg> moveNodes = new HashSet<>();

    public int pass;

    /*
    x5 - x31均可分配
     */
    public int K = 26;
    //t0作为临时寄存器，不参与图着色寄存器分配

    private final HashSet<Reg.PhyReg> Regs = new HashSet<>(
            Arrays.asList(Reg.PhyReg.t1, Reg.PhyReg.t2, Reg.PhyReg.t3,
                    Reg.PhyReg.t4, Reg.PhyReg.t5, Reg.PhyReg.t6, Reg.PhyReg.s0, Reg.PhyReg.s1,
                    Reg.PhyReg.s2, Reg.PhyReg.s3, Reg.PhyReg.s4, Reg.PhyReg.s5, Reg.PhyReg.s6,
                    Reg.PhyReg.s7, Reg.PhyReg.s8, Reg.PhyReg.s9, Reg.PhyReg.s10, Reg.PhyReg.s11,
                    Reg.PhyReg.a0, Reg.PhyReg.a1, Reg.PhyReg.a2, Reg.PhyReg.a3, Reg.PhyReg.a4,
                    Reg.PhyReg.a5, Reg.PhyReg.a6, Reg.PhyReg.a7
            )
    );

    private final HashSet<Reg.PhyReg> unAllocateRegs = new HashSet<>(
            Arrays.asList(Reg.PhyReg.zero, Reg.PhyReg.ra, Reg.PhyReg.sp, Reg.PhyReg.gp, Reg.PhyReg.tp, Reg.PhyReg.t0
            )
    );

    private HashSet<Reg.PhyReg> usedRegs = new HashSet<>();

    /**
     * 用于分配寄存器
     * 将所有虚拟寄存器分配到物理寄存器
     *
     * @param module
     */
    public GPRallocater(RiscvModule module) {
        this.module = module;
        for (RiscvFunction func : module.funcList) {
            if (func.isExternal) continue;
            curFunc = func;
            pass = 0;
            while (true) {
                //标记第几轮循环
//                System.out.println(func.name + " GPR round: " + pass++);
//                System.out.println(func);
                //建立冲突图
                moveList = new HashSet<>();
                moveNodes = new HashSet<>();
                buildConflictGraph();
                init();
                while (!curCG.isEmpty()) {
                    SimplifyCoalesce();
                    FreezeSpill();
                }
                usedRegs.clear();
                if (Select())
                    break;
                ReWrite();
            }
            func.usedRegs.addAll(usedRegs);
        }
    }

    /**
     * 重写代码：
     * 将所有spillNode中虚拟寄存器存入内存
     * 再在变量使用处从内存中取出
     */
    public void ReWrite() {
        for (Reg reg : spillNodes) {
//            System.out.println("spill: " + reg);
            ArrayList<RiscvInstruction> contains = new ArrayList<>(reg.use);
            HashSet<RiscvInstruction> uses = new HashSet<>();
            HashSet<RiscvInstruction> defs = new HashSet<>();
            HashSet<RiscvInstruction> uds = new HashSet<>();
            Reg sp = Reg.getPreColoredReg(Reg.PhyReg.sp, 64);
            for (RiscvInstruction ins : contains) {
                if (ins.def.contains(reg) && ins.use.contains(reg)) uds.add(ins);
                else if (ins.def.contains(reg)) defs.add(ins);
                else if (ins.use.contains(reg)) uses.add(ins);
            }
            for (RiscvInstruction ud : uds) {
                Reg tmp = Reg.getVirtualReg(reg.regType, reg.bits);
                Address offset = StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                RiscvInstruction store = new LS(ud.block, tmp, sp, offset, reg.bits == 32 ? LS.LSType.sw : LS.LSType.sd, true);
                RiscvInstruction load = new LS(ud.block, tmp, sp, offset, reg.bits == 32 ? LS.LSType.lw : LS.LSType.ld, true);
                ud.replaceUseReg(reg, tmp);
                ud.block.riscvInstructions.insertAfter(store, ud);
                ud.block.riscvInstructions.insertBefore(load, ud);
            }
            for (RiscvInstruction def : defs) {
                //如果定义点是lw或者ld指令，则不需要sw保护？
                //错误的，定义点也可能会溢出，比如call多个load或者多个arg
                //非 ssa 在使用点使用新的虚拟寄存器
                if (def instanceof LS && ((LS) def).isSpilled && ((LS) def).rs1 == reg) {
                    LS.LSType type = ((LS) def).type;
                    if (type == LS.LSType.ld || type == LS.LSType.lw) {
                        StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                        continue;
                    }
                }
                RiscvInstruction store;
                Reg tmp = Reg.getVirtualReg(reg.regType, reg.bits);
                Address offset = StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                store = new LS(def.block, tmp, sp, offset, reg.bits == 32 ? LS.LSType.sw : LS.LSType.sd, true);
                def.replaceUseReg(reg, tmp);
                def.block.riscvInstructions.insertAfter(store, def);
            }
            for (RiscvInstruction use : uses) {
                //在使用点使用新的虚拟寄存器
                if (use instanceof LS && ((LS) use).isSpilled && ((LS) use).rs1 == reg) {
                    StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                    continue;
                }
                RiscvInstruction load;
                Reg tmp = Reg.getVirtualReg(reg.regType, reg.bits);
                Address offset = StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                load = new LS(use.block, tmp, sp, offset, reg.bits == 32 ? LS.LSType.lw : LS.LSType.ld, true);
                use.replaceUseReg(reg, tmp);
                use.block.riscvInstructions.insertBefore(load, use);
            }
        }
    }

    /**
     * 初始化两个队列，并将所有非预着色虚拟寄存器的物理寄存器置空
     **/
    public void init() {
        outNodes = new ArrayList<>();
        spillNodes = new HashSet<>();
        for (Reg reg : conflictGraph.keySet()) {
            if (reg.preColored) continue;
            reg.phyReg = null;
        }
    }


    /**
     * 试图向虚拟寄存器分配物理寄存器
     * 通过将图中冲突的已分配物理寄存器从寄存器池删除，再判断是否可以分配
     *
     * @param v 尝试分配物理寄存器的虚拟寄存器
     * @return 分配结果
     */
    private boolean AssignPhy(Reg v) {
        if (v.preColored) {
            return true;
        }
        HashSet<Reg.PhyReg> regs2Assign = new HashSet<>(Regs);
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
    public boolean Select() {
        curCG = new HashMap<>();
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
            usedRegs.add(reg.phyReg);
        }
        int outCnt = outNodes.size();
        for (int i = outCnt - 1; i >= 0; i--) {
            Reg node = outNodes.get(i);
            AddNode(node);
            if (AssignPhy(node)) {
                if (spillNodes.contains(node)) {
                    spillNodes.remove(node);
                }
                usedRegs.add(node.phyReg);
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
    private void AddNode(Reg v) {
        curCG.putIfAbsent(v, new HashSet<>());
        for (Reg t : curCG.keySet()) {
            if (t != v && conflictGraph.get(v).contains(t)) {
                curCG.get(v).add(t);
                curCG.get(t).add(v);
            }
        }
    }

    /**
     * 选择一个高度数的节点删除
     */
    public void FreezeSpill() {
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
            HashSet<R2> freezeMoves = new HashSet<>();
            HashSet<Reg> freezeReg = new HashSet<>();
            freezeReg.add(minReg);
            //确定freezeMoves
            for (R2 move : moveList) {
                if (move.rd == minReg || move.rs == minReg) {
                    freezeMoves.add(move);
                    freezeReg.add((Reg) move.rd);
                    freezeReg.add((Reg) move.rs);
                }
            }
            moveList.removeAll(freezeMoves);
            for (R2 move : moveList) {
                if (freezeReg.contains(move.rs) || freezeReg.contains(move.rd)) {
                    freezeReg.remove((Reg) move.rd);
                    freezeReg.remove((Reg) move.rs);
                }
            }
            moveNodes.removeAll(freezeReg);
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

    public int getDegree(HashSet<Reg> nodes) {
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
    public void SimplifyCoalesce() {
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
                        if (!node.preColored)
                            outNodes.add(node);
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
                        if (r1 == r2) {
                            moveNodes.remove(r1);
                            moveList.remove(move);
                            for (R2 m : moveList) {
                                if (m.use.contains(r1) || m.def.contains(r1)) {
                                    moveNodes.add(r1);
                                    break;
                                }
                            }
                            //删除move指令
                            deleteMove(move);
                            merge = true;
                            change = true;
                            break;
                        }
                        if (r1.preColored && r2.preColored) {
                            moveNodes.remove(r1);
                            moveNodes.remove(r2);
                            moveList.remove(move);
                            for (R2 m : moveList) {
                                if (m.use.contains(r1) || m.def.contains(r1)) {
                                    moveNodes.add(r1);
                                }
                                if (m.use.contains(r2) || m.def.contains(r2)) {
                                    moveNodes.add(r2);
                                }
                            }
                            //删除move指令
                            deleteMove(move);
                            merge = true;
                            change = true;
                            break;
                        }
                        Reg oldReg, newReg;
                        if (r1.preColored) {
                            newReg = r1;
                            oldReg = r2;
                        }
                        else {
                            newReg = r2;
                            oldReg = r1;
                        }
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
                        //合并节点
                        newReg.mergeReg(oldReg);
                        //更新moveList和moveNodes
                        moveNodes.remove(oldReg);
                        moveNodes.remove(newReg);
                        moveList.remove(move);
                        boolean flag = false;
                        for (R2 m : moveList) {
                            if (m.use.contains(newReg) || m.def.contains(newReg)) {
                                moveNodes.add(newReg);
                                flag = true;
                                break;
                            }
                        }

                        //删除move指令
                        deleteMove(move);
                        merge = true;
                        change = true;
                        break;
                    }
                }
            }
        }
    }

    public void deleteMove(R2 move) {
        for (Reg reg : move.use) {
            reg.use.remove(move);
        }
        for (Reg reg : move.def) {
            reg.use.remove(move);
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
    public boolean CanBeMerged(Reg r1, Reg r2) {
        if (r1 == r2) return true;
        if (r1.preColored && r2.preColored)
            if (r1.phyReg == r2.phyReg) return true;
            else {
                return false;
//                throw new RuntimeException("can't merge precolored regs");
            }
        if (curCG.get(r1).contains(r2)) return false;
        //合并策略1：如果两个节点的合并节点度数小于K，则可以合并
        HashSet<Reg> neighbors = new HashSet<>();
        neighbors.addAll(curCG.get(r1));
        neighbors.addAll(curCG.get(r2));
        if (getDegree(neighbors) < K) {
            return true;
        }
        //合并策略2：如果r1的每一个邻居t,t与b已经存在冲突或者t是低度数节点，则可以合并
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
    public void DeleteNode(Reg node) {
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
    public void buildConflictGraph() {
        new LivenessAnalyze(curFunc).genInOutSet();
        conflictGraph = new HashMap<>();
        for (RiscvBlock block : curFunc.blocks) {
            for (RiscvInstruction ins : block.riscvInstructions) {
                for (Reg use : ins.use) {
                    if (use.regType == Reg.RegType.GPR)
                        conflictGraph.putIfAbsent(use, new HashSet<>());
                }
                for (Reg def : ins.def) {
                    if (def.regType == Reg.RegType.GPR) {
                        conflictGraph.putIfAbsent(def, new HashSet<>());
                    }
                    else continue;
                    for (Reg out : ins.out) {
                        addConflict(def, out);
                    }
                }
                for (Reg o1 : ins.out) {
                    for (Reg o2 : ins.out) {
                        addConflict(o1, o2);
                    }
                }
            }
        }
        //对于预着色节点，更新冲突图中多bit情况
        for (Reg reg : conflictGraph.keySet()) {
            if (reg.preColored) {
                if (reg.phyReg == Reg.PhyReg.sp || reg.phyReg == Reg.PhyReg.ra || reg.phyReg == Reg.PhyReg.zero)
                    continue;
                int bits = reg.bits;
                Reg reverse = Reg.getPreColoredReg(reg.phyReg, bits == 32 ? 64 : 32);
                if (conflictGraph.containsKey(reverse)) {
                    for (Reg neighbor : conflictGraph.get(reg)) {
                        conflictGraph.get(reverse).add(neighbor);
                        conflictGraph.get(neighbor).add(reverse);
                    }
                }
            }
        }
        //检查conflictGraph是否有自环
        for (Reg reg : conflictGraph.keySet()) {
            if (conflictGraph.get(reg).contains(reg)) {
                conflictGraph.get(reg).remove(reg);
            }
        }
        //维护moveList
        for (RiscvBlock block : curFunc.blocks) {
            for (RiscvInstruction ins : block.riscvInstructions) {
                if (ins instanceof R2 && ((R2) ins).type == R2.R2Type.mv) {
                    moveList.add((R2) ins);
                }
            }
        }
        //深拷贝删除图
        curCG = new HashMap<>();
        for (Reg reg : conflictGraph.keySet()) {
            curCG.put(reg, new HashSet<>(conflictGraph.get(reg)));
        }
        //对于有冲突的mv取消冻结关系
        HashSet<R2> conflictMove = new HashSet<>();
        for (R2 move : moveList) {
            if (conflictGraph.get(move.rd).contains(move.rs)) {
                conflictMove.add(move);
                continue;
            }
            moveNodes.add((Reg) move.rd);
            moveNodes.add((Reg) move.rs);
        }
        moveList.removeAll(conflictMove);
//        System.out.println(conflictGraph);
    }

    /**
     * 向冲突图中添加冲突
     *
     * @param reg1
     * @param reg2
     */
    public void addConflict(Reg reg1, Reg reg2) {
        if (reg1.equals(reg2)) return;
        if ((reg1).regType != Reg.RegType.GPR || reg2.regType != Reg.RegType.GPR) return;
        if (conflictGraph.containsKey(reg1) && conflictGraph.get(reg1).contains(reg2)) return;
        conflictGraph.putIfAbsent(reg1, new HashSet<>());
        conflictGraph.putIfAbsent(reg2, new HashSet<>());
        conflictGraph.get(reg1).add(reg2);
        conflictGraph.get(reg2).add(reg1);
    }

}
