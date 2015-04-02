package edu.gwu.spatial;

import java.io.IOException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;


public class HelloLucene {
	
	private static void addDoc(IndexWriter w, String value) throws IOException
	{
		Document doc = new Document();
		doc.add(new Field("title", value, Field.Store.YES, Field.Index.ANALYZED));
		w.addDocument(doc);
	}
	
	public static void main(String[] args) throws CorruptIndexException, LockObtainFailedException, IOException, ParseException
	{
		StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_36);
		Directory index = new RAMDirectory();
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_36, analyzer);		
		IndexWriter w = new IndexWriter(index, config);
		
		addDoc(w, "Lucene in Action");
		addDoc(w, "Lucene for Dummies");
		addDoc(w, "Managing Gigabytes");
		addDoc(w, "The Art of Computer Science");
		
		w.close();
		
		String querystr = args.length > 0 ? args[0] : "lucene";
		
		Query q = new QueryParser(Version.LUCENE_36, "title", analyzer).parse(querystr);
		
		int hitsPerPage = 10;
		IndexReader reader = IndexReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);
		searcher.search(q, collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs;
		
		System.out.println("Found "+hits.length+" hist.");
		for(int i=0; i<hits.length; i++)
		{
			int docId = hits[i].doc;
			Document d = searcher.doc(docId);
			System.out.println((i+1) +". "+d.get("title"));
		}
		searcher.close();
	}
	
	
	
}
