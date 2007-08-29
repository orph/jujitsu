package e.edit;

import e.util.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * Shrinks the focused window to its minimum size.
 */
public class ShrinkWindowAction extends ETextAction {
    public ShrinkWindowAction() {
        super("Shrink Buffer");
        // I don't know of any precedent, but C-1 is close to C-` and C-~, which you're likely to have just used.
        // I'm told that emacs uses C-X 1 to mean something similar, so that's good too.
        putValue(ACCELERATOR_KEY, GuiUtilities.makeKeyStroke("0", false));
    }
    
    public void actionPerformed(ActionEvent e) {
        EWindow window = (EWindow) SwingUtilities.getAncestorOfClass(EWindow.class, getFocusedComponent());
        if (window == null) {
            return;
        }
        EColumn column = window.getColumn();
        if (column == null) {
            return;
        }
        
        EWindow newWin = column.cycleWindow(window, 1);
        column.expandComponent(newWin);
    }
}
