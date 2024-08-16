package midend.Transform.Function;

// 在一个纯计算递归函数中
// 返回值是某个参数arg，或者每次递归调用的参数都加上了一个Constant
// 则可以将该参数消除，并将递归函数的返回值 + Constant
// 并且函数外的调用，该参数若不为0，则返回 val + arg
public class CountArg2Ret {
}
