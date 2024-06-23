package backend.riscv.RiscvInstruction;

import backend.riscv.RiscvBlock;

public class Explain extends RiscvInstruction {

    /*
    所翻译的原本的llvm,方便debug
    * */

    private final String content;

    public Explain(RiscvBlock block, String content) {
        super(block);
        this.content = content;
    }

    @Override
    public String toString() {
//        return "";
        return "#   LLVM: " + content;
    }
}
