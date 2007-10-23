package e.edit;

import java.awt.event.*;
import javax.swing.*;

/**
 * Makes the action's workspace the current one.
 */
public class OpenWorkspaceAction extends AbstractAction {
    Workspace workspace;
    
    public OpenWorkspaceAction(Workspace ws) {
        super(ws.getRootDirectory());
        this.workspace = ws;
    }
    
    public void actionPerformed(ActionEvent e) {
        Evergreen.getInstance().showWorkspace(this.workspace);
    }
}
