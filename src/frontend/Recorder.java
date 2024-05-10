package frontend;

import mir.Instruction;

import java.util.ArrayList;

/**
 * for Code backfill
 */
public class Recorder {
    public Recorder() {}

    private ArrayList<Mark> marks = new ArrayList<>();
    public ArrayList<Instruction.Jump> jumps = new ArrayList<>();

    public enum Mark {
        BREAK("break"),
        CONTINUE("continue");

        private final String name;
        Mark(String name) {
            this.name = name;
        }
    }
    public void record(Mark mark, Instruction.Jump jump) {
        marks.add(mark);
        jumps.add(jump);
    }

    public ArrayList<Mark> getMarks() {
        return marks;
    }

    public ArrayList<Instruction.Jump> getJumps() {
        return jumps;
    }
}
