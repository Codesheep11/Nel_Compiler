package backend.Opt.Scheduler;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;

import java.util.*;
import java.util.function.BiConsumer;

public class AfterRAScheduler {


    public static void topDownScheduling(RiscvBlock block,
                                         HashMap<RiscvInstruction, Integer> degrees,//某个指令依赖其他指令的度数
                                         HashMap<RiscvInstruction, HashSet<RiscvInstruction>> antiDeps,
                                         HashMap<RiscvInstruction, HashMap<Integer, Reg>> renameMap,
                                         HashMap<RiscvInstruction, Integer> rank) {
        ScheduleState state = new ScheduleState(renameMap);
        List<RiscvInstruction> newList = new ArrayList<>();
        LinkedList<RiscvInstruction> schedulePlane = new LinkedList<>();
        for (RiscvInstruction inst : block.riscvInstructions) {
            if (degrees.getOrDefault(inst, 0) == 0) {
                schedulePlane.add(inst);
            }
        }
        int maxBusyCycles = 200;
        int busyCycle = 0;
        while (newList.size() != block.riscvInstructions.size()) {
            List<RiscvInstruction> newReadyInsts = new ArrayList<>();
            for (int idx = 0; idx < ScheduleModel.issueWidth; ++idx) {
                int cnt = 0;
                boolean success = false;
                schedulePlane.sort((lhs, rhs) -> {
                    int lhsRank = rank.getOrDefault(lhs, 0);
                    int rhsRank = rank.getOrDefault(rhs, 0);
                    return Integer.compare(rhsRank, lhsRank);
                });
                while (cnt < schedulePlane.size()) {
                    RiscvInstruction inst = schedulePlane.poll();
                    ScheduleClass scheduleClass = ScheduleClass.getInstScheduleClass(inst);
                    if (scheduleClass.schedule(state, inst)) {
                        newList.add(inst);
                        busyCycle = 0;
                        //antiDeps(u):所有依赖u的指令集
                        for (RiscvInstruction v : antiDeps.getOrDefault(inst, new HashSet<>())) {
                            int degree = degrees.get(v) - 1;
                            degrees.put(v, degree);
                            if (degree == 0) {
                                newReadyInsts.add(v);
                            }
                        }
                        success = true;
                        break;
                    }
                    schedulePlane.add(inst);
                    cnt++;
                }
                if (!success) {
                    break;
                }
            }
            state.nextCycle();
            busyCycle++;
            if (busyCycle > maxBusyCycles) {
                throw new RuntimeException("Failed to schedule instructions");
            }
            schedulePlane.addAll(newReadyInsts);
        }
        block.riscvInstructions.clear();
        for (RiscvInstruction instr : newList) {
            block.riscvInstructions.addLast(instr);
        }
    }

    public static void postRAScheduleBlock(RiscvBlock block) {
        HashMap<RiscvInstruction, HashMap<Integer, Reg>> renameMap = new HashMap<>();
        HashMap<RiscvInstruction, HashSet<RiscvInstruction>> antiDeps = new HashMap<>();
        HashMap<Reg, ArrayList<RiscvInstruction>> lastTouch = new HashMap<>();
        HashMap<Reg, RiscvInstruction> lastDef = new HashMap<>();
        HashMap<RiscvInstruction, Integer> degrees = new HashMap<>();
        BiConsumer<RiscvInstruction, RiscvInstruction> addDep = (u, v) -> {
            if (u == v) return;
            antiDeps.computeIfAbsent(v, k -> new HashSet<>());
            //System.out.println("add " + v + " dep on " + u);
            if (!antiDeps.get(v).contains(u)) {
                antiDeps.get(v).add(u);
                degrees.put(u, degrees.getOrDefault(u, 0) + 1);
            }
        };
        RiscvInstruction lastSideEffect = null;
        RiscvInstruction lastInOrder = null;
        for (RiscvInstruction inst : block.riscvInstructions) {
            //System.out.println("----------");
            for (int idx = 0; idx < inst.getOperandNum(); idx++) {
                Reg reg = inst.getRegByIdx(idx);
                // TODO: regRenaming
                renameMap.computeIfAbsent(inst, k -> new HashMap<>()).put(idx, reg);
                if (inst.isUse(idx)) {
                    if (lastDef.containsKey(reg)) {
                        //System.out.println("U");
                        addDep.accept(inst, lastDef.get(reg));
                    }
                    lastTouch.computeIfAbsent(reg, k -> new ArrayList<>()).add(inst);
                }
            }
            for (int idx = 0; idx < inst.getOperandNum(); idx++) {
                Reg reg = inst.getRegByIdx(idx);
                if (inst.isDef(idx)) {
                    //System.out.println(inst+" "+idx+" "+reg);
                    for (RiscvInstruction use : lastTouch.getOrDefault(reg, new ArrayList<>())) {
                        //System.out.println("D");
                        addDep.accept(inst, use);
                    }
                    lastTouch.put(reg, new ArrayList<>(Collections.singletonList(inst)));
                    lastDef.put(reg, inst);
                }
            }
            if (lastInOrder != null) {
                //System.out.println("O");
                addDep.accept(inst, lastInOrder);
            }
            if (inst.hasFlag(RiscvInstruction.InstFlag.SideEffect.value)) {
                if (lastSideEffect != null) {
                    //System.out.println("S");
                    addDep.accept(inst, lastSideEffect);
                }
                lastSideEffect = inst;
                if (inst.hasFlag(RiscvInstruction.InstFlag.Call.value
                        | RiscvInstruction.InstFlag.Terminator.value)) {
                    for (RiscvInstruction prevInst : block.riscvInstructions) {
                        if (prevInst == inst) break;
                        //System.out.println("F");
                        addDep.accept(inst, prevInst);
                    }
                    lastInOrder = inst;
                }
            }
        }
        HashMap<RiscvInstruction, Integer> rank = new HashMap<>();
        HashMap<RiscvInstruction, Set<RiscvInstruction>> deps = new HashMap<>();
        HashMap<RiscvInstruction, Integer> deg = new HashMap<>();
        // 理论上倒也合理，毕竟rank是按照谁依赖更多排序的，
        for (RiscvInstruction inst : block.riscvInstructions) {
            for (RiscvInstruction prev : antiDeps.getOrDefault(inst, new HashSet<>())) {
                deps.computeIfAbsent(prev, k -> new HashSet<>());
                if (!deps.get(prev).contains(inst)) {
                    deps.get(prev).add(inst);
                    deg.put(prev, deg.getOrDefault(prev, 0) + 1);
                }
            }
        }
        Queue<RiscvInstruction> q = new LinkedList<>();
        for (RiscvInstruction inst : block.riscvInstructions) {
            if (deg.getOrDefault(inst, 0) == 0) {
                rank.put(inst, 0);
                q.add(inst);
            }
        }
        while (!q.isEmpty()) {
            RiscvInstruction u = q.poll();
            int ru = rank.get(u);
            for (RiscvInstruction v : deps.getOrDefault(u, Collections.emptySet())) {
                rank.put(v, Math.max(rank.getOrDefault(v, 0), ru + 1));
                deg.put(v, deg.get(v) - 1);
                if (deg.get(v) == 0) {
                    q.add(v);
                }
            }
        }
        topDownScheduling(block, degrees, antiDeps, renameMap, rank);
    }

    public static void postRASchedule(RiscvModule module) {
        for (RiscvFunction function : module.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                postRAScheduleBlock(block);
            }
        }
    }
}




