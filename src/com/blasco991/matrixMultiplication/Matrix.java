package com.blasco991.matrixMultiplication;

import java.util.Arrays;
import java.util.Formatter;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Matrix {

    final static int K = 10;
    private final static int M = 1000;

    final double[][] elements;
    private final static Random random = ThreadLocalRandom.current();

    Matrix(double[][] elements) {
        this.elements = elements;
    }

    Matrix(int m, int n) {
        if (m <= 0 || n <= 0)
            throw new IllegalArgumentException("dimensions should be positive");

        this.elements = new double[m][n];
        IntStream.range(0, m).parallel().forEach(
                i -> Arrays.parallelSetAll(this.elements[i], j -> ThreadLocalRandom.current().nextDouble() * 100.0 - 50.0)
        );

    }

    private Matrix(Matrix left, Matrix right) {
        int m = left.getM();
        int p = left.getN();
        if (p != right.getM())
            throw new IllegalArgumentException("dimensions do not march");

        int n = right.getN();

        this.elements = new double[m][n];
        for (int x = 0; x < n; x++)
            for (int y = 0; y < m; y++) {
                double sum = 0.0;
                for (int k = 0; k < p; k++)
                    sum += left.elements[y][k] * right.elements[k][x];
                this.elements[y][x] = sum;
            }

    }

    private int getM() {
        return elements.length;
    }

    int getN() {
        return elements[0].length;
    }

    Matrix times(Matrix other) {
        return new Matrix(this, other);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int n = getN(), m = getM(), y = 0; y < m; y++) {
            for (int x = 0; x < n; x++)
                sb.append(String.format("%10.2f", elements[y][x]));
            sb.append('\n');
        }
        return sb.toString();
    }

    static void randomMultiplication() {
        // random dimensions between 20 and M, inclusive
        int m = random.nextInt(Matrix.M) + 20;
        int p = random.nextInt(Matrix.M) + 20;
        int n = random.nextInt(Matrix.M) + 20;

        Matrix m1 = new Matrix(m, p);
        Matrix m2 = new Matrix(p, n);
        m1.times(m2);
    }

    static void parallelRandomMultiplication() {
        int m = random.nextInt(Matrix.M) + 20;
        int p = random.nextInt(Matrix.M) + 20;
        int n = random.nextInt(Matrix.M) + 20;
        Matrix m1 = new Matrix(m, p);
        Matrix m2 = new Matrix(p, n);
        m1.parallelMultiply(m2);
    }

    Matrix parallelMultiply(Matrix right) {
        double[][] result = new double[getM()][right.getN()];
        int k = Runtime.getRuntime().availableProcessors() + 1;
        ExecutorService executor = Executors.newCachedThreadPool();
        IntStream.rangeClosed(0, k).forEach(i -> executor.execute(new Worker(i, right, result)));
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return new Matrix(result);
    }

    static void parallelStreamRandomMultiplication() {
        int m = random.nextInt(Matrix.M) + 20;
        int p = random.nextInt(Matrix.M) + 20;
        int n = random.nextInt(Matrix.M) + 20;
        Matrix m1 = new Matrix(m, p);
        Matrix m2 = new Matrix(p, n);
        m1.parallelStreamMultiply(m2);
    }

    public String toStringStream() {
        return Arrays.stream(elements).map(Arrays::toString)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    Matrix parallelStreamMultiply(Matrix right) {

        double[][] result = new double[getM()][right.getN()];

        IntStream.range(0, right.getN()).forEach(
                (int q) -> IntStream.range(0, getM()).forEach(
                        (int i) -> IntStream.range(0, getN()).forEach(
                                (int j) -> result[i][q] += elements[i][j] * right.elements[j][q]
                        )
                )
        );
        return new Matrix(result);
    }

    /**
     * This is gonna fail pretty much always
     */
    boolean arraysDeepEquals(Matrix other) {
        return Arrays.deepEquals(this.elements, other.elements);
    }

    /**
     * This is gonna fail pretty much always
     */
    boolean equals(Matrix other) {
        int equals = 1;
        for (int i = 0; i < this.elements.length; i++)
            equals *= Arrays.equals(this.elements[i], other.elements[i]) ? 1 : 0;
        return equals == 1;
    }

    /**
     * Testing only with 8 decimal precision
     */
    boolean deepEquals(Matrix right) {
        int equals = 1;
        Formatter formatter = new Formatter();
        for (int i = 0; i < this.elements.length; i++) {
            for (int j = 0; j < this.elements.length; j++) {
                equals *= formatter.format("\"%.8f\"", this.elements[i][j]).equals(
                        formatter.format("\"%.8f\"", right.elements[i][j])) ? 1 : 0;
            }
        }
        return equals == 1;
    }

    static UnaryOperator<double[][]> transposeParallel() {
        return (double[][] col) -> IntStream.range(0, col[0].length).parallel().mapToObj(
                (int row) -> IntStream.range(0, col.length).parallel().mapToDouble(
                        (int c) -> col[c][row])
                        .toArray()).toArray(double[][]::new);
    }

    static UnaryOperator<double[][]> transpose() {
        return (double[][] col) -> IntStream.range(0, col[0].length).mapToObj(
                (int row) -> IntStream.range(0, col.length).mapToDouble(
                        (int c) -> col[c][row])
                        .toArray()).toArray(double[][]::new);
    }

    private class Worker implements Runnable {

        private final int id;
        private final Matrix right;
        private final double[][] result;
        private final int k;

        Worker(int id, Matrix right, double[][] result) {
            this.k = Runtime.getRuntime().availableProcessors();
            this.result = result;
            this.right = right;
            this.id = id;
        }

        @Override
        public void run() {
            IntStream.range(0, right.getN()).filter(q -> q % k == id).forEach(
                    (int q) -> IntStream.range(0, getM()).forEach(
                            (int i) -> IntStream.range(0, getN()).forEach(
                                    (int j) -> result[i][q] += elements[i][j] * right.elements[j][q]
                            )
                    )
            );
        }

    }

}