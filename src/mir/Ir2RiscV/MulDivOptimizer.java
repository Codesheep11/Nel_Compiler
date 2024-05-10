package mir.Ir2RiscV;

import mir.Constant;
import mir.Value;

public class MulDivOptimizer {
    // 乘除法优化器
    // 乘法指令的代价,通过这个比较选择原地乘法还是选择拆开
    public static int mulCost = 10;
    public static void opMul(Value value1,Value value2)
    {
       if(!(value1 instanceof Constant)&&!(value2 instanceof Constant) )
       {
           // 两个都不是常数,无法进行乘法优化
           // 默认两个都是常数的话就假定前面已经优化好了
           // 只有一个为常数的情况下才优化
       } else if (value1 instanceof Constant) {

       }else {

       }
    }
}
