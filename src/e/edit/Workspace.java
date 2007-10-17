package e.edit;

import e.gui.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class Workspace extends JPanel {
    private EColumn leftColumn;

    private String title;
    private String rootDirectory;
    private String canonicalRootDirectory;
    private String buildTarget;
    
    private EErrorsPanel errorsPanel;
    private OpenQuicklyPanel openQuicklyPanel;
    private FindInFilesPanel findInFilesPanel;
    
    private EFileDialog openDialog;
    private EFileDialog saveAsDialog;
    
    private WorkspaceFileList fileList;
    
    private ETextWindow rememberedTextWindow;
    
    private List<Evergreen.InitialFile> initialFiles = Collections.emptyList();
    
    public Workspace(String title, final String rootDirectory) {
        super(new BorderLayout());
        
        setTitle(title);
        this.fileList = new WorkspaceFileList(this);
        setRootDirectory(rootDirectory);
        this.buildTarget = "";
        
        leftColumn = new EColumn();
        add(leftColumn, BorderLayout.CENTER);
        leftColumn.addContainerListener(new ContainerListener() {
            public void componentAdded(ContainerEvent e) {
                componentCountChanged();
            }
            public void componentRemoved(ContainerEvent e) {
                componentCountChanged();
            }
            private void componentCountChanged() {
                updateTabForWorkspace();
            }
        });
    }
    
    public WorkspaceFileList getFileList() {
        return fileList;
    }
    
    public void dispose() {
        fileList.dispose();
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
        updateTabForWorkspace();
    }
    
    /**
     * Updates the title of the tab in the JTabbedPane that corresponds to the Workspace that this
     * EColumn represents (if you can follow that). Invoked when the column has a component added
     * or removed.
     */
    public void updateTabForWorkspace() {
        JTabbedPane tabbedPane = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, this);
        if (tabbedPane != null) {
            String title = getTitle();
            int windowCount = leftColumn.getTextWindows().length + getInitialFileCount();
            if (windowCount > 0) {
                title += " (" + windowCount + ")";
            }
            tabbedPane.setTitleAt(tabbedPane.indexOfComponent(this), title);
        }
    }
    
    public String getCurrentDirectory() {
        final ETextWindow textWindow = ETextAction.getFocusedTextWindow();
        if (textWindow == null) {
            return getRootDirectory();
        }
        
        return FileUtilities.fileFromString(textWindow.getFilename()).getParent();
    }
    
    /**
     * Returns the normal, friendly form rather than the OS-canonical one.
     * See also getCanonicalRootDirectory.
     * Ends with File.separator.
     */
    public String getRootDirectory() {
        return rootDirectory;
    }
    
    public void setRootDirectory(String rootDirectory) {
        this.rootDirectory = FileUtilities.getUserFriendlyName(rootDirectory);
        try {
            this.canonicalRootDirectory = FileUtilities.fileFromString(rootDirectory).getCanonicalPath() + File.separator;
        } catch (IOException ex) {
            Log.warn("Failed to cache canonical root directory for workspace \"" + title + "\" with new root \"" + rootDirectory + "\"", ex);
        }
        fileList.rootDidChange();
    }
    
    /**
     * Returns an absolute path in user-friendly form.
     * This avoids any File.separator confusion.
     */
    public String prependRootDirectory(String workspaceRelativeFilename) {
        return rootDirectory + workspaceRelativeFilename;
    }
    
    /**
     * Returns the OS-canonical form rather than the normal, friendly one.
     * See also getRootDirectory.
     */
    public String getCanonicalRootDirectory() {
        // We cache this because it's called when deciding on a workspace for a newly-opened file.
        // We don't want the UI to lock up because an unrelated workspace's NFS mount is temporarily unresponsive.
        return canonicalRootDirectory;
    }
    
    /** Tests whether this workspace is empty. A workspace is still considered empty if all it contains is an errors window. */
    public boolean isEmpty() {
        return leftColumn.getTextWindows().length == 0;
    }
    
    /** Returns an array of this workspace's dirty text windows. */
    public ETextWindow[] getDirtyTextWindows() {
        ArrayList<ETextWindow> dirtyTextWindows = new ArrayList<ETextWindow>();
        ETextWindow[] textWindows = leftColumn.getTextWindows();
        for (ETextWindow textWindow : textWindows) {
            if (textWindow.isDirty()) {
                dirtyTextWindows.add(textWindow);
            }
        }
        return dirtyTextWindows.toArray(new ETextWindow[dirtyTextWindows.size()]);
    }
    
    public EWindow findWindowByName(String name) {
        return leftColumn.findWindowByName(name);
    }
    
    public static boolean isAbsolute(String filename) {
        if (GuiUtilities.isWindows()) {
            /* FIXME: is this a good test for Windows? What about \\ names? */
            return (filename.length() > 1) && (filename.charAt(1) == ':');
        } else {
            return (filename.length() > 0) && (filename.charAt(0) == '/');
        }
    }
    
    /** Returns the EWindow corresponding to the given file. If the file's open, shows the given address. */
    public EWindow findIfAlreadyOpen(String filename, String address) {
        /* Check we don't already have this open as a file or directory. */
        final EWindow window = findWindowByName(filename);
        if (window != null) {
            leftColumn.setSelectedWindow(window);
            window.ensureSufficientlyVisible();
            ETextWindow textWindow = (ETextWindow) window;
            textWindow.jumpToAddress(address);
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    window.requestFocus();
                }
            });
            return window;
        }
        return null;
    }
    
    public EWindow addViewerForFile(final String filename, final String address, final int y) {
        Evergreen.getInstance().showStatus("Opening " + filename + "...");
        EWindow window = null;
        try {
            ETextWindow newWindow = new ETextWindow(filename);
            window = addViewer(newWindow, address, y);
            if (filename.startsWith(getRootDirectory())) {
                int prefixCharsToSkip = getRootDirectory().length();
                String pathWithinWorkspace = filename.substring(prefixCharsToSkip);
                fileList.ensureInFileList(pathWithinWorkspace);
            }
        } catch (Exception ex) {
            Log.warn("Exception while opening file", ex);
        }
        Evergreen.getInstance().showStatus("Opened " + filename);
        return window;
    }
    
    private EWindow addViewer(final EWindow viewer, final String address, final int y) {
        leftColumn.addComponent(viewer, y);
        if (address != null) {
            final ETextWindow textWindow = (ETextWindow) viewer;
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    textWindow.jumpToAddress(address);
                }
            });
        }
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                viewer.requestFocus();
            }
        });
        return viewer;
    }
    
    public EErrorsPanel getErrorsPanel() {
        return errorsPanel;
    }
    
    /**
     * Checks whether this workspace has unsaved files before performing some action.
     * Offers the user the choice of continuing without saving, continuing after saving, or
     * canceling.
     * Returns true if you should continue with your action, false if the user hit 'Cancel'.
     */
    public boolean prepareForAction(String activity, String unsavedDataPrompt) {
        ETextWindow[] dirtyWindows = getDirtyTextWindows();
        if (dirtyWindows.length > 0) {
            String choice = Evergreen.getInstance().askQuestion(activity, unsavedDataPrompt, "Save All", "Don't Save");
            if (choice.equals("Cancel")) {
                return false;
            }
            boolean saveAll = choice.equals("Save All");
            if (saveAll == false) {
                return true;
            }
            boolean okay = saveAll();
            if (okay == false) {
                // Pretend the user canceled, because we shouldn't pretend that everything went okay.
                return false;
            }
        }
        return true;
    }
    
    /**
     * Attempts to save all the dirty files on this workspace.
     * Returns true if all saves were successful, false otherwise.
     */
    public boolean saveAll() {
        for (ETextWindow dirtyWindow : getDirtyTextWindows()) {
            boolean savedOkay = dirtyWindow.save();
            if (savedOkay == false) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Shows the "Find File" dialog with the given String as the contents of
     * the text field. Use the empty string to retain the current contents.
     * There's no way to empty the text field's contents, but that sounds like
     * a bad idea anyway. Either you've got a better suggestion than what the
     * user last typed, or you should leave things as they are.
     */
    public synchronized void showFindInFilesPanel(String pattern, String filenamePattern) {
        if (findInFilesPanel == null) {
            findInFilesPanel = new FindInFilesPanel(this);
            Evergreen.getInstance().getSidebar().addPanel(findInFilesPanel);
        }
        if (pattern != null && pattern.length() > 0) {
            findInFilesPanel.setPattern(pattern);
        }
        if (filenamePattern != null && filenamePattern.length() > 0) {
            findInFilesPanel.setFilenamePattern(filenamePattern);
        }
        
        System.out.println("showFindInFilesPanel called!!!");
        Evergreen.getInstance().getSidebar().showPanel(findInFilesPanel);
    }
    
    /**
     * Shows the "Open Quickly" dialog with the given String as the contents
     * of the text field. Use the empty string to retain the current contents.
     * There's no way to empty the text field's contents, but that sounds like
     * a bad idea anyway. Either you've got a better suggestion than what the
     * user last typed, or you should leave things as they are.
     */
    public void showOpenQuicklyPanel(String filenamePattern) {
        if (openQuicklyPanel == null) {
            openQuicklyPanel = new OpenQuicklyPanel(this);
            Evergreen.getInstance().getSidebar().addPanel(openQuicklyPanel);
        }
        if (filenamePattern != null && filenamePattern.length() > 0 && filenamePattern.contains("\n") == false) {
            openQuicklyPanel.setFilenamePattern(filenamePattern);
        }
        
        Evergreen.getInstance().getSidebar().showPanel(openQuicklyPanel);
    }
    
    public void showErrorsPanel() {
        if (errorsPanel == null) {
            errorsPanel = new EErrorsPanel(this);
            Evergreen.getInstance().getSidebar().addPanel(errorsPanel);
        }
        
        Evergreen.getInstance().getSidebar().showPanel(errorsPanel);
    }
    
    public void showOpenDialog() {
        if (openDialog == null) {
            openDialog = EFileDialog.makeOpenDialog(Evergreen.getInstance().getFrame(), getCurrentDirectory());
        }
        openDialog.show();
        String name = openDialog.getFile();
        if (name != null) {
            Evergreen.getInstance().openFile(name);
        }
    }

    /** Returns the chosen save-as name, or null. */
    public String showSaveAsDialog() {
        if (saveAsDialog == null) {
            saveAsDialog = EFileDialog.makeSaveDialog(Evergreen.getInstance().getFrame(), getCurrentDirectory());
        }
        saveAsDialog.show();
        return saveAsDialog.getFile();
    }
    
    public void takeWindow(EWindow window) {
        window.removeFromColumn();
        leftColumn.addComponent(window, -1);
    }
    
    public void moveFilesToBestWorkspaces() {
        for (ETextWindow textWindow : leftColumn.getTextWindows()) {
            Workspace bestWorkspace = Evergreen.getInstance().getBestWorkspaceForFilename(textWindow.getFilename());
            if (bestWorkspace != this) {
                bestWorkspace.takeWindow(textWindow);
            }
        }        
    }
    
    public void rememberFocusedTextWindow(ETextWindow textWindow) {
        rememberedTextWindow = textWindow;
    }
    
    public void restoreFocusToRememberedTextWindow() {
        if (rememberedTextWindow != null) {
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    rememberedTextWindow.requestFocus();
                }
            });
        }
    }
    
    public String getBuildTarget() {
        return buildTarget;
    }
    
    public void setBuildTarget(String newBuildTarget) {
        this.buildTarget = (newBuildTarget != null) ? newBuildTarget : "";
    }
    
    public void serializeAsXml(org.w3c.dom.Document document, org.w3c.dom.Element workspaces) {
        org.w3c.dom.Element workspace = document.createElement("workspace");
        workspaces.appendChild(workspace);
        workspace.setAttribute("name", getTitle());
        workspace.setAttribute("root", getRootDirectory());
        workspace.setAttribute("buildTarget", getBuildTarget());
        if (Evergreen.getInstance().getCurrentWorkspace() == this) {
            workspace.setAttribute("selected", "true");
        }
        // Remember any files that were actually shown.
        for (ETextWindow textWindow : leftColumn.getTextWindows()) {
            org.w3c.dom.Element file = document.createElement("file");
            workspace.appendChild(file);
            file.setAttribute("name", textWindow.getFilename() + textWindow.getAddress());
            file.setAttribute("y", Integer.toString(textWindow.getY()));
            if (textWindow == rememberedTextWindow) {
                file.setAttribute("lastFocused", "true");
            }
        }
        // Remember any files that would have been shown, had this workspace ever been shown.
        for (Evergreen.InitialFile initialFile : initialFiles) {
            org.w3c.dom.Element file = document.createElement("file");
            workspace.appendChild(file);
            file.setAttribute("name", initialFile.filename);
            file.setAttribute("y", Integer.toString(initialFile.y));
            if (initialFile.lastFocused) {
                file.setAttribute("lastFocused", "true");
            }
        }
    }
    
    public void setInitialFiles(List<Evergreen.InitialFile> initialFiles) {
        this.initialFiles = initialFiles;
        updateTabForWorkspace();
    }
    
    public int getInitialFileCount() {
        synchronized (initialFiles) {
            return initialFiles.size();
        }
    }
    
    /** Opens all the files listed in the file we remembered them to last time we quit. */
    public void openRememberedFiles() {
        synchronized (initialFiles) {
            for (Evergreen.InitialFile file : initialFiles) {
                ETextWindow fileWindow = (ETextWindow) Evergreen.getInstance().openFile(file);
                if (file.lastFocused) {
                    rememberedTextWindow = fileWindow;
                }
            }
            initialFiles.clear();
            updateTabForWorkspace();
            restoreFocusToRememberedTextWindow();
        }
    }
}
