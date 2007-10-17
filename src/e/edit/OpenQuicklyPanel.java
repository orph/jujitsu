package e.edit;

import e.gui.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import org.jdesktop.swingworker.SwingWorker;

/**
 * Improves on Apple Project Builder's "Open Quickly", which just pops up a dialog where you type a name.
 * We have a list -- updated as you type in the filename field -- showing what files match the regular expression you've typed.
 * You can double-click individual entries to open them, or hit Return to open just the selected one(s).
 */
public class OpenQuicklyPanel extends JPanel implements WorkspaceFileList.Listener {
    private SearchField filenameField = new SearchField("Open Quickly");
    private JList matchList;
    private JLabel status = new JLabel(" ");
    private TextChangeTimeout changeTimeout;
    
    /** Which workspace is this "Open Quickly" for? */
    private Workspace workspace;
    
    private void setStatus(boolean good, String text) {
        status.setForeground(good ? Color.BLACK : Color.RED);
        status.setText(text);
    }
    
    private class MatchFinder extends SwingWorker<Object, Object> {
        private String regularExpression;
        private DefaultListModel model;
        private boolean statusGood;
        private String statusText;
        
        private MatchFinder(String regularExpression) {
            this.regularExpression = regularExpression;
        }
        
        @Override
        protected Object doInBackground() {
            model = new DefaultListModel();
            statusGood = true;
            try {
                long startTimeMs = System.currentTimeMillis();
                
                List<String> fileList = workspace.getFileList().getListOfFilesMatching(regularExpression);
                ArrayList<String> shortPathList = new ArrayList<String>();
                for (String path : fileList) {
                    shortPathList.add(workspace.getFileList().getUniqueFilePath(path));
                }
                Collections.sort(shortPathList, String.CASE_INSENSITIVE_ORDER);

                for (String shortPath : shortPathList) {
                    model.addElement(shortPath);
                }

                final int indexedFileCount = workspace.getFileList().getIndexedFileCount();
                if (indexedFileCount != -1) {
                    statusText = fileList.size() + " / " + StringUtilities.pluralize(indexedFileCount, "file", "files") + " match";
                }
                
                long endTimeMs = System.currentTimeMillis();
                Log.warn("Search for files matching \"" + regularExpression + "\" took " + (endTimeMs - startTimeMs) + " ms.");
            } catch (PatternSyntaxException ex) {
                statusGood = false;
                statusText = ex.getDescription();
            }
            return null;
        }
        
        @Override
        public void done() {
            setStatus(statusGood, statusText);
            matchList.setModel(model);
            matchList.setEnabled(true);
            // If we don't set the selected index, the user won't be able to cycle the focus into the list with the Tab key.
            // This also means the user can just hit Return if there's only one match.
            matchList.setSelectedIndex(0);
        }
    }
    
    public synchronized void showMatches() {
        // Only bother if the user can see the results, and we're not currently rescanning the index.
        if (matchList.isShowing() && workspace.getFileList().getIndexedFileCount() != -1) {
            new MatchFinder(filenameField.getText()).execute();
        }
    }
    
    private void openFileAtIndex(int index) {
        String filename = (String) matchList.getModel().getElementAt(index);
        
        // FIXME: This is a slow hack.
        filename = workspace.getFileList().getFullFilePath(filename);

        Evergreen.getInstance().openFile(workspace.prependRootDirectory(filename));
        
        // Now we've opened a new file, that's where focus should go when we're dismissed.
        //form.getFormDialog().setShouldRestoreFocus(false);
        
        // Wrestle focus back from the file we've just opened.
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                SwingUtilities.getWindowAncestor(matchList).toFront();
                filenameField.requestFocus();
            }
        });
    }
    
    public void initMatchList() {
        matchList = new JList();
        matchList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = matchList.locationToIndex(e.getPoint());
                    openFileAtIndex(index);
                    
                    //form.getFormDialog().cancelDialog();
                    if (isVisible()) {
                        Evergreen.getInstance().getSidebar().revertToDefaultPanel();
                    }
                }
            }
        });
        matchList.setCellRenderer(new EListCellRenderer(true));
        matchList.setFont(ChangeFontAction.getConfiguredFont());
        ComponentUtilities.divertPageScrollingFromTo(filenameField, matchList);
    }
    
    public OpenQuicklyPanel(Workspace workspace) {
        super(new BorderLayout());
        setName("Open Quickly");
        
        this.workspace = workspace;
        
        initMatchList();
        initUI();
        
        workspace.getFileList().addFileListListener(this);
    }

    private void addSearchFieldActions(SearchField entry) {
        ActionMap am = filenameField.getActionMap();
        am.put("next-result", new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("actionPerformed::next-result");
                    matchList.setSelectedIndex(matchList.getSelectedIndex() + 1);
                }
            });
        am.put("previous-result", new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("actionPerformed::previous-result");
                    matchList.setSelectedIndex(matchList.getSelectedIndex() - 1);
                }
            });
        am.put("open-result", new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("actionPerformed::open-result");
                    openSelectedFilesFromList();
                }
            });
    }
    
    private void initUI() {
        /*
        form.getFormDialog().setAcceptCallable(new java.util.concurrent.Callable<Boolean>() {
            public Boolean call() {
                openSelectedFilesFromList();
                return true;
            }
        });
        */

        addSearchFieldActions(filenameField);
        changeTimeout = new TextChangeTimeout(filenameField, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    showMatches();
                }
            });

        addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                   filenameField.requestFocus();
                }
            });

        JPanel entriesStatus = new JPanel(new BorderLayout());
        entriesStatus.add(filenameField, BorderLayout.NORTH);
        entriesStatus.add(status, BorderLayout.SOUTH);

        add(entriesStatus, BorderLayout.NORTH); // Names Containing
        add(new JScrollPane(matchList), BorderLayout.CENTER); // Matches
    }
    
    /**
     * Sets the contents of the text field.
     */
    public void setFilenamePattern(String filenamePattern) {
        filenameField.setText(filenamePattern);
    }
    
    public void fileListStateChanged(boolean isNowValid) {
        if (isNowValid) {
            showMatches();
            matchList.setEnabled(true);
        } else {
            matchList.setEnabled(false);
            setStatus(true, " ");
            switchToFakeList();
            filenameField.requestFocusInWindow();
        }
    }
    
    /**
     * Responsible for providing some visual feedback that we're rescanning.
     */
    private synchronized void switchToFakeList() {
        DefaultListModel model = new DefaultListModel();
        model.addElement("Rescan in progress...");
        matchList.setModel(model);
        matchList.setEnabled(false);
    }
    
    public void openSelectedFilesFromList() {
        for (int index : matchList.getSelectedIndices()) {
            openFileAtIndex(index);
        }
    }
}
