package e.edit;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.*;
import javax.swing.tree.*;
import javax.swing.text.JTextComponent;

public class Sidebar extends JPanel {
    private JPanel emptyPanel;
    private Component defaultComponent;
    private Component previousFocusOwner;
    
    public Sidebar() {
        setLayout(new CardLayout());
        initUI();
    }

    private void initUI() {
        emptyPanel = new JPanel();
        emptyPanel.setBackground(UIManager.getColor("Tree.background"));
        emptyPanel.setSize(0, 0);

        defaultComponent = emptyPanel;
        add(emptyPanel, "");
    }

    public void addPanel(JComponent c) {
        assert c.getName() != null;
        
        c.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true), "revert-panel");
        c.getActionMap().put("revert-panel", new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("actionPerformed::revert-panel");
                    revertToDefaultPanel();
                }
            });

        add(c, c.getName());
    }

    public void showPanel(Component c) {
        CardLayout cl = (CardLayout)(getLayout());
        cl.show(this, c.getName());

        if (c != defaultComponent) {
            previousFocusOwner = 
                KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            c.requestFocus();
        }
    }
    
    public void addDefaultPanel(Component c) {
        assert c.getName() != null;

        defaultComponent = c;

        // NOTE: We don't call addPanel since we don't want to set up focus
        //       listeners.
        add(c, c.getName());
    }
    
    public void revertToDefaultPanel() {
        showPanel(defaultComponent);

        if (previousFocusOwner != null) {
            previousFocusOwner.requestFocusInWindow();
            previousFocusOwner = null;
        }
    }
    
    public void showError(String error) {
        JLabel label = new JLabel("<html><body>" + error);
        label.setBackground(UIManager.getColor("Tree.background"));
        label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        label.setOpaque(true);

        add(label, "error");
        showPanel(label);
    }
}
