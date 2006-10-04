package uk.ac.starlink.ttools.task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import uk.ac.starlink.table.ColumnInfo;
import uk.ac.starlink.table.ColumnStarTable;
import uk.ac.starlink.table.ConcatStarTable;
import uk.ac.starlink.table.ConstantColumn;
import uk.ac.starlink.table.DefaultValueInfo;
import uk.ac.starlink.table.DescribedValue;
import uk.ac.starlink.table.EmptyStarTable;
import uk.ac.starlink.table.JoinStarTable;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.task.BooleanParameter;
import uk.ac.starlink.task.Environment;
import uk.ac.starlink.task.Parameter;
import uk.ac.starlink.task.TaskException;

/**
 * TableMapper which concatenates tables top to bottom.
 *
 * @author   Mark Taylor
 * @since    15 Sep 2006
 */
public class CatMapper implements TableMapper {

    private final Parameter seqParam_;
    private final Parameter locParam_;
    private final Parameter ulocParam_;
    private final BooleanParameter lazyParam_;
    private final boolean hasLazy_;

    private static final ValueInfo SEQ_INFO =
        new DefaultValueInfo( "iseq", Short.class,
                              "Sequence number of input table " +
                              "from concatenation operation" );
    private static final ValueInfo LOC_INFO =
        new DefaultValueInfo( "loc", String.class,
                              "Location of input table " +
                              "from concatenation operation" );
    private static final ValueInfo ULOC_INFO =
        new DefaultValueInfo( "uloc", String.class,
                              "Unique part of input table location " +
                              "from concatenation operation" );
    private static final Logger logger_ =
        Logger.getLogger( "uk.ac.starlink.ttools.task" );
    static {
        ((DefaultValueInfo) SEQ_INFO).setNullable( false );
    }

    /**
     * Constructor.
     *
     * @param  hasLazy  whether this mapper is to make use of a lazy parameter
     */
    public CatMapper( boolean hasLazy ) {
        hasLazy_ = hasLazy;

        seqParam_ = new Parameter( "seqcol" );
        seqParam_.setUsage( "<colname>" );
        seqParam_.setNullPermitted( true );
        seqParam_.setDefault( null );
        seqParam_.setDescription( new String[] {
            "<p>Name of a column to be added to the output table",
            "which will contain the sequence number of the input table",
            "from which each row originated.",
            "This column will contain 1 for the rows from the first",
            "concatenated table, 2 for the second, and so on.",
            "</p>",
        } );

        locParam_ = new Parameter( "loccol" );
        locParam_.setUsage( "<colname>" );
        locParam_.setNullPermitted( true );
        locParam_.setDefault( null );
        locParam_.setDescription( new String[] {
            "<p>Name of a column to be added to the output table",
            "which will contain the location",
            "(as specified in the input parameter(s))",
            "of the input table from which each row originated.",
            "</p>",
        } );

        ulocParam_ = new Parameter( "uloccol" );
        ulocParam_.setUsage( "<colname>" );
        ulocParam_.setNullPermitted( true );
        ulocParam_.setDefault( null );
        ulocParam_.setDescription( new String[] {
            "<p>Name of a column to be added to the output table",
            "which will contain the unique part of the location",
            "(as specified in the input parameter(s))",
            "of the input table from which each row originated.",
            "If not null, parameters will also be added to the output table",
            "giving the pre- and post-fix string common to all the locations.",
            "For example, if the input tables are \"/data/cat_a1.fits\"",
            "and \"/data/cat_b2.fits\" then the output table will contain",
            "a new column &lt;colname&gt; which takes the value",
            "\"a1\" for rows from the first table and",
            "\"b2\" for rows from the second, and new parameters",
            "\"" + prefixParamName( "&lt;colname&gt;" ) + "\" and",
            "\"" + postfixParamName( "&lt;colname&gt;" ) + "\"",
            "with the values \"/data/cat_\" and \".fits\" respectively.",
            "</p>",
        } );

        lazyParam_ = new BooleanParameter( "lazy" );
        lazyParam_.setDefault( "false" );
        lazyParam_.setDescription( new String[] {
            "<p>Whether to perform table resolution lazily.",
            "If true, each table is only accessed when the time comes to",
            "add its rows to the output; if false, then all the tables are",
            "accessed up front.  This is mostly a tuning parameter,",
            "and on the whole it doesn't matter much how it is set,",
            "but for joining an enormous number of tables setting it true",
            "may avoid running out of resources.",
            "</p>",
        } );
    }

    public Parameter[] getParameters() {
        List paramList = new ArrayList();
        paramList.add( seqParam_ );
        paramList.add( locParam_ );
        paramList.add( ulocParam_ );
        if ( hasLazy_ ) {
            paramList.add( lazyParam_ );
        }
        return (Parameter[]) paramList.toArray( new Parameter[ 0 ] );
    }

