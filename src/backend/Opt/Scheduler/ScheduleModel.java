package backend.Opt.Scheduler;

public class ScheduleModel {
    public static boolean enablePostRAScheduling = true;
    public static boolean hasRegRenaming = false;
    public static boolean hasMacroFusion = false;
    public static final int issueWidth = 2;// 流水线宽度
    public static boolean outOfOrder = false;
    public static boolean hardwarePrefetch = true;
    public static int maxDataStreams = 8;
    public static int maxStrideByBytes = 256;
    private static final boolean preRA = false;
    private static final boolean postSA = true;
    private static final boolean inSSAForm = false;

}
