package e.edit;

import java.awt.*;
import javax.swing.*;
import javax.swing.tree.*;

import e.gui.*;

public class TagsPanel extends JPanel {
    private JProgressBar progressBar = new JProgressBar();
    private JPanel progressPanel;
    
    public TagsPanel() {
        setName("Symbols");
        setLayout(new BorderLayout());
    }
    
    public void setTagsTree(Component c) {
        if (c != null) {
            setVisibleComponent(c);
        } else {
            ensureTagsAreHidden();
        }
    }
    
    public void createUI() {
        /*
         * Embed the progress bar in a panel so that on platforms where a
         * progress bar can become arbitrarily tall (everything but Mac OS X,
         * seemingly), we don't. Because it looks stupid. Unfortunately, this
         * isn't as nice as the default Mac OS X behavior, which is to center
         * the progress bar vertically.
         */
        progressPanel = new JPanel();
        progressPanel.setBackground(UIManager.getColor("Tree.background"));
        progressPanel.add(progressBar);
    }
    
    public void ensureTagsAreHidden() {
        if (isVisible()) {
            Evergreen.getInstance().getSidebar().revertToDefaultPanel();
        }
    }
    
    private void setVisibleComponent(Component c) {
        removeAll();
        add(c, BorderLayout.CENTER);
        c.invalidate();
        revalidate();
        repaint();
    }
    
    public void showError(String error) {
        JLabel label = new JLabel("<html><body>" + error);
        label.setBackground(UIManager.getColor("Tree.background"));
        label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        label.setOpaque(true);
        setVisibleComponent(label);
    }
    
    public void showProgressBar() {
        setVisibleComponent(progressPanel);
        progressBar.setIndeterminate(true);
    }
    
    public static class TagsTreeRenderer extends DefaultTreeCellRenderer {
        private final Icon icon = new DrawnIcon(new Dimension(10, 10)) {
            public void paintIcon(Component c, Graphics og, int x, int y) {
                Graphics2D g = (Graphics2D) og;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setColor(tag.visibilityColor());
                g.translate(x, y);
                
                Shape typeMarker = tag.type.getShape();
                if (typeMarker != null) {
                    g.draw(typeMarker);
                    if (tag.isAbstract) {
                        g.draw(typeMarker);
                    } else {
                        g.fill(typeMarker);
                    }
                }
                
                g.translate(-x, -y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_DEFAULT);
            }
        };
        
        private TagReader.Tag tag;
        
        public TagsTreeRenderer() {
            setClosedIcon(null);
            setLeafIcon(null);
            setOpenIcon(null);
        }
        
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            if (node.getUserObject() instanceof TagReader.Tag) {
                tag = (TagReader.Tag) node.getUserObject();
                if (selected == false) {
                    // Some LAFs have selection backgrounds that are too dark for black and gray.
                    setForeground(tag.visibilityColor() == TagReader.Tag.PRIVATE ? Color.GRAY : Color.BLACK);
                }
                
                Font font = ChangeFontAction.getConfiguredFont();
                if (tag.isStatic && tag.visibilityColor() != TagReader.Tag.PRIVATE) {
                    font = font.deriveFont(Font.BOLD);
                }
                setFont(font);
                setToolTipText(tag.toolTip);
                setIcon(icon);
            } else {
                tag = null;
            }
            return this;
        }
    }
}
