import manager.Arg;
import manager.Manager;

public class Compiler {

    public static void main(String[] args) {
        Manager manager = new Manager(Arg.parse(args));
        manager.run();
    }
}