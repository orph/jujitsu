package e.edit;

import java.awt.event.*;
import javax.swing.*;
import e.forms.*;
import e.gui.*;
import e.util.*;

/**
 * Opens a dialog where the user can specify a name and root directory for a new workspace,
 * which will then be created.
 */
public class AddWorkspaceAction extends AbstractAction {
    public AddWorkspaceAction() {
        super("Add Workspace...");
    }
    
    public void actionPerformed(ActionEvent e) {
        JTextField nameField = new JTextField("", 40);
        
        FilenameChooserField filenameChooserField = new FilenameChooserField(JFileChooser.DIRECTORIES_ONLY);
        filenameChooserField.setCompanionNameField(nameField);
        
        FormBuilder form = new FormBuilder(Evergreen.getInstance().getFrame(), "Add Workspace");
        FormPanel formPanel = form.getFormPanel();
        formPanel.addRow("Root Directory:", filenameChooserField);
        formPanel.addRow("Name:", nameField);
        
        while (form.show("Add")) {
            String message = FileUtilities.checkDirectoryExistence(filenameChooserField.getPathname());
            if (message != null) {
                Evergreen.getInstance().showAlert(message, "The pathname you supply must exist, and must be a directory.");
            } else {
                Evergreen.getInstance().createWorkspace(nameField.getText(), filenameChooserField.getPathname());
                return;
            }
        }
    }
}
