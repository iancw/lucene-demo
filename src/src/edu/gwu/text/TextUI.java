package edu.gwu.text;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.tika.exception.TikaException;
import org.xml.sax.SAXException;

public class TextUI extends JFrame implements KeyListener, DocumentListener
{
	private JTextField search;
	private final ThreadPoolExecutor executor;
	private final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(10);
	
	private final LuceneTextIndex index;
	private DefaultListModel listModel;
	private JCheckBox explainResults;
	private final AtomicBoolean searching = new AtomicBoolean(false);
	
	public TextUI() throws IOException
	{
		executor = new ThreadPoolExecutor(3, 3, 10, TimeUnit.SECONDS, workQueue, new RejectedExecutionHandler()
		{
			@Override
			public void rejectedExecution(Runnable arg0, ThreadPoolExecutor arg1) {
				System.out.println("Work rejected");
			}
		});
		index = new LuceneTextIndex(new File(System.getProperty("user.dir"), "text-index"));
		updateUI();
	}
	
	private void updateUI()
	{
		search = new JTextField("enter search here...");
		search.addKeyListener(this);
		search.getDocument().addDocumentListener(this);
		search.setPreferredSize(new Dimension(400, 20));
		
		JButton add = new JButton("Index file...");
		add.addActionListener(new ActionListener()
		{
			String defaultDir = System.getProperty("user.dir")+"../samplescen";
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				FileDialog fd = new FileDialog(TextUI.this, "Select Documents");
				fd.setMode(FileDialog.LOAD);
				
				fd.setDirectory(defaultDir);
				fd.setVisible(true);
				String dir = fd.getDirectory();
				String selPath = fd.getFile();
				if(dir != null && selPath != null)
				{
					File file = new File(dir, selPath);
					System.out.println("Indexing "+file.getName()+"...");
					ProgressMonitor mon = new ProgressMonitor(TextUI.this,
							"Indexing "+file.getName()+"...",
                            "", 0, 100);
					mon.setNote("Indexing "+file.getName()+"...");
					
					try {
						index.addFile(file);
					} catch (IOException e) {
						e.printStackTrace();
					} catch (SAXException e) {
						e.printStackTrace();
					} catch (TikaException e) {
						e.printStackTrace();
					}
					System.out.println("done!");
				}
			}
		});
		
		JButton terms = new JButton("Show index terms");
		terms.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e)
			{
				List terms = index.getTerms();
				updateResults(terms);
			}
		});
		
		explainResults = new JCheckBox("Explain results");
		
		JLabel searchLab = new JLabel("Search: ");
		JPanel searchPan = new JPanel();
		
		searchPan.add(searchLab);
		searchPan.add(search);
		searchPan.add(add);
		searchPan.add(terms);
		searchPan.add(explainResults);
		
		listModel = new DefaultListModel();
		listModel.addElement("Search result will go here");
		JList searchResults = new JList(listModel);
		
		JScrollPane scroller = new JScrollPane(searchResults);
		scroller.setPreferredSize(new Dimension(800, 350));
		
		this.getContentPane().add(searchPan, BorderLayout.NORTH);
		this.getContentPane().add(scroller, BorderLayout.CENTER);
	}
	

	private void updateResults(final List results)
	{		
		SwingUtilities.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				listModel.clear();
				for(Object res : results)
				{
					listModel.addElement(res);
				}
			}
		});
	}
	
	public void doSearch()
	{
		if(searching.get())
		{
			return;
		}
		if(search.getText().isEmpty())
		{
			return;
		}
		try {
			searching.set(true);
			List results;
			if(explainResults.isSelected())
			{
				results = index.findResultsExplain(search.getText());
			}else
			{
				results = index.findResults(search.getText());
			}
			
			searching.set(false);
			updateResults(results);			
		} catch (CorruptIndexException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidTokenOffsetsException e) {
			e.printStackTrace();
		} catch(RuntimeException e)
		{
			e.printStackTrace();
		}
		finally
		{
			searching.set(false);
		}		
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
	
	public static void main(String[] args) throws IOException
	{
		TextUI ui = new TextUI();		
        
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
