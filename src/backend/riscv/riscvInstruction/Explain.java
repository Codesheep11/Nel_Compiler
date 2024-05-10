package backend.riscv.riscvInstruction;

import backend.riscv.riscvBlock;

public class Explain extends riscvInstruction {

    /*
    所翻译的原本的llvm,方便debug
    * */

    private final String content;

    public Explain(riscvBlock block,String content) {
        super(block);
        this.content = content;
    }

    @Override
    public String toString() {
//        return "";
        return "#   LLVM: " + content;
    }
}
