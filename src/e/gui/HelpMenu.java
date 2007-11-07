package e.gui;

import e.util.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * A "Help" menu for a GUI application.
 */
public class HelpMenu {
    public HelpMenu() {
    }
    
    public JMenu makeJMenu() {
        JMenu menu = new JMenu("Help");
        menu.setMnemonic('H');
        
        // We don't support this yet, because we've got nothing to point it to.
        //menu.add(new PlaceholderAction(Log.getApplicationName() + " Help"));
        //menu.addSeparator();
        
        String webSiteAddress = AboutBox.getSharedInstance().getWebSiteAddress();
        if (webSiteAddress != null) {
            menu.add(new WebLinkAction("View " + Log.getApplicationName() + " Change Log", webSiteAddress + "/svn/trunk/ChangeLog"));
            menu.add(new WebLinkAction("View " + Log.getApplicationName() + " FAQ", webSiteAddress + "/FAQ"));
            menu.addSeparator();
        }
        
        menu.add(DebugMenu.makeJMenu());
        // FIXME: anyone else using HelpMenu is going to want some control over this. "bk sendbug" and Safari's "Report Bugs to Apple..." are both good role models.
        menu.add(new WebLinkAction("Report a Bug", "mailto:jujitsu@beatniksoftware.com?subject=" + AboutBox.getSharedInstance().getBugReportSubject()));
        // We don't support this yet, because we've got nothing to point it to.
        menu.add(new PlaceholderAction("View Bugs List"));
        
        if (GuiUtilities.isMacOs() == false) {
            // GNOME and Win32 users expect a link to the application's about box on the help menu.
            menu.addSeparator();
            menu.add(new AboutBoxAction());
        }
        
        return menu;
    }
    
    private class AboutBoxAction extends AbstractAction {
        private AboutBoxAction() {
            String name = "About";
            if (GuiUtilities.isMacOs() == false) {
                name += " " + Log.getApplicationName();
            }
            putValue(NAME, name);
            GnomeStockIcon.useStockIcon(this, "gtk-about");
        }
        
        public boolean isEnabled() {
            return AboutBox.getSharedInstance().isConfigured();
        }
        
        public void actionPerformed(ActionEvent e) {
            AboutBox.getSharedInstance().show();
        }
    }
}
