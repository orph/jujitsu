package e.gui;

import com.apple.eawt.*;
import e.ptextarea.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.swing.*;

/**
 * A simple "about box".
 */
public class AboutBox {
    private static final AboutBox INSTANCE = new AboutBox();
    
    private ImageIcon icon;
    private String applicationName = Log.getApplicationName();
    private String webSiteAddress;
    private ArrayList<String> versionLines = new ArrayList<String>();
    private ArrayList<String> copyrightLines = new ArrayList<String>();
    
    private String projectRevision = "unknown";
    private String libraryRevision = "unknown";
    
    private AboutBox() {
        initMacOs();
        initIcon();
        findBuildRevisionFile();
    }
    
    public static AboutBox getSharedInstance() {
        return INSTANCE;
    }
    
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
    
    public String getWebSiteAddress() {
        return webSiteAddress;
    }
    
    /**
     * Sets the base URL for the application's website.
     * The current requirements are:
     * 1. that the address itself goes to some meaningful page about the application.
     * 2. that there be a "ChangeLog.html" and a "faq.html" under this location.
     */
    public void setWebSiteAddress(String webSiteAddress) {
        this.webSiteAddress = webSiteAddress;
    }
    
    public boolean isConfigured() {
        return (applicationName != null);
    }
    
    public void setImage(String filename) {
        try {
            // Apple's HIG says scale to 64x64. I'm not sure there's any advice for the other platforms.
            this.icon = new ImageIcon(ImageUtilities.scale(ImageIO.read(FileUtilities.fileFromString(filename)), 128, 128, ImageUtilities.InterpolationHint.BICUBIC));
            
            if (GuiUtilities.isMacOs()) {
                // Apple's HIG says that these dialog icons should be the application icon.
                UIManager.put("OptionPane.errorIcon", icon);
                UIManager.put("OptionPane.informationIcon", icon);
                UIManager.put("OptionPane.questionIcon", icon);
                UIManager.put("OptionPane.warningIcon", icon);
            }
        } catch (Exception ex) {
            // We can live without an icon.
        }
    }
    
    public void addVersion(String version) {
        versionLines.add(version);
    }
    
    /**
     * Adds a line of copyright text. You can add as many as you like. ASCII
     * renditions of the copyright symbol are automatically converted to the
     * real thing.
     */
    public void addCopyright(String copyright) {
        copyrightLines.add(copyright.replaceAll("\\([Cc]\\)", "\u00a9"));
    }
    
