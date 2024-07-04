package midend;

import mir.*;

import java.util.HashMap;

public class LoopCloneInfo extends CloneInfo {

    public Loop src;

    public Loop cpy;

    public LoopCloneInfo() {
        super();
    }

    public LoopCloneInfo(Loop src, Loop cpy) {
        super();
        this.src = src;
        this.cpy = cpy;
        addValueReflect(src.header, cpy.header);
    }

}
