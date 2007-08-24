package e.ptextarea;

import java.awt.*;
import java.awt.event.*;
import e.util.*;

/**
 * FIXME: This should probably be rewritten to use KeyStroke key bindings.
 * 
 * FIXME: This should pay attention to what system we're running on, and try
 * to use the local key bindings. I've looked at the Mac OS key bindings in
 * vim(1) before now, and don't really understand the format. There's some
 * documentation at http://www.cocoadev.com/index.pl?KeyBindings but the
 * Apple documentation (which you can get to from that page) seems mainly
 * about a different format, used to override the StandardKeyBinding.dict
 * bindings. Project Builder/Xcode seem to use that format for their overrides,
 * and anywhere where we say we don't like the Mac OS behavior, we should be
 * writing our own ~/Library/KeyBindings/DefaultKeyBinding.dict file to change
 * all Mac OS applications, not just hacking about with PTextArea which just
 * makes everything more confusing. (So I guess we need to cope both with the
 * system-wide file and any user overrides.)
 * 
 * FIXME: we should find out if there are MS Windows/GNOME equivalents of the
 * Mac OS key bindings files.
 */
public class PKeyHandler implements KeyListener {
    private PTextArea textArea;
    private UpDownMovementHandler movementHandler = new UpDownMovementHandler();
    
    public PKeyHandler(PTextArea textArea) {
        this.textArea = textArea;
        textArea.addCaretListener(movementHandler);
    }
    
    public void keyPressed(KeyEvent e) {
        if (e.isControlDown() && e.isShiftDown()) {
            switch (e.getKeyCode()) {
            case KeyEvent.VK_T:
                textArea.logLineInfo();
                return;
            case KeyEvent.VK_L:
                textArea.getLineList().logLineInfo();
                return;
            case KeyEvent.VK_R:
                textArea.repaint();
                return;
            }
        }
        if (handleInvisibleKeyPressed(e)) {
            e.consume();
        }
    }
    
    public void keyTyped(KeyEvent e) {
        if (isInsertableCharacter(e) && textArea.isEditable()) {
            insertCharacter(e.getKeyChar()); // Only the char is usable in these events anyway.
            e.consume();
        }
    }
    
    public void keyReleased(KeyEvent e) {
    }
    
    private boolean isInsertableCharacter(KeyEvent e) {
        // On Mac OS, 'alt' is used to insert special characters (alt-3, for example, gives whichever of # and £ isn't on the key cap).
        // On other systems, we should ignore keys typed with 'alt' down. GNOME and Win32 use 'alt' to traverse menus.
        if (GuiUtilities.isMacOs() == false && e.isAltDown()) {
            return false;
        }
        
        // FIXME: is 'alt graph' a special case on some systems? For now, assume it isn't.
        if (e.isAltGraphDown()) {
            return false;
        }
        
        // The 'control' key only inserts characters in terminal emulators.
        // The 'meta' (aka 'command' on Mac OS) key never inserts a character.
        if (e.isControlDown() || e.isMetaDown()) {
            return false;
        }
        
        char ch = e.getKeyChar();
        return (ch != KeyEvent.CHAR_UNDEFINED && ch >= ' ' && ch != Ascii.DEL);
    }
    
