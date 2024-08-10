//package midend.Transform;
//
//import frontend.syntaxChecker.Ast;
//import midend.Analysis.AnalysisManager;
//import midend.Analysis.I32RangeAnalysis;
//import mir.*;
//import utils.Matrix;
//
//import java.util.*;
//import java.util.function.Consumer;
//
//public class ConstraintReduce {
//
//    @FunctionalInterface
//    interface Eval {
//        int eval(int a, int b);
//    }
//
//    public class VarPair {
//        public Value a;
//        public Value b;
//
//        public VarPair(Value a, Value b) {
//            this.a = a;
//            this.b = b;
//        }
//
//        @Override
//        public boolean equals(Object object) {
//            if (this == object) return true;
//            if (object == null || getClass() != object.getClass()) return false;
//            VarPair varPair = (VarPair) object;
//            return this.a == varPair.a && this.b == varPair.b;
//        }
//    }
//
//    public enum KnownRelation {Unknown, True, False}
//
//    public class KnownRelations {
//        KnownRelation equal = KnownRelation.Unknown;
//        KnownRelation lessThan = KnownRelation.Unknown;
//        KnownRelation greaterThan = KnownRelation.Unknown;
//
//        public KnownRelations() {
//        }
//
//        public KnownRelations(KnownRelation equal, KnownRelation lessThan, KnownRelation greaterThan) {
//            this.equal = equal;
//            this.lessThan = lessThan;
//            this.greaterThan = greaterThan;
//        }
//
//        public boolean update(KnownRelations knownRelations) {
//            boolean changed = false;
//            // TODO: check
//            if (this.equal != knownRelations.equal) {
//                this.equal = knownRelations.equal;
//                changed = true;
//            }
//            if (this.lessThan != knownRelations.lessThan) {
//                this.lessThan = knownRelations.lessThan;
//                changed = true;
//            }
//            if (this.greaterThan != knownRelations.greaterThan) {
//                this.greaterThan = knownRelations.greaterThan;
//                changed = true;
//            }
//            return changed;
//        }
//
//        public void merge(KnownRelations knownRelations) {
//            if (this.equal != knownRelations.equal)
//                this.equal = KnownRelation.Unknown;
//            if (this.lessThan != knownRelations.lessThan)
//                this.lessThan = KnownRelation.Unknown;
//            if (this.greaterThan != knownRelations.greaterThan)
//                this.greaterThan = KnownRelation.Unknown;
//        }
//
//        public void infer() {
//            if (this.equal == KnownRelation.True) {
//                this.lessThan = KnownRelation.False;
//                this.greaterThan = KnownRelation.False;
//            }
//            if (this.lessThan == KnownRelation.True) {
//                this.equal = KnownRelation.False;
//                this.greaterThan = KnownRelation.False;
//            }
//            if (this.greaterThan == KnownRelation.True) {
//                this.equal = KnownRelation.False;
//                this.lessThan = KnownRelation.False;
//            }
//
//            if (this.lessThan == KnownRelation.False && this.greaterThan == KnownRelation.False) {
//                this.equal = KnownRelation.True;
//            }
//            if (this.equal == KnownRelation.False && this.greaterThan == KnownRelation.False) {
//                this.lessThan = KnownRelation.True;
//            }
//            if (this.equal == KnownRelation.False && this.lessThan == KnownRelation.False) {
//                this.greaterThan = KnownRelation.True;
//            }
//
//        }
//
//
//        @Override
//        public boolean equals(Object object) {
//            if (this == object) return true;
//            if (object == null || getClass() != object.getClass()) return false;
//            KnownRelations knownRelations = (KnownRelations) object;
//            return this.equal == knownRelations.equal && this.lessThan == knownRelations.lessThan && this.greaterThan == knownRelations.greaterThan;
//        }
//    }
//
//    private Map<VarPair, KnownRelations> relations = new HashMap<>();
//    private static int[][] equalLookupTable = {
//            {0, 0, 0},
//            {0, 1, 2},
//            {0, 2, 0}
//    };
//
//    private static int[][] lessThanLookupTable = {
//            {0, 0, 0},
//            {0, 1, 2},
//            {0, 2, 2}
//    };
//
//    private static int MAX_SIZE = 512;
//
//    private boolean TransitiveClosure(Map<VarPair, KnownRelations> set) {
//        boolean changed = false;
//        LinkedHashSet<Value> _values = new LinkedHashSet<>();
//        for (Map.Entry<VarPair, KnownRelations> entry : set.entrySet()) {
//            VarPair pair = entry.getKey();
//            KnownRelations knownRelations = entry.getValue();
//            if (knownRelations.equal == KnownRelation.True) {
//                _values.add(pair.a);
//                _values.add(pair.b);
//            }
//        }
//        if (_values.size() <= 2)
//            return false;
//        if (_values.size() > MAX_SIZE)
//            return true;
//
//        ArrayList<Value> values = new ArrayList<>(_values);
//        HashMap<Value, Integer> index = new HashMap<>();
//        for (int i = 0; i < values.size(); i++) {
//            index.put(values.get(i), i);
//        }
//
//        // 1 -> equal, 2 -> not equal
//        Matrix eqMat = new Matrix(MAX_SIZE, MAX_SIZE);
//        for (int i = 0; i < values.size(); i++) {
//            eqMat.matrix[i][i] = 1;
//        }
//        for (Map.Entry<VarPair, KnownRelations> entry : set.entrySet()) {
//            VarPair pair = entry.getKey();
//            KnownRelations knownRelations = entry.getValue();
//            if (knownRelations.equal == KnownRelation.True) {
//                int a = index.get(pair.a);
//                int b = index.get(pair.b);
//                eqMat.matrix[a][b] = 1;
//                eqMat.matrix[b][a] = 1;
//            } else if (knownRelations.equal == KnownRelation.False) {
//                int a = index.get(pair.a);
//                int b = index.get(pair.b);
//                eqMat.matrix[a][b] = 2;
//                eqMat.matrix[b][a] = 2;
//            }
//        }
//        for (int i = 0; i < values.size(); i++) {
//            if (values.get(i) instanceof Constant.ConstantInt constant) {
//                for (int j = i + 1; j < values.size(); j++) {
//                    if (values.get(j) instanceof Constant.ConstantInt constant2) {
//                        if (constant.getIntValue() == constant2.getIntValue()) {
//                            eqMat.matrix[i][j] = 1;
//                            eqMat.matrix[j][i] = 1;
//                        } else {
//                            eqMat.matrix[i][j] = 2;
//                            eqMat.matrix[j][i] = 2;
//                        }
//                    }
//                }
//            }
//        }
//        changed |= floyd(eqMat, (a, b) -> equalLookupTable[a][b]);
//
//        // 1 -> less equal than, 2 -> less than
//        Matrix letMat = new Matrix(MAX_SIZE, MAX_SIZE);
//        for (int i = 0; i < values.size(); i++) {
//            eqMat.matrix[i][i] = 1;
//        }
//        for (Map.Entry<VarPair, KnownRelations> entry : set.entrySet()) {
//            VarPair pair = entry.getKey();
//            KnownRelations knownRelations = entry.getValue();
//            if (knownRelations.lessThan == KnownRelation.True) {
//                int a = index.get(pair.a);
//                int b = index.get(pair.b);
//                letMat.matrix[a][b] = 1;
//                letMat.matrix[b][a] = 2;
//            } else if (knownRelations.lessThan == KnownRelation.False) {
//                int a = index.get(pair.a);
//                int b = index.get(pair.b);
//                letMat.matrix[a][b] = 2;
//                letMat.matrix[b][a] = 1;
//            }
//        }
//        for (int i = 0; i < values.size(); i++) {
//            if (values.get(i) instanceof Constant.ConstantInt constant) {
//                for (int j = i + 1; j < values.size(); j++) {
//                    if (values.get(j) instanceof Constant.ConstantInt constant2) {
//                        if (constant.getIntValue() <= constant2.getIntValue()) {
//                            letMat.matrix[i][j] = 1;
//                        }
//                        if (constant.getIntValue() >= constant2.getIntValue()) {
//                            letMat.matrix[j][i] = 1;
//                        }
//                        if (constant.getIntValue() < constant2.getIntValue()) {
//                            letMat.matrix[i][j] = 2;
//                        }
//                        if (constant.getIntValue() > constant2.getIntValue()) {
//                            letMat.matrix[j][i] = 2;
//                        }
//                    }
//                }
//            }
//        }
//        changed |= floyd(letMat, (a, b) -> lessThanLookupTable[a][b]);
//
//        if (!changed)
//            return false;
//        changed = false;
//        for (int i = 0; i < values.size(); i++) {
//            for (int j = i + 1; j < values.size(); j++) {
//                if (eqMat.matrix[i][j] > 0 || letMat.matrix[i][j] > 0) {
//                    Value vi = values.get(i);
//                    Value vj = values.get(j);
//                    if (vi instanceof Constant.ConstantInt && vj instanceof Constant.ConstantInt) {
//                        continue;
//                    }
//                    VarPair pair = new VarPair(vi, vj);
//                    KnownRelations knownRelations = new KnownRelations();
//                    if (eqMat.matrix[i][j] == 1 || eqMat.matrix[j][i] == 1) {
//                        knownRelations.equal = KnownRelation.True;
//                    } else if (eqMat.matrix[i][j] == 2 || eqMat.matrix[j][i] == 2) {
//                        knownRelations.equal = KnownRelation.False;
//                    }
//                    if (letMat.matrix[i][j] == 2) {
//                        knownRelations.lessThan = KnownRelation.True;
//                    } else if (letMat.matrix[j][i] == 1) {
//                        knownRelations.lessThan = KnownRelation.False;
//                    }
//                    if (letMat.matrix[j][i] == 2) {
//                        knownRelations.greaterThan = KnownRelation.True;
//                    } else if (letMat.matrix[i][j] == 1) {
//                        knownRelations.greaterThan = KnownRelation.False;
//                    }
//
//                    changed |= set.get(pair).update(knownRelations);
//                    set.get(pair).infer();
//                }
//            }
//        }
//        return changed;
//    }
//
//    private static boolean floyd(Matrix matrix, Eval evaler) {
//        int n = matrix.matrix.length;
//        boolean ret = false;
//        boolean changed = false;
//        do {
//            changed = false;
//            for (int k = 0; k < n; k++) {
//                for (int i = 0; i < n; i++) {
//                    if (matrix.matrix[i][k] == 0)
//                        continue;
//                    for (int j = 0; j < n; j++) {
//                        if (matrix.matrix[k][j] == 0)
//                            continue;
//                        int newValue = evaler.eval(matrix.matrix[i][k], matrix.matrix[k][j]);
//                        if (matrix.matrix[i][j] == 0 || matrix.matrix[i][j] > newValue) {
//                            matrix.matrix[i][j] = newValue;
//                            changed = true;
//                            ret = true;
//                        }
//                    }
//                }
//            }
//        } while (changed);
//        return ret;
//    }
//
//    private static Map<VarPair, KnownRelations> mergeSets(BasicBlock cur, Map<BasicBlock, Map<VarPair, KnownRelations>> sets) {
//        if (sets.isEmpty())
//            return new HashMap<>();
//        Map<VarPair, KnownRelations> relations = new HashMap<>();
//
//        BasicBlock ancestor = null;
//        for (BasicBlock block : sets.keySet()) {
//            if (ancestor == null) {
//                ancestor = block;
//            } else {
//                ancestor = AnalysisManager.getLCA(ancestor, block);
//            }
//        }
//        if (AnalysisManager.strictlyDominate(ancestor, cur)) {
//            for (BasicBlock block : sets.keySet()) {
//                if (block != ancestor && !AnalysisManager.dominate(cur, block)) {
//                    ancestor = null;
//                    break;
//                }
//            }
//        }
//
//        Map<VarPair, KnownRelations> minimal = null;
//        if (!sets.containsKey(ancestor)) {
//            for (Map<VarPair, KnownRelations> set : sets.values()) {
//                if (minimal == null || set.size() < minimal.size()) {
//                    minimal = set;
//                }
//            }
//        }
//        Map<VarPair, KnownRelations> res = new HashMap<>(minimal);
//
//        ArrayList<VarPair> toRemove = new ArrayList<>();
//        for (Map<VarPair, KnownRelations> set : sets.values()) {
//            if (set == minimal)
//                continue;
//            for (Map.Entry<VarPair, KnownRelations> entry : set.entrySet()) {
//                VarPair pair = entry.getKey();
//                if (res.containsKey(pair)) {
//                    KnownRelations knownRelations = res.get(pair);
//                    knownRelations.merge(entry.getValue());
//                }
//            }
//            if (!sets.containsKey(ancestor)) {
//                toRemove.clear();
//                for (Map.Entry<VarPair, KnownRelations> entry : res.entrySet()) {
//                    VarPair pair = entry.getKey();
//                    if (!set.containsKey(pair)) {
//                        toRemove.add(pair);
//                    }
//                }
//                toRemove.forEach(res::remove);
//            }
//        }
//        return res;
//    }
//
//    private static void addEdge(Map<Value, Boolean> edges, Value cond, boolean value) {
//        // TODO: check
//        if (cond instanceof Constant.ConstantInt constant) {
//            if (constant.getIntValue() == 0) {
//                return;
//            }
//            value = !value;
//        }
//        if (edges.containsKey(cond)) {
//            if (edges.get(cond) != value) {
//                edges.remove(cond);
//            }
//        } else {
//            edges.put(cond, value);
//        }
//    }
//
//    public boolean run(Function function) {
//        var cfg = AnalysisManager.getCFG(function);
//        var dg = AnalysisManager.getDG(function);
//
//        Map<BasicBlock, Map<BasicBlock, Map<Value, Boolean>>> edges = new HashMap<>();
//        Map<BasicBlock, Map<BasicBlock, Map<VarPair, KnownRelations>>> edgeSets = new HashMap<>();
//        Map<BasicBlock, Map<VarPair, KnownRelations>> conditions = new HashMap<>();
//
//        int maxAdditionalFacts = 32;
//        int additionalFacts = 0;
//
//        for (BasicBlock block : function.getBlocks()) {
//            Map<VarPair, KnownRelations> condSet = conditions.computeIfAbsent(block, k -> new HashMap<>());
//            for (Instruction inst : block.getInstructions()) {
//                if (inst.getType() == Type.BasicType.I32_TYPE && additionalFacts < maxAdditionalFacts) {
//                    I32RangeAnalysis.I32Range range =
//                            AnalysisManager.getValueRange(inst, inst.getParentBlock());
//                    if (range.getMinValue() != Integer.MIN_VALUE) {
//                        if (++additionalFacts <= maxAdditionalFacts) {
//                            add2Set(condSet, new VarPair(inst, Constant.ConstantInt.get(range.getMinValue())),
//                                    new KnownRelations(KnownRelation.Unknown, KnownRelation.False, KnownRelation.Unknown));
//                        }
//                    }
//                    if (range.getMaxValue() != Integer.MAX_VALUE) {
//                        if (++additionalFacts <= maxAdditionalFacts) {
//                            add2Set(condSet, new VarPair(inst, Constant.ConstantInt.get(range.getMaxValue())),
//                                    new KnownRelations(KnownRelation.Unknown, KnownRelation.Unknown, KnownRelation.False));
//                        }
//                    }
//                }
//                if (Objects.requireNonNull(inst.getInstType()) == Instruction.InstType.BRANCH) {
//                    Instruction.Branch branch = (Instruction.Branch) inst;
//                    Value cond = branch.getCond();
//                    if (cond instanceof Constant) continue;
//
//                    var trueEdges = edges.computeIfAbsent(branch.getThenBlock(), k -> new HashMap<>());
//                    var falseEdges = edges.computeIfAbsent(branch.getElseBlock(), k -> new HashMap<>());
//
//                    addEdge(trueEdges.computeIfAbsent(block, k -> new HashMap<>()), cond, true);
//                    addEdge(falseEdges.computeIfAbsent(block, k -> new HashMap<>()), cond, false);
//                }
//            }
//        }
//
//        HashSet<BasicBlock> inQueue = new HashSet<>();
//        final int maxEnqueueCount = 60;
//        HashMap<BasicBlock, Integer> enqueueCount = new HashMap<>();
//        Queue<BasicBlock> queue = new LinkedList<>();
//
//        var updateBlock = new Consumer<BasicBlock>() {
//            @Override
//            public void accept(BasicBlock block) {
//                var src = conditions.computeIfAbsent(block, k -> new HashMap<>());
//                if (src.isEmpty()) return;
//                for (BasicBlock succ : cfg.getSuccBlocks(block)) {
//                    var dst = edgeSets.computeIfAbsent(succ, k -> new HashMap<>());
//                    if (updateEdgeSet(src, dst.computeIfAbsent(block, k -> new HashMap<>())) &&
//                            inQueue.add(succ) && enqueueCount.computeIfAbsent(succ, k -> 0) + 1 < maxEnqueueCount) {
//                        queue.add(succ);
//                        enqueueCount.compute(succ, (k, v) -> v + 1);
//                    }
//                }
//            }
//        };
//
//        for (BasicBlock block : function.getBlocks()) {
//            var edgeSet = edgeSets.computeIfAbsent(block, k -> new HashMap<>());
//            for (var entry : edges.get(block).entrySet()) {
//                var set = edgeSet.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
//                for (var entry2 : entry.getValue().entrySet()) {
//                    // TODO: check
//                    add2Set(set, new VarPair(entry2.getKey(), Constant.ConstantInt.get(1)),
//                            new KnownRelations(KnownRelation.Unknown, KnownRelation.Unknown, KnownRelation.Unknown));
//                }
//            }
//        }
//    }
//
//    private boolean updateEdgeSet(Map<VarPair, KnownRelations> src, Map<VarPair, KnownRelations> dst) {
//        boolean changed = false;
//        for (Map.Entry<VarPair, KnownRelations> entry : src.entrySet()) {
//            VarPair pair = entry.getKey();
//            KnownRelations knownRelations = entry.getValue();
//            changed |= add2Set(dst, pair, knownRelations);
//        }
//        return changed;
//    }
//
//    private void add2Set(Map<VarPair, KnownRelations> set, VarPair pair, KnownRelations knownRelations) {
//        // TODO: check
//        if (set.containsKey(pair)) {
//            KnownRelations knownRelations1 = set.get(pair);
//            knownRelations1.update(knownRelations);
//        } else {
//            set.put(pair, knownRelations);
//        }
//    }
//}
//
//