    public TableMapping createMapping( Environment env ) throws TaskException {
        String seqCol = seqParam_.stringValue( env );
        String locCol = locParam_.stringValue( env );
        String ulocCol = ulocParam_.stringValue( env );
        boolean lazy = hasLazy_ ? lazyParam_.booleanValue( env )
                                : false;
        return new CatMapping( seqCol, locCol, ulocCol, lazy );
    }

    /**
     * Name of a parameter to describe the prefix applied to a given column.
     *
     * @param  colName  column name
     * @return  prefix parameter name
     */
    private static String prefixParamName( String colName ) {
        return colName + "_prefix";
    }

    /**
     * Name of a parameter to describe the postfix applied to a given column.
     *
     * @param  colName  column name
     * @return  postfix parameter name
     */
    private static String postfixParamName( String colName ) {
        return colName + "_postfix";
    }

    /**
     * Mapping which concatenates the tables.
     */
    private static class CatMapping implements TableMapping {

        private final String seqCol_;
        private final String locCol_;
        private final String ulocCol_;
        private final boolean lazy_;

        /**
         * Constructor.
         *
         * @param  seqCol  name of sequence column to be added, or null
         * @param  locCol  name of location column to be added, or null
         * @param  ulocCol name of unique location to be added, or null
         */
        CatMapping( String seqCol, String locCol, String ulocCol,
                    boolean lazy ) {
            seqCol_ = seqCol;
            locCol_ = locCol;
            ulocCol_ = ulocCol;
            lazy_ = lazy;
        }

        public StarTable mapTables( final InputTableSpec[] inSpecs )
                throws IOException, TaskException {
            int nTable = inSpecs.length;

            /* Get a list of the table locations. */
            String[] locations = new String[ nTable ];
            for ( int i = 0; i < nTable; i++ ) {
                locations[ i ] = inSpecs[ i ].getLocation();
            }

            /* Prepare an object which knows about common pre- and post-fixes
             * of locations. */
            final Trimmer trimmer = ulocCol_ == null
                                  ? null
                                  : new Trimmer( locations );

            /* Prepare an array of table producers for the input tables. */
            TableProducer[] tProds = new TableProducer[ nTable ];
            for ( int i = 0; i < nTable; i++ ) {
                final int index = i;
                tProds[ i ] = new TableProducer() {
                    public StarTable getTable()
                            throws IOException, TaskException {
                        return CatMapping.this.getTable( inSpecs[ index ],
                                                         index, trimmer );
                    }
                };
            }

            /* Perform the concatenation on the (possibly doctored) input 
             * tables. */
            StarTable out;
            if ( lazy_ ) {
                StarTable meta = tProds[ 0 ].getTable();
                out = new SeqConcatStarTable( meta, tProds );
            }
            else {
                StarTable[] inTables = new StarTable[ nTable ];
                for ( int i = 0; i < nTable; i++ ) {
                    inTables[ i ] = tProds[ i ].getTable();
                }
                out = new ConcatStarTable( inTables[ 0 ], inTables );
            }

            /* Add parameters describing the unique column name truncation
             * if appropriate. */
            if ( ulocCol_ != null ) {
                String preDesc = "String prepended to " + ulocCol_
                               + " column to form source table location";
                String postDesc = "String appended to " + ulocCol_ +
                                  " column to form source table location";
                ValueInfo preInfo =
                    new DefaultValueInfo( prefixParamName( ulocCol_ ), 
                                          String.class, preDesc );
                ValueInfo postInfo = 
                    new DefaultValueInfo( postfixParamName( ulocCol_ ),
                                          String.class, postDesc );
                String pre = trimmer.getPrefix();
                String post = trimmer.getPostfix();
                List outParams = out.getParameters();
                if ( pre.trim().length() > 0 ) {
                    outParams.add( new DescribedValue( preInfo, pre ) );
                }
                if ( post.trim().length() > 0 ) {
                    outParams.add( new DescribedValue( postInfo, post ) );
                }
            }

            /* Hand the output table on for processing. */
            return out;
        }

