This readme describes the use of the two Lucene demo applications in examples.jar.

To run the spatial demo:

java -cp examples.jar edu.gwu.spatial.SpatialUI

This will display a GUI with a map (thanks to NASA's WorldWind package), a search bar, an "Index files..." button, and a results display area.  This application is designed to index USGS Domestic Names file, which is delimited by a "|" character.  This file is large, and can be downloaded in its entirety from http://geonames.usgs.gov/domestic/download_data.htm.  Click on the "NationalFile_yyyymmdd.zip" link.  Because of its size (300M unzipped, 70M zipped), a reduced version that contains only a few locations in Arizona was included.  It is named NationalFile_20120416_reduced.txt.

To index a domestic names file, click the Index file... button and select the domestic names file.

Once this has be indexed, search for place names by typing in the search bar.  If you zoom the map display around, it will restrict matches to only those within the current view bounds.  Searches are initiated by typing in the search bar; camera movements do not trigger new searches.


To run the PDF / MS Word demo:

java -jar examples.jar

This GUI has no map, but has a couple extra buttons.  The index file... button allows you to select any PDF or MS Word document to add to the index.  Those files can then be searched using the search bar.  The 'Show index terms' button dumps the contents of the Lucene index to the results display area.  The 'Explain results' checkbox causes query results to include Lucene's score explanation. 
