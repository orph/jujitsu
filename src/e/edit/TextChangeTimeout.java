package e.edit;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.*;
import javax.swing.tree.*;
import javax.swing.text.JTextComponent;

class TextChangeTimeout implements DocumentListener, ComponentListener, ActionListener {
    private final ActionListener listener;
    private boolean isListenerUpToDate;
    private Timer textChangeTimer;

    public TextChangeTimeout(JTextComponent[] textComponents, ActionListener listener) {
        this.listener = listener;
        this.isListenerUpToDate = true;

        this.textChangeTimer = new Timer(500, this);
        textChangeTimer.setRepeats(false);

        for (JTextComponent field : textComponents) {
            field.getDocument().addDocumentListener(this);
            field.addComponentListener(this);
        }
    }

    public TextChangeTimeout(JTextComponent textComponent, ActionListener listener) {
        this(new JTextComponent[] { textComponent }, listener);
    }

    public void changedUpdate(DocumentEvent e) {
        textChanged();
    }
                
    public void insertUpdate(DocumentEvent e) {
        textChanged();
    }
                
    public void removeUpdate(DocumentEvent e) {
        textChanged();
    }
                
    private void textChanged() {
        textChangeTimer.restart();
        isListenerUpToDate = false;
    }

    public void componentShown(ComponentEvent e) {
        listener.actionPerformed(null);
    }
            
    public void componentHidden(ComponentEvent e) {
        if (isListenerUpToDate == false) {
            listener.actionPerformed(null);
        }
    }

    public void componentMoved(ComponentEvent e) {
        // Do nothing
    }

    public void componentResized(ComponentEvent e) {
        // Do nothing
    }

    public void actionPerformed(ActionEvent e) {
        listener.actionPerformed(null);
        isListenerUpToDate = true;
    }
}
