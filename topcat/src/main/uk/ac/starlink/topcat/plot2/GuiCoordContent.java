package uk.ac.starlink.topcat.plot2;

import java.util.LinkedHashMap;
import java.util.Map;
import uk.ac.starlink.table.ColumnData;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.ttools.plot2.data.Coord;
import uk.ac.starlink.ttools.plot2.data.Input;

/**
 * Aggregates user-supplied information about a coordinate value used
 * as input for a plot.
 * The <code>dataLabels</code> and <code>colDatas</code> arrays both
 * correspond to (and have the same array size as) the
 * {@link uk.ac.starlink.ttools.plot2.data.Coord#getInputs Inputs}
 * arrays for the coord.
 *
 * @see   CoordPanel
 */
public class GuiCoordContent {

    private final Coord coord_;
    private final String[] dataLabels_;
    private final ColumnData[] colDatas_;

    /**
     * Constructor.
     *
     * @param   coord   plot coordinate definition
     * @param  dataLabels   array of strings naming quantities
     *                      for the user variables constituting the coord value;
     *                      these are typically the values typed in by the user
     * @param  colDatas  array of column data arrays supplying values
     *                   for the user variables constituting the coord value
     */
    public GuiCoordContent( Coord coord, String[] dataLabels,
                            ColumnData[] colDatas ) {
        coord_ = coord;
        dataLabels_ = dataLabels;
        colDatas_ = colDatas;
    }

    /**
     * Returns the coordinate definition.
     *
     * @return   coord definition
     */
    public Coord getCoord() {
        return coord_;
    }

    /**
     * Returns the labels describing user input variables.
     *
     * @return   nUserInfo-element array of user variable labels
     */
    public String[] getDataLabels() {
        return dataLabels_;
    }

    /**
     * Returns the column data objects for user input variables.
     *
     * @return   nUserInfo-element array of column data objects
     */
    public ColumnData[] getColDatas() {
        return colDatas_;
    }

    /**
     * Utility method to generate a mapping from user coordinate names
     * to their string specifications, given a set of GuiCoordContents.
     *
     * @param  contents  objects specifying selected coordinate values
     * @return  userInfo name-&gt;coord specifier map conveying
     *          the same information
     * @see   LayerCommand#getInputValues
     */
    public static Map<String,String>
            getInputValues( GuiCoordContent[] contents ) {
        Map<String,String> coordValues = new LinkedHashMap<String,String>();
        for ( int ic = 0; ic < contents.length; ic++ ) {
            GuiCoordContent content = contents[ ic ];
            Input[] inputs = content.getCoord().getInputs();
            String[] dataLabels = content.getDataLabels();
            int nuc = inputs.length;
            assert dataLabels.length == nuc;
            for ( int iuc = 0; iuc < nuc; iuc++ ) {
                coordValues.put( LayerCommand.getInputName( inputs[ iuc ] ),
                                 dataLabels[ iuc ] );
            }
        }
        return coordValues;
    }

    /**
     * Utility method to interrogate a list of GuiCoordContent objects
     * to get a suitable coordinate label (for instance for use as an
     * axis label) for one of the coordinates in a plot.
     * This is not bulletproof because the user coordinate name is not
     * guaranteed unique, but it will probably work as required.
     *
     * @param  userCoordName  user input coordinate name
     * @param  contents  list of GuiCoordContent values associated
     *                   with a plot; null is permitted, and will give
     *                   a null result
     * @return  string that the user will recognise as applying to
     *          <code>userCoordName</code> for plots generated by this control,
     *          or null if no result is found
     * @see uk.ac.starlink.ttools.plot2.data.Coord#getInputs
     */
    public static String getCoordLabel( String userCoordName,
                                        GuiCoordContent[] contents ) {

        /* For each coordinate data item, see if one of the user info
         * names associated with it matches what we're looking for.
         * If so, return the user-entered value (column name or expression),
         * perhaps with a unit string appended. */
        if ( contents != null ) {
            for ( GuiCoordContent content : contents ) {
                Input[] inputs = content.getCoord().getInputs();
                ColumnData[] coldatas = content.getColDatas();
                for ( int ii = 0; ii < inputs.length; ii++ ) {
                    if ( inputs[ ii ].getMeta().getLongName()
                                     .equals( userCoordName ) &&
                         coldatas[ ii ] != null ) {
                        ValueInfo dinfo = coldatas[ ii ].getColumnInfo();
                        String name = dinfo.getName();
                        String unit = dinfo.getUnitString();
                        return unit != null && unit.trim().length() > 0
                             ? name + " / " + unit
                             : name;
                    }
                }
            }
        }

        /* Not found; return null. */
        return null;
    }
}
