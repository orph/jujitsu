package e.ptextarea;

import e.gui.*;
import e.util.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Links to a bug database from check-in comments.
 * 
 * Examples:
 *   Sun Java bug parade: Sun 6227617. Bug id 6227617. Bug 6227617.
 *   RFCs: RFC2229.
 */
public class BugDatabaseHighlighter extends RegularExpressionStyleApplicator {
    private static final ArrayList<SiteLocalScriptEntry> siteLocalScriptEntries = new ArrayList<SiteLocalScriptEntry>();
    private static class SiteLocalScriptEntry {
        String patternToMatch;
        String linkTemplate;
    }
    static {
        // Try to run the site-local script.
        // This is too expensive and unpredictable to do every time we configure a PTextArea, especially because we'll be on the EDT.
        // The script's output format is "^<pattern-to-match>\t<link-template>$" where the pattern's groups are as described in highlightBugs.
        // For example, this uses only Bash, keeps the two parts distinct, and avoids escaping issues:
        //
        // #!/bin/bash
        // echo -nE "\b(D([1-2]\d{4}))\b" ; echo -ne "\t" ; echo -E "http://woggle/%s"
        //
        final String scriptName = "echo-local-bug-database-patterns";
        ArrayList<String> siteLocalScriptLines = new ArrayList<String>();
        String[] command = ProcessUtilities.makeShellCommandArray(scriptName);
        ProcessUtilities.backQuote(null, command, siteLocalScriptLines, new ArrayList<String>());
        for (String line : siteLocalScriptLines) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) {
                // Ignore comments.
                continue;
            }
            int tabIndex = line.indexOf('\t');
            if (tabIndex == -1) {
                Log.warn("BugDatabaseHighlighter didn't understand line \"" + line + "\"; no tab found. Skipping that line.");
                continue;
            }
            SiteLocalScriptEntry entry = new SiteLocalScriptEntry();
            entry.patternToMatch = line.substring(0, tabIndex);
            entry.linkTemplate = line.substring(tabIndex + 1);
            siteLocalScriptEntries.add(entry);
        }
    }
    
    private String urlTemplate;
    
    private BugDatabaseHighlighter(PTextArea textArea, String regularExpression, String urlTemplate) {
        super(textArea, regularExpression, PStyle.HYPERLINK);
        this.urlTemplate = urlTemplate;
    }
    
    public static void highlightBugs(PTextArea textArea) {
        // Group 1 - the text to be underlined.
        // Group 2 - the id, inserted into the template.
        
        // Sun Java bugs.
        highlightBug(textArea, "\\b(([4-6]\\d{6}))\\b", "http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=%s");
        // RFCs; not strictly bugs, but often referenced in comments.
        highlightBug(textArea, "(?i)\\b(rfc\\s*(\\d{3,4}))\\b", "http://tools.ietf.org/html/rfc%s");
        // Site-local bug database links.
        for (SiteLocalScriptEntry entry : siteLocalScriptEntries) {
            highlightBug(textArea, entry.patternToMatch, entry.linkTemplate);
        }
    }
    
    private static void highlightBug(PTextArea textArea, String regularExpression, String urlTemplate) {
        textArea.addStyleApplicator(new BugDatabaseHighlighter(textArea, regularExpression, urlTemplate));
    }
    
    @Override
    public boolean canApplyStylingTo(PStyle style) {
        // In plain text (and maybe HTML documents too), we'd like to link in NORMAL text.
        // An alternative implementation would let the applicable PStyle be passed in to the BugDatabaseHighlighter constructor.
        FileType fileType = textArea.getFileType();
        if (fileType == FileType.PLAIN_TEXT || fileType == FileType.XML) {
            return (style == PStyle.NORMAL);
        }
        
        // In source, though, we should restrict ourselves to COMMENT text.
        return (style == PStyle.COMMENT);
    }
    
    private String urlForMatcher(Matcher matcher) {
        return new Formatter().format(urlTemplate, matcher.group(2)).toString();
    }
    
    @Override
    protected void configureSegment(PTextSegment segment, Matcher matcher) {
        String url = urlForMatcher(matcher);
        segment.setLinkAction(new WebLinkAction("Bug Database Link", url));
        segment.setToolTip(url);
    }
}
