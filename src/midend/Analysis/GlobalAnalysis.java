package midend.Analysis;

import mir.*;
import mir.Module;

//识别出那些所有store都在load之前的全局变量
//将其和所有没有store的全局变量一起视为“onlyRead”
//好处是进一步加强函数分析的结果 给LUT和TCL提供更多的信息
public class GlobalAnalysis {
    public static void run(Module module) {
        for (GlobalVariable gv : module.getGlobalValues()) {

        }
    }
}
