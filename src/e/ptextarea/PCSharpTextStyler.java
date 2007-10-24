package e.ptextarea;

import java.util.*;

public class PCSharpTextStyler extends PAbstractLanguageStyler {
    private static final String[] KEYWORDS = new String[] {
        // http://en.wikibooks.org/wiki/C_Sharp_Programming/Keywords/ref
        "abstract",
        "as",
        "base",
        "break",
        "case",
        "catch",
        "checked",
        "class",
        "const",
        "continue",
        "default",
        "delegate",
        "do",
        "else",
        "enum",
        "event",
        "explicit",
        "extern",
        "false",
        "finally",
        "fixed",
        "for",
        "foreach",
        "goto",
        "if",
        "implicit",
        "in",
        "interface",
        "internal",
        "is",
        "lock",
        "namespace",
        "new",
        "null",
        "object",
        "operator",
        "out",
        "override",
        "params",
        "private",
        "protected",
        "public",
        "readonly",
        "ref",
        "return",
        "sealed",
        "sizeof",
        "stackalloc",
        "static",
        "struct",
        "switch",
        "this",
        "throw",
        "true",
        "try",
        "typeof",
        "unchecked",
        "unsafe",
        "using",
        "virtual",
        "void",
        "volatile",
        "while",
        
        // These ones are context-sensitive, I think.
        "add",
        "alias",
        "get",
        "global",
        "partial",
        "remove",
        "set",
        "value",
        "where",
        "yield",
    };
    
    private static final String[] TYPES = new String[] {
        "bool",
        "byte",
        "char",
        "decimal",
        "double",
        "float",
        "int",
        "long",
        "sbyte",
        "short",
        "string",
        "uint",
        "ulong",
        "ushort",        
    };
    
    public PCSharpTextStyler(PTextArea textArea) {
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
