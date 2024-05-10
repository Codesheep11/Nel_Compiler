package backend.allocater;

import backend.StackManager;
import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.*;
import backend.riscv.riscvInstruction.*;

import java.util.*;

public class FPRallocater {

    public riscvModule module;

    public riscvFunction curFunc; //当前分配的函数

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
            Arrays.asList(Reg.PhyReg.ft0, Reg.PhyReg.ft1, Reg.PhyReg.ft2, Reg.PhyReg.ft3, Reg.PhyReg.ft4, Reg.PhyReg.ft5, Reg.PhyReg.ft6, Reg.PhyReg.ft7,
                    Reg.PhyReg.fs0, Reg.PhyReg.fs1,
                    Reg.PhyReg.fa0, Reg.PhyReg.fa1, Reg.PhyReg.fa2, Reg.PhyReg.fa3, Reg.PhyReg.fa4, Reg.PhyReg.fa5, Reg.PhyReg.fa6, Reg.PhyReg.fa7,
                    Reg.PhyReg.fs2, Reg.PhyReg.fs3, Reg.PhyReg.fs4, Reg.PhyReg.fs5, Reg.PhyReg.fs6, Reg.PhyReg.fs7, Reg.PhyReg.fs8, Reg.PhyReg.fs9, Reg.PhyReg.fs10, Reg.PhyReg.fs11,
                    Reg.PhyReg.ft8, Reg.PhyReg.ft9, Reg.PhyReg.ft10, Reg.PhyReg.ft11
            )
    );

    private HashSet<Reg.PhyReg> usedRegs = new HashSet<>();

    /**
     * 用于分配寄存器
     * 将所有虚拟寄存器分配到物理寄存器
     *
     * @param module
     */
    public FPRallocater(riscvModule module) {
        this.module = module;
        for (riscvFunction func : module.funcList) {
            if (func.isExternal) continue;
            curFunc = func;
            pass = 0;
            while (true) {
                //标记第几轮循环
//                System.out.println(func.name + " FPR round: " + pass++);
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
//            System.out.println("rewrite:" + reg);
            ArrayList<riscvInstruction> contains = new ArrayList<>(reg.use);
            HashSet<riscvInstruction> uses = new HashSet<>();
            HashSet<riscvInstruction> defs = new HashSet<>();
            Reg sp = Reg.getPreColoredReg(Reg.PhyReg.sp, 64);
            for (riscvInstruction ins : contains) {
                if (ins.def.contains(reg)) defs.add(ins);
                else uses.add(ins);
            }
            for (riscvInstruction def : defs) {
                if (defs.size() > 1) throw new RuntimeException("rewrite error");
                //如果定义点是lw或者ld指令，则不需要sw保护？
                //错误的，定义点也可能会溢出，比如call多个load或者多个arg
                if (def instanceof LS && ((LS) def).isSpilled && ((LS) def).rs1 == reg) {
                    LS.LSType type = ((LS) def).type;
                    if (type == LS.LSType.flw) {
                        StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                        continue;
                    }
                }
                riscvInstruction store;
                Address offset = StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                store = new LS(def.block, reg, sp, offset, LS.LSType.fsw, true);
                def.block.riscvInstructions.insertAfter(store, def);
            }
            for (riscvInstruction use : uses) {
                //在使用点使用新的虚拟寄存器
                riscvInstruction load;
                Reg tmp = Reg.getVirtualReg(reg.regType, reg.bits);
                Address offset = StackManager.getInstance().getRegOffset(curFunc.name, reg.toString(), reg.bits / 8);
                load = new LS(use.block, tmp, sp, offset, LS.LSType.flw, true);
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
                if (freezeReg.contains(move.rd) || freezeReg.contains(move.rd)) {
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
//        regs.removeAll(unAllocateRegs);
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
                            move.remove();
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
                            move.remove();
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
                        for (R2 m : moveList) {
                            if (m.use.contains(newReg) || m.def.contains(newReg)) {
                                moveNodes.add(newReg);
                                break;
                            }
                        }
                        //删除move指令
                        move.remove();
                        merge = true;
                        change = true;
                        break;
                    }
                }
            }
        }
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
            else throw new RuntimeException("can't merge precolored regs");
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
        for (riscvBlock block : curFunc.riscvBlocks) {
            for (riscvInstruction ins : block.riscvInstructions) {
                for (Reg use : ins.use) {
                    if (use.regType == Reg.RegType.FPR)
                        conflictGraph.putIfAbsent(use, new HashSet<>());
                }
                for (Reg def : ins.def) {
                    if (def.regType == Reg.RegType.FPR) {
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
        for (riscvBlock block : curFunc.riscvBlocks) {
            for (riscvInstruction ins : block.riscvInstructions) {
                if (ins instanceof R2 && ((R2) ins).type == R2.R2Type.fmv) {
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
    }

    /**
     * 向冲突图中添加冲突
     *
     * @param reg1
     * @param reg2
     */
    public void addConflict(Reg reg1, Reg reg2) {
        if (reg1.equals(reg2)) return;
        if ((reg1).regType != Reg.RegType.FPR || reg2.regType != Reg.RegType.FPR) return;
        if (conflictGraph.containsKey(reg1) && conflictGraph.get(reg1).contains(reg2)) return;
        conflictGraph.putIfAbsent(reg1, new HashSet<>());
        conflictGraph.putIfAbsent(reg2, new HashSet<>());
        conflictGraph.get(reg1).add(reg2);
        conflictGraph.get(reg2).add(reg1);
    }

}
