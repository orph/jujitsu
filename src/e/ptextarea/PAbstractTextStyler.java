package e.ptextarea;

import java.util.List;
import java.util.Collection;

public abstract class PAbstractTextStyler implements PTextStyler {
    protected PTextArea textArea;
    
    public PAbstractTextStyler(PTextArea textArea) {
        this.textArea = textArea;
    }
    
    public abstract List<PLineSegment> getTextSegments(int line);
    
    public boolean keywordsAreCaseSensitive() {
        return true;
    }
    
    public void addTypesTo(Collection<String> collection) {
        // Do nothing.
    }
}
