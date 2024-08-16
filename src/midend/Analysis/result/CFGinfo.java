package midend.Analysis.result;

import mir.BasicBlock;
import mir.Function;

import java.util.ArrayList;
import java.util.HashMap;

public final class CFGinfo {
    private final HashMap<BasicBlock, _CFG_Block_Info> map;

    public CFGinfo(Function function) {
        map = new HashMap<>();
        for (BasicBlock block : function.getBlocks()) {
            map.put(block, new _CFG_Block_Info(block));
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private final class _CFG_Block_Info {
        public _CFG_Block_Info(BasicBlock block) {
            this.block = block;
            this.predBlocks = new ArrayList<>();
            this.succBlocks = new ArrayList<>();
        }
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final BasicBlock block;
        final ArrayList <BasicBlock> predBlocks;
        final ArrayList <BasicBlock> succBlocks;
    }
    public void declareBlock(BasicBlock block) {
        if (map.containsKey(block)) return;
        map.put(block, new _CFG_Block_Info(block));
    }

    public void addPredBlock(BasicBlock block, BasicBlock predBlock) {
        map.get(block).predBlocks.add(predBlock);
    }

    public void addSuccBlock(BasicBlock block, BasicBlock succBlock) {
        map.get(block).succBlocks.add(succBlock);
    }

    public ArrayList<BasicBlock> getPredBlocks(BasicBlock block) {
        return map.get(block).predBlocks;
    }

    public ArrayList<BasicBlock> getSuccBlocks(BasicBlock block) {
        return map.get(block).succBlocks;
    }

    @SuppressWarnings("unused")
    public void printCFG() {
        for (BasicBlock block : map.keySet()) {
            System.out.println("Block: " + block.getLabel());
            System.out.println("Predecessors: ");
            for (BasicBlock pred : map.get(block).predBlocks) {
                System.out.println(pred.getLabel());
            }
            System.out.println("Successors: ");
            for (BasicBlock succ : map.get(block).succBlocks) {
                System.out.println(succ.getLabel());
            }
        }
    }
}
