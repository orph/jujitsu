package e.edit;

import e.gui.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*; 
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;

public class Evergreen {
    private static Evergreen instance;
    
    private JFrame frame;
    private JPanel tabbedPane;
    private JSplitPane splitPane;
    private Sidebar sidebar;
    private TagsPanel tagsPanel;
    private EStatusBar statusLine;
    private Minibuffer minibuffer;
    private JPanel statusArea;
    private EvergreenMenuBar menuBar;
    private Workspace currentWorkspace;
    
    /** Extensions that we shouldn't open. */
    private String[] externalApplicationExtensions;
    
    /** The global find history for all FindDialog instances. */
    private EHistoryComboBoxModel findHistory = new ChronologicalComboBoxModel();
    
    /** Whether we're fully started. */
    private CountDownLatch startSignal = new CountDownLatch(1);
    
    private InitialState initialState = new InitialState();
    
    private class InitialState {
        private ArrayList<InitialWorkspace> initialWorkspaces = new ArrayList<InitialWorkspace>();
        private Point initialLocation = new Point(0, 0);
        private Dimension initialSize = new Dimension(800, 730);
        private boolean showSidebar = true;
        private int splitPaneDividerLocation = -1;
        
        /**
         * Opens all the workspaces listed in the file we remembered them to last time we quit.
         * Also makes the last-visible workspace visible again.
         */
        private void openRememberedWorkspaces() {
            Log.warn("Opening remembered workspaces...");
            if (initialWorkspaces == null) {
                return;
            }
            
            Workspace initiallyVisibleWorkspace = null;
            for (InitialWorkspace initialWorkspace : initialState.initialWorkspaces) {
                Element info = initialWorkspace.xmlWorkspace;
                WorkspaceProperties properties = new WorkspaceProperties();
                properties.name = info.getAttribute("name");
                properties.rootDirectory = info.getAttribute("root");
                properties.buildTarget = info.getAttribute("buildTarget");
                Workspace workspace = openWorkspace(properties, initialWorkspace.initialFiles);
                if (info.hasAttribute("selected")) {
                    initiallyVisibleWorkspace = workspace;
                }
            }
            initialWorkspaces = null;
            
            if (initiallyVisibleWorkspace != null) {
                showWorkspace(initiallyVisibleWorkspace);
            }
        }
        
        /** Shows or hides the tags panel and sets the divider location, as it was last time we quit. */
        private void configureSidebar() {
            if (showSidebar) {
                if (splitPaneDividerLocation >= 0) {
                    splitPane.setDividerLocation(splitPaneDividerLocation);
                } else {
                    // Defaults.
                    splitPane.setDividerLocation(0.8);
                    splitPane.setResizeWeight(0.8);
                }
            } else {
                ShowHideTagsAction.setTagsPanelVisibility(false);
                ShowHideTagsAction.oldDividerLocation = splitPaneDividerLocation;
            }
        }
    }
    
    private static class InitialWorkspace {
        Element xmlWorkspace;
        private List<InitialFile> initialFiles = new ArrayList<InitialFile>();
        
        private InitialWorkspace(Element xmlWorkspace) {
            this.xmlWorkspace = xmlWorkspace;
        }
        
        private void addInitialFile(String name, int y, boolean lastFocused) {
            initialFiles.add(new InitialFile(name, y, lastFocused));
        }
    }
    
    public static class InitialFile {
        String filename; // strictly, filename(:address)?
        int y;
        boolean lastFocused;
        
        private InitialFile(String filename, int y, boolean lastFocused) {
            this.filename = filename;
            this.y = y;
            this.lastFocused = lastFocused;
        }
        
        private InitialFile(String filename) {
            this(filename, -1, false);
        }
    }
    
    public static synchronized Evergreen getInstance() {
        if (instance == null) {
            instance = new Evergreen();
            instance.init();
        }
        return instance;
    }
    
    /** Returns the frame we're using for the main window. */
    public JFrame getFrame() {
        return frame;
    }
    
    public EHistoryComboBoxModel getFindHistory() {
        return findHistory;
    }
    
