package e.tools;

import e.gui.*;
import e.util.*;
import java.awt.*;
import java.io.*;
import javax.swing.*;
import javax.swing.table.*;

/**
 * A stand-alone program to render a table in a window. This is sometimes
 * a convenient thing to have, and something no other tool I know of really
 * helps with.
 */
public class TableViewer extends MainFrame {
    private JTable table;
    
    public TableViewer(String filename) throws IOException {
        super("Table Viewer");
        Log.setApplicationName("TableViewer");
        initTable(filename);
        getContentPane().add(new JScrollPane(table));
        setSize(new Dimension(640, 480));
    }
    
    public void initTable(String filename) throws IOException {
        DefaultTableModel model = new DefaultTableModel();
        InputStream inputStream = System.in;
        if (filename.equals("-") == false) {
            inputStream = new FileInputStream(FileUtilities.fileFromString(filename));
        }
        LineNumberReader in = new LineNumberReader(new InputStreamReader(inputStream, "UTF-8"));
        String line;
        while ((line = in.readLine()) != null) {
            String[] fields = line.split("\t");
            if (fields.length > model.getColumnCount()) {
                model.setColumnCount(fields.length);
            }
            model.addRow(fields);
        }
        Log.warn("rows:"+model.getRowCount()+ ", columns:"+model.getColumnCount());
        table = new ETable();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(model);
        for (int i = 0; i < model.getColumnCount(); i++) {
            packColumn(i);
        }
    }

    public void packColumn(int columnIndex) {
        DefaultTableColumnModel columnModel = (DefaultTableColumnModel) table.getColumnModel();
        TableColumn column = columnModel.getColumn(columnIndex);

        // Get width of column header.
        TableCellRenderer renderer = column.getHeaderRenderer();
        if (renderer == null) {
            renderer = table.getTableHeader().getDefaultRenderer();
        }
        Component component = renderer.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, 0, 0);
        int width = component.getPreferredSize().width;
    
        // Get maximum width of column data.
        for (int row = 0; row < table.getRowCount(); row++) {
            renderer = table.getCellRenderer(row, columnIndex);
            component = renderer.getTableCellRendererComponent(table, table.getValueAt(row, columnIndex), false, false, row, columnIndex);
            width = Math.max(width, component.getPreferredSize().width);
        }
    
        // Set the width
        column.setPreferredWidth(width + 4);
    }
    
    public static void main(String[] arguments) throws Exception {
        GuiUtilities.initLookAndFeel();
        for (String argument : arguments) {
            new TableViewer(argument).setVisible(true);
        }
    }
}
