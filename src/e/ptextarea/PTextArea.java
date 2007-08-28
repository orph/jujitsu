package e.ptextarea;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import e.gui.*;
import e.util.*;

/**
 * A PTextArea is a replacement for JTextArea.
 * 
 * @author Phil Norman
 */
public class PTextArea extends JComponent implements PLineListener, Scrollable, ClipboardOwner {
    private static final int MIN_WIDTH = 50;
    private static final int MAX_CACHED_CHAR = 128;
    
    public static final int NO_MARGIN = -1;
    
    private SelectionHighlight selection;
    private boolean selectionEndIsAnchor;  // Otherwise, selection start is anchor.
    
    private PLineList lines;
    // TODO: experiment with java.util.ArrayDeque in Java 6.
    // But ArrayDeque wouldn't help bulk operations in the middle.
    private List<SplitLine> splitLines;
    
    // We cache the FontMetrics for readability rather than performance.
    private FontMetrics metrics;
    private int[] widthCache;
    
    private PHighlightManager highlights = new PHighlightManager();
    private PTextStyler textStyler = new PPlainTextStyler(this);
    private List<StyleApplicator> styleApplicators;
    private TabStyleApplicator tabStyleApplicator = new TabStyleApplicator(this);
    
    private int rightHandMarginColumn = NO_MARGIN;
    private ArrayList<PCaretListener> caretListeners = new ArrayList<PCaretListener>();
    private TreeMap<Integer, List<PLineSegment>> segmentCache = new TreeMap<Integer, List<PLineSegment>>();
    
    private int rowCount;
    private int columnCount;
    
    private boolean editable;
    private boolean wordWrap;
    
    private FileType fileType;
    private PIndenter indenter;
    private PTextAreaSpellingChecker spellingChecker;
    private EPopupMenu popupMenu;
    private PMouseHandler mouseHandler;
    
    public PTextArea() {
        this(0, 0);
    }
    
    public PTextArea(int rowCount, int columnCount) {
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.editable = true;
        this.wordWrap = false;
        this.fileType = FileType.PLAIN_TEXT;
        this.lines = new PLineList(new PTextBuffer());
        this.selection = new SelectionHighlight(this, 0, 0);
        this.indenter = new PNoOpIndenter(this);
        
        initStyleApplicators();
        lines.addLineListener(this);
        revalidateLineWrappings();
        
        setAutoscrolls(true);
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
        setFont(UIManager.getFont("TextArea.font"));
        setOpaque(true);
        setFocusTraversalKeysEnabled(false);
        
        requestFocusInWindow();
        
        initKeyBindings();
        initListeners();
        initPopupMenu();
    }
    
    private void initListeners() {
        this.mouseHandler = new PMouseHandler(this);
        addComponentListener(new Rewrapper(this));
        addCaretListener(new PMatchingBracketHighlighter(this));
        addKeyListener(new PKeyHandler(this));
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        initFocusListening();
    }
    
    /**
     * Returns the lock object for this component's underlying buffer.  This should only be used within this package.
     */
    PLock getLock() {
        return getTextBuffer().getLock();
    }
    
    private void runWithoutMovingTheVisibleArea(Runnable runnable) {
        if (selection == null || isLineWrappingInvalid()) {
            runnable.run();
        } else {
            int charToKeepInPosition = getSelectionStart();
            int yPosition = -1;
            Rectangle visible = getVisibleRect();
            yPosition = getViewCoordinates(getCoordinates(charToKeepInPosition)).y - visible.y;
            if (yPosition < 0 || yPosition > visible.height) {
                yPosition = visible.y + visible.height / 2;
                charToKeepInPosition = getSplitLine(getNearestCoordinates(new Point(0, yPosition)).getLineIndex()).getTextIndex();
                yPosition = getViewCoordinates(getCoordinates(charToKeepInPosition)).y - visible.y;
            }
            runnable.run();
            visible = getVisibleRect();
            int newYPosition = getViewCoordinates(getCoordinates(charToKeepInPosition)).y - visible.y;
            visible.y += newYPosition - yPosition;
            scrollRectToVisible(visible);
        }
    }
    
    private void initKeyBindings() {
        ComponentUtilities.initKeyBinding(this, PActionFactory.makeCopyAction());
        ComponentUtilities.initKeyBinding(this, PActionFactory.makeCutAction());
        ComponentUtilities.initKeyBinding(this, PActionFactory.makeFindAction());
        ComponentUtilities.initKeyBinding(this, PActionFactory.makeFindNextAction());
        ComponentUtilities.initKeyBinding(this, PActionFactory.makeFindPreviousAction());
        ComponentUtilities.initKeyBinding(this, PActionFactory.makePasteAction());
        ComponentUtilities.initKeyBinding(this, PActionFactory.makeRedoAction());
        ComponentUtilities.initKeyBinding(this, PActionFactory.makeSelectAllAction());
        ComponentUtilities.initKeyBinding(this, PActionFactory.makeUndoAction());
    }
    
    public void addCaretListener(PCaretListener caretListener) {
        caretListeners.add(caretListener);
    }
    
    public void removeCaretListener(PCaretListener caretListener) {
        caretListeners.remove(caretListener);
    }
    
    private void fireCaretChangedEvent() {
        for (PCaretListener caretListener : caretListeners) {
            caretListener.caretMoved(this, getSelectionStart(), getSelectionEnd());
        }
    }
    
    public PTextStyler getTextStyler() {
        return textStyler;
    }
    
    private void initStyleApplicators() {
        styleApplicators = new ArrayList<StyleApplicator>();
        addStyleApplicator(new UnprintableCharacterStyleApplicator(this));
        addStyleApplicator(new HyperlinkStyleApplicator(this));
        if (textStyler instanceof PAbstractLanguageStyler) {
            ((PAbstractLanguageStyler) textStyler).initStyleApplicators();
        }
    }
    
    public void addStyleApplicator(StyleApplicator styleApplicator) {
        styleApplicators.add(styleApplicator);
    }
    
