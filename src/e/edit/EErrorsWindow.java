package e.edit;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import e.ptextarea.*;
import e.gui.*;
import e.util.*;

/**
 * Keeps track of the current directory based on the messages about
 * entering and leaving directories in GNU Make output.
 * 
 * This was called on the event dispatch thread via EErrorsWindow.append,
 * rather than ErrorLinkStyler (where it used to be) because the link
 * styler is called over and over again as we're redrawn or the mouse
 * moves across us, et cetera. Unfortunately, this wasn't the right place
 * either, because the styler still has to use this information to try
 * to turn a relative name into an absolute name, and a big recursive
 * build may have moved on since the output we were currently redrawing.
 * 
 * As far as I know, the best solution is to make sure that your
 * build output only contains absolute names, and to not rely on this
 * code to do anything useful.
 */
public class EErrorsWindow extends EWindow {
    private static final Pattern MAKE_ENTERING_DIRECTORY_PATTERN = Pattern.compile("^make(?:\\[\\d+\\])?: Entering directory `(.*)'$", Pattern.MULTILINE);
    
    /**
     * Matches lines in a Java stack trace, such as "package.Class$Inner$1.method(Class.java:line)"
     */
    private static final Pattern JAVA_STACK_TRACE_PATTERN = Pattern.compile("([\\.\\w]+)(?:(?:\\$\\w+)*?\\.)[\\w\\$<>]+\\(\\w+\\.java(:\\d+)");
    
    private final Workspace workspace;
    private PTextArea textArea;
    
    public EErrorsWindow(Workspace workspace) {
        super("+Errors");
        this.workspace = workspace;
        initTextArea();
        JScrollPane scrollPane = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        GuiUtilities.keepMaximumShowing(scrollPane.getVerticalScrollBar());
        add(scrollPane, BorderLayout.CENTER);
    }
    
    private void initTextArea() {
        textArea = new PTextArea();
        // Default to a fixed-pitch font in errors windows.
        textArea.setFont(ChangeFontAction.getConfiguredFixedFont());
        // But no margin, because all the text should be machine-generated.
        textArea.showRightHandMarginAt(PTextArea.NO_MARGIN);
        textArea.addStyleApplicator(new ErrorLinkStyler(textArea));
        textArea.setWrapStyleWord(true);
        initTextAreaPopupMenu();
    }

    private class ErrorLinkStyler extends RegularExpressionStyleApplicator {
        /**
         * Matches addresses (such as "filename.ext:line:col:line:col").
         * 
         * We avoid matching the " or ' before a filename.
         * We insist that an interesting extension has between 1 and 4 characters, and contains only alphabetic characters.
         * (There's an additional check later that the extension isn't known to be uninteresting, such as ".o" or ".class".)
         */
        private static final String ADDRESS_PATTERN = "(?:^| |\"|')([^ :\"']+(?:Makefile|\\w+\\.[A-Za-z]{1,4}\\b)([\\d:]+)?)";
        
        public ErrorLinkStyler(PTextArea textArea) {
            super(textArea, ADDRESS_PATTERN, PStyle.HYPERLINK);
        }
        
        @Override
        public boolean isAcceptableMatch(CharSequence line, Matcher matcher) {
            String match = matcher.group(1);
            
            if (FileIgnorer.isIgnoredExtension(match)) {
                return false;
            }
            
            return true;
        }
        
        @Override
        protected void configureSegment(PTextSegment segment, Matcher matcher) {
            segment.setLinkAction(new ErrorLinkActionListener(matcher.group(1), segment.getOffset()));
        }
    }
    
    private class ErrorLinkActionListener implements ActionListener {
        private final String address;
        private final int offset;
        
        public ErrorLinkActionListener(String address, int offset) {
            this.address = address;
            this.offset = offset;
        }
        
        public void actionPerformed(ActionEvent e) {
            // We're most useful in providing links to grep matches, so we
            // need to avoid being confused by stuff like File.java:123.
            String name = address;
            String tail ="";
            int colonIndex = name.indexOf(':');
            if (colonIndex != -1) {
                name = name.substring(0, colonIndex);
                tail = address.substring(colonIndex);
            }
            
            if (name.startsWith("/") || name.startsWith("~")) {
                open(address);
                return;
            }
            
            Matcher matcher = JAVA_STACK_TRACE_PATTERN.matcher(address);
            if (matcher.matches()) {
                handleJavaStackTraceMatch(matcher);
            } else {
                handleNonCanonicalFilename(name, tail);
            }
        }
        
        private void handleJavaStackTraceMatch(Matcher matcher) {
            String dottedClassName = matcher.group(1);
            String colonAddress = matcher.group(2);
            
            List<String> candidates = JavaDoc.findSourceFilenames(dottedClassName);
            if (candidates.size() != 1) {
                // FIXME: if there's any reason why this should ever occur in real life, we could offer a dialog so the user can disambiguate.
                Evergreen.getInstance().showAlert("Can't find matching class", "The class name \"" + dottedClassName + "\" is ambiguous.");
                return;
            }
            
            String fullFilename = candidates.get(0);
            open(fullFilename + colonAddress);
        }
        
        private void handleNonCanonicalFilename(String name, String tail) {
            // Try to resolve the non-canonical filename by finding the last-known directory.
            String currentDirectory = workspace.getRootDirectory();
            String errors = textArea.getText();
            Matcher matcher = MAKE_ENTERING_DIRECTORY_PATTERN.matcher(errors);
            while (matcher.find() && matcher.start() < offset) {
                currentDirectory = matcher.group(1);
            }
            open(currentDirectory + File.separator + name + tail);
        }
        
        private void open(String address) {
            Evergreen.getInstance().openFile(address);
        }
    }
    
    public void requestFocus() {
        textArea.requestFocus();
    }
    
    private class AppendRunnable implements Runnable {
        private String text;
        
        public AppendRunnable(String[] lines) {
            this.text = StringUtilities.join(lines, "\n") + "\n";
        }
        
        public void run() {
            textArea.append(text);
        }
    }
    
    private class ClearRunnable implements Runnable {
        public void run() {
            textArea.setText("");
            textArea.getTextBuffer().getUndoBuffer().resetUndoBuffer();
            resetAutoScroll();
        }
    }
    
    public void append(String[] lines) {
        EventQueue.invokeLater(new AppendRunnable(lines));
    }
    
    public void clear() {
        EventQueue.invokeLater(new ClearRunnable());
    }
    
    public void resetAutoScroll() {
        // FIXME: differentiate between stdout and stderr, and pause scrolling as soon as we see anything on stderr.
        //linkFormatter.setAutoScroll(true);
    }
    
    public void initTextAreaPopupMenu() {
        textArea.getPopupMenu().addMenuItemProvider(new MenuItemProvider() {
            public void provideMenuItems(MouseEvent e, Collection<Action> actions) {
                actions.add(new OpenQuicklyAction());
                actions.add(new FindFilesContainingSelectionAction());
                actions.add(null);
                actions.add(new CheckInChangesAction());
                actions.add(null);
                actions.add(new KillErrorsAction());
            }
        });
    }
    
    /** Errors windows are never considered dirty because they're not worth preserving. */
    public boolean isDirty() {
        return false;
    }
    
    public String getContext() {
        return "";
    }
}
