package e.edit;

import e.gui.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import javax.swing.text.JTextComponent;

import java.util.List;
import org.jdesktop.swingworker.SwingWorker;

public class FindInFilesPanel extends JPanel implements WorkspaceFileList.Listener {
    private SearchField regexField = new SearchField("Files Containing");
    private SearchField filenameRegexField = new SearchField("Files Named");
    private JLabel status = new JLabel(" ");
    private ETree matchView;
    private TextChangeTimeout changeTimeout;
    
    private DefaultTreeModel matchTreeModel;
    
    /** Which workspace is this "Find in Files" for? */
    private Workspace workspace;
    
    /** How our worker threads know whether they're still relevant. */
    private static final AtomicInteger currentSequenceNumber = new AtomicInteger(0);
    
    /** We share these between all workspaces, to make it harder to accidentally launch a denial-of-service attack against ourselves. */
    private static final ExecutorService definitionFinderExecutor = ThreadUtilities.newFixedThreadPool(8, "Find Definitions");
    
    public interface ClickableTreeItem {
        public void open();
    }
    
    public class MatchingLine implements ClickableTreeItem {
        private String line;
        private File file;
        private Pattern pattern;
        
        public MatchingLine(String line, File file, Pattern pattern) {
            this.line = line;
            this.file = file;
            this.pattern = pattern;
        }
        
        public void open() {
            EWindow window = Evergreen.getInstance().openFile(file.toString());
            if (window instanceof ETextWindow) {
                ETextWindow textWindow = (ETextWindow) window;
                FindAction.INSTANCE.findInText(textWindow, PatternUtilities.toString(pattern));
                final int lineNumber = Integer.parseInt(line.substring(1, line.indexOf(':', 1)));
                textWindow.getTextArea().goToLine(lineNumber);
            }
        }
        
        public String toString() {
            return line;
        }
    }
    
    public class MatchingFile implements ClickableTreeItem {
        private File file;
        private String name;
        private int matchCount;
        private Pattern pattern;
        private boolean containsDefinition;
        
        /**
         * For matches based just on filename.
         */
        public MatchingFile(File file, String name) {
            this(file, name, 0, null);
        }
        
        /**
         * For matches based on filename and a regular expression.
         */
        public MatchingFile(File file, String name, int matchCount, Pattern pattern) {
            this.file = file;
            this.name = name;
            this.matchCount = matchCount;
            this.pattern = pattern;
            this.containsDefinition = containsDefinition;
            if (pattern != null) {
                definitionFinderExecutor.submit(new DefinitionFinder(file, pattern, this));
            }
        }
        
