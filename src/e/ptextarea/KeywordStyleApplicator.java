package e.ptextarea;

import java.util.*;
import java.util.regex.*;

/**
 * Recognizes keywords within NORMAL text segments and styles them.
 */
public class KeywordStyleApplicator extends RegularExpressionStyleApplicator {
    private Set<String> keywords;
        
    public KeywordStyleApplicator(PTextArea textArea,
                                  Set<String> keywords,
                                  String keywordRegularExpression,
                                  PStyle style) {
        super(textArea, keywordRegularExpression, style);
        this.keywords = keywords;
    }
    
    @Override
    public boolean isAcceptableMatch(CharSequence line, Matcher matcher) {
        return keywords.contains(matcher.group(1));
    }
}