    public Sidebar getSidebar() {
        return sidebar;
    }
    
    public TagsPanel getTagsPanel() {
        return tagsPanel;
    }
    
    public JSplitPane getSplitPane() {
        return splitPane;
    }
    
    public void showStatus(String status) {
        statusLine.setText(status);
    }

    public void showAlert(String title, String message) {
        SimpleDialog.showAlert(getFrame(), title, message);
    }

    public boolean askQuestion(String title, String message, String continueText) {
        return SimpleDialog.askQuestion(getFrame(), title, message, continueText);
    }

    public String askQuestion(String title, String message, String continueTextYes, String continueTextNo) {
        return SimpleDialog.askQuestion(getFrame(), title, message, continueTextYes, continueTextNo);
    }

    private void showDocument(String url) {
        try {
            BrowserLauncher.openURL(url);
        } catch (Throwable th) {
            SimpleDialog.showDetails(getFrame(), "Hyperlink", th);
        }
    }
    
    private boolean isFileForExternalApplication(String filename) {
        if (externalApplicationExtensions == null) {
            externalApplicationExtensions = Parameters.getArrayOfSemicolonSeparatedElements("files.externalApplicationExtensions");
        }
        return FileIgnorer.nameEndsWithOneOf(filename, externalApplicationExtensions);
    }
    
    private void openFileWithExternalApplication(String filename) {
        try {
            // FIXME: for Java 6, use java.awt.Desktop instead.
            String openCommand = "gnome-open";
            if (GuiUtilities.isMacOs()) {
                openCommand = "/usr/bin/open";
            } else if (GuiUtilities.isWindows()) {
                openCommand = "start";
            }
            ProcessUtilities.spawn(null, new String[] { openCommand, FileUtilities.parseUserFriendlyName(filename) });
        } catch (Exception ex) {
            showAlert("Couldn't open \"" + filename + "\"", "The external application failed to start: " + ex.getMessage() + ".");
        }
    }
    
    /**
     * Sees if any of the "path.rewrite" configuration applies to the given filename,
     * and replaces the matching prefix with the appropriate substitution. Returns the
     * original filename if there's no applicable rewrite.
     */
    public String processPathRewrites(String filename) {
        String from;
        for (int i = 0; (from = Parameters.getParameter("path.rewrite.from." + i)) != null; i++) {
            if (filename.startsWith(from)) {
                String to = Parameters.getParameter("path.rewrite.to." + i);
                String result = to + filename.substring(from.length());
                return result;
            }
        }
        return filename;
    }
    
    public EWindow openFile(InitialFile file) {
        try {
            return openFileNonInteractively(file);
        } catch (Exception ex) {
            Log.warn("Problem opening file \"" + file.filename + "\"", ex);
            showAlert("Couldn't open \"" + file.filename + "\"", ex.getMessage());
            return null;
        }
    }
    
    /**
     * Opens a file. If the file's already open, it receives the focus. The 'filename'
     * parameter is actually a grep-style filename:address string.
     * 
     * Returns the EWindow corresponding to the file opened, or null if
     * no file was opened or the file was passed to an external program.
     */
    public EWindow openFile(String filename) {
        return openFile(new InitialFile(filename));
    }
    
    public EWindow openFileNonInteractively(String filename) {
        return openFileNonInteractively(new InitialFile(filename));
    }
    
