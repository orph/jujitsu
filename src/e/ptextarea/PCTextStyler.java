package e.ptextarea;

import java.util.*;

/**
 * A PCTextStyler knows how to apply syntax highlighting for C code.
 * 
 * @author Phil Norman
 */
public class PCTextStyler extends PAbstractLanguageStyler {
    private static final String[] KEYWORDS = new String[] {
        "auto",
        "break",
        "case",
        "const",
        "continue",
        "default",
        "do",
        "else",
        "enum",
        "extern",
        "for",
        "goto",
        "if",
        "register",
        "return",
        "static",
        "struct",
        "switch",
        "typedef",
        "union",
        "void",
        "volatile",
        "while"
    };
    
    private static final String[] TYPES = new String[] {
        "char",
        "double",
        "float",
        "int",
        "long",
        "short",
        "signed",
        "unsigned",        
    };
    
    public PCTextStyler(PTextArea textArea) {
        super(textArea);
    }
    
    @Override
    protected boolean isStartOfCommentToEndOfLine(String line, int atIndex) {
        return line.startsWith("//", atIndex);
    }
    
    @Override
    protected boolean supportMultiLineComments() {
        return true;
    }
    
    public void addKeywordsTo(Collection<String> collection) {
        collection.addAll(Arrays.asList(KEYWORDS));
    }
    
    public void addTypesTo(Collection<String> collection) {
        collection.addAll(Arrays.asList(TYPES));
    }
}
