package e.ptextarea;

import java.util.*;

/**
 * A PCPPTextStyler knows how to apply syntax highlighting for C++ code.
 * 
 * @author Phil Norman
 */
public class PCPPTextStyler extends PAbstractLanguageStyler {
    private static final String[] KEYWORDS = new String[] {
        // ISO+IEC+14882-1998 2.11 table 3:
        "asm",
        "break",
        "case",
        "catch",
        "class",
        "const_cast",
        "continue",
        "default",
        "delete",
        "do",
        "dynamic_cast",
        "else",
        "enum",
        "false",
        "for",
        "friend",
        "goto",
        "if",
        "namespace",
        "new",
        "operator",
        "private",
        "protected",
        "public",
        "reinterpret_cast",
        "return",
        "sizeof",
        "static_cast",
        "struct",
        "switch",
        "template",
        "this",
        "throw",
        "true",
        "try",
        "typedef",
        "typeid",
        "typename",
        "union",
        "using",
        "virtual",
        "while",
        // ISO+IEC+14882-1998 2.11 table 4:
        "and",
        "and_eq",
        "bitand",
        "bitor",
        "compl",
        "not",
        "not_eq",
        "or",
        "or_eq",
        "xor",
        "xor_eq",
        
        // Common C #defines
        "NULL",
        "MAX",
        "MIN",
        "TRUE",
        "FALSE",
        "__LINE__",
        "__DATA__",
        "__FILE__",
        "__func__",
        "__TIME__",
        "__STDC__",
        
        // Common C++ #defines
        "__cplusplus",
    };
    
    private static final String[] TYPES = new String[] {
        // C types
        "bool",
        "char",
        "double",
        "float",
        "int",
        "long",
        "short",
        "signed",
        "size_t",
        "unsigned",
        "void",
        
        // C++ types
        "explicit",
        "export",
        "inline",
        "mutable",
        "wchar_t",
        
        // C storage classes
        "auto",
        "const",
        "extern",
        "inline",
        "register",
        "restrict",
        "static",
        "volatile",
    };
    
    public PCPPTextStyler(PTextArea textArea) {
        super(textArea);
    }
    
    @Override
    public void initStyleApplicators() {
        super.initStyleApplicators();
        // "#else" is PREPROCESSOR, but "else" is KEYWORD, so we need to look for preprocessor directives first.
        textArea.addStyleApplicatorFirst(new PreprocessorStyleApplicator(textArea, false));
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
