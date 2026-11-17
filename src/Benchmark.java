import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Benchmarks the student's existing EnhanceMergesort.sort(int[], int).
 * IMPORTANT: This class DOES NOT implement or modify MergeSort.
 *
 * Input:  data/<type>_n<size>_seed<seed>.txt (first line: n, then n integers)
 * Output: results/*.csv and results/charts/*.svg
 *
 * Run from the project root, without any program arguments.
 * Java 19 compatible; only the Java standard library is used.
 */
public final class Benchmark {
    private static final String[] TYPES = {
        "random", "sorted", "reversed", "nearly_sorted", "many_duplicates", "all_equal"
    };
    private static final int[] SIZES = {1_000, 10_000, 100_000};
    private static final long[] SEEDS = {42L, 2026L, 123456L};
    private static final int[] CUTOFFS = {0, 4, 7, 10, 16, 24, 32, 48, 64};

    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASURED_ROUNDS = 20;
    private static final Path DATA_DIR = Paths.get("data");
    private static final Path RESULTS_DIR = Paths.get("results");
    private static final Path CHART_DIR = RESULTS_DIR.resolve("charts");

    // Observable after timing; the full sorted array is also validated after every run.
    private static volatile int observedValue;

    private Benchmark() { }

    private static final class Stats {
        final double meanNs;
        final double medianNs;
        final double sampleStdDevNs;
        final long minNs;
        final long maxNs;

        Stats(long[] samples) {
            if (samples.length < 2) throw new IllegalArgumentException("At least 2 samples required");
            long[] ordered = samples.clone();
            Arrays.sort(ordered);
            minNs = ordered[0];
            maxNs = ordered[ordered.length - 1];
            double sum = 0.0;
            for (long time : samples) sum += time;
            meanNs = sum / samples.length;
            int m = ordered.length / 2;
            medianNs = (ordered.length % 2 == 0)
                ? (ordered[m - 1] / 2.0 + ordered[m] / 2.0)
                : ordered[m];
            double sumSquares = 0.0;
            for (long time : samples) {
                double delta = time - meanNs;
                sumSquares += delta * delta;
            }
            sampleStdDevNs = Math.sqrt(sumSquares / (samples.length - 1));
        }
    }

    private static final class FileResult {
        final String type;
        final int n;
        final long seed;
        final long[][] timesNs = new long[CUTOFFS.length][MEASURED_ROUNDS];
        final Stats[] stats = new Stats[CUTOFFS.length];

        FileResult(String type, int n, long seed) {
            this.type = type;
            this.n = n;
            this.seed = seed;
        }
    }

    private static final class GroupResult {
        final String type;
        final int n;
        final double[] medianAcrossSeedsNs = new double[CUTOFFS.length];
        final double[] geoMeanSpeedup = new double[CUTOFFS.length];
        int bestCutoffIndex;

        GroupResult(String type, int n) {
            this.type = type;
            this.n = n;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 0) {
            throw new IllegalArgumentException("Run Benchmark without program arguments");
        }
        if (!Files.isDirectory(DATA_DIR)) {
            throw new IOException("Cannot find data folder: " + DATA_DIR.toAbsolutePath()
                + ". Set IntelliJ Working directory to the PROJECT ROOT.");
        }
        verifyFileCount();
        Files.createDirectories(RESULTS_DIR);
        Files.createDirectories(CHART_DIR);

        List<FileResult> results = new ArrayList<>();
        int finished = 0;
        for (String type : TYPES) {
            for (int n : SIZES) {
                for (long seed : SEEDS) {
                    Path file = DATA_DIR.resolve(type + "_n" + n + "_seed" + seed + ".txt");
                    int[] original = readDataset(file, n);
                    int[] expected = original.clone();
                    // Reference sorting happens ONCE, before warm-up and timing.
                    Arrays.sort(expected);

                    FileResult result = measure(type, n, seed, original, expected);
                    results.add(result);
                    finished++;
                    System.out.printf(Locale.US, "Completed %d/54: %-15s n=%6d seed=%d%n",
                        finished, type, n, seed);
                }
            }
        }

