package e.ptextarea;


/**
 * A PAnchor is an index within a PText whose location is fixed not in terms of character
 * index, but rather in terms of the character it indexes.  If a PAnchor is defined as location
 * 100, and then 20 characters are inserted at location 50, that PAnchor's index will be
 * automatically updated to be 120.
 * In order to properly function, a PAnchor must have been added to a PAnchorSet which in
 * turn listens to the PText.
 * 
 * @author Phil Norman
 */

public abstract class PAnchor implements Comparable<PAnchor> {
    private int index;
    
    public PAnchor(int index) {
        this.index = index;
    }
    
    /** Returns the current index at which this anchor is anchored. */
    public int getIndex() {
        return index;
    }
    
    /** Changes the index at which this anchor is anchored. */
    public void setIndex(int index) {
        this.index = index;
    }
    
    /**
     * Notification that this anchor has been destroyed, because the character it indexes
     * has been deleted.  This is used by the PAnchor sub-class in PHighlight to ensure that
     * the entire highlight is destroyed if one of its anchors goes away.
     */
    public abstract void anchorDestroyed();
    
    @Override
    public int hashCode() {
        // FIXME: because this class is mutable, instances MUST NOT be stored long-term in hashes.
        // FIXME: instances are hashed, so we (a) don't want to return a constant here because we want O(1) lookup, and (b) should investigate the performance of this implementation.
        return index;
    }
    
    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof PAnchor) {
            return (index == ((PAnchor) obj).index);
        }
        return false;
    }
    
    //@Override // FIXME: Java 5's javac(1) is broken.
    public final int compareTo(PAnchor other) {
        return (index - other.index);
    }
    
    @Override
    public String toString() {
        return "PAnchor[index=" + index + "]";
    }
}
