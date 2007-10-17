package e.ptextarea;

import java.util.*;
import e.util.*;

/**
 * A PAbstractLanguageStyler does the main work for most of the various
 * computer language's stylers. Subclasses configure functionality implemented
 * here via overriding.
 * 
 * This class understands the shell (hash), C (slash star), and C++ (double
 * slash) comment structures. It also understands quoting (configurable to use
 * any set of single-character quote characters such as single and double quote
 * or backquote. It also understands how to find keywords in what's left over,
 * given a fixed set of keywords.
 * 
 * FIXME: Perl and Ruby have various multiline quoting mechanisms that we don't support.
 * 
 * @author Phil Norman
 */
public abstract class PAbstractLanguageStyler extends PAbstractTextStyler {
    private int lastGoodLine;
    private BitSet commentCache;
    
    public PAbstractLanguageStyler(PTextArea textArea) {
        super(textArea);
        initCommentCache();
        initTextListener();
        textArea.setTextStyler(this);
    }
    
    /**
     * Returns true if the style includes multi-line comments.
     * The multiLineCommentStart and multiLineCommentEnd methods are then used to get the actual delimiters used.
     * The defaults are for C-family multi-line comments.
     */
    protected abstract boolean supportMultiLineComments();
    
    protected String multiLineCommentStart() {
        return "/*";
    }
    
    protected String multiLineCommentEnd() {
        return "*/";
    }
    
    /**
     * Returns the text that introduces comment-to-EOL ("//" in the C family, "#" in the script family, and "--" in various "European" languages) if there's such a thing, null otherwise.
     */
    protected boolean isStartOfCommentToEndOfLine(String line, int atIndex) {
        return false;
    }
    
    /**
     * This is parameterized so that we can recognize the GNU Make keyword "filter-out", and various strange GNU Assembler directives.
     * The value of the first capturing group will be tested to ensure that it's a member of the styler's keyword set.
     */
    protected String getKeywordRegularExpression() {
        return "\\b(\\w+)\\b";
    }
    
    /**
     * Returns true iff the given character is a quote of some sort.
     * It's safe to override this and increase or reduce the number of quote characters.
     * The makefile styler removes single quotes, for example, and the scripting language stylers add backquote.
     */
    protected boolean isQuote(char ch) {
        return (ch == '\'' || ch == '\"');
    }
    
