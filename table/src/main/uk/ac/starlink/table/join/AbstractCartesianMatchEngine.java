package uk.ac.starlink.table.join;

import java.util.HashSet;
import java.util.Set;
import uk.ac.starlink.table.DefaultValueInfo;
import uk.ac.starlink.table.DescribedValue;
import uk.ac.starlink.table.ValueInfo;

/**
 * A matching engine which can match points in an 
 * <tt>ndim</tt>-dimensional space.
 * All tuples (coordinate vectors) submitted to it must be 
 * <code>ndim</code>-element arrays of {@link java.lang.Number} objects.
 * Tuples are considered matching if they fall within an ellipsoid
 * defined by a scalar or vector error parameter.
 *
 * <p>This abstract class defines the mechanics of the matching,
 * but not the match parameters, which will presumably be to do 
 * with error radii.
 *
 * @author   Mark Taylor (Starlink)
 */
public abstract class AbstractCartesianMatchEngine implements MatchEngine {

    private final int ndim_;
    private final int blockSize_;
    private final double[] errors_;
    private final double[] err2rs_;
    private final double[] rBinSizes_;
    private final DescribedValue binFactorParam_;
    private boolean normaliseScores_;
    private double binFactor_;

    /**
     * Factor which determines bin size to use,
     * as a multiple of the maximum error distance, if no
     * bin factor is set explicitly.
     * This is a tuning parameter (any value will give correct results,
     * but performance may be affected).
     * The current value may not be optimal.
     */
    static final double DEFAULT_BIN_FACTOR = 8;

    /**
     * Constructs a matcher which matches points in an
     * <tt>ndim</tt>-dimensional Cartesian space.
     * The error array (error ellipsoid dimensions) is not initialised to
     * anything sensible by this constructor.
     *
     * @param   ndim  dimensionality of the space
     * @param   normaliseScores  <tt>true</tt> iff you want match scores 
     *                           to be normalised
     */
    protected AbstractCartesianMatchEngine( int ndim, 
                                            boolean normaliseScores ) {
        ndim_ = ndim;
        blockSize_ = (int) Math.pow( 3, ndim );
        errors_ = new double[ ndim ];
        err2rs_ = new double[ ndim ];
        rBinSizes_ = new double[ ndim ];
        binFactorParam_ = new BinFactorParameter();
        setNormaliseScores( normaliseScores );
        setBinFactor( DEFAULT_BIN_FACTOR );
    }

    /**
     * Returns the number of dimensions of this matcher.
     *
     * @return  dimensionality of Cartesian space
     */
    public int getDimensions() {
        return ndim_;
    }

    /**
     * Matches two tuples if they represent the coordinates of nearby points.
     * If they match (fall within the same error ellipsoid) the return
     * value is a non-negative value giving the distance between them.
     * According to the value of the <tt>normaliseScores</tt> flag,
     * this is either the actual distance between the points (Pythagoras)
     * or the same thing normalised to the range between 0 (same position) 
     * and 1 (on the boundary of the error ellipsoid).
     * If they don't match, -1 is returned.
     *
     * @param  tuple1  <tt>ndim</tt>-element array of <tt>Number</tt> objects
     *                 representing coordinates of first object
     * @param  tuple2  <tt>ndim</tt>-element array of <tt>Number</tt> objects
     *                 representing coordinates of second object
     * @return  the separation of the points represented by <tt>tuple1</tt>
     *          and <tt>tuple2</tt> if they match, or -1 if they don't
     */
    public double matchScore( Object[] tuple1, Object[] tuple2 ) {

        /* If any of the coordinates is too far away, reject it straight away.
         * This is a cheap test which will normally reject most requests. */
        for ( int i = 0; i < ndim_; i++ ) {
            if ( Math.abs( ((Number) tuple1[ i ]).doubleValue() - 
                           ((Number) tuple2[ i ]).doubleValue() ) 
                 > errors_[ i ] ) {
                return -1.0;
            }
        }

        /* We are in the right ball park - do an accurate calculation. */
        double spaceDist2 = 0.0; 
        double normDist2 = 0.0;
        for ( int i = 0; i < ndim_; i++ ) {
            double d = ((Number) tuple1[ i ]).doubleValue() - 
                       ((Number) tuple2[ i ]).doubleValue();
            double d2 = d * d;
            spaceDist2 += d2;
            normDist2 += d2 * err2rs_[ i ];
        }
        if ( normDist2 <= 1.0 ) {
            return normaliseScores_ ? Math.sqrt( normDist2 ) 
                                    : Math.sqrt( spaceDist2 );
        }
        else {
            return -1.0;
        }
    }