    private Frame findSuitableOwner() {
        if (GuiUtilities.isMacOs()) {
            // On Mac OS, we're supposed to be in the center of the display.
            return null;
        }
        // Find an owner for the about box so we inherit the frame icon and get a sensible relative position.
        Frame owner = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner());
        if (owner == null) {
            Frame[] frames = Frame.getFrames();
            if (frames.length > 0) {
                owner = frames[0];
            }
        }
        return owner;
    }
    
    public void show() {
        JDialog dialog = new JDialog(findSuitableOwner()) {
            @Override
            public void setVisible(boolean newVisibility) {
                super.setVisible(newVisibility);
                if (newVisibility == false) {
                    dispose();
                }
            }
        };
        makeUi(dialog);
        dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        JFrameUtilities.closeOnEsc(dialog);
        dialog.setVisible(true);
    }
    
    private void makeUi(final JDialog dialog) {
        if (GuiUtilities.isMacOs() == false) {
            // GNOME and Win32 applications give their about boxes titles.
            dialog.setTitle("About " + applicationName);
        }
        
        // FIXME: add GNOME and Win32 implementations.
        // http://developer.apple.com/documentation/UserExperience/Conceptual/OSXHIGuidelines/XHIGWindows/chapter_17_section_5.html#//apple_ref/doc/uid/20000961-TPXREF17
        
        // Mac OS font defaults.
        Font applicationNameFont = new Font("Lucida Grande", Font.BOLD, 14);
        Font versionFont = new Font("Lucida Grande", Font.PLAIN, 10);
        Font copyrightFont = new Font("Lucida Grande", Font.PLAIN, 10);
        Font linkFont = versionFont;
        
        if (GuiUtilities.isWindows()) {
            // I don't think this is quite the right font, but it seems to be as close as we can get with the Win32 LAF.
            applicationNameFont = versionFont = copyrightFont = linkFont = UIManager.getFont("MenuItem.font");
        } else if (GuiUtilities.isGtk()) {
            final float PANGO_SCALE_SMALL = (1.0f / 1.2f);
            final float PANGO_SCALE_XX_LARGE = (1.2f * 1.2f * 1.2f);
            final Font gnomeBaseFont = UIManager.getFont("TextArea.font");
            final float baseSize = gnomeBaseFont.getSize2D();
            applicationNameFont = gnomeBaseFont.deriveFont(baseSize * PANGO_SCALE_XX_LARGE).deriveFont(Font.BOLD);
            versionFont = gnomeBaseFont.deriveFont(baseSize * PANGO_SCALE_SMALL);
            copyrightFont = gnomeBaseFont.deriveFont(baseSize * PANGO_SCALE_SMALL);
            linkFont = gnomeBaseFont;
        }
        
        int bottomBorder = 20;
        if (GuiUtilities.isGtk()) {
            bottomBorder = 12;
        }
        
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(8, 12, bottomBorder, 12));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        
        Dimension spacerSize = new Dimension(1, 8);
        
        // The application icon comes first...
        if (icon != null) {
            addLabel(panel, new JLabel(icon));
            panel.add(Box.createRigidArea(spacerSize));
            panel.add(Box.createRigidArea(spacerSize));
        }
        
        // Then the application name...
        addLabel(panel, applicationNameFont, applicationName);
        panel.add(Box.createRigidArea(spacerSize));
        
        // Then version information...
        for (String version : versionLines) {
            addLabel(panel, versionFont, version);
        }
        if (versionLines.size() > 0) {
            panel.add(Box.createRigidArea(spacerSize));
        }
        
        // Then copyright information...
        for (String copyright : copyrightLines) {
            addLabel(panel, copyrightFont, copyright);
        }
        
        // Then any hyperlink...
        if (webSiteAddress != null) {
            panel.add(Box.createRigidArea(spacerSize));
            addLabel(panel, new JHyperlinkButton(webSiteAddress, webSiteAddress, linkFont));
        }
        
        // And finally, for the GTK LAF, buttons...
        if (GuiUtilities.isGtk()) {
            JButton creditsButton = new JButton("Credits");
            GnomeStockIcon.configureButton(creditsButton);
            creditsButton.setEnabled(false);
            creditsButton.setMnemonic(KeyEvent.VK_R);
            
            JButton licenseButton = new JButton("License");
            GnomeStockIcon.configureButton(licenseButton);
            licenseButton.setMnemonic(KeyEvent.VK_L);
            licenseButton.addActionListener(new ShowLicenseActionListener());
            
            JButton closeButton = makeCloseButton(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    dialog.setVisible(false);
                }
            });
            
            ComponentUtilities.tieButtonSizes(creditsButton, licenseButton, closeButton);
            
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
            buttonPanel.add(creditsButton);
            buttonPanel.add(Box.createHorizontalGlue());
            buttonPanel.add(licenseButton);
            buttonPanel.add(Box.createHorizontalGlue());
            buttonPanel.add(closeButton);
            
            panel.add(Box.createRigidArea(spacerSize));
            panel.add(buttonPanel);
        }
        
        dialog.setContentPane(panel);
        
        // Set an appropriate size.
        dialog.pack();
        // Disable the "maximize" button.
        dialog.setMaximumSize(dialog.getPreferredSize());
        dialog.setMinimumSize(dialog.getPreferredSize());
        // Stop resizing.
        dialog.setResizable(false);
        
        // Center on the display.
        // FIXME: use the visual center.
        dialog.setLocationRelativeTo(dialog.getOwner());
    }
    
    private static void addLabel(JPanel panel, Font font, String text) {
        // FIXME: Mac OS actually uses selectable text components which is handy for copying & pasting version information.
        // FIXME: support HTML and automatically install code to change the mouse cursor when hovering over links, and use BrowserLauncher when a link is clicked?
        JLabel label = new JLabel(text);
        label.setFont(font);
        addLabel(panel, label);
    }
    
    private static void addLabel(JPanel panel, JComponent label) {
        label.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        panel.add(label);
    }
    
    private void initMacOs() {
        if (GuiUtilities.isMacOs() == false) {
            return;
        }
        initMacOsAboutMenu();
    }
    
    private void initMacOsAboutMenu() {
        Application.getApplication().addApplicationListener(new ApplicationAdapter() {
            public void handleAbout(ApplicationEvent e) {
                AboutBox.getSharedInstance().show();
                e.setHandled(true);
            }
        });
    }
    
    private void initIcon() {
        if (icon != null) {
            return;
        }
        
        String aboutBoxIconFilename = System.getProperty("com.beatniksoftware.aboutBoxIcon");
        if (aboutBoxIconFilename != null) {
            setImage(aboutBoxIconFilename);
        }
    }
    
    /**
     * "universal.make" arranges to write build information to a file that
     * should be included in any distribution. This method tries to find such
     * a file, and pass it to parseBuildRevisionFile.
     */
    private void findBuildRevisionFile() {
        for (String directory : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File classPathEntry = new File(directory);
            File file = new File(classPathEntry.getParentFile(), ".generated" + File.separator + "build-revision.txt");
            if (file.exists()) {
                parseBuildRevisionFile(file);
                return;
            }
        }
    }
    
    /**
     * Extracts information from the make-generated "build-revision.txt" and
     * turns it into version information for our about box. It's ugly, but it's
     * automated and honest.
     */
    private void parseBuildRevisionFile(File file) {
        String[] content = StringUtilities.readLinesFromFile(file.toString());
        String buildDate = content[0];
        projectRevision = content[1];
        libraryRevision = content[2];
        String[] info = new String[] {
            "Revision " + projectRevision + " (" + libraryRevision + ")",
            "Built " + buildDate,
        };
        for (String line : info) {
            addVersion(line);
            Log.warn(line);
        }
    }
    
    public String getBugReportSubject() {
        String systemDetails = Log.getSystemDetailsForBugReport();
        String subject = applicationName + " bug (" + projectRevision + "/" + libraryRevision + "/" + systemDetails + ")";
        return StringUtilities.urlEncode(subject).replaceAll("\\+", "%20");
    }
    
    private JButton makeCloseButton(ActionListener actionListener) {
        JButton closeButton = new JButton("Close");
        GnomeStockIcon.configureButton(closeButton);
        closeButton.addActionListener(actionListener);
        return closeButton;
    }
    
    private class ShowLicenseActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            final JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
            
            PTextArea textArea = new PTextArea(13, 40);
            textArea.setEditable(false);
            textArea.setText(applicationName + " is free software: you can redistribute it and/or modify\n" +
                "it under the terms of the GNU General Public License as published by\n" +
                "the Free Software Foundation; either version 2 of the License, or\n" +
                "(at your option) any later version.\n" +
                "\n" +
                applicationName + " is distributed in the hope that it will be useful,\n" +
                "but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
                "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\n" +
                "GNU General Public License for more details.\n" +
                "\n"+
                "You should have received a copy of the GNU General Public License\n" +
                "along with " + applicationName + "; If not, see <http://www.gnu.org/licenses/>.\n");
            textArea.setWrapStyleWord(true);
            
            JButton closeButton = makeCloseButton(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    Frame owner = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, panel);
                    owner.setVisible(false);
                }
            });
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 10));
            buttonPanel.add(closeButton);
            
            panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
            panel.add(buttonPanel, BorderLayout.SOUTH);
            JFrameUtilities.makeSimpleWindow("License", panel).setVisible(true);
        }
    }
    
    public static void main(String[] args) {
        GuiUtilities.initLookAndFeel();
        AboutBox aboutBox = AboutBox.getSharedInstance();
        aboutBox.setApplicationName("AboutBox");
        aboutBox.setWebSiteAddress("http://software.jessies.org/");
        aboutBox.addCopyright("Copyright (C) 2006, Elliott Hughes");
        aboutBox.show();
    }
}