    private boolean handleInvisibleKeyPressed(KeyEvent event) {
        boolean byWord = GuiUtilities.isMacOs() ? event.isAltDown() : event.isControlDown();
        boolean extendingSelection = event.isShiftDown();
        int key = event.getKeyCode();
        if (textArea.isEditable() && key == KeyEvent.VK_TAB) {
            if ((event.getModifiers() & ~KeyEvent.SHIFT_MASK) != 0) {
                // If any modifiers (other than shift, which is meaningful to us) are down, pass on this event.
                // Pretty much every modifier+tab combination is used by some system, and we should keep out of the way.
                return false;
            }
            textArea.replaceSelection(event.isShiftDown() ? "\t" : textArea.getIndentationString());
        } else if (textArea.isEditable() && key == KeyEvent.VK_ENTER) {
            new PNewlineInserter(textArea).insertNewline(event.isShiftDown() == false);
        } else if (isStartOfTextKey(event)) {
            moveCaret(extendingSelection, 0);
        } else if (isEndOfTextKey(event)) {
            moveCaret(extendingSelection, textArea.getTextBuffer().length());
        } else if (isStartOfLineKey(event)) {
            moveCaret(extendingSelection, caretToStartOfLine());
        } else if (isEndOfLineKey(event)) {
            moveCaret(extendingSelection, caretToEndOfLine());
        } else if (key == KeyEvent.VK_PAGE_DOWN) {
            moveCaret(extendingSelection, caretPageDown());
        } else if (key == KeyEvent.VK_PAGE_UP) {
            moveCaret(extendingSelection, caretPageUp());
        } else if (GuiUtilities.isMacOs() && (key == KeyEvent.VK_HOME || key == KeyEvent.VK_END)) {
            textArea.ensureVisibilityOfOffset((key == KeyEvent.VK_HOME) ? 0 : textArea.getTextBuffer().length());
        } else if (key == KeyEvent.VK_LEFT) {
            moveLeft(byWord, extendingSelection);
        } else if (key == KeyEvent.VK_RIGHT) {
            moveRight(byWord, extendingSelection);
        } else if (key == KeyEvent.VK_BACK_SPACE) {
            backspace();
        } else if (key == KeyEvent.VK_DELETE) {
            if (event.isShiftDown()) {
                textArea.cut();
            } else {
                delete();
            }
        } else if (key == KeyEvent.VK_INSERT && event.isShiftDown()) {
            textArea.paste();
        } else if (key == KeyEvent.VK_UP || key == KeyEvent.VK_DOWN) {
            movementHandler.handleMovementKeys(event);
        } else if (GuiUtilities.isMacOs() && key == KeyEvent.VK_D && event.isControlDown() && event.isMetaDown()) {
            // FIXME: doesn't work because Mac OS is swallowing the key event.
            showDictionaryDefinition();
        } else {
            return false;
        }
        return true;
    }
    
    private void showDictionaryDefinition() {
        if (textArea.hasSelection()) {
            ProcessUtilities.spawn(null, new String[] { "open", "dict:///" + textArea.getSelectedText() });
        }
    }
    
    private boolean isStartOfLineKey(KeyEvent e) {
        if (GuiUtilities.isMacOs()) {
            return ((e.isControlDown() || e.isMetaDown()) && e.getKeyCode() == KeyEvent.VK_LEFT);
        } else {
            return (e.getKeyCode() == KeyEvent.VK_HOME);
        }
    }
    
    private boolean isEndOfLineKey(KeyEvent e) {
        if (GuiUtilities.isMacOs()) {
            return ((e.isControlDown() || e.isMetaDown()) && e.getKeyCode() == KeyEvent.VK_RIGHT);
        } else {
            return (e.getKeyCode() == KeyEvent.VK_END);
        }
    }
    
    private boolean isStartOfTextKey(KeyEvent e) {
        if (e.isMetaDown() && e.getKeyCode() == KeyEvent.VK_UP) {
            return true;
        }
        return (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_HOME);
    }
    
    private boolean isEndOfTextKey(KeyEvent e) {
        if (e.isMetaDown() && e.getKeyCode() == KeyEvent.VK_DOWN) {
            return true;
        }
        return (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_END);
    }
    
    private void insertCharacter(char ch) {
        CharSequence content = new CharArrayCharSequence(new char[] { ch });
        if (textArea.getIndenter().isElectric(ch)) {
            textArea.getTextBuffer().getUndoBuffer().startCompoundEdit();
            try {
                textArea.replaceSelection(content);
                textArea.getIndenter().fixIndentation();
            } finally {
                textArea.getTextBuffer().getUndoBuffer().finishCompoundEdit();
            }
        } else {
            textArea.replaceSelection(content);
        }
    }
    
    private void backspace() {
        if (textArea.isEditable() == false) {
            return;
        }
        
        Range range = determineRangeToRemove();
        if (range.isNonEmpty()) {
            textArea.delete(range.getStart(), range.length());
        }
    }
    
