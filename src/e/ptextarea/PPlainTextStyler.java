package e.ptextarea;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * A trivial styler for plain text, which colors all text black.
 */
public class PPlainTextStyler extends PAbstractTextStyler {
    public PPlainTextStyler(PTextArea textArea) {
        super(textArea);
    }
    
    @Override
    public List<PLineSegment> getTextSegments(int line) {
        int start = textArea.getLineStartOffset(line);
        int end = textArea.getLineEndOffsetBeforeTerminator(line);
        List<PLineSegment> result = new ArrayList<PLineSegment>();
        result.add(new PTextSegment(textArea, start, end, PStyle.NORMAL));
        return result;
    }

    public void addKeywordsTo(Collection<String> collection) {
        // We have no language, so we have no keywords.
    }
}