    public EWindow openFileNonInteractively(InitialFile file) {
        String filename = file.filename;
        
        // Special case for URIs.
        // We don't insist on matching two slashes because Java has a habit of turning a File into something like "file:/usr/bin/bash".
        // We also insist on at least two characters for the URI scheme, to avoid being fooled by Windows drive letters.
        if (filename.matches("[a-z]{2,}:/.*")) {
            if (filename.startsWith("file:") && filename.matches("file:/.*\\.html") == false) {
                // We open non-HTML file: references ourselves.
                filename = filename.substring(5);
                // Removing leading slashes is tricky, because file URLs aren't as consistent as http ones.
                // There's one common case we have to fix, though, where "file://" has been prepended to a path starting with "~".
                if (filename.startsWith("//~")) {
                    filename = filename.substring(2);
                }
            } else {
                // Everything else is the platform's problem.
                showDocument(filename);
                return null;
            }
        }
        
        // Special case for files to open in external applications.
        if (isFileForExternalApplication(filename)) {
            openFileWithExternalApplication(filename);
            return null;
        }
        
        filename = processPathRewrites(filename);
        
        // Remove local-directory fluff.
        if (filename.startsWith("./") || filename.startsWith(".\\")) {
            filename = filename.substring(2);
        }
        
        // Extract the address and any trailing junk, if this isn't just a
        // filename. We check for the file's existence first to cope with such
        // unfortunate names as "titanfs1-fsm_console-2005-10-27-09:47:47.txt".
        String address = "";
        if (FileUtilities.exists(filename) == false) {
            // Extract any address, which here means a trailing sequence of ":\d+"s.
            // Note that we try hard to cope with trailing junk so we can work with
            // grep(1) matches along the lines of "file.cpp:123:void something()"
            // where the end of the address and the beginning of the actual line
            // are run together. We keep regressing on this behavior!
            Pattern addressPattern = Pattern.compile("^((?:[A-Za-z]:\\\\){0,1}.+?)((:\\d+)*)(:|:?$)");
            Matcher addressMatcher = addressPattern.matcher(filename);
            if (addressMatcher.find()) {
                address = addressMatcher.group(2);
                filename = addressMatcher.group(1);
            }
        }
        
        Log.warn("Opening \"" + filename + "\"" + (address.length() > 0 ? (" at \"" + address + "\"") : ""));
        
        // Give up if the file doesn't exist.
        if (FileUtilities.exists(filename) == false) {
            throw new RuntimeException("File \"" + filename + "\" does not exist.");
        }
        
        try {
            /*
             * Open the file a symbolic link points to, and not the link itself.
             * Clean paths like a/b/../c/d. Let the Java API do this.
             */
            filename = FileUtilities.fileFromString(filename).getCanonicalPath();
        } catch (IOException ex) {
            /* Harmless. */
            ex = ex;
        }
        filename = normalizeWorkspacePrefix(filename);
        filename = FileUtilities.getUserFriendlyName(filename);
        
        // Refuse to open directories.
        if (FileUtilities.fileFromString(filename).isDirectory()) {
            throw new RuntimeException("It's not possible to edit directories, which is what \"" + filename + "\" is.");
        }
        
        // Limit ourselves (rather arbitrarily) to files under half a gigabyte. That's quite a strain on us, at present.
        final int KB = 1024;
        final int MB = 1024 * KB;
        long fileLength = FileUtilities.fileFromString(filename).length();
        if (fileLength > 512 * MB) {
            throw new RuntimeException("The file \"" + filename + "\", which is " + fileLength + " bytes long is too large. This file will not be opened.");
        }
        
        // Find which workspace this file is on/should be on, and make it visible.
        Workspace workspace = getBestWorkspaceForFilename(filename);
        if (startSignal.getCount() == 0) {
            showWorkspace(workspace);
        }
        
        // If the user already has this file open, we shouldn't open it again.
        EWindow alreadyOpenWindow = workspace.findIfAlreadyOpen(filename, address);
        if (alreadyOpenWindow != null) {
            return alreadyOpenWindow;
        }
        
        // Add an appropriate viewer for the filename to the chosen workspace.
        return workspace.addViewerForFile(filename, address, file.y);
    }
    
    /** Returns an array of all the workspaces. */
    public Workspace[] getWorkspaces() {
        Workspace[] result = new Workspace[tabbedPane.getComponentCount()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = (Workspace) tabbedPane.getComponent(i);
        }
        return result;
    }