        List<GroupResult> groups = aggregateByTypeAndSize(results);
        writeRaw(results);
        writeSummary(results);
        writeComparison(groups);
        writeBest(groups);
        writeOverall(groups);
        for (GroupResult group : groups) writeSvgChart(group);

        System.out.println("\nSUCCESS: all 54 datasets validated and benchmarked.");
        System.out.println("Raw:        " + RESULTS_DIR.resolve("raw_results.csv").toAbsolutePath());
        System.out.println("Summary:    " + RESULTS_DIR.resolve("summary_results.csv").toAbsolutePath());
        System.out.println("Comparison: " + RESULTS_DIR.resolve("comparison.csv").toAbsolutePath());
        System.out.println("Best:       " + RESULTS_DIR.resolve("best_cutoff.csv").toAbsolutePath());
        System.out.println("Overall:    " + RESULTS_DIR.resolve("overall_summary.csv").toAbsolutePath());
        System.out.println("Charts:     " + CHART_DIR.toAbsolutePath() + " (18 SVG files)");
        // Read to make the side effect visible and resistant to dead-code elimination.
        System.out.println("Observation sink: " + observedValue);
    }

    /** All 54 expected input files must exist; no additional .txt inputs are allowed. */
    private static void verifyFileCount() throws IOException {
        try (Stream<Path> paths = Files.list(DATA_DIR)) {
            long count = paths.filter(p -> Files.isRegularFile(p)
                && p.getFileName().toString().endsWith(".txt")).count();
            if (count != 54) {
                throw new IOException("Expected 54 .txt files in data/, found " + count);
            }
        }
    }