        /**
         * Obtains a StarTable from an InputTableSpec.
         *
         * @param  inSpec  table specification
         * @param  index   index of the table into the list of tables
         * @param  trimmer  table location trimmer
         * @return  table described by <code>inSpec</code>
         */
        private StarTable getTable( InputTableSpec inSpec, int index,
                                    Trimmer trimmer ) 
                throws IOException, TaskException {
            final StarTable inTable = inSpec.getWrappedTable();
            ColumnStarTable addTable = new ColumnStarTable( inTable ) {
                public long getRowCount() {
                    return inTable.getRowCount();
                }
            };
            if ( seqCol_ != null ) {
                ColumnInfo seqInfo = new ColumnInfo( SEQ_INFO );
                seqInfo.setName( seqCol_ );
                Short iseq = new Short( (short) ( index + 1 ) );
                addTable.addColumn( new ConstantColumn( seqInfo, iseq ) );
            }
            if ( locCol_ != null ) {
                ColumnInfo locInfo = new ColumnInfo( LOC_INFO );
                locInfo.setName( locCol_ );
                locInfo.setElementSize( trimmer.getLocLength() );
                String loc = inSpec.getLocation();
                addTable.addColumn( new ConstantColumn( locInfo, loc ) );
            }
            if ( ulocCol_ != null ) {
                ColumnInfo ulocInfo = new ColumnInfo( ULOC_INFO );
                ulocInfo.setName( ulocCol_ );
                ulocInfo.setElementSize( trimmer.getTrimmedLocLength() );
                String uloc = trimmer.trim( inSpec.getLocation() );
                addTable.addColumn( new ConstantColumn( ulocInfo, uloc ) );
            }
            return addTable.getColumnCount() > 0
                 ? new JoinStarTable( new StarTable[] { inTable, addTable } )
                 : inTable;
        }
    }

    /**
     * Utility class which identifies common pre- and post-fixes of a 
     * set of strings.
     */
    private static class Trimmer {

        private final String pre_;
        private final String post_;
        private final int locLeng_;
        private final int ulocLeng_;

        /**
         * Constructor.
         *
         * @param   locs  array of strings with the same pre- and post-fixes
         */
        public Trimmer( String[] locs ) {

            /* Find minimum common length. */
            int nloc = locs.length;
            String loc0 = locs[ 0 ];
            int leng = loc0.length();
            for ( int iloc = 0; iloc < nloc; iloc++ ) {
                leng = Math.min( leng, locs[ iloc ].length() );
            }

            /* Find length of maximum common prefix string. */
            int npre = -1;
            for ( int ic = 0; ic < leng && npre < 0; ic++ ) {
                char c = loc0.charAt( ic );
                for ( int iloc = 0; iloc < nloc; iloc++ ) {
                    if ( locs[ iloc ].charAt( ic ) != c ) {
                        npre = ic;
                    }
                }
            }
            pre_ = npre >= 0 ? loc0.substring( 0, npre )
                             : "";

            /* Find length of maximum common postfix string. */
            int npost = -1;
            for ( int ic = 0; ic < leng && npost < 0; ic++ ) {
                char c = loc0.charAt( loc0.length() - 1 - ic );
                for ( int iloc = 0; iloc < nloc; iloc++ ) {
                    if ( locs[ iloc ].charAt( locs[ iloc ].length() - 1 - ic )
                         != c ) {
                        npost = ic;
                    }
                }
            }
            post_ = npost >= 0 ? loc0.substring( loc0.length() - npost )
                               : "";

            /* Store max loc and uloc lengths. */
            int locLeng = 0;
            int ulocLeng = 0;
            for ( int i = 0; i < nloc; i++ ) {
                String loc = locs[ i ];
                locLeng = Math.max( locLeng, loc.length() );
                ulocLeng = Math.max( ulocLeng, trim( loc ).length() );
            }
            locLeng_ = locLeng;
            ulocLeng_ = ulocLeng;
        }

        /**
         * Returns the common prefix.
         *
         * @return  prefix
         */
        public String getPrefix() {
            return pre_;
        }

        /**
         * Returns the common postfix.
         *
         * @return  postfix
         */
        public String getPostfix() {
            return post_;
        }

        /**
         * Returns the maximum length of any of the locations this trimmer
         * knows about.
         *
         * @param  maximum loc length
         */
        public int getLocLength() {
            return locLeng_;
        }

        /**
         * Returns the maximum length of the trimmed version of any of the
         * locations this trimmer knows about.
         *
         * @return maximum trimmed loc length
         */
        public int getTrimmedLocLength() {
            return ulocLeng_;
        }

        /**
         * Returns a string trimmed of the common pre- and post-fix.
         * Any of the strings submitted to the constructor can be 
         * thus processed without error.
         *
         * @param   loc  input string
         * @return  trimmed string
         * @throws  IllegalArgumentException if loc doesn't have the right form
         */
        public String trim( String loc ) {
            if ( loc.startsWith( pre_ ) && loc.endsWith( post_ ) ) {
                return loc.substring( pre_.length(),
                                      loc.length() - post_.length() );
            }
            else {
                throw new IllegalArgumentException();
            }
        }
    }
}
