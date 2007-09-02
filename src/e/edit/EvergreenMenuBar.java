package e.edit;

import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import e.gui.*;
import e.ptextarea.*;
import e.util.*;

public class EvergreenMenuBar extends EMenuBar {
    public EvergreenMenuBar() {
        add(makeFileMenu());
        add(makeEditMenu());
        add(makeFindMenu());
        add(makeViewMenu());
        add(makeScmMenu());
        add(makeWorkspaceMenu());
        add(makeToolsMenu());
        add(makeDocumentationMenu());
        add(makeBufferMenu());
        add(makeHelpMenu());
    }
    
    public static class ExitAction extends AbstractAction {
        public ExitAction() {
            super(GuiUtilities.isWindows() ? "Exit" : "Quit");
            GnomeStockIcon.configureAction(this);
        }
        
        public void actionPerformed(ActionEvent e) {
            Evergreen.getInstance().handleQuit(null);
        }
    }
    
    private JMenu makeFileMenu() {
        JMenu menu = new JMenu("File");
        menu.setMnemonic('F');

        menu.add(new NewFileAction());
        menu.add(new OpenQuicklyAction());
        menu.add(new OpenAction());
        // FIXME: Should be "Open Recent >" here.

        menu.add(new JSeparator());
        menu.add(new CloseWindowAction());
        menu.add(new SaveAction());
        menu.add(new SaveAsAction());
        menu.add(new SaveAllAction());
        menu.add(new RevertToSavedAction());
        
        menu.add(new JSeparator());
        menu.add(new FilePropertiesAction());
        
        if (GuiUtilities.isMacOs() == false) {
            menu.add(new JSeparator());
            menu.add(new JMenuItem(new ExitAction()));
        }
        return menu;
    }

    private static JMenuItem makeAcceleratedItem(Action action, String key) {
        return makeAcceleratedItemEx(action, key, false);
    }

    private static JMenuItem makeAcceleratedItemEx(Action action, String key, boolean shifted) {
        JMenuItem item = new JMenuItem(action);
        KeyStroke keyStroke = GuiUtilities.makeKeyStroke(key, shifted);
        if (keyStroke != null) {
            item.setAccelerator(keyStroke);
        }
        return item;
    }
    
    private JMenu makeEditMenu() {
        JMenu menu = new JMenu("Edit");
        menu.setMnemonic('E');

        menu.add(PActionFactory.makeUndoAction());
        menu.add(PActionFactory.makeRedoAction());

        menu.add(new JSeparator());
        menu.add(PActionFactory.makeCutAction());
        menu.add(PActionFactory.makeCopyAction());
        menu.add(PActionFactory.makePasteAction());
        menu.add(new AutoCompleteAction());
        menu.add(PActionFactory.makeSelectAllAction());

        menu.add(new JSeparator());
        menu.add(new CorrectIndentationAction());
        menu.add(new InsertInterfaceAction());
        
        menu.add(new JSeparator());
        menu.add(new ShowMisspellingsAction());
        menu.add(new CompareSelectionAndClipboardAction());

        return menu;
    }

    private JMenu makeFindMenu() {
        JMenu menu = new JMenu("Find");
        menu.setMnemonic('n');

        menu.add(FindAction.INSTANCE);
        menu.add(new FindFilesContainingSelectionAction());
        menu.add(new JSeparator());
        
        menu.add(PActionFactory.makeFindNextAction());
        menu.add(PActionFactory.makeFindPreviousAction());
        menu.add(new JSeparator());
        
        menu.add(new FindAndReplaceAction());
        menu.add(new JSeparator());
        
        menu.add(new GotoAction());
        menu.add(new ScrollToSelectionAction());
        
        return menu;
    }
    
    private JMenu makeViewMenu() {
        JMenu menu = new JMenu("View");
        menu.setMnemonic('V');

        menu.add(new ProportionalFontAction());
        menu.add(new FixedFontAction());
        menu.add(new AppropriateFontAction());
        
        menu.add(new JSeparator());
        menu.add(new ShowCounterpartAction());
        
        menu.add(new JSeparator());
        menu.add(ShowHideTagsAction.makeMenuItem());
        
        return menu;
    }
    
    private JMenu makeToolsMenu() {
        final JMenu menu = new JMenu("Tools");
        menu.setMnemonic('T');

        menu.add(new OpenMakefileAction());
        
        menu.add(new JSeparator());
        menu.add(new KillErrorsAction());
        
        ExternalToolsParser toolsParser = new ExternalToolsParser() {
            public void addItem(ExternalToolAction action) {
                menu.add(action);
            }

            public void addItem(ExternalToolAction action, char keyboardEquivalent) {
                menu.add(makeAcceleratedItemEx(action, "" + keyboardEquivalent, true));
            }

            public void addSeparator() {
                menu.add(new JSeparator());
            }
        };
        toolsParser.parse();
        return menu;
    }
    
