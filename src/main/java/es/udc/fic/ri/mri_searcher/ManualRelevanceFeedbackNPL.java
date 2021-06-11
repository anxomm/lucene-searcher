package es.udc.fic.ri.mri_searcher;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;

public class ManualRelevanceFeedbackNPL {

    private final static String CONFIG_FILE = "config.properties";
    private static Map<String,String> properties;

    public static void main(String[] args) {
        String usage = "java es.udc.fic.ri.mri_searcher.ManualRelevanceFeedbackNPL"
                + " [-retmodel jm LAMBDA | dir MU | tfidf] [-indexin INDEX_PATH] [-cut N] "
                + " [-metrica P | R | MAP] [-query Q]";

        String model = null;
        float modelParameter = 0;
        String indexPath = null;
        int cutN = 0;
        String metrica = null;
        int queryQ = 0;

        /* Check arguments */
        for (int i = 0; i < args.length; i++) {
            if ("-retmodel".equals(args[i])) {
                model = args[++i];
                if (!model.equals("tfidf")) {
                    modelParameter = Float.parseFloat(args[++i]);
                }
            } else if ("-indexin".equals(args[i])) {
                indexPath = args[++i];
            } else if ("-cut".equals(args[i])) {
                cutN = Integer.parseInt(args[++i]);
            } else if ("-metrica".equals(args[i])) {
                metrica = args[++i];
            } else if ("-query".equals(args[i])) {
                queryQ = Integer.parseInt(args[++i]);
            }
        }

        final String QUERIES_FILE = getProperty("queries");
        final String ASSESSMENTS_FILE = getProperty("reldocs");

        if (QUERIES_FILE == null) {
            System.err.println("Queries file must be specified at config.properties");
            System.exit(-1);
        }

        if (ASSESSMENTS_FILE == null) {
            System.err.println("Relevant documents file must be specified at config.properties");
            System.exit(-1);
        }

        if (model == null || indexPath == null || metrica == null) {
            System.err.println(usage);
            System.exit(-1);
        }

        if (cutN <= 0) {
            System.err.println("N must be greater than 0: " + cutN);
            System.exit(-1);
        }

        if (queryQ <= 0) {
            System.err.println("Q must be greater than 0: " + queryQ);
            System.exit(-1);
        }

        if (!(metrica.equals("P") || metrica.equals("R") || metrica.equals("MAP"))) {
            System.err.println("Unknown metrica: " + metrica);
            System.exit(-1);
        }

        /* Computation */
        try {
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            DirectoryReader ireader = DirectoryReader.open(dir);

            IndexSearcher isearcher = new IndexSearcher(ireader);
            if (model.equals("jm")) {
                isearcher.setSimilarity(new LMJelinekMercerSimilarity(modelParameter));
            } else if (model.equals("dir")){
                isearcher.setSimilarity(new LMDirichletSimilarity(modelParameter));
            } else {
                isearcher.setSimilarity(new ClassicSimilarity());  // Implementation of TFIDFSimilarity
            }

            QueryParser parser = new QueryParser("Contents", new StandardAnalyzer());
            String queryText = readQueries(QUERIES_FILE).get(queryQ);
            List<Integer> relevantDocs = readAssessments(ASSESSMENTS_FILE).get(queryQ);

            Scanner scanner = new Scanner(System.in);
            boolean end = false;

            do {
                int relevantsRetrieved = 0;
                float ap = 0;
                int firstRelevantPosition = 0;
                Document firstRelevantDoc = null;
                float score = 0;

                Query query = parser.parse(queryText);
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
                        if (firstRelevantPosition == 0) {
                            firstRelevantPosition = i+1;
                            firstRelevantDoc = isearcher.doc(hits[i].doc);
                            score = hits[i].score;
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

                String docIDNPL = firstRelevantDoc == null ? "0" : firstRelevantDoc.get("DocIDNPL");
                String contents = firstRelevantDoc == null ? "" : firstRelevantDoc.get("Contents");

                System.out.printf("QUERY => %s%n", queryText);
                System.out.printf("%7s%16s%8s%13s%12s   %s%n", "METRICA", "METRICA_VALUE", "RANK",
                        "Score", "DocIDNPL", "Contents");
                System.out.printf("%7s%16.6f%8d%13.6f%12s   %s%n", metrica+"@"+cutN, measure, firstRelevantPosition,
                        score, docIDNPL, contents);

                /* Ask the user about the new query */
                System.out.println("======================================");
                System.out.print("Rewrite the query (empty to finish): ");  // e.g. NUMBER BINARY COMPUTER DIGITAL SYSTEM CIRCUIT
                System.out.println();

                String newQuery = scanner.nextLine();
                if (newQuery.trim().equals("")) {
                    end = true;
                } else {
                    queryText = newQuery;

                }

            } while (!end);

            ireader.close();
            dir.close();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
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

    private static Map<Integer, List<Integer>> readAssessments(String file) {
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

    private static String getProperty(String name) {
        if (properties == null) {
            ClassLoader classLoader = ManualRelevanceFeedbackNPL.class.getClassLoader();
            InputStream inputStream = classLoader.getResourceAsStream(CONFIG_FILE);
            Properties properties = new Properties();
            try {
                properties.load(inputStream);
                inputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            ManualRelevanceFeedbackNPL.properties = (Map<String, String>) new HashMap(properties);
        }
        return properties.get(name);
    }
}