    /**
     * Substituting ~/ for /home/userName isn't the only friendly transformation
     * that we can usefully and easily make.  If the user has configured us
     * with workspace paths which include symbolic links, then it's a reasonable
     * presumption that the user would like us to consistently use the form of
     * the path which they supplied rather than the OS-canonical form (supplied
     * by File.getCanonicalPath).
     * A symbolic link within such a workspace would have both symbolic links
     * canonicalized in openFile and so would end up with a path prefixed by
     * the desired Workspace's canonical name rather than its friendly name.
     * This would send it to the wrong Workspace.
     * FIXME: Consistency probably demands that this be called from
     * FileUtilities.getUserFriendlyName rather than openFile.
     */
    public String normalizeWorkspacePrefix(String filename) {
        for (Workspace workspace : getWorkspaces()) {
            String friendlyPrefix = workspace.getRootDirectory();
            String canonicalPrefix = workspace.getCanonicalRootDirectory();
            if (filename.startsWith(canonicalPrefix)) {
                return friendlyPrefix + filename.substring(canonicalPrefix.length());
            }
        }
        return filename;
    }
    
    /** Returns the workspace whose root directory shares the longest common prefix with the given filename. */
    public Workspace getBestWorkspaceForFilename(String filename) {
        Workspace bestWorkspace = getCurrentWorkspace();
        int bestLength = 0;
        for (Workspace workspace : getWorkspaces()) {
            String workspaceRoot = workspace.getRootDirectory();
            if (filename.startsWith(workspaceRoot)) {
                int length = workspaceRoot.length();
                if (length > bestLength) {
                    bestWorkspace = workspace;
                    bestLength = length;
                }
            }
        }
        return bestWorkspace;
    }
    
    public Workspace getCurrentWorkspace() {
        return currentWorkspace;
    }
    
    public void showWorkspace(final Workspace w) {
        CardLayout cl = (CardLayout) tabbedPane.getLayout();
        cl.show(tabbedPane, w.getTitle());
    }
    
    public void cycleWorkspaces(int indexDelta) {
        CardLayout cl = (CardLayout) tabbedPane.getLayout();
        if (indexDelta > 0) {
            cl.next(tabbedPane);
        } else if (indexDelta < 0) {
            cl.previous(tabbedPane);
        }
    }
    
    public void goToWorkspaceByIndex(int workspaceIndex) {
        if (workspaceIndex >= 0 && workspaceIndex < tabbedPane.getComponentCount()) {
            Workspace w = (Workspace) tabbedPane.getComponent(workspaceIndex);
            showWorkspace(w);
        }
    }
    