    /** Strict format: line 1 == expected N, followed by exactly N integer lines. */
    private static int[] readDataset(Path file, int expectedN) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String first = in.readLine();
            if (first == null) throw new IOException("Empty file: " + file);
            int n = parseInt(first, file, 1);
            if (n != expectedN) {
                throw new IOException(file + ": declared n=" + n + ", expected n=" + expectedN);
            }
            int[] a = new int[n];
            for (int i = 0; i < n; i++) {
                String line = in.readLine();
                if (line == null) {
                    throw new IOException(file + ": only " + i + "/" + n + " values found");
                }
                a[i] = parseInt(line, file, i + 2);
            }
            if (in.readLine() != null) {
                throw new IOException(file + ": extra data after the expected " + n + " values");
            }
            return a;
        }
    }

    private static int parseInt(String text, Path file, int lineNumber) throws IOException {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            throw new IOException(file + ": invalid integer on line " + lineNumber
                + " (" + text + ")", ex);
        }
    }

    /** Only EnhanceMergesort.sort() is timed. Cloning and validation are outside. */
    private static FileResult measure(String type, int n, long seed,
                                      int[] original, int[] expected) {
        FileResult result = new FileResult(type, n, seed);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < CUTOFFS.length; i++) order.add(i);
        // Fixed seed = deterministic order, but timings are not deterministic.
        long shuffleSeed = 0x52AFC019L ^ ((long) type.hashCode() << 1)
            ^ ((long) n << 20) ^ seed;
        Random orderRandom = new Random(shuffleSeed);

        for (int round = 0; round < WARMUP_ROUNDS + MEASURED_ROUNDS; round++) {
            Collections.shuffle(order, orderRandom);
            for (int index : order) {
                int cutoff = CUTOFFS[index];
                int[] working = original.clone(); // NOT timed

                long start = System.nanoTime();
                EnhanceMergesort.sort(working, cutoff); // ONLY measured operation
                long elapsed = System.nanoTime() - start;

                // Both checks are OUTSIDE the measured time.
                if (!isNondecreasing(working) || !Arrays.equals(working, expected)) {
                    throw new IllegalStateException("INCORRECT SORT: type=" + type
                        + ", n=" + n + ", seed=" + seed + ", cutoff=" + cutoff
                        + ", round=" + round);
                }
                observedValue = working[n / 2];
                if (round >= WARMUP_ROUNDS) {
                    result.timesNs[index][round - WARMUP_ROUNDS] = elapsed;
                }
            }
        }
        for (int i = 0; i < CUTOFFS.length; i++) {
            result.stats[i] = new Stats(result.timesNs[i]);
        }
        return result;
    }

    private static boolean isNondecreasing(int[] a) {
        for (int i = 1; i < a.length; i++) if (a[i] < a[i - 1]) return false;
        return true;
    }

    private static List<GroupResult> aggregateByTypeAndSize(List<FileResult> files) {
        List<GroupResult> groups = new ArrayList<>();
        for (String type : TYPES) {
            for (int n : SIZES) {
                List<FileResult> seeds = new ArrayList<>();
                for (FileResult file : files) {
                    if (file.type.equals(type) && file.n == n) seeds.add(file);
                }
                if (seeds.size() != SEEDS.length) {
                    throw new IllegalStateException("Wrong seed count: " + type + ", n=" + n);
                }
                GroupResult group = new GroupResult(type, n);
                for (int i = 0; i < CUTOFFS.length; i++) {
                    double[] perSeedMedians = new double[seeds.size()];
                    double logSpeedup = 0;
                    for (int s = 0; s < seeds.size(); s++) {
                        FileResult file = seeds.get(s);
                        perSeedMedians[s] = file.stats[i].medianNs;
                        // Paired, per-file comparison with cutoff=0.
                        logSpeedup += Math.log(file.stats[0].medianNs / file.stats[i].medianNs);
                    }
                    group.medianAcrossSeedsNs[i] = median(perSeedMedians);
                    group.geoMeanSpeedup[i] = Math.exp(logSpeedup / seeds.size());
                }
                int best = 0;
                for (int i = 1; i < CUTOFFS.length; i++) {
                    if (group.medianAcrossSeedsNs[i] < group.medianAcrossSeedsNs[best]) best = i;
                }
                group.bestCutoffIndex = best;
                groups.add(group);
            }
        }
        return groups;
    }

    private static double median(double[] nums) {
        double[] a = nums.clone();
        Arrays.sort(a);
        int m = a.length / 2;
        return a.length % 2 == 0 ? (a[m - 1] + a[m]) / 2.0 : a[m];
    }

    private static void writeRaw(List<FileResult> files) throws IOException {
        Path path = RESULTS_DIR.resolve("raw_results.csv");
        try (BufferedWriter out = csv(path,
            "data_type,n,seed,cutoff,round,time_ns,time_ms")) {
            for (FileResult file : files) {
                for (int i = 0; i < CUTOFFS.length; i++) {
                    for (int r = 0; r < MEASURED_ROUNDS; r++) {
                        long time = file.timesNs[i][r];
                        out.write(String.format(Locale.US, "%s,%d,%d,%d,%d,%d,%.9f%n",
                            file.type, file.n, file.seed, CUTOFFS[i], r + 1,
                            time, toMs(time)));
                    }
                }
            }
        }
    }

    private static void writeSummary(List<FileResult> files) throws IOException {
        Path path = RESULTS_DIR.resolve("summary_results.csv");
        try (BufferedWriter out = csv(path,
            "data_type,n,seed,cutoff,samples,mean_ms,median_ms,stddev_ms,min_ms,max_ms,speedup_vs_cutoff0")) {
            for (FileResult file : files) {
                double baseline = file.stats[0].medianNs;
                for (int i = 0; i < CUTOFFS.length; i++) {
                    Stats stat = file.stats[i];
                    out.write(String.format(Locale.US,
                        "%s,%d,%d,%d,%d,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f%n",
                        file.type, file.n, file.seed, CUTOFFS[i], MEASURED_ROUNDS,
                        toMs(stat.meanNs), toMs(stat.medianNs),
                        toMs(stat.sampleStdDevNs), toMs(stat.minNs), toMs(stat.maxNs),
                        baseline / stat.medianNs));
                }
            }
        }
    }

    private static void writeComparison(List<GroupResult> groups) throws IOException {
        Path path = RESULTS_DIR.resolve("comparison.csv");
        try (BufferedWriter out = csv(path,
            "data_type,n,cutoff,seed_count,median_of_seed_medians_ms,geomean_speedup_vs_cutoff0")) {
            for (GroupResult g : groups) {
                for (int i = 0; i < CUTOFFS.length; i++) {
                    out.write(String.format(Locale.US, "%s,%d,%d,%d,%.9f,%.9f%n",
                        g.type, g.n, CUTOFFS[i], SEEDS.length,
                        toMs(g.medianAcrossSeedsNs[i]), g.geoMeanSpeedup[i]));
                }
            }
        }
    }

    private static void writeBest(List<GroupResult> groups) throws IOException {
        Path path = RESULTS_DIR.resolve("best_cutoff.csv");
        try (BufferedWriter out = csv(path,
            "data_type,n,best_cutoff,best_median_of_seed_medians_ms,geomean_speedup_vs_cutoff0,runner_up_cutoff,margin_vs_runner_up_percent")) {
            for (GroupResult g : groups) {
                int best = g.bestCutoffIndex;
                int second = -1;
                for (int i = 0; i < CUTOFFS.length; i++) {
                    if (i != best && (second == -1 ||
                        g.medianAcrossSeedsNs[i] < g.medianAcrossSeedsNs[second])) second = i;
                }
                double relativeGap = 100.0 * (g.medianAcrossSeedsNs[second]
                    - g.medianAcrossSeedsNs[best]) / g.medianAcrossSeedsNs[second];
                out.write(String.format(Locale.US, "%s,%d,%d,%.9f,%.9f,%d,%.6f%n",
                    g.type, g.n, CUTOFFS[best],
                    toMs(g.medianAcrossSeedsNs[best]), g.geoMeanSpeedup[best],
                    CUTOFFS[second], relativeGap));
            }
        }
    }

    private static void writeOverall(List<GroupResult> groups) throws IOException {
        Path path = RESULTS_DIR.resolve("overall_summary.csv");
        try (BufferedWriter out = csv(path,
            "cutoff,groups,geomean_speedup_over_18_groups,best_in_group_count")) {
            for (int i = 0; i < CUTOFFS.length; i++) {
                double sumLogs = 0;
                int wins = 0;
                for (GroupResult g : groups) {
                    sumLogs += Math.log(g.geoMeanSpeedup[i]);
                    if (g.bestCutoffIndex == i) wins++;
                }
                out.write(String.format(Locale.US, "%d,%d,%.9f,%d%n",
                    CUTOFFS[i], groups.size(), Math.exp(sumLogs / groups.size()), wins));
            }
        }
    }

    /** Each group gets an SVG line chart; SVG needs no third-party Java library. */
    private static void writeSvgChart(GroupResult g) throws IOException {
        final int width = 880, height = 480;
        final int left = 92, right = 35, top = 68, bottom = 88;
        final double plotW = width - left - right;
        final double plotH = height - top - bottom;
        double maxNs = 0;
        for (double x : g.medianAcrossSeedsNs) maxNs = Math.max(maxNs, x);
        double yMaxMs = Math.max(0.000001, toMs(maxNs) * 1.15);
        double xMin = CUTOFFS[0], xMax = CUTOFFS[CUTOFFS.length - 1];

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 880 480\" ")
            .append("width=\"880\" height=\"480\">\n")
            .append("<rect width=\"880\" height=\"480\" fill=\"white\"/>\n")
            .append("<text x=\"440\" y=\"34\" text-anchor=\"middle\" ")
            .append("font-family=\"Arial,sans-serif\" font-size=\"21\" font-weight=\"bold\">")
            .append("Cutoff vs median time: ").append(g.type).append(", N=").append(g.n)
            .append("</text>\n")
            .append("<text x=\"440\" y=\"55\" text-anchor=\"middle\" ")
            .append("font-family=\"Arial,sans-serif\" font-size=\"13\" fill=\"#666\">")
            .append("Median of 3 per-seed medians; 20 measured runs/seed")
            .append("</text>\n");

        for (int k = 0; k <= 4; k++) {
            double valueMs = yMaxMs * k / 4.0;
            double y = top + plotH * (1.0 - k / 4.0);
            svg.append(String.format(Locale.US,
                "<line x1=\"%d\" x2=\"%d\" y1=\"%.2f\" y2=\"%.2f\" stroke=\"#e4e8ee\"/>%n",
                left, width - right, y, y));
            svg.append(String.format(Locale.US,
                "<text x=\"%d\" y=\"%.2f\" text-anchor=\"end\" font-size=\"12\" font-family=\"Arial,sans-serif\" fill=\"#555\">%.4f</text>%n",
                left - 10, y + 5, valueMs));
        }
        svg.append(String.format(Locale.US,
            "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#666\"/>%n",
            left, top, left, height - bottom));
        svg.append(String.format(Locale.US,
            "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#666\"/>%n",
            left, height - bottom, width - right, height - bottom));

        StringBuilder linePoints = new StringBuilder();
        for (int i = 0; i < CUTOFFS.length; i++) {
            double x = left + plotW * (CUTOFFS[i] - xMin) / (xMax - xMin);
            double y = top + plotH * (1.0 - toMs(g.medianAcrossSeedsNs[i]) / yMaxMs);
            if (i > 0) linePoints.append(' ');
            linePoints.append(String.format(Locale.US, "%.2f,%.2f", x, y));
        }
        svg.append("<polyline fill=\"none\" stroke=\"#276cba\" stroke-width=\"2.6\" points=\"")
            .append(linePoints).append("\"/>\n");
        for (int i = 0; i < CUTOFFS.length; i++) {
            double x = left + plotW * (CUTOFFS[i] - xMin) / (xMax - xMin);
            double y = top + plotH * (1.0 - toMs(g.medianAcrossSeedsNs[i]) / yMaxMs);
            String color = i == g.bestCutoffIndex ? "#16834a" : "#276cba";
            svg.append(String.format(Locale.US,
                "<circle cx=\"%.2f\" cy=\"%.2f\" r=\"%d\" fill=\"%s\"/>%n",
                x, y, i == g.bestCutoffIndex ? 6 : 4, color));
            svg.append(String.format(Locale.US,
                "<text x=\"%.2f\" y=\"%d\" text-anchor=\"middle\" font-family=\"Arial,sans-serif\" font-size=\"12\">%d</text>%n",
                x, height - bottom + 23, CUTOFFS[i]));
        }
        svg.append("<text x=\"440\" y=\"446\" text-anchor=\"middle\" ")
            .append("font-family=\"Arial,sans-serif\" font-size=\"15\">CUTOFF</text>\n")
            .append("<text transform=\"translate(25 245) rotate(-90)\" text-anchor=\"middle\" ")
            .append("font-family=\"Arial,sans-serif\" font-size=\"15\">Time (ms)</text>\n")
            .append("</svg>\n");
        Files.writeString(CHART_DIR.resolve("cutoff_" + g.type + "_n" + g.n + ".svg"),
            svg.toString(), StandardCharsets.UTF_8);
    }

    private static BufferedWriter csv(Path path, String header) throws IOException {
        BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        out.write(header);
        out.newLine();
        return out;
    }

    private static double toMs(double ns) {
        return ns / 1_000_000.0;
    }
}
