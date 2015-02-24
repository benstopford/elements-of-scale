package com.dlab.scale.sequential;

import java.util.*;

import static java.lang.System.*;

/**
 * Best run with the following args:
 * -Xms4g -Xmx4g -verbose:gc -XX:+PrintGCTimeStamps -XX:+PrintGCDetails
 * <p/>
 * Sample results from 2.3 GHz Intel Core i7, 8 GB 1600 MHz DDR3, APPLE SSD SM256E Journaled HFS+
 * <p/>
 * Running with int[]
 * 1,000,000 Sequential reads were 57 times faster than random [1,980,198,020 int/s vs 34,221,964 int/s]
 * 2,000,000 Sequential reads were 135 times faster than random [2,380,952,381 int/s vs 17,542,167 int/s]
 * 4,000,000 Sequential reads were 168 times faster than random [2,339,181,287 int/s vs 13,857,373 int/s]
 * 8,000,000 Sequential reads were 188 times faster than random [2,335,766,423 int/s vs 12,382,521 int/s]
 * 16,000,000 Sequential reads were 234 times faster than random [2,836,879,433 int/s vs 12,077,906 int/s]
 * 32,000,000 Sequential reads were 250 times faster than random [2,823,861,631 int/s vs 11,290,533 int/s]
 * <p/>
 * Running with Integer[]
 * 1,000,000 Sequential reads were 4 times faster than random [96,061,479 int/s vs 24,000,960 int/s]
 * 2,000,000 Sequential reads were 5 times faster than random [98,260,784 int/s vs 17,686,281 int/s]
 * 4,000,000 Sequential reads were 5 times faster than random [77,629,204 int/s vs 13,315,712 int/s]
 * 8,000,000 Sequential reads were 5 times faster than random [65,354,138 int/s vs 12,033,387 int/s]
 * 16,000,000 Sequential reads were 4 times faster than random [55,353,745 int/s vs 11,369,717 int/s]
 * 32,000,000 Sequential reads were 4 times faster than random [50,217,899 int/s vs 10,915,828 int/s]
 */
public class SeqVsRandMemAccess {
    private static final int start = 1000 * 1000;
    private static final int end = 32 * 1000 * 1000;
    private static final int increment = 2;
    private static final boolean debug = false;

    private static boolean warmUp = true;
    private long tmpTime = 0;


    public static void main(String[] args) throws InterruptedException {
        runExperiment(new IntArrayExperiment());
        runExperiment(new IntegerArrayExperiment());
    }
    interface Experiment{
        void run(int size);
    }

    protected static void runExperiment(Experiment experiment) throws InterruptedException {
        int arraySize = start;

        experiment.run(arraySize);
        warmUp = false;

        for (; arraySize <= end; arraySize *= increment) {
            experiment.run(arraySize);
            gc();
            Thread.sleep(1000);
        }
    }

    void start() {
        tmpTime = nanoTime();
    }

    long stop(int arraySize) {
        long took = nanoTime() - tmpTime;
        long throughput = Math.round(arraySize / (took / 1000000000d));
        print(took, throughput);
        return throughput;
    }

    void print(long took, long round) {
        if (debug)
            out.printf("%stook %,dus, throughput of %,d reads/s\n", "", took / 1000, round);
    }

    protected void printResult(long r1, long r2, int arraySize) {
        if (!warmUp)
            out.printf("%,d Sequential reads were %,d times faster than random [%,d int/s vs %,d int/s]\n", arraySize, (r1 / r2), r1, r2);
    }


    private static class IntArrayExperiment extends SeqVsRandMemAccess implements Experiment{


        public void run(int arraySize) {
            out.println("Running using int[]\n");
            int[] data = new int[arraySize];
            int check1 = 0;
            int check2 = 0;
            Arrays.fill(data, -1);
            int sum = populateShuffled(data, arraySize);

            start();

            for (int i = 0; i < arraySize; i++) {
                check1 += data[i];
            }

            long r1 = stop(arraySize);

            start();

            int pos = 0;
            for (int i = 0; i < arraySize; i++) {
                pos = data[pos];
                check2 += pos;
            }

            long r2 = stop(arraySize);

            check(check1, check2, sum);

            printResult(r1, r2, arraySize);
        }

        private void check(int check1, int check2, int sum) {
            if (check1 != sum || check2 != sum) out.println("Something went wrong!");
        }

        /**
         * Create a shuffled array of linked positions that randomly traverse the number space from 0->n.
         * So the int in position 0 points to the next offset to navigate to. This process repeats to n without
         * returning to previously visited positions (which would create a sub-loop)
         * <p/>
         * This version works for int[]
         */
        private int populateShuffled(int[] data, int arraySize) {
            int checksum = 0;
            int next = 0;
            int count = arraySize;

            //Create list of shuffled indexes to consume later
            Queue<Integer> positions = new LinkedList<Integer>();
            for (int i = 0; i < data.length; i++)
                positions.add(i);

            Collections.shuffle((List) positions);

            while (count-- > 0) {
                int current = positions.peek();

                if (positions.size() == 1) {
                    //the end so loop back to our starting point (0)
                    current = 0;
                } else {
                    //only insert positions that correspond to empty slots in the array
                    while (data[current] != -1) {
                        positions.add(positions.poll());
                        current = positions.peek();
                    }
                }

                positions.poll();

                data[next] = current;
                next = current;
                checksum += current;
            }
            return checksum;
        }
    }

    private static class IntegerArrayExperiment extends SeqVsRandMemAccess implements Experiment {

        public void run(int arraySize) {
            out.println("Running using Integer[]\n");
            Integer[] data = new Integer[arraySize];
            int check1 = 0;
            int check2 = 0;

            int sum = populateShuffled(data, arraySize);

            start();

            for (int i = 0; i < arraySize; i++) {
                check1 += data[i];
            }

            long r1 = stop(arraySize);

            start();

            int pos = 0;
            for (int i = 0; i < arraySize; i++) {
                pos = data[pos];
                check2 += pos;
            }

            long r2 = stop(arraySize);

            check(check1, check2, sum);

            printResult(r1, r2, arraySize);
        }

        private void check(int check1, int check2, int sum) {
            if (check1 != sum || check2 != sum) out.println("Something went wrong!");
        }

        /**
         * Create a shuffled array of linked positions that randomly traverse the number space from 0->n.
         * So the int in position 0 points to the next offset to navigate to. This process repeats to n without
         * returning to previously visited positions (which would create a sub-loop)
         * <p/>
         * This version works for Integer[]
         */
        private int populateShuffled(Integer[] data, int arraySize) {
            int checksum = 0;
            int next = 0;
            int count = arraySize;

            //Create list of shuffled indexes to consume later
            Queue<Integer> positions = new LinkedList<Integer>();
            for (int i = 0; i < data.length; i++)
                positions.add(i);

            Collections.shuffle((List) positions);

            while (count-- > 0) {
                int current = positions.peek();

                if (positions.size() == 1) {
                    //the end so loop back to our starting point (0)
                    current = 0;
                } else {
                    //only insert positions that correspond to empty slots in the array
                    while (data[current] != null) {
                        positions.add(positions.poll());
                        current = positions.peek();
                    }
                }

                positions.poll();

                data[next] = current;
                next = current;
                checksum += current;
            }
            return checksum;
        }
    }
}
