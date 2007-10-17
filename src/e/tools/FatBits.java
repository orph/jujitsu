package e.tools;

import com.apple.eawt.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import e.forms.*;
import e.gui.*;
import e.util.*;

/**
 * A Java equivalent of Apple's Pixie.app magnifying glass utility. This is
 * useful for checking layout, graphics, and colors in your own programs and
 * those of others.
 */
public class FatBits extends MainFrame {
    private Robot robot;
    private RepeatingComponentTimer timer;
    private ScaledImagePanel scaledImagePanel;
    private int scaleFactor;
    
    private JSlider scaleSlider;
    private JCheckBox showCrosshairCheckBox;
    private JCheckBox showGridCheckBox;
    private JCheckBox keepOnTopCheckBox;
    
    private JLabel positionLabel;
    
    private JLabel colorLabel;
    private JLabel colorSwatchLabel;
    private ColorSwatchIcon colorSwatch;
    
    public FatBits() {
        super("FatBits");
        try {
            robot = new Robot();
        } catch (AWTException ex) {
            Log.warn("failed to create a Robot", ex);
        }
        setSize(new Dimension(280, 300));
        setContentPane(makeUi());
        setJMenuBar(new FatBitsMenuBar());
        timer = new RepeatingComponentTimer(this, 50, new MouseTracker());
        timer.start();
    }
    
    private JComponent makeUi() {
        initAboutBox();
        initMacOsEventHandlers();
        
        initColorLabel();
        initKeepOnTopCheckBox();
        initPositionLabel();
        initScaledImagePanel();
        initScaleSlider();
        initShowCrosshairCheckBox();
        initShowGridCheckBox();
        
        JPanel result = new JPanel(new BorderLayout());
        result.add(scaledImagePanel, BorderLayout.CENTER);
        result.add(makeInfoPanel(), BorderLayout.SOUTH);
        return result;
    }
    
    private void initAboutBox() {
        AboutBox aboutBox = AboutBox.getSharedInstance();
        aboutBox.setWebSiteAddress("http://software.jessies.org/salma-hayek/");
        aboutBox.addCopyright("Copyright (C) 2005-2007 Free Software Foundation, Inc.");
        aboutBox.addCopyright("All Rights Reserved.");
    }
    
    private void initMacOsEventHandlers() {
        if (GuiUtilities.isMacOs() == false) {
            return;
        }
        
        Application.getApplication().setEnabledPreferencesMenu(true);
        Application.getApplication().addApplicationListener(new ApplicationAdapter() {
            @Override
            public void handlePreferences(ApplicationEvent e) {
                showPreferencesDialog();
                e.setHandled(true);
            }
            
            @Override
            public void handleQuit(ApplicationEvent e) {
                e.setHandled(true);
            }
        });
    }
    
    private JPanel makeInfoPanel() {
        JPanel infoPanel = new JPanel(new BorderLayout(8, 0));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        infoPanel.add(colorSwatchLabel, BorderLayout.WEST);
        if (GuiUtilities.isGtk()) {
            JButton infoButton = new JButton(new PreferencesAction());
            infoButton.setText("");
            infoPanel.add(infoButton, BorderLayout.EAST);
        }
        
        JPanel textLines = new JPanel(new GridLayout(2,1));
        textLines.add(colorLabel);
        textLines.add(positionLabel);
        
        infoPanel.add(textLines, BorderLayout.CENTER);
        return infoPanel;
    }
    
    private void initScaledImagePanel() {
        this.scaledImagePanel = new ScaledImagePanel();
    }
    
    private void initPositionLabel() {
        this.positionLabel = new JLabel(" ");
        positionLabel.setFont(colorLabel.getFont());
    }
    
    private boolean updatePosition(Point p) {
        if (scaledImagePanel.isShowing() == false) {
            return false;
        }
        
        positionLabel.setText("X:" + p.x + " Y:" + p.y);
        
        Rectangle screenCaptureBounds = getScreenCaptureBounds(p);
        BufferedImage capturedImage = robot.createScreenCapture(screenCaptureBounds);
        updateCenterColor(capturedImage.getRGB(capturedImage.getWidth() / 2, capturedImage.getHeight() / 2));
        
        Image scaledImage = scaleImage(capturedImage, roundLengthDown(scaledImagePanel.getWidth()), roundLengthDown(scaledImagePanel.getHeight()));
        scaledImagePanel.setImage(scaledImage);
        
        return true;
    }
    
    private static Image scaleImage(Image sourceImage, int width, int height) {
        return ImageUtilities.scale(sourceImage, width, height, ImageUtilities.InterpolationHint.REPLICATE);
    }
    
    private void initColorLabel() {
        this.colorLabel = new JLabel(" ");
        Font font = colorLabel.getFont();
        colorLabel.setFont(new Font(GuiUtilities.getMonospacedFontName(), font.getStyle(), font.getSize()));
        int height = colorLabel.getPreferredSize().height - 2;
        this.colorSwatch = new ColorSwatchIcon(null, new Dimension(20, height));
        this.colorSwatchLabel = new JLabel(colorSwatch);
    }
    