    protected static final boolean isShellComment(String line, int i) {
        // Only recognize # comments if they're at the start of the line or the preceding character was whitespace.
        // This stops us mistaking "$#" in Perl or # characters in Ruby regular expressions for comments, for example.
        return line.charAt(i) == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)));
    }
    
    /**
     * Adds a text segment of type String to the given segment list.  Override
     * this method if you wish to perform some validation on the string and introduce
     * error-style sections into it.
     */
    public void addStringSegment(TextSegmentListBuilder builder, String line, int start, int end) {
        builder.addStyledSegment(end, PStyle.STRING);
    }
    
    public void initStyleApplicators() {
        Set<String> keywords;
        if (keywordsAreCaseSensitive()) {
            keywords = new HashSet<String>();
        } else {
            keywords = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        }
        addKeywordsTo(keywords);
        if (keywords.size() > 0) {
            textArea.addStyleApplicator(new KeywordStyleApplicator(textArea, keywords, getKeywordRegularExpression()));
        }
    }
    
    private void initTextListener() {
        textArea.getTextBuffer().addTextListener(new PTextListener() {
            public void textCompletelyReplaced(PTextEvent event) {
                initCommentCache();
            }
            
            public void textInserted(PTextEvent event) {
                dirtyFromOffset(event);
            }
            
            public void textRemoved(PTextEvent event) {
                dirtyFromOffset(event);
            }
        });
    }
    
    private void initCommentCache() {
        lastGoodLine = 0;
        commentCache = new BitSet();
    }
    
    public List<PLineSegment> getTextSegments(int lineIndex) {
        String line = textArea.getLineContents(lineIndex).toString();
        return getMainSegments(lineIndex, line);
    }
    
    private List<PLineSegment> getMainSegments(int lineIndex, String line) {
        TextSegmentListBuilder builder = new TextSegmentListBuilder(textArea.getLineStartOffset(lineIndex));
        boolean comment = startsCommented(lineIndex);
        int lastStart = 0;
        for (int i = 0; i < line.length(); ) {
            if (comment) {
                int commentEndIndex = line.indexOf(multiLineCommentEnd(), i);
                if (commentEndIndex == -1) {
                    commentEndIndex = line.length();
                } else {
                    commentEndIndex += multiLineCommentEnd().length();
                }
                builder.addStyledSegment(commentEndIndex, PStyle.COMMENT);
                i = commentEndIndex;
                lastStart = commentEndIndex;
                comment = false;
            } else {
                if (isStartOfCommentToEndOfLine(line, i)) {
                    comment = true;
                    if (lastStart < i) {
                        builder.addStyledSegment(i, PStyle.NORMAL);
                    }
                    builder.addStyledSegment(line.length(), PStyle.COMMENT);
                    i = line.length();
                    lastStart = i;
                    break;
                }
                
                if (supportMultiLineComments() && line.startsWith(multiLineCommentStart(), i)) {
                    comment = true;
                    if (lastStart < i) {
                        builder.addStyledSegment(i, PStyle.NORMAL);
                    }
                    lastStart = i;
                    i += multiLineCommentStart().length();
                } else if (isQuote(line.charAt(i))) {
                    if (lastStart < i) {
                        builder.addStyledSegment(i, PStyle.NORMAL);
                    }
                    int stringEnd = i + 1;
                    String matchString = String.valueOf(line.charAt(i));
                    while (stringEnd != -1) {
                        stringEnd = line.indexOf(matchString, stringEnd);
                        if (stringEnd != -1) {
                            stringEnd++;
                            if (getBackslashBeforeCount(line, stringEnd - 1) % 2 == 0) {
                                break;  // Not escaped.
                            }
                        }
                    }
                    // If it falls out because stringEnd == -1, we have an unterminated string.
                    if (stringEnd == -1) {
                        builder.addStyledSegment(line.length(), PStyle.ERROR);
                        i = line.length();
                    } else {
                        addStringSegment(builder, line, i, stringEnd);
                        i = stringEnd;
                    }
                    lastStart = i;
                } else {
                    i++;
                }
            }
        }
        if (lastStart < line.length()) {
            builder.addStyledSegment(line.length(), comment ? PStyle.COMMENT : PStyle.NORMAL);
        }
        return builder.getSegmentList();
    }
    
    private int getBackslashBeforeCount(String string, int index) {
        int result = 0;
        for (int i = index - 1; i >= 0; i--) {
            if (string.charAt(i) == '\\') {
                result++;
            } else {
                break;
            }
        }
        return result;
    }
    
    private boolean startsCommented(int lineIndex) {
        if (lastGoodLine < lineIndex) {
            PLineList lineList = textArea.getLineList();
            for (int i = lastGoodLine; i < lineIndex; i++) {
                String line = lineList.getLine(i).getContents().toString();
                commentCache.set(i + 1, lineEndsCommented(line, commentCache.get(i)));
            }
            lastGoodLine = lineIndex;
        }
        return commentCache.get(lineIndex);
    }
    
    /**
     * Returns true if the given line will end commented. By "end commented",
     * I think this means "end in an open comment that implies that the next
     * line begins inside a comment".
     */
    private boolean lineEndsCommented(String line, boolean startsCommented) {
        boolean comment = startsCommented;
        int index = 0;
        while (true) {
            if (comment) {
                // Commented - comments eat strings.
                int endIndex = line.indexOf(multiLineCommentEnd(), index);
                if (endIndex == -1) {
                    break;
                }
                comment = false;
                index = endIndex + 2;
            } else {
                // Uncommented - strings eat comments.
                char previous = 0;
                char lastQuote = 0;
                boolean escaped = false;
                for (int i = index; i < line.length(); i++) {
                    char thisChar = line.charAt(i);
                    if (lastQuote == 0) {
                        if (escaped == false && isQuote(thisChar)) {
                            lastQuote = thisChar;
                        }
                        if (supportMultiLineComments() && line.startsWith(multiLineCommentStart(), i)) {
                            comment = true;
                            i += multiLineCommentStart().length();
                        } else if (isStartOfCommentToEndOfLine(line, i)) {
                            break;
                        }
                    } else {
                        if (escaped == false && thisChar == lastQuote) {
                            lastQuote = 0;
                        }
                    }
                    if (thisChar == '\\') {
                        escaped = !escaped;
                    } else {
                        escaped = false;
                    }
                    previous = thisChar;
                }
                if (comment == false) {
                    break;
                }
            }
        }
        return comment;
    }
    
    private void dirtyFromOffset(PTextEvent event) {
        if (textArea.isLineWrappingInvalid()) {
            return;
        }
        
        boolean startsOrEndsMultiLineComment = false;
        if (supportMultiLineComments()) {
            int HORIZON = Math.max(multiLineCommentStart().length(), multiLineCommentEnd().length());
            CharSequence entireText = textArea.getTextBuffer();
            String prefix = entireText.subSequence(Math.max(0, event.getOffset() - HORIZON), event.getOffset()).toString();
            int endIndex = event.getOffset();
            if (event.isInsert()) {
                endIndex += event.getLength();
            }
            String suffix = entireText.subSequence(endIndex, Math.min(endIndex + HORIZON, entireText.length())).toString();
            String withMiddleText = prefix + event.getCharacters() + suffix;
            String withoutMiddleText = prefix + suffix;
            startsOrEndsMultiLineComment = hasCommentMarker(withMiddleText) || hasCommentMarker(withoutMiddleText);
        }
        
        if (hasNewline(event.getCharacters()) || startsOrEndsMultiLineComment) {
            lastGoodLine = Math.min(lastGoodLine, textArea.getLineList().getLineIndex(event.getOffset()));
            textArea.repaintFromLine(textArea.getSplitLineIndex(lastGoodLine));
        }
    }
    
    private boolean hasNewline(CharSequence text) {
        return StringUtilities.contains(text, '\n');
    }
    
    private boolean hasCommentMarker(String text) {
        return text.contains(multiLineCommentStart()) || text.contains(multiLineCommentEnd());
    }
    
    protected class TextSegmentListBuilder {
        private ArrayList<PLineSegment> list = new ArrayList<PLineSegment>();
        private int lineStartOffset;
        private int start = 0;
        
        public TextSegmentListBuilder(int lineStartOffset) {
            this.lineStartOffset = lineStartOffset;
        }
        
        public void addStyledSegment(int end, PStyle style) {
            list.add(new PTextSegment(textArea, lineStartOffset + start, lineStartOffset + end, style));
            start = end;
        }
        
        public List<PLineSegment> getSegmentList() {
            return list;
        }
    }
}
