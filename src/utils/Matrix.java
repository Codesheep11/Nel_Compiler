package utils;

public class Matrix {

    public final int[][] matrix;

    public Matrix(int rows, int cols) {
        matrix = new int[rows][cols];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int[] row : matrix) {
            for (int col : row) {
                sb.append(col).append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
