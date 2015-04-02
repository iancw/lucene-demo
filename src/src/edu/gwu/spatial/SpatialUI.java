package edu.gwu.spatial;

import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.Factory;
import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.EarthFlat;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.layers.Earth.BMNGOneImage;
import gov.nasa.worldwind.render.GlobeAnnotation;
import gov.nasa.worldwind.util.StatusBar;
import gov.nasa.worldwindx.examples.util.HighlightController;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryParser.ParseException;


public class SpatialUI extends JFrame implements DocumentListener, KeyListener
{
	protected static class WWPanel extends JPanel
    {
        protected WorldWindowGLCanvas wwd;
        protected HighlightController highlightController;

        public WWPanel(Dimension size, Model model)
        {
            this.wwd = new WorldWindowGLCanvas();
            this.wwd.setSize(size);
            this.wwd.setModel(model);

            this.setLayout(new BorderLayout(5, 5));
            this.add(this.wwd, BorderLayout.CENTER);

            StatusBar statusBar = new StatusBar();
            statusBar.setEventSource(wwd);
            this.add(statusBar, BorderLayout.SOUTH);

            this.highlightController = new HighlightController(this.wwd, SelectEvent.ROLLOVER);
        }
    }
	
	protected Dimension canvasSize = new Dimension(800, 600);
	private WWPanel wwPanel;
	private RenderableLayer resultsLayer = new RenderableLayer();
	private final ThreadPoolExecutor executor;
	private final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(10);
	
	private USGSDomesticNamesIndex lucene;
	
	JTextField search;
	DefaultListModel listModel;
	
	public SpatialUI() throws IOException
	{
		executor = new ThreadPoolExecutor(3, 3, 10, TimeUnit.SECONDS, workQueue, new RejectedExecutionHandler()
		{
			@Override
			public void rejectedExecution(Runnable arg0, ThreadPoolExecutor arg1) {
				System.out.println("Work rejected");
			}
		});
		
		lucene = new USGSDomesticNamesIndex(new File("lucene-index"));
				
		buildUI();
	}
	
	private void buildUI()
	{
		LayerList layers = this.makeCommonLayers();
		EarthFlat globe = new EarthFlat();
		Model model = new BasicModel(globe, new LayerList(layers));
		this.wwPanel = new WWPanel(canvasSize, model);
		
		resultsLayer.setName("Search results");
		model.getLayers().add(resultsLayer);

		
		search = new JTextField("enter search here...");
		search.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				//search.setText("");
			}
		});
		search.addKeyListener(this);
		search.getDocument().addDocumentListener(this);
		search.setPreferredSize(new Dimension(400, 20));
		
		JButton add = new JButton("Index file...");
		add.addActionListener(new ActionListener()
		{
			String defaultDir = System.getProperty("user.dir");
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				FileDialog fd = new FileDialog(SpatialUI.this, "Select Documents");
				fd.setMode(FileDialog.LOAD);
				fd.setDirectory(defaultDir);
				fd.setVisible(true);
				String dir = fd.getDirectory();
				String selPath = fd.getFile();
				if(dir != null && selPath != null)
				{
					File file = new File(selPath);
					System.out.println("Indexing "+file.getName()+"...");
					ProgressMonitor mon = new ProgressMonitor(SpatialUI.this,
							"Indexing "+file.getName()+"...",
                            "", 0, 100);
					mon.setNote("Indexing "+file.getName()+"...");
					
					try {
						lucene.addFile(file);
					} catch (IOException e) {
					}
				}
			}
		});
		
		JLabel searchLab = new JLabel("Search: ");
		JPanel searchPan = new JPanel();
		
		searchPan.add(searchLab);
		searchPan.add(search);
		searchPan.add(add);
		
		listModel = new DefaultListModel();
		listModel.addElement("Search result will go here");
		JList searchResults = new JList(listModel);
		
		JScrollPane scroller = new JScrollPane(searchResults);
		scroller.setPreferredSize(new Dimension(800, 350));
		
		this.getContentPane().add(searchPan, BorderLayout.CENTER);
		this.getContentPane().add(this.wwPanel, BorderLayout.NORTH);
		this.getContentPane().add(scroller, BorderLayout.SOUTH);
        
	}
	
	private void updateResults(final List<GeoResult> results)
	{
		resultsLayer.removeAllRenderables();
		for(GeoResult res : results)
		{
			resultsLayer.addRenderable(new GlobeAnnotation(res.name, new Position(res.pos, 5)));
		}
		resultsLayer.firePropertyChange(AVKey.LAYER, null, resultsLayer);
		
		SwingUtilities.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				listModel.clear();
				for(GeoResult res : results)
				{
					listModel.addElement(res);
				}
			}
		});
	}
	
	private void doSearch()
	{
		try {
			Dimension size = this.wwPanel.getSize();
			Position pos1 = this.wwPanel.wwd.getView().computePositionFromScreenPoint(0, 0);			
			Position pos2 = this.wwPanel.wwd.getView().computePositionFromScreenPoint(size.width-1, size.height-1);
			if(search.getText().isEmpty())
			{
				updateResults(Collections.EMPTY_LIST);
			}
			
			
			System.out.println("Finding results between "+pos1+", and "+pos2);
			List<GeoResult> results = lucene.findResults(search.getText(), pos1, pos2);
			System.out.println("Found "+results.size()+" results");
			updateResults(results);
		} catch (CorruptIndexException e) {
		} catch (ParseException e) {
		} catch (IOException e) {
		}
	}
	
    protected LayerList makeCommonLayers()
    {
        LayerList layerList = new LayerList();

        layerList.add(new BMNGOneImage());

        Factory factory = (Factory) WorldWind.createConfigurationComponent(AVKey.LAYER_FACTORY);
        Layer layer = (Layer) factory.createFromConfigSource("config/Earth/BMNGWMSLayer.xml", null);
        layer.setEnabled(true);
        layerList.add(layer);

        return layerList;
    }
	
	public static void main(String[] args) throws IOException
	{
		SpatialUI ui = new SpatialUI();		
        
		ui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		ui.pack();
		ui.setVisible(true);
	}

	@Override
	public void keyPressed(KeyEvent arg0) {
	}

	@Override
	public void keyReleased(KeyEvent arg0) {
	}

	@Override
	public void keyTyped(KeyEvent arg0) {		
	}

	@Override
	public void changedUpdate(DocumentEvent arg0) {
	}

	@Override
	public void insertUpdate(DocumentEvent arg0) {
		executor.execute(new Runnable()
		{
			@Override
			public void run()
			{
				doSearch();
			}
		});
	}

	@Override
	public void removeUpdate(DocumentEvent arg0) {
		executor.execute(new Runnable()
		{
			@Override
			public void run()
			{
				doSearch();
			}
		});
	}

}