    public ValueInfo getMatchScoreInfo() {
        String descrip = getNormaliseScores()
            ? "Normalised distance between matched points" +
              "(0 is identical position, 1 is worst permissible match)"
            : "Spatial distance between matched points";
        DefaultValueInfo scoreInfo = 
            new DefaultValueInfo( "Separation", Double.class, descrip );
        scoreInfo.setUCD( "pos.distance" );
        return scoreInfo;
    }

    /**
     * Returns a set of Cell objects representing the cell in which 
     * this tuple falls and some or all of its neighbouring ones.
     *
     * @param  tuple  <tt>ndim</tt>-element array of <tt>Number</tt> objects
     *                representing coordinates of an object
     */
    public Object[] getBins( Object[] tuple ) {
        double[] coords = new double[ ndim_ ];
        for ( int i = 0; i < ndim_; i++ ) {
            if ( tuple[ i ] instanceof Number ) {
                coords[ i ] = ((Number) tuple[ i ]).doubleValue();
            }
            else {
                return NO_BINS;
            }
        }
        return getCellBlock( coords );
    }

    /**
     * Returns an array of tuple infos, one for each Cartesian dimension.
     */
    public ValueInfo[] getTupleInfos() {
        ValueInfo[] infos = new ValueInfo[ ndim_ ];
        for ( int i = 0; i < ndim_; i++ ) {
            infos[ i ] = createCoordinateInfo( ndim_, i );
        }
        return infos;
    }

    public boolean canBoundMatch() {
        return true;
    }

    public Comparable[][] getMatchBounds( Comparable[] minIn, 
                                          Comparable[] maxIn ) {
        Comparable[] minOut = new Comparable[ ndim_ ];
        Comparable[] maxOut = new Comparable[ ndim_ ];
        for ( int i = 0; i < ndim_; i++ ) {
            double err = getError( i );

            /* The output minimum for each dimension is the input minimum
             * minus the error in that dimension.  Calculate it and
             * set the result as the same kind of object (or null). */
            if ( minIn[ i ] instanceof Number ) {
                minOut[ i ] = add( (Number) minIn[ i ], -err );
            }

            /* Do the same for the output maximum, this time adding the
             * error. */
            if ( maxIn[ i ] instanceof Number ) {
                maxOut[ i ] = add( (Number) maxIn[ i ], +err );
            }
        }

        /* Return the doctored result. */
        return new Comparable[][] { minOut, maxOut };
    }

    public abstract DescribedValue[] getMatchParameters();

    public DescribedValue[] getTuningParameters() {
        return new DescribedValue[] { binFactorParam_ };
    }

    /**
     * Returns the matching error along a given axis.
     * This is the principal radius of an ellipsoid within which two points
     * must fall in order to match.
     *
     * @return  error array
     */
    protected double getError( int idim ) {
        return errors_[ idim ];
    }

    /**
     * Sets one of the principal radii of the ellipsoid within which 
     * two points have to fall in order to match.
     *
     * @param  idim  index of axis
     * @param  error  error along axis <tt>idim</tt>
     */
    public void setError( int idim, double error ) {
        errors_[ idim ] = error;
        err2rs_[ idim ] = error == 0.0 ? Double.MAX_VALUE
                                       : 1.0 / ( error * error );
        configureScale( idim );
    }

    /**
     * Returns the grid scaling factor.
     *
     * @return   grid scaling factor
     */
    public double getBinFactor() {
        return binFactor_;
    }

    /**
     * Sets the grid scaling factor which determines the size of a grid cell
     * as a multiple of the size of the matching error in each dimension.
     * It can be used as a tuning parameter.  It must be >= 1.
     *
     * @param   binFactor   new bin scaling factor
     * @throws  IllegalArgumentException  if out of range
     */
    public void setBinFactor( double binFactor ) {
        if ( ! ( binFactor >= 1.0 ) ) {
            throw new IllegalArgumentException( "Bin factor " + binFactor
                                              + " must be >= 1" );
        }
        binFactor_ = binFactor;
        for ( int idim = 0; idim < ndim_; idim++ ) {
            configureScale( idim );
        }
    }

