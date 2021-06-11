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
import java.nio.file.Paths;
import java.util.*;

public class SearchEvalNPL {

    private static final String QUERIES_FILE = "/home/anxomm/Desktop/RI/angel.miguelez.millos/npl/query-text";
    private static final String ASSESSMENTS_FILE = "/home/anxomm/Desktop/RI/angel.miguelez.millos/npl/rlv-ass";

    private enum TypeQuery { SIMPLE, RANGE, ALL }

    public static void main(String[] args) {
        String usage = "java es.udc.fic.ri.mri_searcher.SearchEvalNPL"
                + " [-search jm lambda | dir mu | tfidf] [-indexin INDEX_PATH] [-cut N]"
                + " [-metrica P | R | MAP] [-top M] [-queries all | INT1 | INT1-INT2]";

        String similarityMode = null;
        float similarityValue = 0;
        String indexPath = null;
        int cutN = 0;
        String metrica = null;
        int topM = 0;
        TypeQuery typeQuery = null;
        int queryInt1 = 0;
        int queryInt2 = 0;

        /* Check arguments */
        for (int i = 0; i < args.length; i++) {
            if ("-search".equals(args[i])) {
                similarityMode = args[++i];
                if (!similarityMode.equals("tfidf")) {
                    similarityValue = Float.parseFloat(args[++i]);
                }
            } else if ("-indexin".equals(args[i])) {
                indexPath = args[++i];
            } else if ("-cut".equals(args[i])) {
                cutN = Integer.parseInt(args[++i]);
            } else if ("-metrica".equals(args[i])) {
                metrica = args[++i];
            } else if ("-top".equals(args[i])) {
                topM = Integer.parseInt(args[++i]);
            } else if ("-queries".equals(args[i])) {
                String value = args[++i];
                if (value.equals("all")) {
                    typeQuery = TypeQuery.ALL;
                } else {
                    String[] range = value.split("-");
                    queryInt1 = Integer.parseInt(range[0]);
                    if (range.length == 2) {
                        typeQuery = TypeQuery.RANGE;
                        queryInt2 = Integer.parseInt(range[1]);
                    } else {
                        typeQuery = TypeQuery.SIMPLE;
                    }
                }
            }
        }

        if (similarityMode == null || indexPath == null || metrica == null || cutN == 0 || typeQuery == null) {
            System.err.println(usage);
            System.exit(-1);
        }

        if (!(similarityMode.equals("jm") || similarityMode.equals("dir") || similarityMode.equals("tfidf"))) {
            System.err.println("Unknown indexingmodel: " + similarityMode);
            System.exit(-1);
        }

        if (cutN < 0) {
            System.err.println("N must be greater than 0: " + cutN);
            System.exit(-1);
        }

        if (!(metrica.equals("P") || metrica.equals("R") || metrica.equals("MAP"))) {
            System.err.println("Unknown metrica: " + metrica);
            System.exit(-1);
        }

        if (topM < 0) {
            System.err.println("M must be greater than 0: " + topM);
            System.exit(-1);
        }

        if (queryInt1 < 0) {
            System.err.println("query value must be greater than 0: " + queryInt1);
            System.exit(-1);
        }

        if (typeQuery == TypeQuery.RANGE && queryInt1 > queryInt2) {
            System.err.println("Invalid query range: " + queryInt1 + "-" + queryInt2);
            System.exit(-1);
        }

        /* Query processing */
        try {
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            DirectoryReader ireader = DirectoryReader.open(dir);
            IndexSearcher isearcher = new IndexSearcher(ireader);

            switch (similarityMode) {
                case "jm":
                    isearcher.setSimilarity(new LMJelinekMercerSimilarity(similarityValue));
                    break;
                case "dir":
                    isearcher.setSimilarity(new LMDirichletSimilarity(similarityValue));
                    break;
                case "tfidf":
                    isearcher.setSimilarity(new ClassicSimilarity()); // Implementation of TFIDF
                    break;
            }

            NavigableMap<Integer,String> queries = readQueries(QUERIES_FILE);
            Map<Integer,List<Integer>> assessments = readAssessments(ASSESSMENTS_FILE);

            if (typeQuery == TypeQuery.ALL) {
                queryInt1 = queries.firstKey();
                queryInt2 = queries.lastKey();
            } else if (typeQuery == TypeQuery.SIMPLE) {
                queryInt2 = queryInt1;
            }

            QueryParser parser = new QueryParser("Contents", new StandardAnalyzer());

            int nQueriesWithRelevants = 0;
            float accum = 0;

            for (int i=queryInt1; i<=queryInt2; i++) {
                System.out.printf("%nQUERY %2d => %s%n", i, queries.get(i));
                if (assessments.get(i).size() == 0) {
                    System.out.println("Ignored. This query has no relevants.");
                    continue;
                }
                nQueriesWithRelevants++;
                System.out.printf("%5s%12s%12s%12s%12s%n", "Rank", "DocIDNPL", "Score", "Relevant", "Contents");

                int relevantsRetrieved = 0;
                float ap = 0;

                int limit = Math.max(cutN, topM);
                Query query = parser.parse(queries.get(i));
                ScoreDoc[] hits = isearcher.search(query, limit).scoreDocs;
                for (int n=0; n<Math.min(hits.length,limit); n++) {
                    Document hitDoc = isearcher.doc(hits[n].doc);
                    String docIDNPL = hitDoc.get("DocIDNPL");
                    String content = hitDoc.get("Contents");

                    float score = hits[n].score;
                    boolean isRelevant = assessments.get(i).contains(Integer.parseInt(docIDNPL));

                    /* Compute to the metric */
                    if (n < cutN) {
                        if (isRelevant) {
                            relevantsRetrieved++;
                            ap += (float) relevantsRetrieved / (n+1);
                        }
                    }

                    /* Show the ranking */
                    if (n < topM) {
                        System.out.printf("%5d%12s%12f%12b    %s%n", n+1, docIDNPL, score, isRelevant, content);
                    }
                }

                if (metrica.equals("P")) {
                    float precision = (float) relevantsRetrieved / cutN;
                    accum += precision;
                    System.out.printf("P@%d = %f%n", cutN, precision);
                } else if (metrica.equals("R")) {
                    float recall = (float) relevantsRetrieved / assessments.get(i).size();
                    accum += recall;
                    System.out.printf("R@%d = %f%n", cutN, recall);
                } else if (metrica.equals("MAP")) {
                    ap /= assessments.get(i).size();
                    accum += ap;
                    System.out.printf("AP@%d = %f%n", cutN, ap);
                }
            }

            System.out.println("--------------------------");
            if (metrica.equals("P")) {
                System.out.printf("MeanPrecision@%d = %f%n", cutN, accum / nQueriesWithRelevants);
            } else if (metrica.equals("R")) {
                System.out.printf("MeanRecall@%d = %f%n", cutN, accum / nQueriesWithRelevants);
            } else if (metrica.equals("MAP")) {
                System.out.printf("MAP@%d = %f%n", cutN, accum / nQueriesWithRelevants);
            }

            ireader.close();
            dir.close();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

    }

    private static NavigableMap<Integer,String> readQueries(String file) {
        NavigableMap<Integer,String> queries = new TreeMap<>();

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
                } else if (!line.equals("")) {  // La query no tiene relevantes
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
