package e.edit;

import e.gui.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * Opens a dialog where the user can modify the details of an existing workspace.
 */
public class EditWorkspaceAction extends AbstractAction {
    private Workspace boundWorkspace;
    
    // Edit the given workspace.
    public EditWorkspaceAction(Workspace workspace) {
        super("Project Settings...");
        GnomeStockIcon.useStockIcon(this, "gtk-preferences");
        this.boundWorkspace = workspace;
    }
    
    // Edit the current workspace at the time the action is performed.
    public EditWorkspaceAction() {
        this(null);
    }
    
    public void actionPerformed(ActionEvent e) {
        Workspace workspace = (boundWorkspace != null) ? boundWorkspace : Evergreen.getInstance().getCurrentWorkspace();
        
        WorkspaceProperties properties = new WorkspaceProperties();
        properties.name = workspace.getTitle();
        properties.rootDirectory = workspace.getRootDirectory();
        properties.buildTarget = workspace.getBuildTarget();
        
        if (properties.showWorkspacePropertiesDialog("Project Settings", "Apply") == true) {
            workspace.setTitle(properties.name);
            workspace.setRootDirectory(properties.rootDirectory);
            workspace.setBuildTarget(properties.buildTarget);
        }
    }
}