    /**
     * Updates internal state for the current values of error and 
     * scaling factor in a given dimension.
     *
     * @param  idim  dimension index
     */
    private void configureScale( int idim ) {
        assert binFactor_ >= 1.0;
        rBinSizes_[ idim ] = 1.0 / ( binFactor_ * errors_[ idim ] );
    }

    /**
     * Determines whether the results of the {@link #matchScore} method
     * will be normalised or not.  
     * If <tt>norm</tt> is true, 
     * successful matches always result in a score between 0 and 1; 
     * if it's false, 
     * the score is the distance in the space defined by the supplied tuples.
     *
     * <p>If your errors are significantly anisotropic 
     * and/or your coordinates do not represent a physical space, 
     * you probably want to set this false.
     *
     * @param  norm  <tt>true</tt> iff you want match scores to be normalised
     */
    public void setNormaliseScores( boolean norm ) {
        normaliseScores_ = norm;
    }

    /**
     * Indicates whether the results of the {@link #matchScore} method
     * will be normalised.
     *
     * @return   <tt>true</tt> iff match scores will be normalised
     */
    public boolean getNormaliseScores() {
        return normaliseScores_;
    }

    public abstract String toString();

    /**
     * Returns the cell label corresponding to the given coordinate set.
     *
     * @param  coords  ndim-dimensional array of coordinate values
     * @return  ndim-dimensional array of cell label indices
     */
    private int[] getBaseLabel( double[] coords ) {
        int[] label = new int[ ndim_ ];
        for ( int i = 0; i < ndim_; i++ ) {
            label[ i ] = (int) Math.floor( coords[ i ] * rBinSizes_[ i ] );
        }
        return label;
    }

    /**
     * Returns an array of Cell objects corresponding to the cell in which
     * <tt>coords</tt> falls and all its nearest neighbours.
     *
     * @param  coords  coordinates of reference points
     * @return  <tt>3^ndim</tt>-element array of Cells surrounding 
     *          <tt>coords</tt>
     */
    private Cell[] getCellBlock( double[] coords ) {

        /* Iterate over the 3^ndim points which are the given point and
         * all the points separated from it by err[i] in any direction i,
         * and accumulate a set of the cells in which each such point lies.
         * Any point which is near the given one must lie in one of those 
         * cells. */
        Set cells = new HashSet();
        int[] offset = new int[ ndim_ ];
        double[] pos = new double[ ndim_ ];
        for ( int icell = 0; icell < blockSize_; icell++ ) {

            /* Get the position of the next point. */
            for ( int i = 0; i < ndim_; i++ ) {
                pos[ i ] = coords[ i ] + ( offset[ i ] - 1 ) * errors_[ i ];
            }

            /* Ensure that the grid cell in which that point lies is 
             * in the accumulated set. */
            Cell cell = new Cell( getBaseLabel( pos ) );
            cells.add( cell );

            /* Bump the n-dimensional offset to the next point. */
            for ( int j = 0; j < ndim_; j++ ) {
                if ( ++offset[ j ] < 3 ) {
                    break;
                }
                else {
                    offset[ j ] = 0;
                }
            }
        }

        /* Sanity check. */
        for ( int i = 0; i < ndim_; i++ ) {
            assert offset[ i ] == 0;
        }

        /* Returns the set of cells as an array. */
        return (Cell[]) cells.toArray( new Cell[ cells.size() ] );
    }

    /**
     * Returns a description of the tuple element containing one of
     * the Cartesian coordinates.
     *
     * @param  ndim  total number of Cartesian coordinates
     * @param  idim  index of the coordinate in question
     * @return  metadata for coordinate <tt>idim</tt>
     */
    static ValueInfo createCoordinateInfo( int ndim, int idim ) {
        DefaultValueInfo info =
            new DefaultValueInfo( getCoordinateName( ndim, idim ), Number.class,
                                  getCoordinateDescription( ndim, idim ) );
        info.setNullable( false );
        return info;
    }

