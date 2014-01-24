package uk.ac.starlink.ttools.plot2.data;

import uk.ac.starlink.ttools.plot2.DataGeom;
import uk.ac.starlink.util.IntList;

/**
 * Expresses the content of a set of coordinates used for a plot layer,
 * and how to find the values of these coordinates from a corresponding
 * DataSpec.  A given CoordGroup instance is tied to a particular
 * arrangement of corresponding DataSpec objects.
 *
 * <p>This abstraction is defined in a somewhat ad hoc way at present;
 * features have been introduced according to what is required from
 * existing plotters.  It may be changed or rationalised in the future.
 * That is one reason this functionality is split out into its own class
 * rather than being part of the Plotter interface itself, and also why
 * implementation of this class is controlled (instances only available
 * from factory methods of this class).
 *
 * @author   Mark Taylor
 * @since    20 Jan 2014
 */
public abstract class CoordGroup {

    /**
     * Private constructor prevents subclassing.
     * This guarantees that the only instances are those dispensed by
     * factory methods of this class, making it safer to redefine the
     * contract in future if required.
     */
    private CoordGroup() {
    }

    /**
     * Returns the number of data positions per tuple used by this plotter.
     * For instance
     * a scatter plot would use 1,
     * a plot linking pairs of positions in the same table would use 2,
     * and an analytic function would use 0.
     * Each of these is turned into a data space position by use of the
     * DataGeom presented at layer creation time.
     * A position corresponds to a (fixed) number of coordinate values.
     *
     * @return   number of sets of positional coordinates
     */
    public abstract int getPositionCount();

    /*
     * Returns any coordinates used by this plotter additional to the
     * positional ones.  In fact there is nothing to stop you using
     * positional coordinates here,
     * but they will not be treated in the generic way accorded to
     * those accounted for by {@link #getPositionCount}.
     *
     * @return  non-positional coordinates
     */
    public abstract Coord[] getExtraCoords();

    /**
     * Returns the starting coordinate index in a DataSpec at which
     * a given one of the positional coordinates represented
     * by this coord group will appear.
     *
     * @param  ipos  index of position supplied by this group
     *               (first position is zero)
     * @param  geom  data geom with which index will be used
     * @return  index of starting coordinate for given position in dataspec
     */
    public abstract int getPosCoordIndex( int ipos, DataGeom geom );

    /**
     * Returns the coordinate index in a DataSpec at which
     * a given one of the non-positional coordinates represented
     * by this coord group will appear.
     *
     * @param  iExtra  index of non-positional coordinate
     *                 (first extra coord is zero)
     * @param  geom  data geom with which index will be used
     * @return  index of given extra coordinate in dataspec
     */
    public abstract int getExtraCoordIndex( int iExtra, DataGeom geom );

    /**
     * Returns a list of the coordinate indices in a DataSpec of
     * those coordinates whose change should trigger a re-range of
     * the plot surface.
     *
     * @param  geom  data geom with which indices will be used
     * @return   array of indices into DataSpec coordinates
     */
    public abstract int[] getRangeCoordIndices( DataGeom geom );

    /**
     * Indicates whether this group deals with "partial" positions.
     * That is to say that the coordinates represent data positions,
     * but that those data position arrays have at least one element
     * equal to NaN, indicating for instance a line rather than a
     * point in the data space.
     *
     * @return  true iff this group represents a single partial position
     */
    public abstract boolean isSinglePartialPosition();

    /**
     * Returns a coord group which contains only a single data space position.
     *
     * @return  new coord group
     */
    public static CoordGroup createSinglePositionCoordGroup() {
        return new BasicCoordGroup( 1, new Coord[ 0 ] );
    }

    /**
     * Returns a coord group which contains zero or more positions and
     * zero or more additional ("extra") coordinates.
     *
     * @param  npos  number of positions
     * @param  extras  non-positional coordinates
     * @return  new coord group
     */
    public static CoordGroup createCoordGroup( int npos, Coord[] extras ) {
        return new BasicCoordGroup( npos, extras );
    }

    /**
     * Returns a coord group which contains a single partial position.
     *
     * @param   coords   all coordinates, starting with those constituting
     *                   the partial position
     * @param  rangeCoordFlags  array of flags corresponding to the
     *         <code>coords</code> array, true for any coord whose change
     *         should cause a re-range
     * @return  new coord group
     */
    public static CoordGroup createPartialCoordGroup(
                                 Coord[] coords, boolean[] rangeCoordFlags ) {
        return new PartialPosCoordGroup( coords, rangeCoordFlags );
    }

    /**
     * Returns a coord group with no coordinates.
     *
     * @return  new coord group
     */
    public static CoordGroup createEmptyCoordGroup() {
        return new BasicCoordGroup( 0, new Coord[ 0 ] );
    }

    /**
     * Returns the number of coordinates used to store a single point position,
     * for a given DataGeom.
     *
     * @param  geom   data geom
     * @return    geom.getPosCoords().length;
     */
    private static int getPosCoordCount( DataGeom geom ) {
        return geom.getPosCoords().length;
    }

    /**
     * CoordGroup implementation with positional and extra coordinates.
     */
    private static class BasicCoordGroup extends CoordGroup {
        final int npos_;
        final Coord[] extraCoords_;

        /**
         * Constructor.
         *
         * @param  npos  number of data positions
         * @param  extraCoords  non-positional coordinates
         */
        BasicCoordGroup( int npos, Coord[] extraCoords ) {
            npos_ = npos;
            extraCoords_ = extraCoords;
        }
        public int getPositionCount() {
            return npos_;
        }
        public Coord[] getExtraCoords() {
            return extraCoords_;
        }
        public int getPosCoordIndex( int ipos, DataGeom geom ) {
            return getPosCoordCount( geom ) * ipos;
        }
        public int getExtraCoordIndex( int iExtra, DataGeom geom ) {
            return getPosCoordCount( geom ) * npos_ + iExtra;
        }
        public int[] getRangeCoordIndices( DataGeom geom ) {
            int[] ixs = new int[ getPosCoordCount( geom ) * npos_ ];
            for ( int i = 0; i < ixs.length; i++ ) {
                ixs[ i ] = i;
            }
            return ixs;
        }
        public boolean isSinglePartialPosition() {
            return false;
        }
    }

    /**
     * CoordGroup implementation representing a single partial position.
     */
    private static class PartialPosCoordGroup extends CoordGroup {
        final Coord[] coords_;
        final int[] rangeCoordIndices_;

        /**
         * Constructor.
         *
         * @param   coords   all coordinates, starting with those constituting
         *                   the partial position
         * @param  rangeCoordFlags  array of flags corresponding to the
         *         <code>coords</code> array, true for any coord whose change
         *         should cause a re-range
         */
        PartialPosCoordGroup( Coord[] coords, boolean[] rangeCoordFlags ) {
            coords_ = coords;
            IntList ilist = new IntList();
            for ( int i = 0; i < coords.length; i++ ) {
                if ( rangeCoordFlags[ i ] ) {
                    ilist.add( i );
                }
            }
            rangeCoordIndices_ = ilist.toIntArray();
        }
        public int getPositionCount() {
            return 0;
        }
        public Coord[] getExtraCoords() {
            return coords_;
        }
        public int getPosCoordIndex( int ipos, DataGeom geom ) {
            return -1;
        }
        public int getExtraCoordIndex( int iExtra, DataGeom geom ) {
            return iExtra;
        }
        public int[] getRangeCoordIndices( DataGeom geom ) {
            return rangeCoordIndices_;
        }
        public boolean isSinglePartialPosition() {
            return true;
        }
    }
}
