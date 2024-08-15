package midend;

import mir.Module;

public abstract class Pass {

    public abstract boolean run(Module module);
}