    /**
     * Returns the name of one of the coordinates.
     *
     * @param  ndim  total number of Cartesian coordinates
     * @param  idim  index of coordinate
     * @return  name to use for coordinate <tt>idim</tt>
     */
    static String getCoordinateName( int ndim, int idim ) {
        if ( idim >= ndim ) {
            throw new IllegalArgumentException();
        }
        return ndim <= 3 ? new String[] { "X", "Y", "Z" }[ idim ]
                         : ( "Co-ord #" + ( idim + 1 ) );
    }

    /**
     * Returns the description of one of the coordinates.
     *
     * @param  ndim  total number of Cartesian coordinates
     * @param  idim  index of coordinate
     * @return  description to use for coordinate <tt>idim</tt>
     */
    static String getCoordinateDescription( int ndim, int idim ) {
        if ( idim >= ndim ) {
            throw new IllegalArgumentException();
        }
        return "Cartesian co-ordinate #" + ( idim + 1 );
    }

    /**
     * Adds a numeric value to a Number object, and returns an object of
     * the same type having the new value.
     * If the addition can't be done, null is returned.
     * The signature looks strange, because Number doesn't implement
     * Comparable, though all the Number subclasses that this method
     * can cope with do.
     *
     * @param   in   input number object
     * @param   inc  value to increment input number by
     * @return  object of same type as <code>in</code> and the incremented value
     */
    static Comparable add( Number in, double inc ) {
        if ( in == null || Double.isNaN( inc ) ) {
            return null;
        }
        double dval = in.doubleValue() + inc;
        Class clazz = in.getClass();
        if ( inc < 0 ) {
            if ( clazz == Byte.class &&
                 Math.floor( dval ) >= Byte.MIN_VALUE ) {
                return new Byte( (byte) Math.floor( dval ) );
            }
            else if ( clazz == Short.class &&
                      Math.floor( dval ) >= Short.MIN_VALUE ) {
                return new Short( (short) Math.floor( dval ) );
            }
            else if ( clazz == Integer.class &&
                      Math.floor( dval ) >= Integer.MIN_VALUE ) {
                return new Integer( (int) Math.floor( dval ) );
            }
            else if ( clazz == Long.class &&
                      Math.floor( dval ) >= Long.MIN_VALUE ) {
                return new Long( (long) Math.floor( dval ) );
            }
            else if ( clazz == Float.class ) {
                return new Float( (float) dval );
            }
            else if ( clazz == Double.class ) {
                return new Double( dval );
            }
            else {
                return null;
            }
        }
        else if ( inc > 0 ) {
            if ( clazz == Byte.class &&
                 Math.ceil( dval ) <= Byte.MAX_VALUE ) {
                return new Byte( (byte) Math.ceil( dval ) );
            }
            else if ( clazz == Short.class &&
                      Math.ceil( dval ) <= Short.MAX_VALUE ) {
                return new Short( (short) Math.ceil( dval ) );
            }
            else if ( clazz == Integer.class &&
                      Math.ceil( dval ) <= Integer.MAX_VALUE ) {
                return new Integer( (int) Math.ceil( dval ) );
            }
            else if ( clazz == Long.class &&
                      Math.ceil( dval ) <= Long.MAX_VALUE ) {
                return new Long( (long) Math.ceil( dval ) );
            }
            else if ( clazz == Float.class ) {
                return new Float( (float) dval );
            }
            else if ( clazz == Double.class ) {
                return new Double( dval );
            }
            else {
                return null;
            }
        }
        else {
            assert inc == 0;
            return in instanceof Comparable ? (Comparable) in : null;
        }
    }

    /**
     * Implements the tuning parameter which controls bin scaling factor.
     */
    private class BinFactorParameter extends DescribedValue {
        BinFactorParameter() {
            super( new DefaultValueInfo( "Bin Factor", Double.class,
                                         "Scaling factor to adjust bin size; "
                                       + "larger values mean larger bins. "
                                       + "Minimum legal value is 1." ) );
        }
        public Object getValue() {
            return new Double( getBinFactor() );
        }
        public void setValue( Object value ) {
            setBinFactor( ((Number) value).doubleValue() );
        }
    }
}
