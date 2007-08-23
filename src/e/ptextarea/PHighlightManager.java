package e.ptextarea;

import java.util.*;

public class PHighlightManager {
    private Map<String, HighlightSet> highlighterSets = new LinkedHashMap<String, HighlightSet>();
    
    public synchronized int countHighlightsOfType(String highlighterName) {
        HighlightSet set = highlighterSets.get(highlighterName);
        return (set != null) ? set.size() : 0;
    }
    
    public synchronized void add(PHighlight highlight) {
        String highlighterName = highlight.getHighlighterName();
        if (highlighterSets.containsKey(highlighterName) == false) {
            highlighterSets.put(highlighterName, new HighlightSet());
        }
        highlighterSets.get(highlighterName).add(highlight);
    }
    
    public synchronized void remove(PHighlight highlight) {
        String highlighterName = highlight.getHighlighterName();
        if (highlighterSets.containsKey(highlighterName)) {
            highlighterSets.get(highlighterName).remove(highlight);
        }
    }
    
    /**
     * Returns all highlighters overlapping the range [beginOffset, endOffset).
     */
    public synchronized List<PHighlight> getHighlightsOverlapping(int beginOffset, int endOffset) {
        List<PHighlight> result = new ArrayList<PHighlight>();
        for (String highlighter : highlighterSets.keySet()) {
            result.addAll(getNamedHighlightsOverlapping(highlighter, beginOffset, endOffset));
        }
        return result;
    }
    
    /**
     * Returns all highlighters matching highlighterName overlapping the range [beginOffset, endOffset).
     */
    public synchronized List<PHighlight> getNamedHighlightsOverlapping(String highlighterName, int beginOffset, int endOffset) {
        HighlightSet set = highlighterSets.get(highlighterName);
        if (set != null) {
            return set.getHighlightsOverlapping(beginOffset, endOffset);
        } else {
            return Collections.emptyList();
        }
    }
    
    public synchronized PHighlight getNextOrPreviousHighlight(String highlighterName, boolean next, int offset) {
        HighlightSet set = highlighterSets.get(highlighterName);
        if (set == null) {
            return null;
        }
        return next ? set.getHighlightAfter(offset) : set.getHighlightBefore(offset);
    }
    
    private static class HighlightSet {
        private TreeSet<Wrapper> highlights = new TreeSet<Wrapper>();
        
        private void add(PHighlight highlight) {
            highlights.add(new Wrapper(highlight));
        }
        
        private void remove(PHighlight highlight) {
            highlights.remove(new Wrapper(highlight));
        }
        
        private int size() {
            return highlights.size();
        }
        
        private PHighlight getHighlightAfter(int offset) {
            SortedSet<Wrapper> after = highlights.tailSet(new ProbeWrapper(offset));
            return (after.size() == 0) ? null : after.first().get();
        }
        
        private PHighlight getHighlightBefore(int offset) {
            SortedSet<Wrapper> before = highlights.headSet(new ProbeWrapper(offset));
            return (before.size() == 0) ? null : before.last().get();
        }
        
        private List<PHighlight> getHighlightsOverlapping(int beginOffset, int endOffset) {
            // The 'firstItem' is to be the lowest-indexed highlight wrapper which *overlaps* the range.
            // We must check highlights which start before beginOffset to determine if they end past beginOffset.
            Wrapper firstItem = new ProbeWrapper(beginOffset);
            SortedSet<Wrapper> highlightsBeforeStart = highlights.headSet(firstItem);
            if (highlightsBeforeStart.size() > 0) {
                Wrapper lastBefore = highlightsBeforeStart.last();
                if (lastBefore.get().getEndIndex() > beginOffset) {
                    firstItem = lastBefore;
                }
            }
            
            // Now we have the start, and the end too, so we can simply grab the subset between these two extremes (inclusive of firstItem) and return as a list.
            SortedSet<Wrapper> highlightsInRange = highlights.subSet(firstItem, new ProbeWrapper(endOffset));
            List<PHighlight> result = new ArrayList<PHighlight>(highlightsInRange.size());
            for (Wrapper wrapper : highlightsInRange) {
                result.add(wrapper.get());
            }
            return result;
        }
    }
    
    private static class Wrapper implements Comparable<Wrapper> {
        private PHighlight highlight;
        
        private Wrapper(PHighlight highlight) {
            this.highlight = highlight;
        }
        
        public boolean equals(Object obj) {
            return (obj instanceof Wrapper) && (((Wrapper) obj).getOffset() == getOffset());
        }
        
        public int compareTo(Wrapper other) {
            return getOffset() - other.getOffset();
        }
        
        protected int getOffset() {
            return highlight.getStartIndex();
        }
        
        private PHighlight get() {
            return highlight;
        }
    }
    
    private static class ProbeWrapper extends Wrapper {
        private int fixedOffset;
        
        private ProbeWrapper(int fixedOffset) {
            super(null);
            this.fixedOffset = fixedOffset;
        }
        
        @Override
        protected int getOffset() {
            return fixedOffset;
        }
    }
}