    private Range determineRangeToRemove() {
        if (textArea.hasSelection()) {
            // The user's already done our work for us.
            return new Range(textArea.getSelectionStart(), textArea.getSelectionEnd());
        }
        
        int position = textArea.getSelectionStart();
        if (position == 0) {
            // We can't remove anything before the beginning.
            return Range.NULL_RANGE;
        }
        
        int charactersToDelete = 1;
        final int lineNumber = textArea.getLineOfOffset(position);
        String whitespace = textArea.getIndenter().getCurrentIndentationOfLine(lineNumber);
        int lineOffset = position - textArea.getLineStartOffset(lineNumber);
        CharSequence chars = textArea.getTextBuffer();
        if (Parameters.getParameter("hungryDelete", false)) {
            int startPosition = position - 1;
            if (Character.isWhitespace(chars.charAt(startPosition))) {
                while (startPosition > 0 && Character.isWhitespace(chars.charAt(startPosition - 1))) {
                    startPosition--;
                    charactersToDelete++;
                }
            }
        } else if (lineOffset > 1 && lineOffset <= whitespace.length()) {
            String tab = textArea.getIndentationString();
            whitespace = whitespace.substring(0, lineOffset);
            while (whitespace.startsWith(tab)) {
                whitespace = whitespace.substring(tab.length());
            }
            charactersToDelete = whitespace.length();
            if (charactersToDelete == 0) {
                charactersToDelete = tab.length();
            }
        }
        
        return new Range(position - charactersToDelete, position);
    }
    
    private void delete() {
        if (textArea.isEditable() == false) {
            return;
        }
        
        int start = textArea.getSelectionStart();
        int end = textArea.getSelectionEnd();
        if (start == end && end < textArea.getTextBuffer().length()) {
            ++end;
        }
        if (start != end) {
            textArea.delete(start, end - start);
        }
    }
    
    private void moveCaret(boolean extendingSelection, int newOffset) {
        if (extendingSelection) {
            textArea.changeUnanchoredSelectionExtreme(newOffset);
        } else {
            textArea.setCaretPosition(newOffset);
        }
    }
    
    private int caretPageDown() {
        int lineIndex = textArea.getLineOfOffset(textArea.getUnanchoredSelectionExtreme());
        int pageLineCount = textArea.getVisibleRect().height / textArea.getLineHeight();
        int newLine = lineIndex + pageLineCount;

        if (newLine + pageLineCount < textArea.getLineCount() - 1) {
           textArea.ensureVisibilityOfOffset(
               textArea.getLineStartOffset(newLine + (newLine % pageLineCount)));
        }

        return textArea.getLineStartOffset(Math.min(newLine, textArea.getLineCount() - 1));
    }
    
    private int caretPageUp() {
        int lineIndex = textArea.getLineOfOffset(textArea.getUnanchoredSelectionExtreme());
        int pageLineCount = textArea.getVisibleRect().height / textArea.getLineHeight();
        int newLine = lineIndex - pageLineCount;

        textArea.ensureVisibilityOfOffset(
            textArea.getLineStartOffset(newLine - (newLine % pageLineCount)));

        return textArea.getLineStartOffset(Math.max(0, newLine));
    }
    
    private int caretToStartOfLine() {
        int offset = textArea.getUnanchoredSelectionExtreme();
        int lineIndex = textArea.getLineOfOffset(offset);
        int lineStart = textArea.getLineStartOffset(lineIndex);
        int indentLen = textArea.getIndenter().getCurrentIndentationOfLine(lineIndex).length();

        if (offset != lineStart + indentLen) {
            return lineStart + indentLen;
        } else {
            return lineStart;
        }
    }
    
    private int caretToEndOfLine() {
        int lineIndex = textArea.getLineOfOffset(textArea.getUnanchoredSelectionExtreme());
        return textArea.getLineEndOffsetBeforeTerminator(lineIndex);
    }
    
    private void moveLeft(boolean byWord, boolean extendingSelection) {
        int newOffset = byWord ? caretToPreviousWord() : caretLeft(extendingSelection);
        moveCaret(extendingSelection, newOffset);
    }
    
    private void moveRight(boolean byWord, boolean extendingSelection) {
        int newOffset = byWord ? caretToNextWord() : caretRight(extendingSelection);
        moveCaret(extendingSelection, newOffset);
    }
    
