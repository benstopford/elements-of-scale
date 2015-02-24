package com.dlab.scale.branch;

import java.util.Arrays;
import java.util.Random;

/**
 * http://www.cs.columbia.edu/~kar/pubsk/selcondsTODS.pdf
 * <p/>
 * -Xms4g -Xmx4g -verbose:gc -XX:+PrintGCTimeStamps -XX:+PrintGCDetails
 *
 * Sample output:
        Random took 682,719,000
        Sorted took 152,058,000
 *
 */
public class BranchMispredict {

    public static final Random rand = new Random();
    private static long start;
    private static boolean warmup = true;

    public static void main(String[] args) {
        warmup();

        run();
        run();
    }

    private static void warmup() {
        run();
        warmup = false;
    }

    private static void run() {
        int countUnsorted = 0;
        int countSorted = 0;

        Boolean[] branches = new Boolean[128 * 1024 * 1024];
        for (int i = 0; i < branches.length; i++)
            branches[i] = (rand.nextBoolean());


        start();
        for (boolean b : branches) {
            if (b)
                countUnsorted++;
            else
                countUnsorted--;
        }
        stop("Random");

        Arrays.sort(branches);

        start();
        for (boolean b : branches) {
            if (b)
                countSorted++;
            else
                countSorted--;
        }
        stop("Sorted");


        if (countUnsorted != countSorted) throw new RuntimeException("Oops");
    }

    private static void start() {
        start = System.nanoTime();
    }

    private static void stop(String s) {
        long stop = System.nanoTime();
        if (!warmup)
            System.out.printf("%s took %,d\n", s, (stop - start));
    }
}
