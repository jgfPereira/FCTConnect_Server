package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FuzzySearcher {

    private static final int QUERY_SEARCH_LIMIT = 10;
    private final Directory memIndex = new ByteBuffersDirectory();
    private final Document document = new Document();
    private final IndexWriter indexWriter;

    private FuzzySearcher() {
        try {
            final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(new PortugueseAnalyzer());
            indexWriter = new IndexWriter(memIndex, indexWriterConfig);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> fuzzySearch(List<String> data, String fieldName, String queryString) {
        return new FuzzySearcher().fuzzySearchImpl(data, fieldName, queryString);
    }

    private void populateIndex(List<String> data, final String fieldName) {
        try {
            for (String fieldValue : data) {
                document.add(new TextField(fieldName, fieldValue, Field.Store.YES));
            }
            indexWriter.addDocument(document);
            indexWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Document> searchIndex(String fieldName, String queryString) {
        try {
            final Term term = new Term(fieldName, queryString);
            final Query query = new FuzzyQuery(term);
            final IndexReader indexReader = DirectoryReader.open(memIndex);
            final IndexSearcher searcher = new IndexSearcher(indexReader);
            final TopDocs hitDocs = searcher.search(query, QUERY_SEARCH_LIMIT);
            final StoredFields storedFields = indexReader.storedFields();
            final List<Document> documents = new ArrayList<>();
            for (ScoreDoc hit : hitDocs.scoreDocs) {
                documents.add(storedFields.document(hit.doc));
            }
            return documents;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> fuzzySearchImpl(List<String> data, String fieldName, String queryString) {
        populateIndex(data, fieldName);
        final List<Document> hits = searchIndex(fieldName, queryString);
        final List<String> res = new ArrayList<>();
        for (Document hit : hits) {
            res.addAll(Arrays.asList(hit.getValues(fieldName)));
        }
        return res;
    }
}