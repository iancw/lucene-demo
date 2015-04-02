package edu.gwu.spatial;

import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeFilter;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;


public class USGSDomesticNamesIndex 
{
	StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_36);
	Directory index;
	
	
	public USGSDomesticNamesIndex(File indexDir) throws IOException
	{
		index = new SimpleFSDirectory(indexDir);
	}
	
	
	public void addFile(File file) throws IOException
	{
		Date start = new Date();
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_36, analyzer);
		IndexWriter w = new IndexWriter(index, config);
		
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String firstLine =reader.readLine();

		String delim = "\\|";
		String[] header = firstLine.split(delim);
		String dataLine = reader.readLine();
		while(dataLine != null)
		{
			Document doc = new Document();
			String[] dataFields = dataLine.split(delim);
			for(int i=0; i<header.length && i<dataFields.length; i++)
			{
				String curHeader = header[i];
				String curData = dataFields[i];
				if(curData == null || curData.isEmpty())
				{
					continue;
				}
				if(curHeader.contains("LAT_DEC") || curHeader.contains("LONG_DEC")
						|| curHeader.contains("ELEV"))
				{
					NumericField num = new NumericField(curHeader, Field.Store.YES, true);
					num.setDoubleValue(Double.parseDouble(curData));
					doc.add(num);
				}else if(curHeader.contains("STATE_ALPHA") || curHeader.contains("FEATURE_NAME")
						||curHeader.contains("COUNTY_NAME") || curHeader.contains("MAP_NAME")
						|| curHeader.contains("FEATURE_CLASS"))
				{
					doc.add(new Field(curHeader, curData, Field.Store.YES, Field.Index.ANALYZED));
				}
			}			
			w.addDocument(doc);
			dataLine = reader.readLine();
		}
		
		w.close();
		System.out.println("Done indexing!");
		Date end = new Date();
		long ms = end.getTime() - start.getTime();
		System.out.println("Took "+printFormattedMS(ms)+".");
	}
	
	private String printFormattedMS(long ms)
	{
		double seconds = 0.0;
		if(ms > 1000)
		{
			seconds = ms / 1000.0;
		}else
		{
			return ms+" ms";
		}
		double minutes = 0.0;
		if(seconds > 60.0)
		{
			minutes = seconds / 60.0;
			seconds = seconds - (minutes * 60.0);
		}else
		{
			return seconds +" seconds";
		}
		return minutes+" minutes, "+seconds+" seconds";
		
	}
	
	private Query buildFilteredQuery(String query, Position nw, Position se) throws ParseException
	{
		Query q = new QueryParser(Version.LUCENE_36, "FEATURE_NAME", analyzer).parse(query);
		
		NumericRangeFilter<Double> latRange = NumericRangeFilter.newDoubleRange("PRIM_LAT_DEC", 
				se.getLatitude().getDegrees(), 
				nw.getLatitude().getDegrees(),
				true, true);
		
		//west will be lower than east....
		NumericRangeFilter<Double> lonRange = NumericRangeFilter.newDoubleRange("PRIM_LONG_DEC", 
				nw.getLongitude().getDegrees(),
				se.getLongitude().getDegrees(),				
				true, true);
		FilteredQuery fq = new FilteredQuery(new FilteredQuery(q,latRange), lonRange);
		return fq;
	}
	
	private Query buildRangeQuery(String query, Position nw, Position se) throws ParseException
	{
		Query q = new QueryParser(Version.LUCENE_36, "FEATURE_NAME", analyzer).parse(query);
		
		NumericRangeQuery<Double> latRange = NumericRangeQuery.newDoubleRange("PRIM_LAT_DEC", 
				se.getLatitude().getDegrees(), 
				nw.getLatitude().getDegrees(),
				true, true);
		
		//west will be lower than east....
		NumericRangeQuery<Double> lonRange = NumericRangeQuery.newDoubleRange("PRIM_LONG_DEC", 
				nw.getLongitude().getDegrees(),
				se.getLongitude().getDegrees(),				
				true, true);
		BooleanQuery fq = new BooleanQuery();
		fq.add(q, BooleanClause.Occur.MUST);
		fq.add(latRange, BooleanClause.Occur.MUST);
		fq.add(lonRange, BooleanClause.Occur.MUST);
		return fq;
	}
	
	public List<GeoResult> findResults(String query, Position nw, Position se) throws ParseException, CorruptIndexException, IOException
	{
		Date start = new Date();
		
		int hitsPerPage = 100;
		IndexReader reader = IndexReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);
		
		
		searcher.search(buildRangeQuery(query, nw, se), collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs;
		
		List<GeoResult> results = new ArrayList<GeoResult>(hits.length);
		for(int i=0; i<hits.length; i++)
		{
			int docId = hits[i].doc;
			Document d = searcher.doc(docId);
			String primLat = d.get("PRIM_LAT_DEC");
			String primLon = d.get("PRIM_LONG_DEC");
			String coords = "";
			Angle lat = Angle.ZERO;
			Angle lon = Angle.ZERO;
			if(primLat != null && primLon != null)
			{
				lat = Angle.fromDegrees(Double.parseDouble(primLat));
				lon = Angle.fromDegrees(Double.parseDouble(primLon));
				coords = "("+d.get("PRIM_LAT_DEC")+", "+d.get("PRIM_LONG_DEC")+")";
			}
			
			String text = (i+1) +". "+d.get("FEATURE_NAME")+", "+d.get("COUNTY_NAME")+"/"+d.get("STATE_ALPHA")+" ("
			+d.get("FEATURE_CLASS")+"),  USGS Map: "+d.get("MAP_NAME") + coords;
			
					
			results.add(new GeoResult(new LatLon(lat, lon), text));
		}
		searcher.close();
		Date end = new Date();
		System.out.println(printFormattedMS(end.getTime() - start.getTime()));
		return results;
	}
}
