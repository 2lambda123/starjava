package uk.ac.starlink.table.gui;

import java.awt.Dimension;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * Provides a component suitable for use as a rowHeader component in
 * the same <tt>JScrollPane</tt> as is being used to house a 
 * <tt>JTable</tt>.  It displays the row indices starting at 1 and increasing.
 * If you want some other number to be displayed, override the
 * {@link #rowNumber} method.
 * <p>
 * You would normally use this class as follows:
 * <pre>
 *     JTable jtab = ...
 *     JScrollPane scrollpane = new JScrollPane( jtab );
 *     scrollpane.setRowHeaderView( new TableRowHeader( jtab ) );
 * </pre>
 * This header will register itself as a listener on the master table's
 * model so that it can respond to changes.
 * In the event that the master JTable's model changes during the lifetime of
 * this header table, then {@link #modelChanged} should be called.
 * At construction time the master table's selection model will be 
 * installed in this header too, so that you can make row selections by
 * clicking on the header or the master.
 *
 * @author   Mark Taylor (Starlink)
 * @see      javax.swing.JScrollPane
 */
public class TableRowHeader extends JTable {

    private JTable masterTable;
    private AbstractTableModel rowModel;
    private TableModel masterModel;
    private TableModelListener listener;

    /**
     * Construct a new TableRowHeader.
     */
    public TableRowHeader( JTable table ) {
        this.masterTable = table;
        this.masterModel = masterTable.getModel();

        /* Set the model. */
        rowModel = new AbstractTableModel() {
            public int getRowCount() {
                return masterTable.getRowCount();
            }
            public int getColumnCount() {
                return 1;
            }
            public Object getValueAt( int irow, int icol ) {
                return new Integer( rowNumber( irow ) ) + "  ";
            }
        };
        setModel( rowModel );

        /* Use the same selection model as the master table, so that you
         * can select rows by clicking on the row header or the master. */
        setSelectionModel( masterTable.getSelectionModel() );

        /* Set up a listener on the master table which will trigger change
         * events on this table when the master model changes. */
        listener = new TableModelListener() {
            public void tableChanged( TableModelEvent evt ) {
                TableModel mmodel = masterTable.getModel();
                if ( mmodel != masterModel ) {
                    masterModel.removeTableModelListener( this );
                    masterModel = mmodel;
                    masterModel.addTableModelListener( this );
                }
                rowModel.fireTableDataChanged();
            }
        };
        masterModel.addTableModelListener( listener );

        /* Configure to be uninteresting as a JTable. */
        setTableHeader( null );
        setAutoResizeMode( AUTO_RESIZE_OFF );
        setPreferredScrollableViewportSize( masterTable.getPreferredSize() );
        setColumnSelectionAllowed( false );
        setRowSelectionAllowed( false );

        /* Create a suitable renderer. */
        DefaultTableCellRenderer rend = 
            (DefaultTableCellRenderer) new JTableHeader().getDefaultRenderer();
        rend.setFont( UIManager.getFont( "TableHeader.font" ) );
        rend.setBackground( UIManager.getColor( "TableHeader.background" ) );
        rend.setForeground( UIManager.getColor( "TableHeader.foreground" ) );
        rend.setHorizontalAlignment( SwingConstants.RIGHT );

        /* Set up the sole column. */
        TableColumn col = new TableColumn( 0, 64, rend, null ) {
            public int getPreferredWidth() {
                JTable tab = TableRowHeader.this;
                int nrow = masterTable.getRowCount();
                if ( nrow > 0 ) {
                    int first = StarJTable.getCellWidth( tab, 0, 0 );
                    int last = StarJTable.getCellWidth( tab, nrow - 1, 0);
                    int guess = tab.getCellRenderer( 0, 0 )
                                   .getTableCellRendererComponent( tab, 
                                        new Integer( nrow + 1 ) + "  ",
                                        false, false, 0, 0 )
                                   .getPreferredSize().width;
                    return 8 + Math.max( Math.max( first, last ), guess );
                }
                else {
                    return 64; // no rows - make something up
                }
            }
        };

        /* Configure it into the column model. */
        TableColumnModel tcm = new DefaultTableColumnModel();
        tcm.addColumn( col );
        setColumnModel( tcm );
    }

    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    /**
     * This method should be called to notify this header that the 
     * master table's TableModel has been changed.
     */
    public void modelChanged() {
        listener.tableChanged( null );
    }

    /**
     * Determines the numeric index to be displayed for a given row 
     * number into the table.  The default implementation returns
     * <tt>irow+1</tt> so that the first row is labelled 1, the second
     * one 2 etc, but this method may be overridden for more specialised
     * behaviour.
     * 
     * @param  irow  the row index of the displayed row (starts at zero)
     * @return  the number of the row it should be labelled
     */
    public int rowNumber( int irow ) {
        return irow + 1;
    }

}
