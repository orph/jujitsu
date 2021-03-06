package e.edit;

import e.forms.*;
import e.gui.*;
import e.util.*;
import java.io.*;
import javax.swing.*;

/**
 * Factors out code common to the workspace creation/modification dialogs.
 */
public class WorkspaceProperties {
    public String name;
    public String rootDirectory;
    public String buildTarget;
    
    public boolean showWorkspacePropertiesDialog(String dialogTitle, String buttonTitle) {
        JTextField nameField = new JTextField("", 40);
        if (name != null) {
            nameField.setText(name);
        }
        
        FilenameChooserField filenameChooserField = new FilenameChooserField(JFileChooser.DIRECTORIES_ONLY);
        filenameChooserField.setCompanionNameField(nameField);
        if (rootDirectory != null) {
            filenameChooserField.setPathname(rootDirectory);
        }
        
        JTextField buildTargetField = new JTextField("", 40);
        if (buildTarget != null) {
            buildTargetField.setText(buildTarget);
        }
        
        FormBuilder form = new FormBuilder(Evergreen.getInstance().getFrame(), dialogTitle);
        FormPanel formPanel = form.getFormPanel();
        formPanel.addRow("Root Directory:", filenameChooserField);
        formPanel.addRow("Name:", nameField);
        formPanel.addRow("Build Target:", buildTargetField);
        
        while (form.show(buttonTitle)) {
            String pathname = filenameChooserField.getPathname();
            // Protect Windows users against accidental use of '/', which will probably mostly work, but is likely to lead to confusion.
            pathname = pathname.replace('/', File.separatorChar).replace('\\', File.separatorChar);
            if (checkPathnameIsDirectory(pathname)) {
                name = nameField.getText();
                if (name.trim().length() == 0) {
                    // Default the visible workspace name to the leafname of the workspace root.
                    name = FileUtilities.fileFromString(pathname).getName();
                }
                rootDirectory = pathname;
                buildTarget = buildTargetField.getText();
                return true;
            }
        }
        return false;
    }
    
    private boolean checkPathnameIsDirectory(String pathname) {
        File proposedDirectory = FileUtilities.fileFromString(pathname);
        if (proposedDirectory.exists() == false) {
            boolean createDirectory = Evergreen.getInstance().askQuestion("Create directory?", "The directory \"" + proposedDirectory + "\" doesn't exist.", "Create");
            if (createDirectory == false) {
                return false;
            }
            proposedDirectory.mkdirs();
            return true;
        } else if (proposedDirectory.isDirectory() == false) {
            Evergreen.getInstance().showAlert("Not a directory", "The path \"" + pathname + "\" exists but is not a directory.");
            return false;
        }
        return true;
    }
}
