package uk.ac.starlink.table;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * StarTable which wraps another StarTable.  This class acts as a wrapper
 * around an existing 'base' StarTable object; almost all its methods are
 * implemented by forwarding to the corresponding methods of that base
 * table.  The exception is the {@link #getURL} method which returns
 * <tt>null</tt> as an indication that the actual table is not a persistent
 * one (though it may be based on, and even identical to, a persistent one).
 * <p>
 * This class is provided so that it can be extended by
 * subclasses which modify the view of the base table in useful ways.
 *
 * @author   Mark Taylor (Starlink)
 * @see      WrapperRowSequence
 */
public class WrapperStarTable implements StarTable {

    protected StarTable baseTable;

    /**
     * Constructs a new <tt>WrapperStarTable</tt> from a given base table.
     *
     * @param  baseTable  the table to which methods invoked upon the
     *         new wrapper table will be forwarded
     */
    public WrapperStarTable( StarTable baseTable ) {
        this.baseTable = baseTable;
    }

    /**
     * Returns the base table underlying this wrapper table.
     *
     * @return  the table to which methods invoked upon this 
     *          wrappter table are forwarded
     */
    public StarTable getBaseTable() {
        return baseTable;
    }

    public int getColumnCount() {
        return baseTable.getColumnCount();
    }

    public long getRowCount() {
        return baseTable.getRowCount();
    }

    /**
     * Returns <tt>null</tt> to indicate that this table itself is not
     * persistent.
     */
    public URL getURL() {
        return null;
    }

    public String getName() {
        return baseTable.getName();
    }

    public List getParameters() {
        return baseTable.getParameters();
    }

    public DescribedValue getParameterByName( String parname ) {
        return baseTable.getParameterByName( parname );
    }
    
    public ColumnInfo getColumnInfo( int icol ) {
        return baseTable.getColumnInfo( icol );
    }

    public List getColumnAuxDataInfos() {
        return baseTable.getColumnAuxDataInfos();
    }

    public RowSequence getRowSequence() throws IOException {
        return baseTable.getRowSequence();
    }

    public boolean isRandom() {
        return baseTable.isRandom();
    }

    public Object getCell( long irow, int icol ) throws IOException {
        return baseTable.getCell( irow, icol );
    }

    public Object[] getRow( long irow ) throws IOException {
        return baseTable.getRow( irow );
    }

    /**
     * Convenience method to get an <tt>int</tt> value from a <tt>long</tt>.
     * If the supplied long integer <tt>lval</tt> is out of the range
     * which can be represented in an <tt>int</tt>, then unlike a
     * typecast, this method will throw an <tt>IllegalArgumentException</tt>.
     *
     * @param  the <tt>long</tt> value to convert
     * @return an <tt>int</tt> value which has the same value as <tt>lval</tt>
     * @throws IllegalArgumentException  if the conversion cannot be done
     */
    public static int checkedLongToInt( long lval ) {
        return AbstractStarTable.checkedLongToInt( lval );
    }

}
