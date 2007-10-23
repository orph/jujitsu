package e.edit;

import e.gui.*;
import java.awt.event.*;
import javax.swing.*;

public class CloseWorkspaceAction extends AbstractAction {
    private Workspace boundWorkspace;
    
    // Remove the given workspace.
    public CloseWorkspaceAction(Workspace workspace) {
        super("Close Project");
        GnomeStockIcon.useStockIcon(this, "gtk-close");
        this.boundWorkspace = workspace;
    }
    
    // Remove the current workspace at the time the action is performed.
    public CloseWorkspaceAction() {
        this(null);
    }
    
    public void actionPerformed(ActionEvent e) {
        Workspace workspace = (boundWorkspace != null) ? boundWorkspace : Evergreen.getInstance().getCurrentWorkspace();
        Evergreen.getInstance().removeWorkspace(workspace);
    }
}
