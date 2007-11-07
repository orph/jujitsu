package e.edit;

import e.gui.*;
import e.ptextarea.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import org.jdesktop.swingworker.SwingWorker;

public class Advisor extends JPanel {
    private static Advisor instance;
    
    private ArrayList<WorkspaceResearcher> researchers;
    
    /** The advice window. */
    private AdvisorHtmlPane advicePane = new AdvisorHtmlPane();
    
    private JFrame frame;
    
    public static synchronized Advisor getInstance() {
        if (instance == null) {
            instance = new Advisor();
        }
        return instance;
    }
    
    private Advisor() {
        setLayout(new BorderLayout());
        add(advicePane.makeToolBar(), BorderLayout.NORTH);
        add(advicePane, BorderLayout.CENTER);
        
        /*
        final JTextField textField = new JTextField();
        textField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                linkClicked(textField.getText());
            }
        });
        add(textField, BorderLayout.NORTH);
        */
    }
    
    private synchronized ArrayList<WorkspaceResearcher> getResearchers() {
        if (researchers == null) {
            researchers = new ArrayList<WorkspaceResearcher>();
            researchers.add(JavaResearcher.getSharedInstance());
            researchers.add(ManPageResearcher.getSharedInstance());
            researchers.add(new NumberResearcher());
            researchers.add(new PerlDocumentationResearcher());
            researchers.add(new RubyDocumentationResearcher());
            researchers.add(new StlDocumentationResearcher());
        }
        return researchers;
    }
    
    private synchronized JFrame getFrame() {
        if (frame == null) {
            frame = JFrameUtilities.makeSimpleWindow("Evergreen Documentation Browser", this);
            frame.setSize(new Dimension(600, 500));
            JFrameUtilities.closeOnKeyStroke(frame, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false));
        }
        return frame;
    }
    
    public synchronized void showDocumentation() {
        advicePane.clearAdvice();
        getFrame().setVisible(true);
        startResearch(getSearchTerm());
    }
    
    private class ResearchRunner extends SwingWorker<String, Object> {
        private String searchTerm;
        private ETextWindow textWindow;
        
        private ResearchRunner(String searchTerm) {
            this.searchTerm = searchTerm;
            this.textWindow = ETextAction.getFocusedTextWindow();
            advicePane.setTemporaryText("Searching for documentation on \"" + searchTerm + "\"...");
        }
        
        @Override
        protected String doInBackground() {
            StringBuilder newText = new StringBuilder();
            for (WorkspaceResearcher researcher : getResearchers()) {
                if (textWindow == null || researcher.isSuitable(textWindow)) {
                    String result = researcher.research(searchTerm, textWindow);
                    if (result != null && result.length() > 0) {
                        newText.append(result);
                    }
                }
            }
            
            if (newText.length() == 0) {
                newText.append("No documentation found for \"" + searchTerm + "\".");
            }
            
            return newText.toString();
        }
        
        @Override
        public void done() {
            try {
                showDocumentation(get());
            } catch (Exception ex) {
                Log.warn("ResearchRunner failed", ex);
            }
        }
    }
    
    public void showDocumentation(String content) {
        if (content.startsWith("<html>") == false) {
            content = "<html><head><title></title></head><body bgcolor=#FFFFFF>" + content + "</body></html>";
        }
        // JEditorPane.setText is thread-safe.
        advicePane.setText(content);
    }
    
    private String getSearchTerm() {
        ETextArea textArea = ETextAction.getFocusedTextArea();
        if (textArea == null) {
            return "";
        }
        
        // We use the selection, if there is one.
        String selection = textArea.getSelectedText();
        if (selection.length() > 0) {
            return selection.trim();
        }
        
        // Otherwise, we use the word at the caret.
        CharSequence chars = textArea.getTextBuffer();
        String stopChars = chooseStopChars();
        int caretPosition = textArea.getSelectionStart();
        int start = PWordUtilities.getWordStart(chars, caretPosition, stopChars);
        int end = PWordUtilities.getWordEnd(chars, caretPosition, stopChars);
        return chars.subSequence(start, end).toString();
    }
    
    private static String chooseStopChars() {
        FileType fileType = ETextAction.getFocusedTextWindow().getFileType();
        String stopChars = PWordUtilities.DEFAULT_STOP_CHARS;
        if (fileType == FileType.C_PLUS_PLUS) {
            // "::" is useful in C++, so remove it from the stop list.
            // An alternative would be to stop insisting on the "std::" prefix for STL lookups, but that would introduce ambiguity: std::string versus string(3), for example.
            stopChars = stopChars.replace(":", "");
        }
        return stopChars;
    }
    
    private void startResearch(String text) {
        new ResearchRunner(text).execute();
    }
    
    public void linkClicked(String link) {
        new LinkClickRunner(link).execute();
    }
    
    private class LinkClickRunner extends SwingWorker<Object, Object> {
        private String link;
        
        private LinkClickRunner(String link) {
            this.link = link;
        }
        
        @Override
        protected Object doInBackground() {
            // Anything off the web or local HTML should be displayed in the documentation browser, rather than handed off to the platform's web browser.
            // Non-HTML files, though, need to be handed off so they're opened for editing.
            if (link.startsWith("http:") || link.matches("file:.*\\.html(#.*)?")) {
                advicePane.setPage(link);
                return null;
            }
            // Offer the link to each researcher.
            for (WorkspaceResearcher researcher : getResearchers()) {
                if (researcher.handleLink(link)) {
                    return null;
                }
            }
            // Hand it on to the file-opening code to work out what to do with it.
            Evergreen.getInstance().openFile(link);
            return null;
        }
    }
    
    public static String findToolOnPath(String tool) {
        ArrayList<String> availableTools = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(null, new String[] { "which", tool }, availableTools, errors);
        if (status != 0 || availableTools.size() == 0) {
            return null;
        }
        return availableTools.get(0);
    }
}
