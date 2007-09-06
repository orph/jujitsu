package e.edit;

import e.gui.*;
import e.util.*;
import java.awt.event.*;

/**
 * Opens the "Open Quickly" dialog with the current selection entered in the dialog's
 * text field.
 */
public class OpenQuicklyAction extends ETextAction {
    public OpenQuicklyAction() {
        super("Open Quickly...");
        putValue(ACCELERATOR_KEY, e.util.GuiUtilities.makeKeyStroke("O", false));
        GnomeStockIcon.useStockIcon(this, "gtk-open");
    }
    
    public void actionPerformed(ActionEvent e) {
        String filename = getSelectedText();
        if (filename.startsWith("~") || filename.startsWith("/")) {
            // If we have an absolute name, we can go straight there.
            Evergreen.getInstance().openFile(filename);
        } else {
            // Jujitsu uses the sidebar version
            //Evergreen.getInstance().getCurrentWorkspace().showOpenQuicklyDialog(StringUtilities.regularExpressionFromLiteral(filename));
            Evergreen.getInstance().getCurrentWorkspace().showOpenQuicklyPanel(StringUtilities.regularExpressionFromLiteral(filename));
        }
    }
}
