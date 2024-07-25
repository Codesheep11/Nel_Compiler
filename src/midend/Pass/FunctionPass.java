package midend.Pass;

import mir.Module;

/**
 * All FunctionPass execute on EACH function in the program INDEPENDENT of all of the other functions in the program.
 *
 *
 * To be explicit, FunctionPass subclasses are not allowed to:
 * Inspect or modify a Function other than the one currently being processed.
 * Add or remove Functions from the current Module.
 * Add or remove global variables from the current Module.
 * Maintain state across invocations of runOnFunction (including global data).
 *
 */
public abstract class FunctionPass extends Pass {
    public abstract Boolean doInitialization(Module module);

    public abstract Boolean runOnFunc(Module module);

    public abstract Boolean doFinalization(Module module);


}
