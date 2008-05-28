package uk.ac.starlink.tplot;

import java.util.TreeMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.logging.Logger;

/**
 * BinnedData implementation that uses a map.
 * Bins are dispensed from the iterator in order. 
 *
 * @author   Mark Taylor
 * @since    14 Nov 2005
 */
public class MapBinnedData implements BinnedData {

    /**
     * Map containing the binned data.
     * The keys are objects managed by the mapper.  The values are int[] 
     * arrays giving the bin occupancy counts indexed by subset index.
     */
    private final SortedMap map_;

    private final int nset_;
    private final BinMapper mapper_;
    private boolean isFloat_;
    private static final Logger logger_ =
        Logger.getLogger( "uk.ac.starlink.tplot" );

    /**
     * Constructs a new BinnedData.
     *
     * @param  nset  the number of subsets that this BinnedData can deal with
     * @param  mapper  a BinMapper implementation that defines the bin ranges
     */
    public MapBinnedData( int nset, BinMapper mapper ) {
        nset_ = nset;
        mapper_ = mapper;
        map_ = new TreeMap();
    }

    public void submitDatum( double value, double weight, boolean[] setFlags ) {

        /* If it's a NaN, this implementation will end up binning it with
         * zeros, so just ignore it.  You could do something sensible 
         * with NaNs, but I've encountered terrible trouble with a 
         * Java 1.4.1 JIT bug (?) which seems to have had something to 
         * do with NaN processing, so be very careful if you try to
         * implement that. */
        if ( Double.isNaN( value ) || Double.isNaN( weight ) || weight == 0 ) {
            return;
        }

        /* Update integer flag. */
        isFloat_ = isFloat_ || ( weight != (int) weight );

        /* Normal value, go ahead. */
        Object key = mapper_.getKey( value );
        if ( key != null ) {
            double[] counts = (double[]) map_.get( key );
            if ( counts == null ) {
                counts = new double[ nset_ ];
                map_.put( key, counts );
            }
            for ( int is = 0; is < nset_; is++ ) {
                if ( setFlags[ is ] ) {
                    counts[ is ] += weight;
                }
            }
        }
    }

    public Iterator getBinIterator( boolean includeEmpty ) {
        final Iterator keyIt = ( includeEmpty && ! map_.isEmpty() )
                             ? mapper_.keyIterator( map_.firstKey(),
                                                    map_.lastKey() )
                             : map_.keySet().iterator();
        return new Iterator() {
            final double[] EMPTY_SUMS = new double[ nset_ ];
            public boolean hasNext() {
                return keyIt.hasNext();
            }
            public Object next() {
                Object key = keyIt.next();
                final double[] sums = map_.containsKey( key )
                                    ? (double[]) map_.get( key )
                                    : EMPTY_SUMS; 
                final double[] bounds = mapper_.getBounds( key );
                return new Bin() {
                    public double getLowBound() {
                        return bounds[ 0 ];
                    }
                    public double getHighBound() {
                        return bounds[ 1 ];
                    }
                    public double getWeightedCount( int iset ) {
                        return sums[ iset ];
                    }
                };
            }
            public void remove() {
                keyIt.remove();
            }
        };
    }

    public int getSetCount() {
        return nset_;
    }

    public boolean isInteger() {
        return ! isFloat_;
    }

    /**
     * Returns the BinMapper object used by this BinnedData.
     *
     * @return bin mapper
     */
    public BinMapper getMapper() {
        return mapper_;
    }

    /**
     * Constructs a new BinnedData with linearly spaced bins.
     *
     * @param  nset  number of subsets
     * @param  binWidth  bin spacing
     * @param  zeroMid  true if zero is to be in the middle of a bin,
     *         false if it falls on a bin boundary
     * @return   new BinnedData object
     */
    public static MapBinnedData createLinearBinnedData( int nset,
                                                        double binWidth,
                                                        boolean zeroMid ) {
        return new MapBinnedData( nset,
                                  new LinearBinMapper( binWidth, zeroMid ) );
    }

    /**
     * Constructs a new BinnedData with logarithmically spaced bins.
     *
     * @param  nset  number of subsets
     * @param  binFactor   logarithmic spacing of bins
     * @return  new BinnedData object
     */
    public static MapBinnedData createLogBinnedData( int nset,
                                                     double binFactor ) {
        return new MapBinnedData( nset, new LogBinMapper( binFactor ) );
    }