    private void addWorkspace(final Workspace workspace) {
        workspace.addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                currentWorkspace = workspace;
                menuBar.getWorkspaceMenu().setText("Project: " + workspace.getRootDirectory());
                
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        workspace.openRememberedFiles();
                    }
                });
            }
        });
        
        tabbedPane.add(workspace, workspace.getTitle());
        showWorkspace(workspace);
        fireWorkspacesChange();
        
        // We need to ensure that the workspace has been validated so that it
        // and its children have bounds, so that EColumn has a non-zero height
        // so that when we come to add components to it using EColumn.moveTo,
        // we don't stack them all on top of one another.
        workspace.revalidate();
        frame.validate();
        
        showStatus("Added workspace \"" + workspace.getTitle() + "\" (" +
                   workspace.getRootDirectory() + ")");
    }

    public Workspace createWorkspace(WorkspaceProperties properties) {
        boolean noNonEmptyWorkspaceOfThisNameExists = removeWorkspaceByName(properties.name);
        if (noNonEmptyWorkspaceOfThisNameExists == false) {
            showAlert("Couldn't create workspace", "A non-empty workspace with the name \"" + properties.name + "\" already exists.");
            return null;
        }
        Workspace workspace = new Workspace(properties.name, properties.rootDirectory);
        workspace.setBuildTarget(properties.buildTarget);
        addWorkspace(workspace);
        moveFilesToBestWorkspaces();
        return workspace;
    }
    
    /**
     * Removes any workspaces with the given name. Returns false if there was a workspace,
     * but it couldn't be removed because it had open files, true otherwise.
     */
    public boolean removeWorkspaceByName(String name) {
        for (Workspace workspace : getWorkspaces()) {
            if (workspace.getTitle().equals(name)) {
                if (workspace.isEmpty() == false) {
                    return false;
                }
                tabbedPane.remove(workspace);
                fireWorkspacesChange();
            }
        }
        return true;
    }
    
    public void moveFilesToBestWorkspaces() {
        for (Workspace workspace : getWorkspaces()) {
            workspace.moveFilesToBestWorkspaces();
        }
    }
    
    public void removeWorkspace(Workspace workspace) {
        assert workspace != null;
        
        if (getWorkspaces().length == 1) {
            showAlert("Couldn't remove workspace", "The last workspace cannot be removed.");
            return;
        }
        String question = "Close the project \"" + workspace.getTitle() + "\"?";
        if (workspace.isEmpty() == false) {
            question += " Open windows will be moved to the next best workspace.";
        }
        boolean remove = askQuestion("Close Project?", question, "Close");
        if (remove == false) {
            return;
        }
        
        tabbedPane.remove(workspace);
        fireWorkspacesChange();
        workspace.moveFilesToBestWorkspaces();
        workspace.dispose();
    }
    
    /**
     * Writes out the workspace list on a new thread to avoid disk access on
     * the event dispatch thread. Any code that adds or removes workspaces
     * should invoke this method. Annoyingly, JTabbedPane doesn't make such
     * changes observable, so it's down to us to be careful.
     */
    private void fireWorkspacesChange() {
        JMenu menu = menuBar.getWorkspaceMenu();

        // Clear workspaces up to the first separator
        while (menu.getItem(0) instanceof JMenuItem) {
            menu.remove(0);
        }

        int wsCount = 0;
        for (Workspace ws : getWorkspaces()) {
            JMenuItem item = new JMenuItem(new OpenWorkspaceAction(ws));
            menu.insert(item, 0);
            wsCount++;
        }
        for (int idx = 0; idx < Math.min(10, wsCount); idx++) {
           JMenuItem item = menu.getItem(idx);
           if (item != null) {
              String wsAccel = Integer.toString(idx+1);
              item.setText(wsAccel + ". " + item.getText());
              item.setMnemonic(wsAccel.charAt(0));
           }
        }

        new Thread(new Runnable() {
            public void run() {
                rememberState();
            }
        }).start();
    }
    
    public void createWorkspaceForCurrentDirectory() {
        String currentDirectory = System.getProperty("user.dir");
        WorkspaceProperties properties = new WorkspaceProperties();
        properties.name = currentDirectory.substring(currentDirectory.lastIndexOf(File.separatorChar) + 1);
        properties.rootDirectory = currentDirectory;
        createWorkspace(properties);
    }
    
    public String getResourceFilename(String leafName) {
        return System.getenv("EDIT_HOME") + File.separator + "lib" + File.separator + "data" + File.separator + leafName;
    }
    
    public void initWindowIcon() {
        JFrameUtilities.setFrameIcon(frame);
    }
    
    /**
     * Attempts to quit. All the workspaces are asked if it's safe for them to be
     * closed. Only if all workspaces agree that it's safe will we actually quit.
     */
    public void handleQuit(com.apple.eawt.ApplicationEvent e) {
        boolean isSafeToQuit = true;
        boolean onMacOS = (e != null);
        
        ArrayList<String> dirtyFileNames = new ArrayList<String>();
        ArrayList<Workspace> dirtyWorkspaces = new ArrayList<Workspace>();
        for (Workspace workspace : getWorkspaces()) {
            ETextWindow[] dirtyWindows = workspace.getDirtyTextWindows();
            for (ETextWindow dirtyWindow : dirtyWindows) {
                dirtyFileNames.add(dirtyWindow.getFilename());
            }
            if (dirtyWindows.length != 0) {
                isSafeToQuit = false;
                dirtyWorkspaces.add(workspace);
            }
        }
        
        if (onMacOS) {
            // Let Apple's library code know whether or not to terminate us when we return.
            e.setHandled(isSafeToQuit);
        }
        
        if (isSafeToQuit == false) {
            if (dirtyWorkspaces.size() == 1) {
                // Ensure that the workspace in question is visible.
                Workspace firstDirtyWorkspace = dirtyWorkspaces.get(0);
                showWorkspace(firstDirtyWorkspace);
                // Ensure that at least the first dirty window is maximally visible.
                ETextWindow firstDirtyWindow = firstDirtyWorkspace.getDirtyTextWindows()[0];
                firstDirtyWindow.expand();
                firstDirtyWindow.requestFocusInWindow();
            }
            showAlert("You have files with unsaved changes", "There are unsaved files:<p>" + StringUtilities.join(dirtyFileNames, "<br>") + "<p>Please deal with them and try again.");
            return;
        }
        
        // We're definitely going to quit now...
        rememberState();
        if (onMacOS == false) {
            System.exit(0);
        }
    }

    /**
     * Writes out all our various bits of state to disk, so that next
     * time we start, we start more or less where we left off.
     */
    public void rememberState() {
        if (startSignal.getCount() != 0) {
            // If we haven't finished initializing, we may not have
            // read all the state in, so writing it back out could
            // actually throw state away. We don't want to do that.
            return;
        }

        //SpellingChecker.dumpKnownBadWordsTo(System.out);
        JFrameUtilities.writeGeometriesTo(getDialogGeometriesPreferenceFilename());
        
        writeSavedState();
    }
    
    /** Returns the full pathname for the given preference file. */
    public String getPreferenceFilename(String leafname) {
        return System.getProperty("preferencesDirectory") + File.separator + leafname;
    }
    
    public String getDialogGeometriesPreferenceFilename() {
        return getPreferenceFilename("dialog-geometries");
    }
    
    public String getOpenFileListPreferenceFilename() {
        return getPreferenceFilename("open-file-list");
    }
    
    public String getOpenWorkspaceListPreferenceFilename() {
        return getPreferenceFilename("open-workspace-list");
    }
    
    private void readSavedState() {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(FileUtilities.fileFromString(getPreferenceFilename("saved-state.xml")));
            
            Element root = document.getDocumentElement();
            initialState.initialLocation.x = Integer.parseInt(root.getAttribute("x"));
            initialState.initialLocation.y = Integer.parseInt(root.getAttribute("y"));
            initialState.initialSize.width = Integer.parseInt(root.getAttribute("width"));
            initialState.initialSize.height = Integer.parseInt(root.getAttribute("height"));
            
            if (root.hasAttribute("showSidebar")) {
                initialState.showSidebar = Boolean.parseBoolean(root.getAttribute("showSidebar"));
                if (root.hasAttribute("splitPaneDividerLocation")) {
                    initialState.splitPaneDividerLocation = Integer.parseInt(root.getAttribute("splitPaneDividerLocation"));
                }
            }
            
            for (Node xmlWorkspace = root.getFirstChild(); xmlWorkspace != null; xmlWorkspace = xmlWorkspace.getNextSibling()) {
                if (xmlWorkspace instanceof Element) {
                    InitialWorkspace workspace = new InitialWorkspace((Element) xmlWorkspace);
                    initialState.initialWorkspaces.add(workspace);
                    for (Node file = xmlWorkspace.getFirstChild(); file != null; file = file.getNextSibling()) {
                        if (file instanceof Element) {
                            Element fileElement = (Element) file;
                            workspace.addInitialFile(fileElement.getAttribute("name"), Integer.parseInt(fileElement.getAttribute("y")), fileElement.hasAttribute("lastFocused"));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.warn("Problem reading saved state", ex);
        }
        frame.setLocation(initialState.initialLocation);
        frame.setSize(initialState.initialSize);
        JFrameUtilities.constrainToScreen(frame);
    }
    
    private synchronized void writeSavedState() {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.newDocument();
            
            Element root = document.createElement("edit");
            document.appendChild(root);
            
            Point position = frame.getLocation();
            root.setAttribute("x", Integer.toString((int) position.getX()));
            root.setAttribute("y", Integer.toString((int) position.getY()));
            Dimension size = frame.getSize();
            root.setAttribute("width", Integer.toString((int) size.getWidth()));
            root.setAttribute("height", Integer.toString((int) size.getHeight()));
            
            root.setAttribute("showSidebar", Boolean.toString(sidebar.isVisible()));
            root.setAttribute("splitPaneDividerLocation", Integer.toString(sidebar.isVisible() ? splitPane.getDividerLocation() : ShowHideTagsAction.oldDividerLocation));
            
            for (Workspace workspace : getWorkspaces()) {
                workspace.serializeAsXml(document, root);
            }
            
            // Set up a Transformer to produce indented XML output.
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xalan}indent-amount", "4");
            
            // Create the XML content...
            StringWriter content = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(content));
            
            // And then carefully write it to disk.
            File file = FileUtilities.fileFromString(getPreferenceFilename("saved-state.xml"));
            if (writeAtomicallyTo(file, content.toString()) == false) {
                Log.warn("\"" + file + "\" content should have been:\n" + content.toString());
            }
        } catch (Exception ex) {
            Log.warn("Problem writing saved state", ex);
        }
    }
    
    private static boolean writeAtomicallyTo(File file, CharSequence chars) {
        // We save to a new file first, to reduce our chances of corrupting the real file, or at least increase our chances of having one intact copy.
        File backupFile = new File(file.toString() + ".bak");
        try {
            StringUtilities.writeFile(backupFile, chars);
        } catch (Exception ex) {
            return false;
        }
        
        // Now we write to the intended destination.
        // If the destination was a symbolic link on a CIFS server, it's important to write to the original file rather than creating a new one.
        
        // CIFS also causes problems if we try renaming the backup file to the intended file.
        // For one thing, the destination must not exist, but removing the destination would make it harder to be atomic.
        // Also, the source must not be open, which is not easy to guarantee in Java, and often not the case as soon as you'd like.
        try {
            StringUtilities.writeFile(file, chars);
        } catch (Exception ex) {
            return false;
        }
        
        // Everything went well so far, so delete the backup file (ignoring failures) and return success.
        backupFile.delete();
        return true;
    }
    
    private Workspace openWorkspace(WorkspaceProperties properties, List<InitialFile> initialFiles) {
        Log.warn("Opening workspace \"" + properties.name + "\" with root \"" + properties.rootDirectory + "\"");
        Workspace workspace = createWorkspace(properties);
        if (workspace == null) {
            return null;
        }
        
        workspace.setInitialFiles(initialFiles);
        File rootDirectory = FileUtilities.fileFromString(workspace.getRootDirectory());

        // FIXME: Need to report that the workspace/project doesn't exist.
        /*
        int which = tabbedPane.indexOfComponent(workspace);
        if (rootDirectory.exists() == false) {
            tabbedPane.setForegroundAt(which, Color.RED);
            tabbedPane.setToolTipTextAt(which, properties.rootDirectory + " doesn't exist.");
        } else if (rootDirectory.isDirectory() == false) {
            tabbedPane.setForegroundAt(which, Color.RED);
            tabbedPane.setToolTipTextAt(which, properties.rootDirectory + " isn't a directory.");
        }
        */

        return workspace;
    }
    
    public void initWindowListener() {
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                handleQuit(null);
            }
        });
    }
    
    public void initWindow() {
        frame = new JFrame("Jujitsu");
        initWindowIcon();
        initWindowListener();
    }
    
    public void initTagsPanel() {
        tagsPanel = new TagsPanel();
    }
    
    public void initPreferences() {
        Parameters.readPropertiesFile(getPreferenceFilename("edit.properties"));
    }
    
    public void initStatusArea() {
        statusLine = new EStatusBar();
        minibuffer = new Minibuffer();
        
        statusArea = new JPanel(new BorderLayout());
        statusArea.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        statusArea.add(statusLine, BorderLayout.NORTH);
        statusArea.add(minibuffer, BorderLayout.SOUTH);
    }
    
    public void showMinibuffer(MinibufferUser minibufferUser) {
        minibuffer.activate(minibufferUser);
    }
    
    private Evergreen() {
    }
    
    private void initMacOs() {
        if (GuiUtilities.isMacOs() == false) {
            return;
        }
        com.apple.eawt.Application.getApplication().addApplicationListener(new com.apple.eawt.ApplicationAdapter() {
            public void handleQuit(com.apple.eawt.ApplicationEvent e) {
                Evergreen.this.handleQuit(e);
            }
        });
    }
    
    private void initAboutBox() {
        AboutBox aboutBox = AboutBox.getSharedInstance();
        aboutBox.setWebSiteAddress("http://beatniksoftware.com/jujitsu");
        aboutBox.addCopyright("Copyright (C) 2004-2007 Free Software Foundation, Inc.");
        aboutBox.addCopyright("All Rights Reserved.");
    }
    
    public void awaitInitialization() {
        try {
            startSignal.await();
        } catch (InterruptedException ex) {
        }
    }    
    
    private void init() {
        final long t0 = System.nanoTime();
        
        initPreferences();
        initMacOs();
        initAboutBox();
        JavaResearcher.initOnBackgroundThread();
        JFrameUtilities.readGeometriesFrom(getDialogGeometriesPreferenceFilename());
        initWindow();
        initTagsPanel();

        sidebar = new Sidebar();
        sidebar.addDefaultPanel(tagsPanel);
        sidebar.showPanel(tagsPanel);

        tabbedPane = new JPanel();
        tabbedPane.setLayout(new CardLayout());

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, tabbedPane, sidebar);
        initStatusArea();
        readSavedState();
        
        InetAddress wildcardAddress = null;
        new InAppServer("EditServer", getPreferenceFilename("edit-server-port"), wildcardAddress, EditServer.class, new EditServer(this));
        
        frame.getContentPane().add(splitPane, BorderLayout.CENTER);
        frame.getContentPane().add(statusArea, BorderLayout.SOUTH);
        
        menuBar = new EvergreenMenuBar();
        frame.setJMenuBar(menuBar);
        
        initialState.openRememberedWorkspaces();
        
        frame.setVisible(true);
        GuiUtilities.finishGnomeStartup();
        Log.warn("Frame visible after " + TimeUtilities.nsToString(System.nanoTime() - t0) + ".");
        
        // These things want to be done after the frame is visible...
        
        initialState.configureSidebar();
        
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                if (getWorkspaces().length == 0) {
                    // If we didn't create any workspaces, give the user some help...
                    showAlert("Welcome to Jujitsu!", "This looks like the first time you've used Jujitsu. You'll need to tell Jujitsu about the projects you wish to work on.<p>Choose \"Add Project...\" from the the \"Project\" menu.<p>");
                }
                
                Thread fileListUpdaterStarterThread = new Thread(new Runnable() {
                    public void run() {
                        final long t1 = System.nanoTime();
                        try {
                            // What I really want to say is "wait until the event queue is empty and everything's caught up".
                            // I haven't yet found out how to do that, and the fact that using EventQueue.invokeLater here doesn't achieve the desired effect makes me think that what I want might not be that simple anyway.
                            // Seemingly, though, if we run at MIN_PRIORITY, we don't get much of a look-in until the EDT is idle anyway.
                            // Hans Muller's got code to wait for the event queue to clear: https://appframework.dev.java.net/source/browse/appframework/trunk/AppFramework/src/application/Application.java?rev=36&view=auto&content-type=text/vnd.viewcvs-markup
                            // Until that's available as GPL/LGPL, or as part of the JDK, here's a simpler alternative that's good enough for our purposes here.
                            EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
                            while (queue.peekEvent() != null) {
                                Thread.sleep(100);
                            }
                        } catch (InterruptedException ex) {
                        }
                        
                        Log.warn("Workers free to start after " + TimeUtilities.nsToString(System.nanoTime() - t0) + " (slept for " + TimeUtilities.nsToString(System.nanoTime() - t1) + ").");
                        startSignal.countDown();
                    }
                });
                fileListUpdaterStarterThread.setPriority(Thread.MIN_PRIORITY);
                fileListUpdaterStarterThread.start();
            }
        });
    }
}
