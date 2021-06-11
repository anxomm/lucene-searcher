package es.udc.fic.ri.mri_searcher;

import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Compare {

    public static void main(String[] args) {
        String usage = "java es.udc.fic.ri.mri_searcher.Compare"
                + " [-results RESULTS1_FILE RESULTS2_FILE] [-test [t | wilcoxon] alpha]";

        String path1 = null, path2 = null;
        String testMode = null;
        double alpha = 0;

        /* Check arguments */
        for (int i = 0; i < args.length; i++) {
            if ("-results".equals(args[i])) {
                path1 = args[++i];
                path2 = args[++i];
            } else if ("-test".equals(args[i])) {
                testMode = args[++i];
                alpha = Double.parseDouble(args[++i]);
            }
        }

        if (path1 == null || path2 == null || testMode == null) {
            System.err.println(usage);
            System.exit(-1);
        }

        if (!(testMode.equals("t") || testMode.equals("wilcoxon"))) {
            System.err.println("Unknown test mode: " + testMode);
            System.exit(-1);
        }

        if (testMode.equals("t") && (alpha <= 0 || alpha > 0.5)) {
            System.err.println("alpha value must be in the range (0,0.5]: " + alpha);
            System.exit(-1);
        }

        try {
            double[] sample1 = readSample(path1);
            double[] sample2 = readSample(path2);

            double pvalue = 0;
            if (testMode.equals("t")) {
                TTest test = new TTest();
                pvalue = test.pairedTTest(sample1, sample2);
            } else {
                WilcoxonSignedRankTest test = new WilcoxonSignedRankTest();
                pvalue = test.wilcoxonSignedRankTest(sample1, sample2, sample1.length <= 30);
            }

            boolean rejected = pvalue <= alpha;  // null hypothesis rejected (B better than A)
            System.out.printf("alpha = %f%np-value = %f%n", alpha, pvalue);
            System.out.printf("The null hypothesis %s be rejected in favor of the alternate hypoteshis " +
                            "because p-value %s alpha%n", rejected ? "can" : "cannot", rejected ? "<=" : ">");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static double[] readSample(String path) throws IOException {
        List<Double> results = new ArrayList<>();

        try (Scanner scanner = new Scanner(new File(path))) {
            while (scanner.hasNext()) {
                String[] tokens = scanner.nextLine().trim().split(",");
                results.add(Double.parseDouble(tokens[1]));
            }
        }

        return results.stream().mapToDouble(d -> d).toArray();
    }
}
