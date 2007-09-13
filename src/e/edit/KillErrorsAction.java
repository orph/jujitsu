package e.edit;

import java.awt.event.*;
import javax.swing.*;

public class KillErrorsAction extends AbstractAction {
    public KillErrorsAction() {
        super("Clear Errors");
        putValue(ACCELERATOR_KEY, e.util.GuiUtilities.makeKeyStroke("K", false));
    }
    
    public void actionPerformed(ActionEvent e) {
        Workspace workspace = Evergreen.getInstance().getCurrentWorkspace();
        if (workspace == null) {
            return;
        }
        EErrorsPanel errorsPanel = workspace.getErrorsPanel();
        if (errorsPanel == null) {
            return;
        }
        errorsPanel.clear();
    }
}