    public void addStyleApplicatorFirst(StyleApplicator styleApplicator) {
        styleApplicators.add(0, styleApplicator);
    }
    
    // Selection methods.
    public String getSelectedText() {
        getLock().getReadLock();
        try {
            int start = selection.getStartIndex();
            int end = selection.getEndIndex();
            if (start == end) {
                return "";
            } else {
                return getTextBuffer().subSequence(start, end).toString();
            }
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    PHighlightManager getHighlightManager() {
        return highlights;
    }
    
    SelectionHighlight getSelection() {
        return selection;
    }
    
    public int getSelectionStart() {
        return selection.getStartIndex();
    }
    
    public int getSelectionEnd() {
        return selection.getEndIndex();
    }
    
    public void setCaretPosition(int offset) {
        select(offset, offset);
    }
    
    public int getUnanchoredSelectionExtreme() {
        return selectionEndIsAnchor ? getSelectionStart() : getSelectionEnd();
    }
    
    public void changeUnanchoredSelectionExtreme(int newPosition) {
        int anchorPosition = selectionEndIsAnchor ? getSelectionEnd() : getSelectionStart();
        int minPosition = Math.min(newPosition, anchorPosition);
        int maxPosition = Math.max(newPosition, anchorPosition);
        boolean endIsAnchor = (maxPosition == anchorPosition);
        setSelection(minPosition, maxPosition, endIsAnchor);
    }
    
    public void select(int start, int end) {
        setSelection(start, end, false);
    }
    
    public void setSelection(int start, int end, boolean selectionEndIsAnchor) {
        getLock().getWriteLock();
        try {
            setSelectionWithoutScrolling(start, end, selectionEndIsAnchor);
            ensureVisibilityOfOffset(getUnanchoredSelectionExtreme());
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    public void setSelectionWithoutScrolling(int start, int end, boolean selectionEndIsAnchor) {
        getLock().getWriteLock();
        try {
            this.selectionEndIsAnchor = selectionEndIsAnchor;
            SelectionHighlight oldSelection = selection;
            selection = new SelectionHighlight(this, start, end);
            updateSystemSelection();
            oldSelection.detachAnchors();
            if (oldSelection.isEmpty() != selection.isEmpty()) {
                repaintHighlight(selection.isEmpty() ? oldSelection : selection);
                repaintCaret(selection.isEmpty() ? selection : oldSelection);
            } else if (oldSelection.isEmpty() == false && selection.isEmpty() == false) {
                int minStart = Math.min(oldSelection.getStartIndex(), selection.getStartIndex());
                int maxStart = Math.max(oldSelection.getStartIndex(), selection.getStartIndex());
                if (minStart != maxStart) {
                    repaintLines(getCoordinates(minStart).getLineIndex(), getCoordinates(maxStart).getLineIndex() + 1);
                }
                int minEnd = Math.min(oldSelection.getEndIndex(), selection.getEndIndex());
                int maxEnd = Math.max(oldSelection.getEndIndex(), selection.getEndIndex());
                if (minEnd != maxEnd) {
                    repaintLines(getCoordinates(minEnd).getLineIndex() - 1, getCoordinates(maxEnd).getLineIndex());
                }
            } else {
                repaintCaret(oldSelection);
                repaintCaret(selection);
            }
            fireCaretChangedEvent();
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    /**
     * Copies the selected text to X11's selection.
     * Does nothing on other platforms.
     */
    private void updateSystemSelection() {
        if (hasSelection() == false) {
            // Almost all X11 applications leave the selection alone in this case.
            return;
        }
        Clipboard systemSelection = getToolkit().getSystemSelection();
        if (systemSelection != null) {
            systemSelection.setContents(new LazyStringSelection() {
                public String reallyGetText() {
                    return getSelectedText();
                }
            }, this);
        }
    }
    
    private void copyToClipboard(Clipboard clipboard) {
        String newContents = getSelectedText();
        if (newContents.length() == 0) {
            return;
        }
        StringSelection selection = new StringSelection(newContents);
        clipboard.setContents(selection, this);
    }
    
    /**
     * Invoked to notify us that we no longer own the clipboard.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        // Firefox doesn't clear its selection when someone else claims the
        // X11 selection and I imagine it could be quite annoying in Evergreen.
        // So deliberately do nothing.
        
        // If we already have the read lock and ask for the write lock in
        // setSelectionWithoutScrolling, that would cause deadlock if
        // another thread has the read lock and is waiting for this thread.
    }
    
    /**
     * Avoids NullPointerExceptions when we try to use the splitLines before they're available.
     * I think the only way we can fix this properly is to remember what we've been asked to do, and do it as soon as we're able.
     * Hence the name.
     * 
     * FIXME: lose this and do the right thing instead..
     */
    private boolean weAreTooBrokenToWaitUntilWeAreAbleToCarryThisOut() {
        return (isLineWrappingInvalid() || isShowing() == false);
    }
    
    public void centerOffsetInDisplay(int offset) {
        if (weAreTooBrokenToWaitUntilWeAreAbleToCarryThisOut()) {
            return;
        }
        
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
        if (viewport == null) {
            return;
        }
        
        Point point = getViewCoordinates(getCoordinates(offset));
        final int height = viewport.getExtentSize().height;
        int y = point.y - height/2;
        y = Math.max(0, y);
        y = Math.min(y, getHeight() - height);
        viewport.setViewPosition(new Point(0, y));
    }
    
    public void ensureVisibilityOfOffset(int offset) {
        if (weAreTooBrokenToWaitUntilWeAreAbleToCarryThisOut()) {
            return;
        }
        
        Point point = getViewCoordinates(getCoordinates(offset));
        scrollRectToVisible(new Rectangle(point.x - 1, point.y - metrics.getMaxAscent(), 3, metrics.getHeight()));
    }
    
    /**
     * Returns the indenter responsible for auto-indent (and other aspects of
     * indentation correction) in this text area.
     */
    public PIndenter getIndenter() {
        return indenter;
    }
    
    /**
     * Sets the indenter responsible for this text area. Typically useful when
     * you know more about the language of the content than PTextArea does.
     */
    public void setIndenter(PIndenter newIndenter) {
        this.indenter = newIndenter;
    }
    
    /**
     * Returns the text of the given line (without the newline).
     */
    public String getLineText(int lineNumber) {
        getLock().getReadLock();
        try {
            int start = getLineStartOffset(lineNumber);
            int end = getLineEndOffsetBeforeTerminator(lineNumber);
            return (start == end) ? "" : getTextBuffer().subSequence(start, end).toString();
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    /**
     * Returns the string to use as a single indent level in this text area.
     */
    public String getIndentationString() {
        String result = (String) getTextBuffer().getProperty(PTextBuffer.INDENTATION_PROPERTY);
        if (result == null) {
            result = "\t";
        }
        return result;
    }
    
    public void insert(CharSequence chars) {
        getLock().getWriteLock();
        try {
            SelectionSetter endCaret = new SelectionSetter(getSelectionStart() + chars.length());
            int length = getSelectionEnd() - getSelectionStart();
            getTextBuffer().replace(new SelectionSetter(), getSelectionStart(), length, chars, endCaret);
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    public void replaceRange(CharSequence replacement, int start, int end) {
        getLock().getWriteLock();
        try {
            SelectionSetter endCaret = new SelectionSetter(start + replacement.length());
            getTextBuffer().replace(new SelectionSetter(), start, end - start, replacement, endCaret);
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    public void replaceSelection(CharSequence replacement) {
        getLock().getWriteLock();
        try {
            if (hasSelection()) {
                replaceRange(replacement, getSelectionStart(), getSelectionEnd());
            } else {
                insert(replacement);
            }
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    public void delete(int startFrom, int charCount) {
        getLock().getWriteLock();
        try {
            SelectionSetter endCaret = new SelectionSetter(startFrom);
            getTextBuffer().replace(new SelectionSetter(), startFrom, charCount, "", endCaret);
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    private class SelectionSetter implements PTextBuffer.SelectionSetter {
        private static final int DO_NOT_CHANGE = -1;
        
        private int start;
        private int end;
        
        public SelectionSetter() {
            this(getSelectionStart(), getSelectionEnd());
        }
        
        public SelectionSetter(int offset) {
            this(offset, offset);
        }
        
        public SelectionSetter(int start, int end) {
            this.start = start;
            this.end = end;
        }
        
        public void modifySelection() {
            if (start != DO_NOT_CHANGE && end != DO_NOT_CHANGE) {
                select(start, end);
            }
        }
    }
    
    public boolean hasSelection() {
        return (getSelectionStart() != getSelectionEnd());
    }
    
    public void selectAll() {
        getLock().getReadLock();
        try {
            select(0, getTextBuffer().length());
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    /**
     * Repaints us when we gain/lose focus, so we can re-color the selection,
     * like a native text component.
     */
    private void initFocusListening() {
        addFocusListener(new FocusListener() {
            private boolean firstFocusGain = true;

            public void focusGained(FocusEvent e) {
                repaint();
                if (firstFocusGain) {
                    initSpellingChecking();
                    firstFocusGain = false;
                }
            }
            
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }
    
    private void initSpellingChecking() {
        spellingChecker = new PTextAreaSpellingChecker(this);
        spellingChecker.checkSpelling();
    }
    
    public PTextAreaSpellingChecker getSpellingChecker() {
        return spellingChecker;
    }
    
    // Utility methods.
    public int getLineCount() {
        return lines.size();
    }
    
    /**
     * Returns a CharSequence providing access to all the characters in the given line up to but not
     * including the line terminator.
     */
    public CharSequence getLineContents(int line) {
        getLock().getReadLock();
        try {
            return lines.getLine(line).getContents();
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    public int getLineStartOffset(int line) {
        getLock().getReadLock();
        try {
            return lines.getLine(line).getStart();
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    /**
     * Returns the offset at the end of the given line, but before the
     * newline. This differs from JTextArea's getLineEndOffset, where the
     * line end offset is taken to include the newline.
     */
    public int getLineEndOffsetBeforeTerminator(int line) {
        getLock().getReadLock();
        try {
            return lines.getLine(line).getEndOffsetBeforeTerminator();
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    public int getLineOfOffset(int offset) {
        getLock().getReadLock();
        try {
            return lines.getLineIndex(offset);
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    public void setWrapStyleWord(boolean newWordWrapState) {
        if (wordWrap != newWordWrapState) {
            wordWrap = newWordWrapState;
            revalidateLineWrappings();
        }
    }
    
    /**
     * Sets the column number at which to draw the margin. Typically, this is
     * 80 when using a fixed-width font. Use the constant NO_MARGIN to suppress
     * the drawing of any margin.
     */
    public void showRightHandMarginAt(int rightHandMarginColumn) {
        this.rightHandMarginColumn = rightHandMarginColumn;
    }
    
    /**
     * Returns the column number at which the margin is drawn.
     * Test against the constant NO_MARGIN to see if no margin is being drawn.
     */
    public int getRightHandMarginColumn() {
        return rightHandMarginColumn;
    }
    
    public void setTextStyler(PTextStyler textStyler) {
        this.textStyler = textStyler;
        initStyleApplicators();
        clearSegmentCache();
        repaint();
    }
    
    public void setFont(final Font font) {
        runWithoutMovingTheVisibleArea(new Runnable() {
            public void run() {
                PTextArea.super.setFont(font);
                getLock().getWriteLock();
                try {
                    cacheFontMetrics();
                    boolean isFixedFont = GuiUtilities.isFontFixedWidth(font);
                    showRightHandMarginAt(isFixedFont ? 80 : NO_MARGIN);
                    //FIXME: setTabSize(isFixedFont ? 8 : 2);
                    lines.invalidateWidths();
                    revalidateLineWrappings();
                } finally {
                    getLock().relinquishWriteLock();
                }
            }
        });
        repaint();
    }
    
    private void cacheFontMetrics() {
        metrics = getFontMetrics(getFont());
        widthCache = new int[MAX_CACHED_CHAR];
        for (int i = 0; i < MAX_CACHED_CHAR; i++) {
            widthCache[i] = metrics.charWidth(i);
        }
    }
    
    public void addHighlight(PHighlight highlight) {
        getLock().getWriteLock();
        try {
            highlights.add(highlight);
            repaintHighlight(highlight);
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    public List<PHighlight> getNamedHighlights(String highlighterName) {
        return getNamedHighlightsOverlapping(highlighterName, 0, getTextBuffer().length() + 1);
    }
    
    /**
     * Returns all highlights matching highlighterName overlapping the range [beginOffset, endOffset).
     */
    public List<PHighlight> getNamedHighlightsOverlapping(String highlighterName, int beginOffset, int endOffset) {
        getLock().getReadLock();
        try {
            return highlights.getNamedHighlightsOverlapping(highlighterName, beginOffset, endOffset);
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    /**
     * Selects the given highlight.
     */
    public void selectHighlight(PHighlight highlight) {
        getLock().getReadLock();
        try {
            centerOffsetInDisplay(highlight.getStartIndex());
            select(highlight.getStartIndex(), highlight.getEndIndex());
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    public void removeHighlights(String highlightManager) {
        removeHighlights(highlightManager, 0, getTextBuffer().length() + 1);
    }
    
    /**
     * Removes highlights matching "highlighterName" in the range [beginOffset, endOffset).
     */
    public void removeHighlights(String highlighterName, int beginOffset, int endOffset) {
        getLock().getWriteLock();
        try {
            List<PHighlight> removeList = highlights.getNamedHighlightsOverlapping(highlighterName, beginOffset, endOffset);
            IdentityHashMap<PAnchor, Object> deadAnchors = new IdentityHashMap<PAnchor, Object>();
            for (PHighlight highlight : removeList) {
                highlight.collectAnchors(deadAnchors);
                highlights.remove(highlight);
            }
            getTextBuffer().getAnchorSet().removeAll(deadAnchors);
            if (removeList.size() == 1) {
                repaintHighlight(removeList.get(0));
            } else if (removeList.size() > 1) {
                repaint();
            }
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    public void removeHighlight(PHighlight highlight) {
        getLock().getWriteLock();
        try {
            highlights.remove(highlight);
            highlight.detachAnchors();
            repaintHighlight(highlight);
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    public PLineList getLineList() {
        return lines;
    }
    
    public PTextBuffer getTextBuffer() {
        return lines.getTextBuffer();
    }
    
    public PCoordinates getNearestCoordinates(Point point) {
        getLock().getReadLock();
        try {
            Insets insets = getInsets();
            if (point.y < insets.top) {
                return new PCoordinates(0, 0);
            }
            generateLineWrappings();
            final int lineIndex = getLineIndexAtLocation(point);
            int charOffset = 0;
            int x = insets.left;
            for (PLineSegment segment : getLineSegmentsForSplitLine(lineIndex)) {
                int width = segment.getDisplayWidth(metrics, x);
                if (x + width > point.x) {
                    charOffset += segment.getCharOffset(metrics, x, point.x);
                    return new PCoordinates(lineIndex, charOffset);
                }
                charOffset += segment.getModelTextLength();
                x += width;
            }
            return new PCoordinates(lineIndex, charOffset);
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    /**
     * Returns the line index corresponding to the given point. To properly
     * cope with clicks past the end of the text, this method may update point
     * to have a huge x-coordinate. The line index will be that of the last
     * line in the document, because that's how other text components behave;
     * it's just that all clicks after the end of the text should correspond
     * to the last character in the document, and a huge x-coordinate is the
     * easiest way to ensure that code that goes on to work out which
     * character we're pointing to on the returned line will behave correctly.
     */
    private int getLineIndexAtLocation(Point point) {
        final int maxLineIndex = splitLines.size() - 1;
        int lineIndex = (point.y - getInsets().top) / metrics.getHeight();
        if (lineIndex > maxLineIndex) {
            point.x = Integer.MAX_VALUE;
        }
        lineIndex = Math.max(0, Math.min(maxLineIndex, lineIndex));
        return lineIndex;
    }
    
    public PLineSegment getLineSegmentAtLocation(Point point) {
        getLock().getReadLock();
        try {
            generateLineWrappings();
            int x = getInsets().left;
            for (PLineSegment segment : getLineSegmentsForSplitLine(getLineIndexAtLocation(point))) {
                int width = segment.getDisplayWidth(metrics, x);
                if (x + width > point.x) {
                    return segment;
                }
                x += width;
            }
            return null;
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    public Iterator<PLineSegment> getLogicalSegmentIterator(int offset) {
        return new PLogicalSegmentIterator(this, offset);
    }
    
    public Iterator<PLineSegment> getWrappedSegmentIterator(int offset) {
        return new PWrappedSegmentIterator(this, offset);
    }
    
    private List<PLineSegment> getLineSegmentsForSplitLine(int splitLineIndex) {
        return getLineSegmentsForSplitLine(getSplitLine(splitLineIndex));
    }
    
    /**
     * Returns a series of segments of text describing how to render each part of the
     * specified line.
     * FIXME - delete once all this is sorted out properly.
     * FIXME - this is moved straight out of PAbstractTextStyler.  It needs major work.
     * 
     * FIXME: when should you call getLineSegments, and when should you call getLineSegmentsForSplitLine?
     */
    private final List<PLineSegment> getLineSegmentsForSplitLine(SplitLine splitLine) {
        int lineIndex = splitLine.getLineIndex();
        String fullLine = getLineList().getLine(lineIndex).getContents().toString();
        List<PLineSegment> segments = getLineSegments(lineIndex);
        int index = 0;
        ArrayList<PLineSegment> result = new ArrayList<PLineSegment>();
        int start = splitLine.getOffset();
        int end = start + splitLine.getLength();
        
        for (int i = 0; index < end && i < segments.size(); ++i) {
            PLineSegment segment = segments.get(i);
            if (start >= index + segment.getModelTextLength()) {
                index += segment.getModelTextLength();
                continue;
            }
            if (start > index) {
                int skip = start - index;
                segment = segment.subSegment(skip);
                index += skip;
            }
            if (end < index + segment.getModelTextLength()) {
                segment = segment.subSegment(0, end - index);
            }
            result.add(segment);
            index += segment.getModelTextLength();
        }
        return result;
    }
    
    // FIXME: when should you call getLineSegments, and when should you call getLineSegmentsForSplitLine?
    public List<PLineSegment> getLineSegments(int lineIndex) {
        getLock().getReadLock();
        try {
            // Return it straight away if we've already cached it.
            synchronized (segmentCache) {
                if (segmentCache.containsKey(lineIndex)) {
                    return segmentCache.get(lineIndex);
                }
            }
            
            // Let the styler have the first go.
            List<PLineSegment> segments = textStyler.getTextSegments(lineIndex);
            
            // Then let the style applicators add their finishing touches.
            String line = getLineContents(lineIndex).toString();
            for (StyleApplicator styleApplicator : styleApplicators) {
                segments = applyStyleApplicator(styleApplicator, line, segments);
            }
            
            // Finally, deal with tabs.
            segments = applyStyleApplicator(tabStyleApplicator, line, segments);
            synchronized (segmentCache) {
                segmentCache.put(lineIndex, segments);
            }
            return segments;
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    private void clearSegmentCacheFrom(int lineIndex) {
        synchronized (segmentCache) {
            // Take a copy of the indices.  Otherwise, if we try removing stuff from the segment cache
            // based on values taken from the sub-tree from the line index onwards, we incur a
            // concurrent modification exception.
            // FIXME: http://java.sun.com/j2se/1.5.0/docs/api/java/util/HashMap.html#keySet() implies that we can use Iterator.remove to avoid this copy.
            Set<Integer> keySet = segmentCache.tailMap(lineIndex).keySet();
            Integer[] indices = keySet.toArray(new Integer[keySet.size()]);
            for (int index : indices) {
                segmentCache.remove(index);
            }
        }
    }
    
    private void clearSegmentCache() {
        synchronized (segmentCache) {
            segmentCache.clear();
        }
    }
    
    private List<PLineSegment> applyStyleApplicator(StyleApplicator styleApplicator, String line, List<PLineSegment> inputSegments) {
        List<PLineSegment> result = new ArrayList<PLineSegment>();
        for (PLineSegment segment : inputSegments) {
            if (styleApplicator.canApplyStylingTo(segment.getStyle())) {
                result.addAll(styleApplicator.applyStylingTo(line, segment));
            } else {
                result.add(segment);
            }
        }
        return result;
    }
    
    private void addTabbedSegments(PLineSegment segment, ArrayList<PLineSegment> target) {
        while (true) {
            String text = segment.getViewText();
            int tabIndex = text.indexOf('\t');
            if (tabIndex == -1) {
                target.add(segment);
                return;
            }
            target.add(segment.subSegment(0, tabIndex));
            int tabEnd = tabIndex;
            while (tabEnd < text.length() && text.charAt(tabEnd) == '\t') {
                tabEnd++;
            }
            int offset = segment.getOffset();
            target.add(new PTabSegment(this, offset + tabIndex, offset + tabEnd));
            segment = segment.subSegment(tabEnd);
            if (segment.getModelTextLength() == 0) {
                return;
            }
        }
    }
    
    public PCoordinates getCoordinates(int location) {
        getLock().getReadLock();
        try {
            if (isLineWrappingInvalid()) {
                return new PCoordinates(-1, -1);
            }
            int min = 0;
            int max = splitLines.size();
            while (max - min > 1) {
                int mid = (min + max) / 2;
                SplitLine line = getSplitLine(mid);
                if (line.containsIndex(location)) {
                    return new PCoordinates(mid, location - line.getTextIndex());
                } else if (location < line.getTextIndex()) {
                    max = mid;
                } else {
                    min = mid;
                }
            }
            return new PCoordinates(min, location - getSplitLine(min).getTextIndex());
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    public Point getViewCoordinates(PCoordinates coordinates) {
        getLock().getReadLock();
        try {
            int baseline = getBaseline(coordinates.getLineIndex());
            int x = getInsets().left;
            int charOffset = 0;
            for (PLineSegment segment : getLineSegmentsForSplitLine(coordinates.getLineIndex())) {
                if (coordinates.getCharOffset() <= charOffset + segment.getModelTextLength()) {
                    x += segment.getDisplayWidth(metrics, x, coordinates.getCharOffset() - charOffset);
                    return new Point(x, baseline);
                }
                charOffset += segment.getModelTextLength();
                x += segment.getDisplayWidth(metrics, x);
            }
            return new Point(x, baseline);
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    public int getTextIndex(PCoordinates coordinates) {
        return getSplitLine(coordinates.getLineIndex()).getTextIndex() + coordinates.getCharOffset();
    }
    
    private void repaintHighlight(PHighlight highlight) {
        repaintIndexRange(highlight.getStartIndex(), highlight.getEndIndex());
    }
    
    private void repaintIndexRange(int startIndex, int endIndex) {
        if (isLineWrappingInvalid()) {
            return;
        }
        PCoordinates start = getCoordinates(startIndex);
        PCoordinates end = getCoordinates(endIndex);
        repaintLines(start.getLineIndex(), end.getLineIndex());
    }
    
    private void repaintCaret(SelectionHighlight caret) {
        if (isLineWrappingInvalid()) {
            return;
        }

        // FIXME: Only repaint the damaged area.
        repaintIndexRange(caret.getStartIndex(), caret.getEndIndex());

        /*
        Point point = getViewCoordinates(getCoordinates(caret.getStartIndex()));
        repaint(point.x - 1, point.y - metrics.getMaxAscent(), 3, metrics.getMaxAscent() + metrics.getMaxDescent());
        */
    }
    
    public int getLineTop(int lineIndex) {
        return lineIndex * metrics.getHeight() + getInsets().top;
    }
    
    public int getLineHeight() {
        return metrics.getHeight();
    }
    
    public int getBaseline(int lineIndex) {
        return lineIndex * metrics.getHeight() + metrics.getMaxAscent() + getInsets().top;
    }
    
    public void paintComponent(Graphics oldGraphics) {
        getLock().getReadLock();
        try {
            generateLineWrappings();
            
            PTextAreaRenderer renderer = new PTextAreaRenderer(this, (Graphics2D) oldGraphics, metrics);
            renderer.render();
        } finally {
            getLock().relinquishReadLock();
        }
    }

    public void linesAdded(PLineEvent event) {
        if (isLineWrappingInvalid()) {
            return;
        }
        int lineIndex = event.getLineIndex();
        clearSegmentCacheFrom(lineIndex);
        int splitIndex = getSplitLineIndex(lineIndex);
        int firstSplitIndex = splitIndex;
        changeLineIndices(lineIndex, event.getLength());
        for (int i = 0; i < event.getLength(); i++) {
            splitIndex += addSplitLines(lineIndex++, splitIndex);
        }
        updateHeight();
        repaintFromLine(firstSplitIndex);
    }
    
    public void linesRemoved(PLineEvent event) {
        if (isLineWrappingInvalid()) {
            return;
        }
        clearSegmentCacheFrom(event.getLineIndex());
        int beginSplitIndex = getSplitLineIndex(event.getLineIndex());
        int endSplitIndex = getSplitLineIndex(event.getLineIndex() + event.getLength());
        removeSplitLines(beginSplitIndex, endSplitIndex);
        changeLineIndices(event.getLineIndex() + event.getLength(), -event.getLength());
        updateHeight();
        repaintFromLine(beginSplitIndex);
    }
    
    public void linesCompletelyReplaced(PLineEvent event) {
        clearSegmentCache();
        revalidateLineWrappings();
    }
    
    private void changeLineIndices(int lineIndex, int change) {
        for (int i = getSplitLineIndex(lineIndex); i < splitLines.size(); i++) {
            SplitLine line = getSplitLine(i);
            line.setLineIndex(line.getLineIndex() + change);
        }
    }
    
    public void linesChanged(PLineEvent event) {
        clearSegmentCacheFrom(event.getLineIndex());
        if (isLineWrappingInvalid()) {
            return;
        }
        int lineCountChange = 0;
        int minLine = Integer.MAX_VALUE;
        int visibleLineCount = 0;
        for (int i = 0; i < event.getLength(); i++) {
            PLineList.Line line = lines.getLine(event.getLineIndex() + i);
            setLineWidth(line);
            int beginSplitIndex = getSplitLineIndex(event.getLineIndex()); // FIXME: shouldn't this be "+ i" too?
            int endSplitIndex = getSplitLineIndex(event.getLineIndex() + 1);
            if (i == 0) {
                minLine = beginSplitIndex;
            }
            int removedCount = removeSplitLines(beginSplitIndex, endSplitIndex);
            lineCountChange -= removedCount;
            int addedCount = addSplitLines(event.getLineIndex(), beginSplitIndex);
            lineCountChange += addedCount;
            visibleLineCount += addedCount;
        }
        if (lineCountChange != 0) {
            updateHeight();
            repaintFromLine(getSplitLineIndex(event.getLineIndex()));
        } else {
            repaintLines(minLine, minLine + visibleLineCount);
        }
    }
    
    public void repaintFromLine(int splitIndex) {
        int lineTop = getLineTop(splitIndex);
        Dimension size = getSize();
        repaint(0, lineTop, size.width, size.height - lineTop);
    }
    
    public void repaintLines(int minSplitIndex, int maxSplitIndex) {
        int top = getLineTop(minSplitIndex);
        int bottom = getLineTop(maxSplitIndex + 1);
        repaint(0, top, getWidth(), bottom - top);
    }
    
    public boolean isLineWrappingInvalid() {
        return (splitLines == null);
    }
    
    private void revalidateLineWrappings() {
        getLock().getWriteLock();
        try {
            splitLines = null;
            generateLineWrappings();
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    /** Only for use by class Rewrapper. */
    void rewrap() {
        if (isShowing()) {
            runWithoutMovingTheVisibleArea(new Runnable() {
                public void run() {
                    revalidateLineWrappings();
                }
            });
        }
    }
    
    /**
     * Calls generateLineWrappings so we're ready to use as soon as we're
     * added to a parent, rather than having to wait until we're first
     * painted. This means that we can be created on the Event Dispatch Thread
     * and manipulated immediately (centerOffsetInDisplay being a common use
     * case).
     */
    public void addNotify() {
        super.addNotify();
        generateLineWrappings();
    }
    
    private void generateLineWrappings() {
        getLock().getWriteLock();
        try {
            if (isLineWrappingInvalid() && isShowing()) {
                splitLines = new ArrayList<SplitLine>();
                for (int i = 0; i < lines.size(); i++) {
                    PLineList.Line line = lines.getLine(i);
                    if (line.isWidthValid() == false) {
                        setLineWidth(line);
                    }
                    addSplitLines(i, splitLines.size());
                }
                updateHeight();
            }
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    private void updateHeight() {
        Dimension size = getSize();
        Insets insets = getInsets();
        size.height = metrics.getHeight() * splitLines.size() + insets.top + insets.bottom;
        setSize(size);
        setPreferredSize(size);
    }
    
    private int removeSplitLines(int beginSplitIndex, int endSplitIndex) {
        splitLines.subList(beginSplitIndex, endSplitIndex).clear();
        return endSplitIndex - beginSplitIndex;
    }
    
    public int getSplitLineIndex(int lineIndex) {
        getLock().getReadLock();
        try {
            if (lineIndex > getSplitLine(splitLines.size() - 1).getLineIndex()) {
                return splitLines.size();
            }
            int min = 0;
            int max = splitLines.size();
            while (max - min > 1) {
                int mid = (min + max) / 2;
                int midIndex = getSplitLine(mid).getLineIndex();
                if (midIndex == lineIndex) {
                    return backtrackToLineStart(mid);
                } else if (midIndex > lineIndex) {
                    max = mid;
                } else {
                    min = mid;
                }
            }
            return backtrackToLineStart(min);
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    private int backtrackToLineStart(int splitIndex) {
        while (getSplitLine(splitIndex).getOffset() > 0) {
            splitIndex--;
        }
        return splitIndex;
    }
    
    public void logLineInfo() {
        Log.warn("Dumping PTextArea SplitLine info:");
        for (int i = 0; i < splitLines.size(); i++) {
            SplitLine line = getSplitLine(i);
            Log.warn("SplitLine " + i + ": line " + line.getLineIndex() + ", offset " + line.getOffset() + ", length " + line.getLength());
        }
    }
    
    public int getSplitLineCount() {
        return (splitLines != null) ? splitLines.size() : getLineCount();
    }
    
    public SplitLine getSplitLineOfOffset(int offset) {
        return getSplitLine(getCoordinates(offset).getLineIndex());
    }
    
    public SplitLine getSplitLine(int index) {
        return splitLines.get(index);
    }
    
    private int addSplitLines(int lineIndex, int index) {
        PLineList.Line line = lines.getLine(lineIndex);
        final int initialSplitLineCount = splitLines.size();
        if (line.isWidthValid() == false) {
            setLineWidth(line);
        }
        Insets insets = getInsets();
        int width = getWidth() - insets.left - insets.right;
        if (width <= 0) {
            width = Integer.MAX_VALUE;  // Don't wrap if we don't have any size.
        }
        width = Math.max(width, MIN_WIDTH);  // Ensure we're at least a sensible width.
        if (line.getWidth() <= width) {
            // The whole line fits.
            splitLines.add(index, new SplitLine(this, lineIndex, 0, line.getContents().length()));
        } else {
            // The line's too long, so break it into SplitLines.
            int x = 0;
            CharSequence chars = line.getContents();
            int lastSplitOffset = 0;
            for (int i = 0; i < chars.length(); i++) {
                char ch = chars.charAt(i);
                x = addCharWidth(x, ch);
                if (x >= width - getMinimumWrapMarkWidth()) {
                    if (wordWrap) {
                        // Try to find a break before the last break.
                        for (int splitOffset = i; splitOffset >= lastSplitOffset; --splitOffset) {
                            if (chars.charAt(splitOffset) == ' ' && splitOffset < chars.length() - 1) {
                                // Break so that the word goes to the next line
                                // but the inter-word character stays where it
                                // was.
                                i = splitOffset + 1;
                                ch = chars.charAt(i);
                                break;
                            }
                        }
                    }
                    splitLines.add(index++, new SplitLine(this, lineIndex, lastSplitOffset, i - lastSplitOffset));
                    lastSplitOffset = i;
                    x = addCharWidth(0, ch);
                }
            }
            if (x > 0) {
                splitLines.add(index++, new SplitLine(this, lineIndex, lastSplitOffset, chars.length() - lastSplitOffset));
            }
        }
        return (splitLines.size() - initialSplitLineCount);
    }
    
    /**
     * Returns the amount of space that must remain to the right of a character
     * for that character not to cause a line wrap. We use this to ensure that
     * there's at least this much space for the wrap mark.
     */
    private int getMinimumWrapMarkWidth() {
        return widthCache['W'];
    }
    
    private int addCharWidth(int x, char ch) {
        // FIXME: this is a hack, and doesn't generalize to arbitrary PTextSegments for which getViewText and getCharSequence (that is, the model text) return different strings. I tried to rewrite the wrapping code to use getLineSegments. setLineWidth is easy, but addSplitLines is pretty difficult because you need to keep track of the two strings and the correspondence between offsets in them, or rewrite it completely to work on the text segments itself. This code has been known broken since at least 2005-06, so another special case is better than nothing.
        if (ch == '\t') {
            return x + PTabSegment.SINGLE_TAB.getDisplayWidth(metrics, x);
        } else if (ch < ' ' || ch == '\u007f') {
            // FIXME: we could cache these, since there are so few.
            StringBuilder chars = new StringBuilder(6);
            StringUtilities.appendUnicodeEscape(chars, ch);
            return x + metrics.stringWidth(chars.toString());
        } else if (ch < MAX_CACHED_CHAR) {
            return x + widthCache[(int) ch];
        } else {
            return x + metrics.charWidth(ch);
        }
    }
    
    private void setLineWidth(PLineList.Line line) {
        CharSequence chars = line.getContents();
        int width = 0;
        for (int i = 0; i < chars.length(); ++i) {
            width = addCharWidth(width, chars.charAt(i));
        }
        line.setWidth(width);
    }
    
    /**
     * Replaces the entire contents of this text area with the given CharSequence.
     */
    public void setText(CharSequence newText) {
        getTextBuffer().replace(new SelectionSetter(), 0, getTextBuffer().length(), newText, new SelectionSetter(0));
    }
    
    /**
     * Appends the given string to the end of the text. This is meant for
     * programmatic use, and so does not pay attention to or modify the
     * selection.
     */
    public void append(String newText) {
        getLock().getWriteLock();
        try {
            SelectionSetter noChange = new SelectionSetter(SelectionSetter.DO_NOT_CHANGE);
            PTextBuffer buffer = getTextBuffer();
            buffer.replace(noChange, buffer.length(), 0, newText, noChange);
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    /**
     * Returns a copy of the text in this text area.
     */
    public String getText() {
        getLock().getReadLock();
        try {
            return getTextBuffer().toString();
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }
    
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return visible.height;  // We should never be asked for orientation=H.
    }
    
    public boolean getScrollableTracksViewportHeight() {
        // If our parent is larger than we are, expand to fill the space.
        return getParent().getHeight() > getPreferredSize().height;
    }
    
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }
    
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return metrics.getHeight();
    }
    
    public Dimension getPreferredSize() {
        Dimension result = super.getPreferredSize();
        Insets insets = getInsets();
        if (columnCount != 0) {
            result.width = Math.max(result.width, columnCount * metrics.charWidth('m') + insets.left + insets.right);
        }
        if (rowCount != 0) {
            result.height = Math.max(result.height, rowCount * getLineHeight() + insets.top + insets.bottom);
        }
        return result;
    }
    
    /**
     * Override this to modify pasted text before it replaces the selection.
     */
    protected String reformatPastedText(String pastedText) {
        return pastedText;
    }
    
    /**
     * Pastes the clipboard contents. The pasted content replaces the selection.
     */
    public void paste() {
        pasteClipboard(getToolkit().getSystemClipboard());
    }
    
    /**
     * Pastes the system selection, generally only available on X11.
     */
    public void pasteSystemSelection() {
        Clipboard systemSelection = getToolkit().getSystemSelection();
        if (systemSelection != null) {
            pasteClipboard(systemSelection);
        }
    }
    
    private void pasteClipboard(Clipboard clipboard) {
        if (isEditable() == false) {
            return;
        }
        getLock().getReadLock();
        try {
            Transferable contents = clipboard.getContents(this);
            String string = reformatPastedText((String) contents.getTransferData(DataFlavor.stringFlavor));
            pasteAndReIndent(string);
        } catch (Exception ex) {
            Log.warn("Couldn't paste.", ex);
        } finally {
            getLock().relinquishReadLock();
        }
    }
    
    public void pasteAndReIndent(String string) {
        getLock().getWriteLock();
        try {
            final int startOffsetOfReplacement = getSelectionStart();
            final int endOffsetOfReplacement = startOffsetOfReplacement + string.length();
            getTextBuffer().getUndoBuffer().startCompoundEdit();
            try {
                replaceSelection(string);
                getIndenter().fixIndentationBetween(startOffsetOfReplacement, endOffsetOfReplacement);
            } finally {
                getTextBuffer().getUndoBuffer().finishCompoundEdit();
            }
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    public void copy() {
        copyToClipboard(getToolkit().getSystemClipboard());
    }
    
    public void cut() {
        getLock().getWriteLock();
        try {
            if (hasSelection() && isEditable()) {
                copy();
                replaceSelection("");
            }
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    public boolean isEditable() {
        return editable;
    }
    
    /**
     * Sets whether or not this text component should be editable by the user.
     * A PropertyChange event ("editable") is fired when the state is changed.
     */
    public void setEditable(boolean newState) {
        if (editable == newState) {
            return;
        }
        boolean oldState = this.editable;
        this.editable = newState;
        firePropertyChange("editable", Boolean.valueOf(oldState), Boolean.valueOf(newState));
    }
    
    //
    // Find functionality.
    //
    
    /**
     * Highlights all matches of the given regular expression.
     * The given BirdView (which can be null) will be updated to correspond to the new matches.
     */
    public int findAllMatches(String regularExpression, BirdView birdView) {
        getLock().getWriteLock();
        try {
            return findAllMatchesWithWriteLockAlreadyHeld(regularExpression, birdView);
        } finally {
            getLock().relinquishWriteLock();
        }
    }
    
    private int findAllMatchesWithWriteLockAlreadyHeld(String regularExpression, BirdView birdView) {
        removeHighlights(PFind.MatchHighlight.HIGHLIGHTER_NAME);
        if (birdView != null) {
            birdView.clearMatchingLines();
        }
        
        // Anything to search for?
        if (regularExpression == null || regularExpression.length() == 0) {
            return 0;
        }
        
        // Find all the matches.
        if (birdView != null) {
            birdView.setValueIsAdjusting(true);
        }
        try {
            int matchCount = 0;
            Matcher matcher = PatternUtilities.smartCaseCompile(regularExpression).matcher(getTextBuffer());
            while (matcher.find()) {
                if (birdView != null) {
                    birdView.addMatchingLine(getLineOfOffset(matcher.end()));
                }
                addHighlight(new PFind.MatchHighlight(this, matcher.start(), matcher.end()));
                ++matchCount;
            }
            return matchCount;
        } finally {
            if (birdView != null) {
                birdView.setValueIsAdjusting(false);
            }
        }
    }
    
    public void findNext() {
        findNextOrPrevious(true);
    }
    
    public void findPrevious() {
        findNextOrPrevious(false);
    }
    
    protected void findNextOrPrevious(boolean next) {
        updateFindResults();
        PHighlight nextHighlight = highlights.getNextOrPreviousHighlight(PFind.MatchHighlight.HIGHLIGHTER_NAME, next, next ? getSelectionEnd() : getSelectionStart());
        if (nextHighlight != null) {
            selectHighlight(nextHighlight);
        }
    }
    
    public int getFindMatchCount() {
        return highlights.countHighlightsOfType(PFind.MatchHighlight.HIGHLIGHTER_NAME);
    }
    
    /**
     * Override this to update the find results just in time when the user
     * tries to move to the next or previous match.
     */
    protected void updateFindResults() {
        // Do nothing.
    }
    
    public EPopupMenu getPopupMenu() {
        return popupMenu;
    }
    
    private void initPopupMenu() {
        popupMenu =  new EPopupMenu(this);
        popupMenu.addMenuItemProvider(new ExternalSearchItemProvider(this));
    }
    
    /**
     * Selects the given line, and ensures that the selection is visible.
     * For this method, intended to be directly connected to the UI, line numbers are 1-based.
     */
    public void goToLine(int line) {
        // Humans number lines from 1, the rest of PTextArea from 0.
        --line;
        final int start = getLineStartOffset(line);
        final int end = getLineEndOffsetBeforeTerminator(line);
        centerOnNewSelection(start, end);
    }
    
    /**
     * Changes the selection, and centers the selection on the display.
     */
    public void centerOnNewSelection(final int start, final int end) {
        // Center first, to avoid flicker because PTextArea.select may
        // have caused some scrolling to ensure that the selection is
        // visible, but probably won't have to scrolled such that our
        // offset is centered.
        centerOffsetInDisplay(start);
        select(start, end);
    }
    
    public FileType getFileType() {
        return fileType;
    }
    
    public void setFileType(FileType newFileType) {
        fileType = newFileType;
    }
}