    private void updateCenterColor(int argb) {
        Color color = new Color(argb);
        colorLabel.setText(String.format("#%06x RGB:%d,%d,%d", argb & 0xffffff, color.getRed(), color.getGreen(), color.getBlue()));
        colorSwatch.setColor(color);
        colorSwatchLabel.repaint();
    }
    
    private void initShowCrosshairCheckBox() {
        this.showCrosshairCheckBox = new JCheckBox("Show crosshair");
        showCrosshairCheckBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                scaledImagePanel.setShowCrosshair(showCrosshairCheckBox.isSelected());
            }
        });
        showCrosshairCheckBox.setSelected(true);
    }
    
    private void initShowGridCheckBox() {
        this.showGridCheckBox = new JCheckBox("Show grid");
        showGridCheckBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                scaledImagePanel.setShowGrid(showGridCheckBox.isSelected());
            }
        });
        showGridCheckBox.setSelected(false);
    }
    
    private void initKeepOnTopCheckBox() {
        this.keepOnTopCheckBox = new JCheckBox("Keep window on top");
        keepOnTopCheckBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                FatBits.this.setAlwaysOnTop(keepOnTopCheckBox.isSelected());
            }
        });
        keepOnTopCheckBox.setSelected(false);
    }
    
    private void initScaleSlider() {
        this.scaleSlider = new JSlider(1, 4);
        scaleSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                scaleFactor = (1 << scaleSlider.getValue());
                updatePosition(getPointerLocation());
            }
        });
        Hashtable<Integer, JComponent> labels = new Hashtable<Integer, JComponent>();
        for (int i = scaleSlider.getMinimum(); i <= scaleSlider.getMaximum(); ++i) {
            labels.put(i, new JLabel(Integer.toString(1 << i) + "x"));
        }
        scaleSlider.setLabelTable(labels);
        scaleSlider.setPaintLabels(true);
        scaleSlider.setPaintTicks(true);
        scaleSlider.setSnapToTicks(true);
        scaleSlider.setValue(1);
    }
    
    private int roundLengthDown(int length) {
        return (length - (length % scaleFactor));
    }
    
    private Rectangle getScreenCaptureBounds(Point center) {
        Point topLeft = new Point(center.x - scaledImagePanel.getWidth() / (2 * scaleFactor), center.y - scaledImagePanel.getHeight() / (2 * scaleFactor));
        Rectangle result = new Rectangle(topLeft, scaledImagePanel.getSize());
        result.width /= scaleFactor;
        result.height /= scaleFactor;
        
        // Constrain the capture to the display.
        // Apple's 1.5 VM crashes if you don't.
        result.x = Math.max(result.x, 0);
        result.y = Math.max(result.y, 0);
        Dimension screenSize = getToolkit().getScreenSize();
        if (result.x + result.width > screenSize.width) {
            result.x = screenSize.width - result.width;
        }
        if (result.y + result.height > screenSize.height) {
            result.y = screenSize.height - result.height;
        }
        return result;
    }
    
    private Point getPointerLocation() {
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        return pointerInfo.getLocation();
    }
    
    private class ScaledImagePanel extends JComponent {
        private Image image;
        private boolean showCrosshair;
        private boolean showGrid;
        
        public ScaledImagePanel() {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (timer.isRunning() == false) {
                        BufferedImage image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
                        paintImage(image.getGraphics());
                        updateCenterColor(image.getRGB(e.getX(), e.getY()));
                    }
                }
            });
        }
        
        public void setImage(Image image) {
            this.image = image;
            repaint();
        }
        
        public Image getImage() {
            return image;
        }
        
        public void setShowCrosshair(boolean showCrosshair) {
            this.showCrosshair = showCrosshair;
            repaint();
        }
        
        public void setShowGrid(boolean showGrid) {
            this.showGrid = showGrid;
            repaint();
        }
        
        @Override
        public void paintComponent(Graphics g) {
            paintImage(g);
            paintGridLines(g, xOrigin(), yOrigin());
            paintCrosshair(g);
        }
        
        private void paintImage(Graphics g) {
            g.drawImage(image, xOrigin(), yOrigin(), null);
        }
        
        private void paintCrosshair(Graphics g) {
            if (showCrosshair == false) {
                return;
            }
            g.setColor(showGrid ? Color.RED : Color.BLACK);
            g.drawLine(getWidth() / 2, 0, getWidth() / 2, getHeight());
            g.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
        }
        
        private void paintGridLines(Graphics g, int xOrigin, int yOrigin) {
            if (showGrid == false) {
                return;
            }
            g.setColor(Color.BLACK);
            final int maxX = xOrigin + roundLengthDown(getWidth());
            final int maxY = yOrigin + roundLengthDown(getHeight());
            for (int x = xOrigin + scaleFactor; x < maxX; x += scaleFactor) {
                g.drawLine(x, yOrigin, x, maxY - 1);
            }
            for (int y = yOrigin + scaleFactor; y < maxY; y += scaleFactor) {
                g.drawLine(xOrigin, y, maxX - 1, y);
            }
        }
        
        private int xOrigin() {
            return origin(getWidth());
        }
        
        private int yOrigin() {
            return origin(getHeight());
        }
        
        private int origin(int dimension) {
            return (dimension - roundLengthDown(dimension)) / 2;
        }
    }
    
    private class MouseTracker implements ActionListener {
        private Point lastPosition = null;

        public void actionPerformed(ActionEvent e) {
            // Has the pointer moved?
            Point p = getPointerLocation();
            if (scaledImagePanel.isShowing() && lastPosition != null && lastPosition.equals(p)) {
                return;
            }
            
            // Update.
            boolean updated = updatePosition(p);
            if (updated) {
                lastPosition = p;
            }
        }
    }
    
    private void showPreferencesDialog() {
        FormBuilder form = new FormBuilder(this, "FatBits Preferences");
        FormPanel formPanel = form.getFormPanel();
        formPanel.addRow("Scale:", scaleSlider);
        formPanel.addRow("", showCrosshairCheckBox);
        formPanel.addRow("", showGridCheckBox);
        formPanel.addRow("", keepOnTopCheckBox);
        form.showNonModal();
        // also: [x] refresh only when mouse moves
        //       [ ] show mouse hot-spot
        // alternative grid colors?
    }
    
    private class FatBitsMenuBar extends JMenuBar {
        private FatBitsMenuBar() {
            if (GuiUtilities.isMacOs() == false) {
                add(makeFileMenu());
            }
            add(makeEditMenu());
            add(makeImageMenu());
            add(makeHelpMenu());
        }
        
        private JMenu makeFileMenu() {
            JMenu menu = new JMenu("File");
            menu.add(new QuitAction());
            return menu;
        }
        
        private JMenu makeEditMenu() {
            JMenu menu = new JMenu("Edit");
            menu.add(new CopyImageAction());
            if (GuiUtilities.isMacOs() == false) {
                menu.add(new PreferencesAction());
            }
            return menu;
        }
        
        private JMenu makeImageMenu() {
            JMenu menu = new JMenu("Image");
            menu.add(new MouseMotionAction("Left", -1, 0));
            menu.add(new MouseMotionAction("Right", +1, 0));
            menu.add(new MouseMotionAction("Up", 0, -1));
            menu.add(new MouseMotionAction("Down", 0, +1));
            menu.add(new JSeparator());
            menu.add(new JCheckBoxMenuItem(new LockImageAction()));
            return menu;
        }
        
        private JMenu makeHelpMenu() {
            HelpMenu helpMenu = new HelpMenu();
            return helpMenu.makeJMenu();
        }
    }
    
    private class MouseMotionAction extends AbstractAction {
        private int dx;
        private int dy;
        
        private MouseMotionAction(String direction, int dx, int dy) {
            super("Move " + direction);
            this.dx = dx;
            this.dy = dy;
            putValue(ACCELERATOR_KEY, GuiUtilities.makeKeyStroke(direction.toUpperCase(), false));
        }
        
        public void actionPerformed(ActionEvent e) {
            Point p = getPointerLocation();
            robot.mouseMove(p.x + dx, p.y + dy);
        }
    }
    
    private class QuitAction extends AbstractAction {
        private QuitAction() {
            super("Quit");
            putValue(ACCELERATOR_KEY, GuiUtilities.makeKeyStroke("Q", false));
            GnomeStockIcon.configureAction(this);
        }
        
        public void actionPerformed(ActionEvent e) {
            setVisible(false);
        }
    }
    
    private class PreferencesAction extends AbstractAction {
        private PreferencesAction() {
            super("Preferences...");
            GnomeStockIcon.configureAction(this);
        }
        
        public void actionPerformed(ActionEvent e) {
            showPreferencesDialog();
        }
    }
    
    private class CopyImageAction extends AbstractAction {
        private CopyImageAction() {
            super("Copy Image");
            putValue(ACCELERATOR_KEY, GuiUtilities.makeKeyStroke("C", false));
        }
        
        public void actionPerformed(ActionEvent e) {
            ImageSelection.copyImageToClipboard(scaledImagePanel.getImage());
        }
    }
    
    private class LockImageAction extends AbstractAction {
        private LockImageAction() {
            super("Lock Image");
            putValue(ACCELERATOR_KEY, GuiUtilities.makeKeyStroke("L", false));
        }
        
        public void actionPerformed(ActionEvent e) {
            JCheckBoxMenuItem menuItem = (JCheckBoxMenuItem) e.getSource();
            boolean lock = menuItem.getState();
            if (lock) {
                timer.stop();
            } else {
                timer.start();
            }
        }
    }
    
    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                GuiUtilities.initLookAndFeel();
                new FatBits().setVisible(true);
            }
        });
    }
}
