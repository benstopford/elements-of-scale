package com.dlab.scale.sequential;


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Random;


/**
 *
 * Simple class that baselines the single thread performance of reads and writes to the local drive
 *
 * Sample results from 2.3 GHz Intel Core i7, 8 GB 1600 MHz DDR3, APPLE SSD SM256E Journaled HFS+
 *
    Writing file of length 268,435,456B took 2,831ms resulting in throughput 92MB/s
    Sequentially reading file of length 268,435,456B took 1,578ms resulting in throughput 166MB/s. We read 170,111,000 ints/sec
    Writing file of length 268,435,456B took 1,730ms resulting in throughput 151MB/s
    Randomly reading file of length 268,435,456B took 299,159ms resulting in throughput 0MB/s. We read 897,000 ints/sec
 *
 * -Xms4g -Xmx4g -verbose:gc -XX:+PrintGCTimeStamps -XX:+PrintGCDetails
 */
public class SeqVsRandDiskRead {

    public static final String dir = "/tmp/foo/";
    public static final String file = dir + "data.txt";
    public static final int buffLen = 4 * 1024;

    public static void main(String[] args) throws IOException, InterruptedException {
        SeqVsRandDiskRead exp = new SeqVsRandDiskRead();
        int count = 64 * 1024 * 1024; //REDUCE TO SPEED UP TEST

        boolean seqential = true;
        exp.runExperiment(count, seqential);

        seqential = false;
        exp.runExperiment(count, seqential);

        System.exit(-1);
    }

    private void runExperiment(int count, boolean seqential) throws IOException, InterruptedException {

        clear();
        try {
            long write = write(file, count);
            clearFileCache();

            long read;
            if (seqential)
                read = readSequential(file, count);
            else
                read = readRandom(file, count);

            assertTrue(write != 0);
            assertTrue(write == read);

        } finally {
            clear();
        }
    }

    private void assertTrue(boolean b) {
        if (!b) throw new RuntimeException("Assertion failed");
    }

    private void clearFileCache() throws IOException {
        System.out.printf("Clear the buffer cache and then press enter [on OSX its 'sudo purge']");
        System.in.read();
        System.out.println("...");
    }

    private long write(String fileName, int amount) throws IOException {
        RandomAccessFile file = new RandomAccessFile(fileName, "rw");

        long checksum = 0;
        long start = System.currentTimeMillis();

        byte[] buffer = new byte[buffLen];
        int pos = 0;
        for (Integer i = 0; i < amount; i++) {
            byte[] iBuf = toBytes(i);
            for (byte b : iBuf) {
                buffer[pos++] = b;
            }
            checksum += i;
            if (pos == buffer.length) {
                file.write(buffer);
                pos = 0;
            }
        }
        if (pos > 0) file.write(buffer, 0, pos);

        long fileLength = file.length();
        long took = System.currentTimeMillis() - start;

        checkWrite(amount, file);
        file.close();

        System.out.printf("Writing file of length %,dB took %,dms resulting in throughput %,dMB/s\n", fileLength, took, took == 0 ? 0 : (amount * 4L / 1024L / took));

        return checksum;
    }

    private void checkWrite(int amount, RandomAccessFile file) throws IOException {
        if (file.length() !=amount*4) {
            throw new RuntimeException("something went wrong with the write. File length: " + file.length());
        }
    }

    public static int toInt(byte[] b) {
        return b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }

    public static byte[] toBytes(int a) {
        return new byte[]{
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }

    private long readSequential(String fileName, int amount) throws IOException {
        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        long checksum = 0;
        long start = System.currentTimeMillis();
        byte[] buffer = new byte[buffLen];
        ByteBuffer intBuf = ByteBuffer.allocate(4);
        while (file.read(buffer) > 0) {
            for (byte b : buffer) {
                intBuf.put(b);
                if (intBuf.position() == 4) {
                    int i = toInt(intBuf.array());
                    checksum += i;
                    intBuf.flip();
                }
            }
        }

        long fileLength = file.length();
        file.close();
        long took = System.currentTimeMillis() - start;

        log("Sequentially",amount, fileLength, took);

        return checksum;
    }

    private void log(String s, int amount, long fileLength, long took) {
        System.out.printf(s+" reading file of length %,dB took %,dms resulting in throughput %,dMB/s. We read %,d ints/sec\n", fileLength, took, (amount * 4 / 1024L / took), amount * 4 / took * 1000);
    }

    private long readRandom(String fileName, int amount) throws IOException {
        RandomAccessFile file = new RandomAccessFile(fileName, "rw");

        int[] positions = shuffle(positions(amount));

        long checksum = 0;
        long start = System.currentTimeMillis();
        byte[] buff = new byte[4];
        for (int i : positions) {
            int offset = 4 * i;
            file.seek(offset);
            file.read(buff);
            int i1 = toInt(buff);
            checksum += i1;
        }

        long fileLength = file.length();
        file.close();
        long took = System.currentTimeMillis() - start;

        log("Randomly",amount, fileLength, took);

        return checksum;
    }


    private int[] positions(int amount) {
        int[] positions = new int[amount];
        for (int i = 0; i < amount; i++)
            positions[i] = i;
        return positions;
    }

    private int[] shuffle(int[] positions) {
        Random random = new Random();
        for (int i = positions.length - 1; i > 0; i--) {
            int r = random.nextInt(i + 1);
            int temp = positions[i];
            positions[i] = positions[r];
            positions[r] = temp;
        }
        return positions;
    }

    public void clear() {
        File dir = new File(SeqVsRandDiskRead.dir);
        deleteDirectory(dir);
        dir.mkdir();
    }

    public static boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (null != files) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        deleteDirectory(files[i]);
                    } else {
                        files[i].delete();
                    }
                }
            }
        }
        return (directory.delete());
    }
}
