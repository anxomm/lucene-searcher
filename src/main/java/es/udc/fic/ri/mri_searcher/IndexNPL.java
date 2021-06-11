package es.udc.fic.ri.mri_searcher;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class IndexNPL {

    private final static String CONFIG_FILE = "config.properties";
    private static Map<String,String> properties;

    public static void main(String[] args) {
        String usage = "java es.udc.fic.ri.mri_searcher.IndexNPL"
                + " [-index INDEX_PATH] [-openmode create | append | create_or_append]";

        String indexPath = null;
        IndexWriterConfig.OpenMode openMode = null;
        String docPath = null;
        String[] similarityParams = null;

        /* Check arguments */
        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                indexPath = args[++i];
            } else if ("-openmode".equals(args[i])) {
                String mode = args[++i];
                switch (mode) {
                    case "create":
                        openMode = IndexWriterConfig.OpenMode.CREATE;
                        break;
                    case "append":
                        openMode = IndexWriterConfig.OpenMode.APPEND;
                        break;
                    case "create_or_append":
                        openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND;
                        break;
                }
            }
        }

        if (indexPath == null || openMode == null) {
            System.err.println(usage);
            System.exit(-1);
        }

        /* Check config.properties parameters */
        docPath = getProperty("docs");
        if (docPath == null || docPath.equals("")) {
            System.err.println("docs value must be provided in config.properties");
            System.exit(-1);
        }

        if (!Files.isReadable(Paths.get(docPath))) {
            System.err.println("Document directory '" + docPath + "' does not exist or is not readable, please check the path");
            System.exit(-1);
        }

        similarityParams =  getProperty("indexingmodel").split(" ");
        if (!(similarityParams[0].equals("jm") || similarityParams[0].equals("dir") || similarityParams[0].equals("tfidf"))) {
            System.err.println("Unknown indexingmodel: " + similarityParams[0]);
            System.exit(-1);
        }

        /* Indexing */
        Date start = new Date();

        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            /* Create IndexWriter */
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(openMode);

            switch (similarityParams[0]) {
                case "jm":
                    iwc.setSimilarity(new LMJelinekMercerSimilarity(Float.parseFloat(similarityParams[1])));
                    break;
                case "dir":
                    iwc.setSimilarity(new LMDirichletSimilarity(Float.parseFloat(similarityParams[1])));
                    break;
                case "tfidf":
                    iwc.setSimilarity(new ClassicSimilarity()); // Implementation of TFIDF
                    break;
            }

            IndexWriter writer = new IndexWriter(dir, iwc);

            /* Extract each doc from the file and add to the index */
            indexDocs(writer, Paths.get(docPath));

            /* Close resources */
            writer.close();
            dir.close();

            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void indexDocs(IndexWriter writer, Path file) {

        try (Scanner scanner = new Scanner(file.toFile())) {
            String id = "";
            String content = "";
            boolean nextDoc = true;

            while (scanner.hasNext()) {
                String line = scanner.nextLine().trim();

                if (line.equals("/")) {
                    indexDoc(writer, id, content);
                    nextDoc = true;
                    content = "";
                } else if (nextDoc) {
                    id = line;
                    nextDoc = false;
                } else {
                    content = content.equals("") ? line : content + " " + line;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void indexDoc(IndexWriter writer, String id, String content) throws IOException {
        System.out.println("adding doc with id " + id);

        Document document = new Document();
        document.add(new StringField("DocIDNPL", id, Field.Store.YES));
        document.add(new TextField("Contents", content, Field.Store.YES));
        writer.addDocument(document);
    }

    private static String getProperty(String name) {
        if (properties == null) {
            ClassLoader classLoader = IndexNPL.class.getClassLoader();
            InputStream inputStream = classLoader.getResourceAsStream(CONFIG_FILE);
            Properties properties = new Properties();
            try {
                properties.load(inputStream);
                inputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            IndexNPL.properties = (Map<String, String>) new HashMap(properties);
        }
        return properties.get(name);
    }
}
