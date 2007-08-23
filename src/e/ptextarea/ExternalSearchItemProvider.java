package e.ptextarea;

import e.gui.*;
import e.util.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

class ExternalSearchItemProvider implements MenuItemProvider {
    private PTextArea textArea;
    
    public ExternalSearchItemProvider(PTextArea textArea) {
        this.textArea = textArea;
    }
    
    public void provideMenuItems(MouseEvent e, Collection<Action> actions) {
        if (GuiUtilities.isMacOs()) {
            actions.add(new SearchInSpotlightAction());
        }
        actions.add(new SearchInGoogleAction());
        actions.add(new LookUpInDictionaryAction());
    }
    
    private abstract class ExternalSearchAction extends AbstractAction {
        public ExternalSearchAction(String name) {
            super(name);
        }
        
        // It only makes sense to search for a non-empty selection.
        public boolean isEnabled() {
            return (textArea.getSelectionStart() != textArea.getSelectionEnd());
        }
    }
    
    private class SearchInSpotlightAction extends ExternalSearchAction {
        public SearchInSpotlightAction() {
            super("Search in Spotlight");
        }
        
        public void actionPerformed(ActionEvent e) {
            // Our "NSPerformService" helper needs to be on the path.
            String searchTerm = textArea.getSelectedText().trim();
            ProcessUtilities.spawn(null, new String[] { "NSPerformService", "Spotlight", searchTerm });
        }
    }
    
    private class SearchInGoogleAction extends ExternalSearchAction {
        public SearchInGoogleAction() {
            super("Search in Google");
        }
        
        public void actionPerformed(ActionEvent e) {
            try {
                String encodedSelection = StringUtilities.urlEncode(textArea.getSelectedText().trim());
                BrowserLauncher.openURL("http://www.google.com/search?q=" + encodedSelection + "&ie=UTF-8&oe=UTF-8");
            } catch (Exception ex) {
                Log.warn("Exception launching browser", ex);
            }
        }
    }
    
    private class LookUpInDictionaryAction extends ExternalSearchAction {
        public LookUpInDictionaryAction() {
            super("Look Up in Dictionary");
        }
        
        public void actionPerformed(ActionEvent e) {
            try {
                // We need to rewrite spaces as "%20" for them to find their
                // way to Dictionary.app unmolested. The usual url-encoded
                // form ("+") doesn't work, for some reason.
                String encodedSelection = textArea.getSelectedText().trim().replaceAll("\\s+", "%20");
                if (GuiUtilities.isMacOs()) {
                    // In Mac OS 10.4.1, a dict: URI that causes Dictionary.app to
                    // start doesn't actually cause the definition to be shown, so
                    // we need to ask twice. If we knew the dictionary was already
                    // open, we could avoid the flicker. But we may as well wait
                    // for Apple to fix the underlying problem.
                    BrowserLauncher.openURL("dict:///");
                    BrowserLauncher.openURL("dict:///" + encodedSelection);
                } else {
                    BrowserLauncher.openURL("http://www.answers.com/" + encodedSelection);
                }
            } catch (Exception ex) {
                Log.warn("Exception launching browser", ex);
            }
        }
    }
}