    private int caretLeft(boolean extendingSelection) {
        int newOffset = textArea.getSelectionStart();
        if (extendingSelection) {
            newOffset = textArea.getUnanchoredSelectionExtreme() - 1;
        } else if (textArea.hasSelection() == false) {
            --newOffset;
        }
        return Math.max(0, newOffset);
    }
    
    private int caretRight(boolean extendingSelection) {
        int newOffset = textArea.getSelectionEnd();
        if (extendingSelection) {
            newOffset = textArea.getUnanchoredSelectionExtreme() + 1;
        } else if (textArea.hasSelection() == false) {
            ++newOffset;
        }
        return Math.min(newOffset, textArea.getTextBuffer().length());
    }
    
    private int caretToPreviousWord() {
        CharSequence chars = textArea.getTextBuffer();
        String stopChars = PWordUtilities.DEFAULT_STOP_CHARS;
        int offset = textArea.getUnanchoredSelectionExtreme();
        
        // If we're at the start of the document, we're not going far.
        if (offset == 0) {
            return 0;
        }
        
        // If we're at the start of a word, go to the start of the word before.
        if (PWordUtilities.isInWord(chars, offset - 1, stopChars) == false) {
            return PWordUtilities.getWordStart(chars, PWordUtilities.getNonWordStart(chars, offset - 1, stopChars), stopChars);
        }
        
        // Otherwise go to the start of the current word.
        return PWordUtilities.getWordStart(chars, offset, stopChars);
    }
    
    private int caretToNextWord() {
        CharSequence chars = textArea.getTextBuffer();
        String stopChars = PWordUtilities.DEFAULT_STOP_CHARS;
        int offset = textArea.getUnanchoredSelectionExtreme();
        
        // If we're at the end of the document, we're not going far.
        if (offset == chars.length()) {
            return offset;
        }
        
        // If we're in a word, go to the end of this word.
        if (PWordUtilities.isInWord(chars, offset, stopChars)) {
            return PWordUtilities.getWordEnd(chars, offset, stopChars);
        }
        
        // Otherwise go to the start of the next word.
        return PWordUtilities.getWordEnd(chars, PWordUtilities.getNonWordEnd(chars, PWordUtilities.getWordEnd(chars, offset, stopChars), stopChars), stopChars);
    }
    
    private class UpDownMovementHandler implements PCaretListener {
        private boolean isEntered = false;
        private int xPixelLocation = -1;
        
        public void caretMoved(PTextArea textArea, int selectionStart, int selectionEnd) {
            if (isEntered == false) {
                xPixelLocation = -1;
            }
        }
        
        public void handleMovementKeys(KeyEvent event) {
            isEntered = true;
            if (xPixelLocation == -1) {
                xPixelLocation = getCurrentXPixelLocation();
            }
            boolean extendingSelection = event.isShiftDown();
            try {
                moveCaret(extendingSelection, event.getKeyCode() == KeyEvent.VK_UP ? caretUp() : caretDown());
            } finally {
                isEntered = false;
            }
        }
        
        private int getCurrentXPixelLocation() {
            PCoordinates coords = textArea.getCoordinates(textArea.getUnanchoredSelectionExtreme());
            return textArea.getViewCoordinates(coords).x;
        }
        
        private int caretUp() {
            PCoordinates coords = textArea.getCoordinates(textArea.getUnanchoredSelectionExtreme());
            int lineIndex = coords.getLineIndex();
            if (lineIndex == 0) {
                return 0;
            } else {
                int y = textArea.getViewCoordinates(new PCoordinates(lineIndex - 1, 0)).y;
                return textArea.getTextIndex(textArea.getNearestCoordinates(new Point(xPixelLocation, y)));
            }
        }
        
        private int caretDown() {
            PCoordinates coords = textArea.getCoordinates(textArea.getUnanchoredSelectionExtreme());
            int lineIndex = coords.getLineIndex();
            if (lineIndex == textArea.getSplitLineCount() - 1) {
                return textArea.getTextBuffer().length();
            } else {
                int y = textArea.getViewCoordinates(new PCoordinates(lineIndex + 1, 0)).y;
                return textArea.getTextIndex(textArea.getNearestCoordinates(new Point(xPixelLocation, y)));
            }
        }
    }
}