    /**
     * Defines the mapping of numerical values to map keys.
     * The keys must implement <code>equals</code> and <code>hashCode</code>
     * properly.
     */
    public interface BinMapper {

        /**
         * Returns the key to use for a given value.
         * May return <code>null</code> to indicate that the given value
         * cannot be binned.
         *
         * @param  value  numerical value
         * @return   object to be used as a key for the bin into which
         *           <code>value</code> falls
         */
        Comparable getKey( double value );

        /**
         * Returns the upper and lower bounds of the bin corresponding to
         * a given key.
         *
         * @param   key  bin key object
         * @return   2-element array giving (lower,upper) bound for
         *           bin <code>key</code>
         */
        double[] getBounds( Object key );

        /**
         * Returns an iterator which covers all keys between the given
         * low and high keys inclusive.
         * <code>loKey</code> and <code>hiKey</code> must be possible keys
         * for this mapper and arranged in the right order.
         *
         * @param  loKey  lower bound (inclusive) for key iteration
         * @param  hiKey  upper bound (inclusive) for key iteration
         * @return  iterator
         */
        Iterator keyIterator( Object loKey, Object hiKey );
    }

    /**
     * Linear scaled implementation of BinMapper.
     */
    private static class LinearBinMapper implements BinMapper {
        final double width_;
        final double base_;

        /**
         * Constructs a new linear mapper.
         *
         * @param  binWidth  width of the bins
         * @param  zeroMid  true if zero is to be in the middle of a bin,
         *         false if it falls on a bin boundary
         */
        LinearBinMapper( double binWidth, boolean zeroMid ) {
            if ( binWidth <= 0 || Double.isNaN( binWidth ) ) {
                throw new IllegalArgumentException( "Bad width " + binWidth );
            }
            width_ = binWidth;
            base_ = zeroMid ? ( - binWidth / 2.0 ) : 0.0;
        }
        public Comparable getKey( double value ) {
            return new Long( (long) Math.floor( ( value - base_ ) / width_ ) );
        }
        public double[] getBounds( Object key ) {
            final long keyval = ((Long) key).longValue();
            final double bottom = keyval * width_ + base_;

            /* This nonsensical looking test is here as debugging code for
             * a diabolical JVM bug which was plaguing this code at one
             * stage; it did sometimes log the warning at java 1.4.1 
             * using the default (client) hotspot compiler.  
             * I've modified the code elsewhere to do more explicit NaN
             * testing upstream of this method and it seems to have gone
             * away now, but leave the test here in case it rears its
             * ugly head again. */
            if ( Double.isNaN( bottom ) ) {
                if ( ! Double.isNaN( bottom ) ) {
                    logger_.warning( "Monstrous Java 1.4.1 JVM bug" );
                }
            }

            return new double[] { bottom, bottom + width_ };
        }
        public Iterator keyIterator( final Object loKey, final Object hiKey ) {
            return new Iterator() {
                final long hiVal_ = ((Long) hiKey).longValue();
                long val_ = ((Long) loKey).longValue();
                public boolean hasNext() {
                    return val_ <= hiVal_;
                }
                public Object next() {
                    return new Long( val_++ );
                }
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    /**
     * Logarithmically scaled implementation of BinMapper.
     */
    private static class LogBinMapper implements BinMapper {
        final double factor_;
        final double logFactor_;
        final double sqrtFactor_;
        LogBinMapper( double factor ) {
            factor_ = factor;
            logFactor_ = Math.log( factor );
            sqrtFactor_ = Math.sqrt( factor );
        }
        public Comparable getKey( double value ) {
            return value > 0.0 
                 ? new Long( Math.round( Math.log( value ) / logFactor_ ) )
                 : null;
        }
        public double[] getBounds( Object key ) {
            double centre = Math.pow( factor_, ((Long) key).doubleValue() );
            return new double[] { centre / sqrtFactor_, centre * sqrtFactor_ };
        }
        public Iterator keyIterator( final Object loKey, final Object hiKey ) {
            return new Iterator() {
                final long hiVal_ = ((Long) hiKey).longValue();
                long val_ = ((Long) loKey).longValue();
                public boolean hasNext() {
                    return val_ <= hiVal_;
                }
                public Object next() {
                    return new Long( val_++ );
                }
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
