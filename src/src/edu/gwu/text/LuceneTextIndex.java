package edu.gwu.text;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.analysis.CachingTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.lucene.LucenePDFDocument;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

public class LuceneTextIndex {
	StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_36);
	Directory index;	
	
	public LuceneTextIndex(File indexDir) throws IOException
	{
		index = new SimpleFSDirectory(indexDir);
	}
	
	public void addFile(File file) throws IOException, SAXException, TikaException
	{
		addFilePDFBox(file);
	}
	
	public void addFileTika(File file) throws IOException, SAXException, TikaException
	{
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_36, analyzer);
		IndexWriter w = new IndexWriter(index, config);

	    FileInputStream is = new FileInputStream(file);

	    BodyContentHandler contenthandler = new BodyContentHandler();
	    Metadata metadata = new Metadata();
	    metadata.set(Metadata.RESOURCE_NAME_KEY, file.getName());
	    Parser parser = new AutoDetectParser();
	    ParseContext ctxt = new ParseContext();
	    parser.parse(is, contenthandler, metadata, ctxt);

	    Document doc = new Document();
	    doc.add(new Field("name",file.getName(),Field.Store.YES, Field.Index.NOT_ANALYZED));
	    doc.add(new Field("path",file.getCanonicalPath(),Field.Store.YES, Field.Index.NOT_ANALYZED));
	    if(metadata.get(Metadata.TITLE) != null)
	    {
	    	doc.add(new Field("title",metadata.get(Metadata.TITLE),Field.Store.YES, Field.Index.ANALYZED));
	    }
	    if(metadata.get(Metadata.AUTHOR) != null)
	    {
	    	doc.add(new Field("author",metadata.get(Metadata.AUTHOR),Field.Store.YES, Field.Index.ANALYZED));
	    }
	    String contents = contenthandler.toString();
	    System.out.println("Contents: "+contents.length());
	    doc.add(new Field("contents",contents,Field.Store.YES,Field.Index.ANALYZED));
	    	    
	    w.addDocument(doc);
	    w.close();
	}
	
	public void addFilePDFBox(File file) throws IOException
	{
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_36, analyzer);
		IndexWriter w = new IndexWriter(index, config);
		
		if(file.getName().toLowerCase().endsWith("pdf"))
		{
			
			Document pdfDoc = LucenePDFDocument.getDocument(file);
			List<Fieldable> fields = pdfDoc.getFields();
			for(Fieldable field : fields)
			{
				System.out.println("Field name: "+field.name()+", field value: "+field.stringValue());
			}
			
			PDFParser parser = new PDFParser(new FileInputStream(file));  
			parser.parse();  
			COSDocument cd = parser.getDocument();  
			PDFTextStripper stripper = new PDFTextStripper();			
			String text = stripper.getText(new PDDocument(cd));
			pdfDoc.add(new Field("contents", text, Store.YES, Index.ANALYZED));
			w.addDocument(pdfDoc);
			cd.close();			
		}
		w.close();		
	}
	
	public List getTerms()
	{
		List<String> results = new LinkedList<String>();
		try
		{
			IndexReader reader = IndexReader.open(index);
			TermEnum enumTerm = reader.terms();

			
			while(enumTerm.next())
			{
				Term term = enumTerm.term();
				results.add(term.field()+": "+term.text()+" (doc freq="+enumTerm.docFreq()+")");
			}
		}catch(IOException e)
		{
			e.printStackTrace();
		}
		return results;
	}
	
	private String highlightResults(Query q, Fieldable f) throws IOException, InvalidTokenOffsetsException
	{
		TokenStream ts = TokenSources.getTokenStream("contents", f.stringValue(), analyzer);
		QueryScorer scorer = new QueryScorer(q, "contents");
		scorer.init(new CachingTokenFilter(ts));
		
		Fragmenter frag = new SimpleSpanFragmenter(scorer, 30);
		Highlighter highlighter = new Highlighter(scorer);
		highlighter.setTextFragmenter(frag);
		String[] fragments = highlighter.getBestFragments(ts, f.stringValue(), 10);
		StringBuffer res = new StringBuffer();
		res.append("<b>Matches:</b><br/>");
		for(String fragment : fragments)
		{
			res.append(fragment);
			res.append("<br/>");
		}
		return res.toString();
	}
	
	public List findResults(String query) throws ParseException, CorruptIndexException, IOException, InvalidTokenOffsetsException
	{
		Query q = new QueryParser(Version.LUCENE_36, "contents", analyzer).parse(query);
		
		int hitsPerPage = 100;
		IndexReader reader = IndexReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);		
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);
		
		try
		{
			searcher.search(q, collector);		
			
			ScoreDoc[] hits = collector.topDocs().scoreDocs;
			List results = new ArrayList(hits.length);
			for(int i=0; i<hits.length; i++)
			{
				Document d = searcher.doc(hits[i].doc);
				StringBuffer res = new StringBuffer();
				res.append("<html>");
				Fieldable f = d.getFieldable("path");
				if(f != null)
				{
					res.append("<b>");
					res.append(f.name()+": ");
					res.append("</b>");
					res.append(f.stringValue());				
					res.append("<br/>");
				}
				f = d.getFieldable("contents");
				if(f != null)
				{
					res.append(highlightResults(q, f));
				}
				res.append("</html>");
				results.add(res.toString());				
			}
			
			return results;
		}finally
		{
			searcher.close();	
		}
	}
	
	public List findResultsExplain(String query) throws ParseException, CorruptIndexException, IOException, InvalidTokenOffsetsException
	{
		Query q = new QueryParser(Version.LUCENE_36, "contents", analyzer).parse(query);
		
		int hitsPerPage = 100;
		IndexReader reader = IndexReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);		
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);
				
		try
		{
			searcher.search(q, collector);		
			
			ScoreDoc[] hits = collector.topDocs().scoreDocs;
			List results = new ArrayList(hits.length);
			String [] displayFields = new String[]{"name", "path", "summary"};
			for(int i=0; i<hits.length; i++)
			{				
				Document d = searcher.doc(hits[i].doc);
				StringBuffer res = new StringBuffer();
				res.append("<html>");			
				
				for(String dispField : displayFields)
				{
					Fieldable f = d.getFieldable(dispField);
					if(f == null)
					{
						continue;
					}
					res.append("<b>");
					res.append(f.name()+": ");
					res.append("</b>");
					res.append(f.stringValue());				
					res.append("<br/>");
				}
				res.append("<b>Score: </b>: "+hits[i].score+"<br/>");
				res.append("Explanation:<br/>");
				res.append(searcher.explain(q, hits[i].doc).toHtml());
				
				Fieldable f = d.getFieldable("contents");
				if(f != null)
				{
					res.append(highlightResults(q, f));
				}
				res.append("</html>");
				results.add(res.toString());
				
			}
			return results;
		}finally
		{
			searcher.close();	
		}
	}	
}
