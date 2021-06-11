package es.udc.fic.ri.mri_searcher;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TrainingTestNPL {

    private static final String QUERIES_FILE = "/home/anxomm/Desktop/RI/angel.miguelez.millos/npl/query-text";
    private static final String ASSESSMENTS_FILE = "/home/anxomm/Desktop/RI/angel.miguelez.millos/npl/rlv-ass";

    private static String evalMode = null;
    private static int trainInt1= 0, trainInt2 = 0;
    private static int testInt1 = 0, testInt2 = 0;
    private static int cutN = 0;
    private static String metrica = null;
    private static String indexPath = null;
    private static String outputFile = null;

    public static void main(String[] args) {
        String usage = "java es.udc.fic.ri.mri_searcher.TrainingTestNPL"
                + " [-evaljm INT1-INT2 INT3-INT4| -evaldir INT1-INT2 INT3-INT4] [-cut N] "
                + " [-metrica P | R | MAP] [-indexin -INDEX_PATH] [-outfile OUTPUT_FILE]";

        /* Check arguments */
        for (int i = 0; i < args.length; i++) {
            if ("-evaljm".equals(args[i]) || "-evaldir".equals(args[i])) {
                evalMode = args[i].equals("-evaljm") ? "jm" : "dir";

                String[] trainRange = args[++i].split("-");
                trainInt1 = Integer.parseInt(trainRange[0]);
                trainInt2 = Integer.parseInt(trainRange[1]);

                String[] testRange = args[++i].split("-");
                testInt1 = Integer.parseInt(testRange[0]);
                testInt2 = Integer.parseInt(testRange[1]);
            } else if ("-indexin".equals(args[i])) {
                indexPath = args[++i];
            } else if ("-cut".equals(args[i])) {
                cutN = Integer.parseInt(args[++i]);
            } else if ("-metrica".equals(args[i])) {
                metrica = args[++i];
            } else if ("-outfile".equals(args[i])) {
                outputFile = args[++i];
            }
        }

        if (evalMode == null || indexPath == null || metrica == null || cutN == 0 || outputFile == null) {
            System.err.println(usage);
            System.exit(-1);
        }

        if (cutN < 0) {
            System.err.println("N must be greater than 0: " + cutN);
            System.exit(-1);
        }

        if (trainInt1 > trainInt2) {
            System.err.println("INT2 must be greater than INT1: " + trainInt1 + "-" + trainInt2);
            System.exit(-1);
        }

        if (testInt1 > testInt2) {
            System.err.println("INT3 must be greater than INT4: " + testInt1 + "-" + testInt2);
            System.exit(-1);
        }

        if (!(metrica.equals("P") || metrica.equals("R") || metrica.equals("MAP"))) {
            System.err.println("Unknown metrica: " + metrica);
            System.exit(-1);
        }

        /* Training and test computation */
        try {
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            DirectoryReader ireader = DirectoryReader.open(dir);
            IndexSearcher isearcher = new IndexSearcher(ireader);
            QueryParser parser = new QueryParser("Contents", new StandardAnalyzer());

            Map<Integer,Query> queries = new HashMap<>();
            for (Map.Entry<Integer,String> entry : readQueries(QUERIES_FILE).entrySet()) {
                Query query = parser.parse(entry.getValue());
                queries.put(entry.getKey(), query);
            }

            Map<Integer,List<Integer>> assessments = readAssessments(ASSESSMENTS_FILE);

            /* Train and test */
            float bestParameterLM = train(isearcher, queries, assessments);
            test(isearcher, queries, assessments, bestParameterLM);

            /* Close resources */
            ireader.close();
            dir.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static float train(IndexSearcher isearcher, Map<Integer,Query> queries, Map<Integer,List<Integer>> assessments) throws IOException{
        float increment = evalMode.equals("jm") ? 0.1f : 500;

        List<Float> increments = new ArrayList<>();
        for (int i=0; i<(evalMode.equals("jm") ? 10 : 11); i++) {
            float parameter = i*increment + (evalMode.equals("jm") ? 0.1f : 0);
            parameter = BigDecimal.valueOf(parameter).setScale(2, RoundingMode.HALF_UP).floatValue();
            increments.add(parameter);
        }

        System.out.printf("Results of %s@%d in training (evalMode=%s):%n", metrica, cutN, evalMode);

        System.out.print("      ");
        for (int i=trainInt1; i<=trainInt2; i++) {
            System.out.printf("%8s", i);
        }
        System.out.printf("%8s%n", "avg");

        List<Float> trainResults = new ArrayList<>();
        for (float parameterLM : increments) {
            if (evalMode.equals("jm")) {
                isearcher.setSimilarity(new LMJelinekMercerSimilarity(parameterLM));
            } else {
                isearcher.setSimilarity(new LMDirichletSimilarity(parameterLM));
            }
            System.out.printf("%6.1f", parameterLM);
            float average = queryAverageValue(isearcher, queries, trainInt1, trainInt2, assessments);
            trainResults.add(average);
            System.out.printf("%8.4f%n", average);
        }
        System.out.println();

        float bestParameterLM = increments.get(trainResults.indexOf(Collections.max(trainResults)));
        return bestParameterLM;
    }

    private static void test(IndexSearcher isearcher, Map<Integer,Query> queries, Map<Integer,List<Integer>> assessments, float parameter) throws IOException {
        System.out.printf("Testing with a %s value of %.1f%n",
                evalMode.equals("jm") ? "lambda" : "nu", parameter);
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile))) {

            if (evalMode.equals("jm")) {
                isearcher.setSimilarity(new LMJelinekMercerSimilarity(parameter));
            } else {
                isearcher.setSimilarity(new LMDirichletSimilarity(parameter));
            }

            float accum = 0;
            for (int i=testInt1; i<=testInt2; i++) {
                float measure = queryValue(isearcher, queries.get(i), assessments.get(i));
                accum += measure;
                writer.write(i + "," + measure + "\n");
                System.out.printf("QUERY %2d => %.4f%n", i, measure);
            }
            System.out.println("--------------------------");
            System.out.printf("%s@%d in test: %.4f%n", metrica, cutN, accum/(testInt2-testInt1+1));
        }
    }

    private static float queryAverageValue(IndexSearcher isearcher, Map<Integer,Query> queries, int start, int end,
                                           Map<Integer,List<Integer>> assessments) throws IOException{
        float nQueriesWithRelevants = 0;
        float accum = 0;

        for (int i=start; i<=end; i++) {
            // No hay documentos relevantes para la query
            if (assessments.get(i).size() == 0) {
                System.out.print("Ignored");
                continue;
            }
            nQueriesWithRelevants++;
            float measure = queryValue(isearcher, queries.get(i), assessments.get(i));
            accum += measure;
            System.out.printf("%8.4f", measure);

        }
        return accum / nQueriesWithRelevants;
    }

    private static float queryValue(IndexSearcher isearcher, Query query, List<Integer> relevantDocs) throws IOException{
        int relevantsRetrieved = 0;
        float ap = 0;

        ScoreDoc[] hits = isearcher.search(query, cutN).scoreDocs;
        for (int i=0; i<Math.min(hits.length,cutN); i++) {
            Document hitDoc = isearcher.doc(hits[i].doc);
            String docIDNPL = hitDoc.get("DocIDNPL");

            boolean isRelevant = relevantDocs.contains(Integer.parseInt(docIDNPL));
            if (isRelevant) {
                relevantsRetrieved++;
                if (metrica.equals("MAP")) {
                    ap += (float) relevantsRetrieved / (i+1);
                }
            }
        }

        float measure = 0;
        if (metrica.equals("P")) {
            measure = (float) relevantsRetrieved / cutN;
        } else if (metrica.equals("R")) {
            measure = (float) relevantsRetrieved / relevantDocs.size();
        } else if (metrica.equals("MAP")) {
            measure = ap / relevantDocs.size();
        }
        return measure;
    }

    private static Map<Integer,String> readQueries(String file) {
        Map<Integer,String> queries = new HashMap<>();

        try (Scanner scanner = new Scanner(new File(file))) {
            int id = 0;
            String query = "";
            boolean nextQuery = true;

            while (scanner.hasNext()) {
                String line = scanner.nextLine().trim();

                if (line.equals("/")) {
                    queries.put(id, query.toLowerCase());
                    nextQuery = true;
                    query = "";
                } else if (nextQuery) {
                    id = Integer.parseInt(line);
                    nextQuery = false;
                } else {
                    query = query.equals("") ? line : query + " " + line;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return queries;
    }

    private static Map<Integer,List<Integer>> readAssessments(String file) {
        Map<Integer,List<Integer>> assessments = new HashMap<>();

        try (Scanner scanner = new Scanner(new File(file))) {
            int query = 0;
            boolean nextQuery = true;
            List<Integer> docs = new LinkedList<>();

            while (scanner.hasNext()) {
                String line = scanner.nextLine().trim();

                if (line.equals("/")) {
                    assessments.put(query, docs);
                    nextQuery = true;
                    docs = new LinkedList<>();
                } else if (nextQuery) {
                    query = Integer.parseInt(line);
                    nextQuery = false;
                } else if (!line.equals("")){
                    for (String doc : line.split("\\s+")) {
                        int docID = Integer.parseInt(doc);
                        docs.add(docID);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return assessments;
    }

}