        public void setContainsDefinition(boolean newState) {
            this.containsDefinition = newState;
            // The check-in comment for revision 673 mentioned that JTree
            // caches node widths, and that changing the font for an individual
            // node could lead to unwanted clipping. The caching is done by
            // subclasses of javax.swing.tree.AbstractLayoutCache, and there
            // are methods called invalidatePathBounds and invalidateSizes, but
            // neither is easily accessible. We can access the first only via
            // the support for editable nodes. The latter we can access via
            // various setters. This relies on the fact that the setter
            // setRootVisible doesn't optimize out the case where the new state
            // is the same as the old state. We also need to explicitly request
            // a repaint. But this potentially fragile hack does let us work
            // around a visual glitch in the absence of an approved means of
            // invalidating the JTree UI delegate's layout cache. Having the
            // source is one of the things that makes Java great!
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    matchView.setRootVisible(true);
                    matchView.setRootVisible(false);
                    matchView.repaint();
                }
            });
        }
        
        public boolean containsDefinition() {
            return containsDefinition;
        }
        
        public void open() {
            EWindow window = Evergreen.getInstance().openFile(workspace.prependRootDirectory(name));
            if (window instanceof ETextWindow && pattern != null) {
                ETextWindow textWindow = (ETextWindow) window;
                FindAction.INSTANCE.findInText(textWindow, PatternUtilities.toString(pattern));
                textWindow.getTextArea().findNext();
            }
        }
        
        public String toString() {
            StringBuilder result = new StringBuilder(file.getName());
            if (matchCount != 0) {
                result.append(" (");
                result.append(StringUtilities.pluralize(matchCount, "matching line", "matching lines"));
                if (containsDefinition) {
                    result.append(" including definition");
                }
                result.append(")");
            }
            return result.toString();
        }
    }
    
    public class FileFinder extends SwingWorker<DefaultMutableTreeNode, DefaultMutableTreeNode> {
        private List<String> fileList;
        private DefaultMutableTreeNode matchRoot;
        private String regex;
        private String fileRegex;
        private String errorMessage;
        
        private HashMap<String, DefaultMutableTreeNode> pathMap = new HashMap<String, DefaultMutableTreeNode>();
        
        private int sequenceNumber;
        
        private AtomicInteger doneFileCount;
        private AtomicInteger matchingFileCount;
        private int totalFileCount;
        private int percentage;
        
        private long startTimeMs;
        private long endTimeMs;
        
        public FileFinder() {
            this.sequenceNumber = currentSequenceNumber.incrementAndGet();
            
            this.matchRoot = new DefaultMutableTreeNode();
            this.regex = regexField.getText();
            this.fileRegex = filenameRegexField.getText();
            this.fileList = workspace.getFileList().getListOfFilesMatching(fileRegex);
            
            this.doneFileCount = new AtomicInteger(0);
            this.matchingFileCount = new AtomicInteger(0);
            this.totalFileCount = fileList.size();
            this.percentage = -1;
            
            matchTreeModel.setRoot(matchRoot);
        }
        
        private void updateStatus() {
            int newPercentage = (doneFileCount.get() * 100) / totalFileCount;
            if (newPercentage != percentage) {
                percentage = newPercentage;
                String status = makeStatusString() + " (" + percentage + "%)";
                setStatus(status, false);
            }
        }
        
        @Override
        protected DefaultMutableTreeNode doInBackground() {
            Thread.currentThread().setName("Search for \"" + regex + "\" in files matching \"" + fileRegex + "\"");
            
            startTimeMs = System.currentTimeMillis();
            endTimeMs = 0;
            
            try {
                Pattern pattern = PatternUtilities.smartCaseCompile(regex);
                
                final int threadCount = Runtime.getRuntime().availableProcessors() + 1;
                ThreadPoolExecutor executor = (ThreadPoolExecutor) ThreadUtilities.newFixedThreadPool(threadCount, "find-in-files");
                for (String candidate : fileList) {
                    executor.execute(new FileSearchRunnable(candidate, pattern));
                }
                executor.shutdown();
                try {
                    executor.awaitTermination(3600, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    ex = ex; // Fine; we're still finished.
                }
                
                endTimeMs = System.currentTimeMillis();
                Log.warn("Search for \"" + regex + "\" in files matching \"" + fileRegex + "\" took " + (endTimeMs - startTimeMs) + " ms.");
            } catch (PatternSyntaxException ex) {
                errorMessage = ex.getDescription();
            } catch (Exception ex) {
                Log.warn("Problem searching files for \"" + regex + "\".", ex);
            }
            return matchRoot;
        }
        
        private DefaultMutableTreeNode getPathNode(String pathname) {
            String[] pathElements = pathname.split(Pattern.quote(File.separator));
            String pathSoFar = "";
            DefaultMutableTreeNode parentNode = matchRoot;
            DefaultMutableTreeNode node = matchRoot;
            synchronized (matchView) {
                for (int i = 0; i < pathElements.length - 1; ++i) {
                    pathSoFar += pathElements[i] + File.separator;
                    node = pathMap.get(pathSoFar);
                    if (node == null) {
                        node = new DefaultMutableTreeNode(pathElements[i] + File.separator);
                        parentNode.add(node);
                        matchTreeModel.nodesWereInserted(parentNode, new int[] { parentNode.getIndex(node) });
                        pathMap.put(pathSoFar, node);
                    }
                    parentNode = node;
                }
            }
            return node;
        }
        
        @Override
        protected void process(DefaultMutableTreeNode... treeNodes) {
            if (currentSequenceNumber.get() != sequenceNumber) {
                return;
            }
            
            synchronized (matchView) {
                for (DefaultMutableTreeNode node : treeNodes) {
                    // I've no idea why new nodes default to being collapsed.
                    matchView.expandOrCollapsePath(node.getPath(), true);
                }
            }
        }
        
        @Override
        protected void done() {
            if (currentSequenceNumber.get() != sequenceNumber) {
                return;
            }
            
            if (errorMessage != null) {
                setStatus(errorMessage, true);
            } else {
                setStatus(makeStatusString(), false);
            }
        }
        
        private String makeStatusString() {
            String status = matchingFileCount.get() + " / " + StringUtilities.pluralize(totalFileCount, "file", "files");
            int indexedFileCount = workspace.getFileList().getIndexedFileCount();
            if (indexedFileCount != -1 && indexedFileCount != totalFileCount) {
                status += " (from " + indexedFileCount + ")";
            }
            status += " match.";
            if (endTimeMs != 0) {
                status += " Took " + TimeUtilities.msToString(endTimeMs - startTimeMs) + ".";
            }
            return status;
        }
        
        private class FileSearchRunnable implements Runnable {
            private String candidate;
            private Pattern pattern;
            
            private FileSearchRunnable(String candidate, Pattern pattern) {
                this.candidate = candidate;
                this.pattern = pattern;
            }
            
            public void run() {
                if (currentSequenceNumber.get() != sequenceNumber) {
                    return;
                }
                try {
                    long t0 = System.currentTimeMillis();
                    FileSearcher fileSearcher = new FileSearcher(pattern);
                    File file = FileUtilities.fileFromParentAndString(workspace.getRootDirectory(), candidate);
                    
                    // Update our percentage-complete status, but only if we've
                    // taken enough time for the user to start caring, so we
                    // don't make quick searches unnecessarily slow.
                    doneFileCount.incrementAndGet();
                    if (t0 - startTimeMs > 300) {
                        updateStatus();
                    }
                    
                    if (regex.length() != 0) {
                        ArrayList<String> matches = new ArrayList<String>();
                        boolean wasText = fileSearcher.searchFile(file, matches);
                        if (wasText == false) {
                            // FIXME: should we do the grep(1) thing of "binary file <x> matches"?
                            return;
                        }
                        long t1 = System.currentTimeMillis();
                        if (t1 - t0 > 500) {
                            Log.warn("Searching file \"" + file + "\" for \"" + regex + "\" took " + (t1 - t0) + "ms!");
                        }
                        final int matchCount = matches.size();
                        if (matchCount > 0) {
                            synchronized (matchView) {
                                DefaultMutableTreeNode pathNode = getPathNode(candidate);
                                MatchingFile matchingFile = new MatchingFile(file, candidate, matchCount, pattern);
                                DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(matchingFile);
                                for (String line : matches) {
                                    fileNode.add(new DefaultMutableTreeNode(new MatchingLine(line, file, pattern)));
                                }
                                pathNode.add(fileNode);
                                matchTreeModel.nodesWereInserted(pathNode, new int[] { pathNode.getIndex(fileNode) });
                                matchingFileCount.incrementAndGet();
                                // Make sure the new node gets expanded.
                                publish(fileNode);
                            }
                        }
                    } else {
                        synchronized (matchView) {
                            DefaultMutableTreeNode pathNode = getPathNode(candidate);
                            pathNode.add(new DefaultMutableTreeNode(new MatchingFile(file, candidate)));
                            // Make sure the new node gets expanded.
                            publish(pathNode);
                        }
                    }
                } catch (FileNotFoundException ex) {
                    // This special case is worthwhile if your workspace's index is out of date.
                    // A common case is when the index contains generated files that may be removed during a build.
                    ex = ex;
                } catch (Throwable th) {
                    Log.warn("FileSearchRunnable.call caught something", th);
                }
            }
        }
    }
    
    public static class DefinitionFinder implements Runnable, TagReader.TagListener {
        private File file;
        private MatchingFile matchingFile;
        private Pattern pattern;
        
        public DefinitionFinder(File file, Pattern pattern, MatchingFile matchingFile) {
            this.file = file;
            this.matchingFile = matchingFile;
            this.pattern = pattern;
        }
        
        public void run() {
            // FIXME: obviously not all files are really UTF-8.
            new TagReader(file, null, "UTF-8", null, this);
        }
        
        public void tagFound(TagReader.Tag tag) {
            // Function prototypes and Java packages probably aren't interesting.
            if (tag.type == TagType.PROTOTYPE) {
               return;
            }
            if (tag.type == TagType.PACKAGE) {
               return;
            }
            if (pattern.matcher(tag.identifier).find()) {
                matchingFile.setContainsDefinition(true);
            }
        }
        
        public void taggingFailed(Exception ex) {
            Log.warn("Failed to use tags to check for a definition.", ex);
        }
    }
    
    public synchronized void showMatches() {
        if (matchView.isShowing() == false) {
            // There's no point doing a search if the user can't see the results.
            return;
        }
        new Thread(new FileFinder(), "Find in Files for " + workspace.getTitle()).start();
    }
    
    public void initMatchList() {
        matchTreeModel = new DefaultTreeModel(null);
        matchView = new ETree(matchTreeModel);

        matchView.setRootVisible(false);
        matchView.setShowsRootHandles(true);
        matchView.putClientProperty("JTree.lineStyle", "None");
        
        // Set a custom cell renderer, and tell the tree that all cells have the same height to improve performance.
        matchView.setCellRenderer(new MatchTreeCellRenderer());
        TreeCellRenderer renderer = matchView.getCellRenderer();
        JComponent rendererComponent = (JComponent) renderer.getTreeCellRendererComponent(matchView, new DefaultMutableTreeNode("Hello"), true, true, true, 0, true);
        matchView.setRowHeight(rendererComponent.getPreferredSize().height);
        matchView.setLargeModel(true);
        
        matchView.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) matchView.getLastSelectedPathComponent();
                if (node == null) {
                    return;
                }
                
                // Directories are currently represented by String objects.
                Object userObject = node.getUserObject();
                if (userObject instanceof ClickableTreeItem) {
                    ClickableTreeItem match = (ClickableTreeItem) node.getUserObject();
                    match.open();
                }
            }
        });
        
        ComponentUtilities.divertPageScrollingFromTo(regexField, matchView);
        ComponentUtilities.divertPageScrollingFromTo(filenameRegexField, matchView);
    }
    
    public void fileListStateChanged(boolean isNowValid) {
        if (isNowValid) {
            showMatches();
            matchView.setEnabled(true);
        } else {
            switchToFakeTree();
            matchView.setEnabled(false);
        }
    }
    
    private void switchToFakeTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        root.add(new DefaultMutableTreeNode("Rescan in progress..."));
        matchTreeModel.setRoot(root);
    }
    
    public class MatchTreeCellRenderer extends DefaultTreeCellRenderer {
        private Font defaultFont = null;
        
        public MatchTreeCellRenderer() {
            setClosedIcon(null);
            setOpenIcon(null);
            setLeafIcon(null);
        }
        
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean isLeaf,  int row,  boolean hasFocus) {
            Component c = super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, isLeaf, row, hasFocus);
            
            // Unlike the foreground Color, the Font gets remembered, so we
            // need to manually revert it each time.
            if (defaultFont == null) {
                defaultFont = ChangeFontAction.getConfiguredFont();
            }
            c.setFont(defaultFont);
            
            // Work around JLabel's tab-rendering stupidity.
            String text = getText();
            if (text != null && text.contains("\t")) {
                setText(text.replaceAll("\t", "    "));
            }
            
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            if (node.getUserObject() instanceof MatchingFile) {
                MatchingFile file = (MatchingFile) node.getUserObject();
                if (file.containsDefinition()) {
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                }
            } else {
                c.setForeground(Color.GRAY);
            }
            
            c.setEnabled(tree.isEnabled());
            
            return c;
        }
    }
    
    private void setStatus(final String message, final boolean isError) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                status.setForeground(isError ? Color.RED : Color.BLACK);
                status.setText(message);
            }
        });
    }
    
    public FindInFilesPanel(Workspace workspace) {
        this.workspace = workspace;
        
        initMatchList();
        initUI();
        initSaveMonitor();
        
        workspace.getFileList().addFileListListener(this);
    }

    private void addSearchFieldActions(SearchField entry) {
        ActionMap am = entry.getActionMap();
        am.put("next-result", new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("actionPerformed::next-result");
                    //matchView.setSelectedIndex(matchTreeModel.getSelectedIndex() + 1);
                }
            });
        am.put("previous-result", new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("actionPerformed::previous-result");
                    //matchView.setSelectedIndex(matchTreeModel.getSelectedIndex() - 1);
                }
            });
        am.put("open-result", new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("actionPerformed::open-result");
                }
            });
    }
    
    private void initUI() {
        addSearchFieldActions(regexField);
        addSearchFieldActions(filenameRegexField);

        changeTimeout = new TextChangeTimeout(new JTextComponent[] { 
            regexField, 
            filenameRegexField 
        }, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    showMatches();
                }
            });

        addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                   regexField.requestFocus();
                }
            });

        JPanel entries = new JPanel(new BorderLayout());
        entries.add(regexField, BorderLayout.NORTH); // Files Containing
        entries.add(filenameRegexField, BorderLayout.SOUTH); // Whose Names Match

        JPanel entriesStatus = new JPanel(new BorderLayout());
        entriesStatus.add(entries, BorderLayout.NORTH);
        entriesStatus.add(PatternUtilities.addRegularExpressionHelpToComponent(status), 
                          BorderLayout.SOUTH);

        setName("Find in Files");
        setLayout(new BorderLayout());
        add(entriesStatus, BorderLayout.NORTH);
        add(new JScrollPane(matchView), BorderLayout.CENTER); // Matches
    }
    
    private void initSaveMonitor() {
        // Register for notifications of files saved while our dialog is up, so we can update the matches.
        final SaveMonitor.Listener saveListener = new SaveMonitor.Listener() {
            public void fileSaved() {
                // FIXME: Ideally, we'd be a bit more intelligent about this than re-searching the whole tree.
                showMatches();
            }
        };
        SaveMonitor.getInstance().addSaveListener(saveListener);
    }
    
    /**
     * Sets the contents of the text field.
     * The value null causes the pattern to stay as it was.
     */
    public void setPattern(String pattern) {
        if (pattern == null) {
            return;
        }
        regexField.setText(pattern);
    }
    
    public void setFilenamePattern(String pattern) {
        filenameRegexField.setText(pattern);
    }
}