    private JMenu makeDocumentationMenu() {
        JMenu menu = new JMenu("Documentation");
        menu.setMnemonic('D');

        menu.add(new ShowDocumentationAction());
        
        // UI guidelines.
        menu.add(new JSeparator());
        menu.add(makeLocalOrRemoteLink("Apple Human Interface Guidelines", null, "http://developer.apple.com/documentation/UserExperience/Conceptual/OSXHIGuidelines/index.html"));
        menu.add(makeLocalOrRemoteLink("GNOME Human Interface Guidelines", null, "http://developer.gnome.org/projects/gup/hig/2.0/"));
        
        // C/C++.
        menu.add(new JSeparator());
        menu.add(makeLocalOrRemoteLink("GNU C Library Documentation", "/usr/share/doc/glibc-doc/html/index.html", "http://www.gnu.org/software/libc/manual/html_node/index.html"));
        menu.add(makeLocalOrRemoteLink("STL Documentation", "/usr/share/doc/stl-manual/html/index.html", "http://www.sgi.com/tech/stl/"));
        
        // Java.
        menu.add(new JSeparator());
        menu.add(makeLocalOrRemoteLink("Java Language Specification, 3e", null, "http://java.sun.com/docs/books/jls/third_edition/html/j3TOC.html"));
        menu.add(makeLocalOrRemoteLink("Java Tutorial", null, "http://java.sun.com/docs/books/tutorial/"));
        menu.add(makeLocalOrRemoteLink("Java VM Specification, 2e", null, "http://java.sun.com/docs/books/vmspec/2nd-edition/html/VMSpecTOC.doc.html"));
        
        // Tools.
        menu.add(new JSeparator());
        menu.add(makeLocalOrRemoteLink("GNU Make Manual", null, "http://www.gnu.org/software/make/manual/make.html"));
        
        // General.
        menu.add(new JSeparator());
        // FIXME: some day it would be nice to have our own regular expression quick reference.
        menu.add(makeLocalOrRemoteLink("Regular Expression Documentation", "/usr/share/doc/sun-java5-jdk/html/api/java/util/regex/Pattern.html", PatternUtilities.DOCUMENTATION_URL));
        
        return menu;
    }
    
    private WebLinkAction makeLocalOrRemoteLink(String name, String localFilename, String remoteUrl) {
        String url = (localFilename != null && FileUtilities.exists(localFilename)) ? "file://" + localFilename : remoteUrl;
        return new WebLinkAction(name, url);
    }
    
    private JMenu makeScmMenu() {
        JMenu menu = new JMenu("SCM");
        menu.setMnemonic('S');

        menu.add(new CheckInChangesAction());
        menu.add(new ShowHistoryAction());
        return menu;
    }
    
    private JMenu makeWorkspaceMenu() {
        JMenu menu = new JMenu("Workspace");
        menu.setMnemonic('W');

        menu.add(new BuildAction());
        menu.add(new RescanWorkspaceAction());
        menu.add(new JSeparator());
        menu.add(new AddWorkspaceAction());
        menu.add(new EditWorkspaceAction());
        menu.add(new RemoveWorkspaceAction());
        menu.add(new JSeparator());
        menu.add(new CycleWorkspacesAction(1));
        menu.add(new CycleWorkspacesAction(-1));
        return menu;
    }
    
    private JMenu makeBufferMenu() {
        JMenu menu = new JMenu("Buffer");
        menu.setMnemonic('B');

        menu.add(new CycleWindowsAction(1));
        menu.add(new CycleWindowsAction(-1));
        menu.add(new JSeparator());
        menu.add(new ExpandWindowAction());
        menu.add(new ShrinkWindowAction());
        return menu;
    }
    
    private JMenu makeHelpMenu() {
        HelpMenu helpMenu = new HelpMenu();
        return helpMenu.makeJMenu();
    }
    
    @Override
    protected boolean processKeyBinding(KeyStroke ks, KeyEvent event, int condition, boolean pressed) {
        int modifier = KeyEvent.ALT_MASK;
        if ((event.getModifiers() & modifier) == modifier) {
            char ch = event.getKeyChar();
            if (ch >= '1' && ch <= '9') {
                Evergreen.getInstance().goToWorkspaceByIndex(ch - '1');
                return true;
            }
        }
        return super.processKeyBinding(ks, event, condition, pressed);
    }
}
