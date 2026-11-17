import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Generates deterministic datasets for experiments on MergeSort + InsertionSort cutoff.
 *
 * Usage (from the project root):
 *   javac GenerateTestData.java
 *   java GenerateTestData
 *   java GenerateTestData --sample 20
 *
 * This file ONLY generates input data; it does not implement sorting benchmarks.
 */
public class GenerateTestData {
    private static final int[] SIZES = {1_000, 10_000, 100_000};
    private static final long[] SEEDS = {42L, 2026L, 123456L};
    private static final int RANDOM_BOUND = 1_000_000; // Values: 0..999999
    private static final int ALL_EQUAL_VALUE = 7;
    private static final Path DATA_DIR = Paths.get("data");

    private enum DataType {
        RANDOM("random"),
        SORTED("sorted"),
        REVERSED("reversed"),
        NEARLY_SORTED("nearly_sorted"),
        MANY_DUPLICATES("many_duplicates"),
        ALL_EQUAL("all_equal");

        final String fileName;
        DataType(String fileName) { this.fileName = fileName; }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 2 && "--sample".equals(args[0])) {
            int n = Integer.parseInt(args[1]);
            if (n < 2 || n > 100) {
                throw new IllegalArgumentException("Sample size must be from 2 to 100");
            }
            System.out.println("Sample arrays, n=" + n + ", seed=42:");
            for (DataType type : DataType.values()) {
                int[] a = generate(type, n, 42L);
                System.out.println(type.fileName + " = " + Arrays.toString(a));
            }
            return;
        }
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: java GenerateTestData [--sample 20]");
        }

        Files.createDirectories(DATA_DIR);
        int count = 0;
        for (DataType type : DataType.values()) {
            for (int n : SIZES) {
                for (long seed : SEEDS) {
                    int[] data = generate(type, n, seed);
                    String fileName = type.fileName + "_n" + n + "_seed" + seed + ".txt";
                    Path target = DATA_DIR.resolve(fileName);
                    writeDataset(target, data);
                    System.out.println("Created: " + target + " (n=" + n + ")");
                    count++;
                }
            }
        }
        System.out.println("Done: " + count + " datasets in " + DATA_DIR.toAbsolutePath());
    }

    /** Same (type, n, seed) -> exactly the same array on the same Java implementation. */
    private static int[] generate(DataType type, int n, long seed) {
        int[] a = new int[n];
        SplittableRandom rng = new SplittableRandom(seed);

        if (type == DataType.ALL_EQUAL) {
            // Seed cannot influence a constant-valued array, intentionally.
            Arrays.fill(a, ALL_EQUAL_VALUE);
            return a;
        }
        if (type == DataType.MANY_DUPLICATES) {
            for (int i = 0; i < n; i++) {
                a[i] = rng.nextInt(10); // 0..9, near-uniform among 10 possible keys
            }
            return a;
        }

        // Shared random base for random/sorted/reversed/nearly_sorted with same n & seed.
        for (int i = 0; i < n; i++) {
            a[i] = rng.nextInt(RANDOM_BOUND);
        }
        if (type == DataType.RANDOM) {
            return a;
        }
        Arrays.sort(a); // Only used for DATA GENERATION, not benchmark sorting.

        if (type == DataType.REVERSED) {
            for (int left = 0, right = n - 1; left < right; left++, right--) {
                swap(a, left, right);
            }
        } else if (type == DataType.NEARLY_SORTED) {
            // Exactly floor(n/100) swaps using distinct indices (about 1% of n pairs).
            // A separate RNG stream ensures base sorted data stays unchanged.
            int swaps = Math.max(1, n / 100);
            int[] positions = new int[n];
            for (int i = 0; i < n; i++) positions[i] = i;
            SplittableRandom swapRng = new SplittableRandom(seed ^ 0x9E3779B97F4A7C15L);
            // Partial Fisher-Yates shuffle: select 2*swaps distinct indices.
            for (int i = 0; i < 2 * swaps; i++) {
                int j = i + swapRng.nextInt(n - i);
                swap(positions, i, j);
            }
            for (int k = 0; k < swaps; k++) {
                swap(a, positions[2 * k], positions[2 * k + 1]);
            }
        }
        return a;
    }

    private static void swap(int[] a, int i, int j) {
        int tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

    /** First line: n; following lines: one integer per line; always LF line endings. */
    private static void writeDataset(Path path, int[] a) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write(Integer.toString(a.length));
            out.write('\n');
            for (int value : a) {
                out.write(Integer.toString(value));
                out.write('\n');
            }
        }
    }
}
