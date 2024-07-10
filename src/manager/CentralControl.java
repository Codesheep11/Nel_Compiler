package manager;

public class CentralControl {
    // 用于控制浮点数精度
    public static double epsilon = 1e-6;
    // 用于控制是否开启各种优化
    public static boolean _GVN_OPEN = true;

    public static boolean _GCM_OPEN = true;

    public static boolean _DCD_OPEN = true;

    public static boolean _FUNC_INLINE_OPEN = true;

    public static boolean _LUS_OPEN = true;
}
