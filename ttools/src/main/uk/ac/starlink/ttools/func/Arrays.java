// The doc comments in this class are processed to produce user-visible
// documentation as part of the package build process.  For this reason
// care should be taken to make the doc comment style comprehensible,
// consistent, concise, and not over-technical.

package uk.ac.starlink.ttools.func;

import gnu.jel.CompilationException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import uk.ac.starlink.table.Tables;
import uk.ac.starlink.ttools.build.HideDoc;
import uk.ac.starlink.ttools.filter.Quantiler;
import uk.ac.starlink.ttools.filter.SortQuantiler;
import uk.ac.starlink.ttools.jel.JELArrayFunction;

/**
 * Functions which operate on array-valued cells.
 * The array parameters of these functions can only be used on values
 * which are already arrays (usually, numeric arrays).
 * In most cases that means on values in table columns which are declared
 * as array-valued.  FITS and VOTable tables can have columns which contain
 * array values, but other formats such as CSV cannot.
 *
 * <p>If you want to calculate aggregating functions like sum, min, max etc
 * on multiple values which are not part of an array,
 * it's easier to use the functions from the <code>Lists</code> class.
 *
 * <p>Note that none of these functions will calculate statistical functions
 * over a whole column of a table.
 *
 * <p>The functions fall into a number of categories:
 * <ul>
 * <li>Aggregating operations, which map an array value to a scalar, including
 *     <code>size</code>,
 *     <code>count</code>,
 *     <code>countTrue</code>,
 *     <code>maximum</code>,
 *     <code>minimum</code>,
 *     <code>sum</code>,
 *     <code>mean</code>,
 *     <code>median</code>,
 *     <code>quantile</code>,
 *     <code>stdev</code>,
 *     <code>variance</code>,
 *     <code>join</code>.
 *     </li>
 * <li>Operations on one or more arrays which produce array results, including
 *     <code>add</code>,
 *     <code>subtract</code>,
 *     <code>multiply</code>,
 *     <code>divide</code>,
 *     <code>reciprocal</code>,
 *     <code>condition</code>,
 *     <code>slice</code>,
 *     <code>pick</code>.
 *     Mostly these work on any numeric array type and return
 *     floating point (double precision) values,
 *     but some of them (<code>slice</code>, <code>pick</code>)
 *     have variants for different array types.
 *     </li>
 * <li>The function <code>array</code>,
 *     which lets you assemble a floating point array value from
 *     a list of scalar numbers.
 *     There are variants (<code>intArray</code>, <code>stringArray</code>)
 *     for some different array types. 
 *     </li>
 * </ul>
 *
 * @author   Mark Taylor
 * @since    14 Jul 2008
 */
public class Arrays {

    /** Array element variable name in arrayFunc expressions. */
    public static final String ARRAY_ELEMENT_VARNAME = "x";

    /** Array index variable name in arrayFunc expressions. */
    public static final String ARRAY_INDEX_VARNAME = "i";

    private static final ThreadLocal<ArrayFuncMap> afuncsThreadLocal_ =
        ThreadLocal.withInitial( ArrayFuncMap::new );
    private static final Logger logger_ =
        Logger.getLogger( "uk.ac.starlink.ttools.func" );

    /**
     * Private constructor prevents instantiation.
     */
    private Arrays() {
    }

    /**
     * Returns the sum of all the non-blank elements in the array.
     * If <code>array</code> is not a numeric array, <code>null</code>
     * is returned.
     *
     * @param   array  array of numbers
     * @return  sum of all the numeric values in <code>array</code>
     */
    public static double sum( Object array ) {
        try {
            int n = Array.getLength( array );
            double sum = 0;
            for ( int i = 0; i < n; i++ ) {
                double d = Array.getDouble( array, i );
                if ( ! Double.isNaN( d ) ) {
                    sum += d;
                }
            }
            return sum;
        }
        catch ( RuntimeException e ) {
            return Double.NaN;
        }
    }

    /**
     * Returns the mean of all the non-blank elements in the array.
     * If <code>array</code> is not a numeric array, <code>null</code>
     * is returned.
     *
     * @param  array  array of numbers
     * @return  mean of all the numeric values in <code>array</code>
     */
    public static double mean( Object array ) {
        try {
            int n = Array.getLength( array );
            double sum = 0;
            int count = 0;
            for ( int i = 0; i < n; i++ ) {
                double d = Array.getDouble( array, i );
                if ( ! Double.isNaN( d ) ) {
                    sum += d;
                    count++;
                }
            }
            return sum / (double) count;
        }
        catch ( RuntimeException e ) {
            return Double.NaN;
        }
    }

    /**
     * Returns the population variance of all the non-blank elements
     * in the array.  If <code>array</code> is not a numeric array,
     * <code>null</code> is returned.
     *
     * @param  array  array of numbers
     * @return  variance of the numeric values in <code>array</code>
     */
    public static double variance( Object array ) {
        try {
            int n = Array.getLength( array );
            double sum = 0;
            double sum2 = 0;
            int count = 0;
            for ( int i = 0; i < n; i++ ) {
                double d = Array.getDouble( array, i );
                if ( ! Double.isNaN( d ) ) {
                    sum += d;
                    sum2 += d * d;
                    count++;
                }
            }
            double mean = sum / (double) count;
            return sum2 / (double) count - mean * mean;
        }
        catch ( RuntimeException e ) {
            return Double.NaN;
        }
    }

    /**
     * Returns the population standard deviation of all the non-blank elements
     * in the array.  If <code>array</code> is not a numeric array,
     * <code>null</code> is returned.
     *
     * @param   array  array of numbers
     * @return  standard deviation of the numeric values in <code>array</code>
     */
    public static double stdev( Object array ) {
        return Math.sqrt( variance( array ) );
    }

    /**
     * Returns the smallest of the non-blank elements in the array.
     * If <code>array</code> is not a numeric array, <code>null</code>
     * is returned.
     *
     * @param  array  array of numbers
     * @return  minimum of the numeric values in <code>array</code>
     */
    public static double minimum( Object array ) {
        try {
            int n = Array.getLength( array );
            double min = Double.NaN;
            for ( int i = 0; i < n; i++ ) {
                double d = Array.getDouble( array, i );
                if ( ! Double.isNaN( d ) && ! ( d > min ) ) {
                    min = d;
                }
            }
            return min;
        }
        catch ( RuntimeException e ) {
            return Double.NaN;
        }
    }

    /**
     * Returns the largest of the non-blank elements in the array.
     * If <code>array</code> is not a numeric array, <code>null</code>
     * is returned.
     *
     * @param   array  array of numbers
     * @return   maximum of the numeric values in <code>array</code>
     */
    public static double maximum( Object array ) {
        try {
            int n = Array.getLength( array );
            double max = Double.NaN;
            for ( int i = 0; i < n; i++ ) {
                double d = Array.getDouble( array, i );
                if ( ! Double.isNaN( d ) && ! ( d < max ) ) {
                    max = d;
                }
            }
            return max;
        }
        catch ( RuntimeException e ) {
            return Double.NaN;
        }
    }

    /**
     * Returns the median of the non-blank elements in the array.
     * If <code>array</code> is not a numeric array, <code>null</code>
     * is returned.
     *
     * @param   array  array of numbers
     * @return   median of the numeric values in <code>array</code>
     */
    public static double median( Object array ) {
        return quantile( array, 0.5 );
    }

    /**
     * Returns a quantile value of the non-blank elements in the array.
     * Which quantile is determined by the <code>quant</code> value;
     * values of 0, 0.5 and 1 give the minimum, median and maximum
     * respectively.  A value of 0.99 would give the 99th percentile.
     * 
     * @param   array  array of numbers
     * @param   quant  number in the range 0-1 deterining which quantile
     *                 to calculate
     * @return   quantile corresponding to <code>quant</code>
     */
    public static double quantile( Object array, double quant ) {
        try {
            int n = Array.getLength( array );
            Quantiler qc = new SortQuantiler( n );
            for ( int i = 0; i < n; i++ ) {
                qc.acceptDatum( Array.getDouble( array, i ) );
            }
            qc.ready();
            return qc.getValueAtQuantile( quant );
        }
        catch ( RuntimeException e ) {
            return Double.NaN;
        }
    }

    /**
     * Returns the number of elements in the array.
     * If <code>array</code> is not an array, zero is returned.
     *
     * @param  array  array
     * @return  size of <code>array</code>
     */
    public static int size( Object array ) {
        return array != null && array.getClass().isArray()
             ? Array.getLength( array )
             : 0;
    }

    /**
     * Returns the number of non-blank elements in the array.
     * If <code>array</code> is not an array, zero is returned.
     *
     * @param  array   array (may or may not be numeric)
     * @return   number of non-blank elements in <code>array</code>
     */
    public static int count( Object array ) {
        try {
            int n = Array.getLength( array );
            int count = 0;
            for ( int i = 0; i < n; i++ ) {
                if ( ! Tables.isBlank( Array.get( array, i ) ) ) {
                    count++;
                }
            }
            return count;
        }
        catch ( RuntimeException e ) {
            return 0;
        }
    }

    /**
     * Returns the number of true elements in an array of boolean values.
     *
     * @param  array  array of true/false values
     * @return  number of true values in <code>array</code>
     */
    public static int countTrue( boolean[] array ) {
        int count = 0;
        for ( boolean b : array ) {
             if ( b ) {
                 count++;
             }
        }
        return count;
    }

    /**
     * Returns a string composed of concatenating all the elements of an
     * array, separated by a joiner string.
     * If <code>array</code> is not an array, null is returned.
     *
     * @example <code>join(array(1.5,2.1,-3.9), "; ") = "1.5; 2.1; -3.9"</code>
     *
     * @param  array   array of numbers or strings
     * @param  joiner  text string to interpose between adjacent elements
     * @return  string composed of <code>array</code> elements separated by
     *          <code>joiner</code> strings
     */
    public static String join( Object array, String joiner ) {
        StringBuilder sbuf = new StringBuilder();
        try {
            int n = Array.getLength( array );
            for ( int i = 0; i < n; i++ ) {
                if ( i > 0 ) {
                    sbuf.append( joiner );
                }
                sbuf.append( Array.get( array, i ) );
            }
            return sbuf.toString();
        }
        catch ( RuntimeException e ) {
            return null;
        }
    }

    /**
     * Returns the result of adding two numeric arrays element by element.
     * Both arrays must be numeric, and the arrays must have the same length.
     * If either of those conditions is not true, <code>null</code> is returned.
     * The types of the arrays do not need to be the same,
     * so for example it is permitted to add an integer array
     * to a floating point array.
     *
     * @example  <code>add(array(1,2,3), array(0.1,0.2,0.3))
     *                 = [1.1, 2.2, 3.3]</code>
     *
     * @param   array1  first array of numeric values
     * @param   array2  second array of numeric values
     * @return    element-by-element sum of
     *            <code>array1</code> and <code>array2</code>,
     *            the same length as the input arrays
     */
    public static double[] add( Object array1, Object array2 ) {
        int n = getNumericArrayLength( array1 );
        if ( n >= 0 && getNumericArrayLength( array2 ) == n ) {
            double[] out = new double[ n ];
            for ( int i = 0; i < n; i++ ) {
                out[ i ] = Array.getDouble( array1, i )
                         + Array.getDouble( array2, i );
            }
            return out;
        }
        else {
            return null;
        }
    }

    /**
     * Returns the result of adding a constant value to every element of
     * a numeric array.
     * If the supplied <code>array</code> argument is not a numeric array,
     * <code>null</code> is returned.
     *
     * @example  <code>add(array(1,2,3), 10) = [11,12,13]</code>
     *
     * @param  array   array input
     * @param  constant   value to add to each array element
     * @return   array output,
     *           the same length as the <code>array</code> parameter
     */
    public static double[] add( Object array, double constant ) {
        int n = getNumericArrayLength( array );
        if ( n >= 0 ) {
            double[] out = new double[ n ];
            for ( int i = 0; i < n; i++ ) {
                out[ i ] = Array.getDouble( array, i ) + constant;
            }
            return out;
        }
        else {
            return null;
        }
    }

    /**
     * Returns the result of subtracting one numeric array from the other
     * element by element.
     * Both arrays must be numeric, and the arrays must have the same length.
     * If either of those conditions is not true, <code>null</code> is returned.
     * The types of the arrays do not need to be the same,
     * so for example it is permitted to subtract an integer array
     * from a floating point array.
     *
     * @example  <code>subtract(array(1,2,3), array(0.1,0.2,0.3))
     *                 = [0.9, 1.8, 2.7]</code>
     *
     * @param   array1  first array of numeric values
     * @param   array2  second array of numeric values
     * @return    element-by-element difference of
     *            <code>array1</code> and <code>array2</code>,
     *            the same length as the input arrays
     */
    public static double[] subtract( Object array1, Object array2 ) {
        int n = getNumericArrayLength( array1 );
        if ( n >= 0 && getNumericArrayLength( array2 ) == n ) {
            double[] out = new double[ n ];
            for ( int i = 0; i < n; i++ ) {
                out[ i ] = Array.getDouble( array1, i )
                         - Array.getDouble( array2, i );
            }
            return out;
        }
        else {
            return null;
        }
    }

    /**
     * Returns the result of multiplying two numeric arrays element by element.
     * Both arrays must be numeric, and the arrays must have the same length.
     * If either of those conditions is not true, <code>null</code> is returned.
     * The types of the arrays do not need to be the same,
     * so for example it is permitted to multiply an integer array
     * by a floating point array.
     *
     * @example  <code>multiply(array(1,2,3), array(2,4,6)) = [2, 8, 18]</code>
     *
     * @param   array1  first array of numeric values
     * @param   array2  second array of numeric values
     * @return    element-by-element product of
     *            <code>array1</code> and <code>array2</code>,
     *            the same length as the input arrays
     */
    public static double[] multiply( Object array1, Object array2 ) {
        int n = getNumericArrayLength( array1 );
        if ( n >= 0 && getNumericArrayLength( array2 ) == n ) {
            double[] out = new double[ n ];
            for ( int i = 0; i < n; i++ ) {
                out[ i ] = Array.getDouble( array1, i )
                         * Array.getDouble( array2, i );
            }
            return out;
        }
        else {
            return null;
        }
    }

    /**
     * Returns the result of multiplying every element of a numeric array
     * by a constant value.
     * If the supplied <code>array</code> argument is not a numeric array,
     * <code>null</code> is returned.
     *
     * @example  <code>multiply(array(1,2,3), 2) = [2, 4, 6]</code>
     *
     * @param  array   array input
     * @param  constant   value by which to multiply each array element
     * @return   array output,
     *           the same length as the <code>array</code> parameter
     */
    public static double[] multiply( Object array, double constant ) {
        int n = getNumericArrayLength( array );
        if ( n >= 0 ) {
            double[] out = new double[ n ];
            for ( int i = 0; i < n; i++ ) {
                out[ i ] = Array.getDouble( array, i ) * constant;
            }
            return out;
        }
        else {
            return null;
        }
    }

    /**
     * Returns the result of dividing two numeric arrays element by element.
     * Both arrays must be numeric, and the arrays must have the same length.
     * If either of those conditions is not true, <code>null</code> is returned.
     * The types of the arrays do not need to be the same,
     * so for example it is permitted to divide an integer array
     * by a floating point array.
     *
     * @example  <code>divide(array(0,9,4), array(1,3,8)) = [0, 3, 0.5]</code>
     *
     * @param   array1  array of numerator values (numeric)
     * @param   array2  array of denominator values (numeric)
     * @return    element-by-element result of <code>array1[i]/array2[i]</code>
     *            the same length as the input arrays
     */
    public static double[] divide( Object array1, Object array2 ) {
        int n = getNumericArrayLength( array1 );
        if ( n >= 0 && getNumericArrayLength( array2 ) == n ) {
            double[] out = new double[ n ];
            for ( int i = 0; i < n; i++ ) {
                out[ i ] = Array.getDouble( array1, i )
                         / Array.getDouble( array2, i );
            }
            return out;
        }
        else {
            return null;
        }
    }

    /**
     * Returns the result of taking the reciprocal of every element of
     * a numeric array.
     * If the supplied <code>array</code> argument is not a numeric array,
     * <code>null</code> is returned.
     *
     * @example  <code>reciprocal(array(1,2,0.25) = [1, 0.5, 4]</code>
     *
     * @param   array  array input
     * @return  array output,
     *          the same length as the <code>array</code> parameter
     */
    public static double[] reciprocal( Object array ) {
        int n = getNumericArrayLength( array );
        if ( n >= 0 ) {
            double[] out = new double[ n ];
            for ( int i = 0; i < n; i++ ) {
                out[ i ] = 1.0 / Array.getDouble( array, i );
            }
            return out;
        }
        else {
            return null;
        }
    }

    /**
     * Maps a boolean array to a numeric array by using supplied numeric
     * values to represent true and false values from the input array.
     *
     * <p>This has the same effect as applying the expression
     * <code>outArray[i] = flagArray[i] ? trueValue : falseValue</code>.
     *
     * @example   <code>condition([true, false, true], 1, 0) = [1, 0, 1]</code>
     *
     * @param   flagArray   array of boolean values
     * @param   trueValue   output value corresponding to an input true value
     * @param   falseValue  output value corresponding to an input false value
     * @return    output numeric array, same length as <code>flagArray</code>
     */
    public static double[] condition( boolean[] flagArray,
                                      double trueValue, double falseValue ) {
        int n = flagArray.length;
        double[] out = new double[ n ];
        for ( int i = 0; i < n; i++ ) {
            out[ i ] = flagArray[ i ] ? trueValue : falseValue;
        }
        return out;
    }

    /**
     * Returns a sub-sequence of values from a given array.
     *
     * <p>The semantics are like python array slicing, though both limits
     * have to be specified: the output array contains the sequence of
     * elements in the input array from <code>i0</code> (inclusive)
     * to <code>i1</code> (exclusive).  If a negative value is given
     * in either case, it is added to the length of the input array,
     * so that -1 indicates the last element of the input array.
     * The indices are capped at 0 and the input array length respectively,
     * so a large positive value may be used to indicate the end of the array.
     * If the end index is less than or equal to the start index,
     * a zero-length array is returned.
     *
     * <p><strong>Note:</strong>
     * This documents the double-precision version of the routine.
     * Corresponding routines exist for other data types
     * (<code>float</code>, <code>long</code>, <code>int</code>,
     * <code>short</code>, <code>byte</code>, <code>String</code>,
     * <code>Object</code>).
     *
     * @example <code>slice(array(10,11,12,13), 0, 3) = [10, 11, 12]</code>
     * @example <code>slice(array(10,11,12,13), -2, 999) = [12, 13]</code>
     *
     * @param   array  input array
     * @param   i0  index of first element, inclusive
     *              (may be negative to count back from the end)
     * @param   i1  index of the last element, exclusive
     *              (may be negative to count back from the end)
     * @return   array giving the sequence of
     *           elements specified by <code>i0</code> and <code>i1</code>
     */
    public static double[] slice( double[] array, int i0, int i1 ) {
        if ( array != null ) {
            int leng = array.length;
            int j0 = effectiveIndex( i0, leng );
            int j1 = effectiveIndex( i1, leng );
            int count = Math.max( 0, j1 - j0 );
            double[] out = new double[ count ];
            System.arraycopy( array, j0, out, 0, count );
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static float[] slice( float[] array, int i0, int i1 ) {
        if ( array != null ) {
            int leng = array.length;
            int j0 = effectiveIndex( i0, leng );
            int j1 = effectiveIndex( i1, leng );
            int count = Math.max( 0, j1 - j0 );
            float[] out = new float[ count ];
            System.arraycopy( array, j0, out, 0, count );
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static long[] slice( long[] array, int i0, int i1 ) {
        if ( array != null ) {
            int leng = array.length;
            int j0 = effectiveIndex( i0, leng );
            int j1 = effectiveIndex( i1, leng );
            int count = Math.max( 0, j1 - j0 );
            long[] out = new long[ count ];
            System.arraycopy( array, j0, out, 0, count );
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static int[] slice( int[] array, int i0, int i1 ) {
        if ( array != null ) {
            int leng = array.length;
            int j0 = effectiveIndex( i0, leng );
            int j1 = effectiveIndex( i1, leng );
            int count = Math.max( 0, j1 - j0 );
            int[] out = new int[ count ];
            System.arraycopy( array, j0, out, 0, count );
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static short[] slice( short[] array, int i0, int i1 ) {
        if ( array != null ) {
            int leng = array.length;
            int j0 = effectiveIndex( i0, leng );
            int j1 = effectiveIndex( i1, leng );
            int count = Math.max( 0, j1 - j0 );
            short[] out = new short[ count ];
            System.arraycopy( array, j0, out, 0, count );
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static byte[] slice( byte[] array, int i0, int i1 ) {
        if ( array != null ) {
            int leng = array.length;
            int j0 = effectiveIndex( i0, leng );
            int j1 = effectiveIndex( i1, leng );
            int count = Math.max( 0, j1 - j0 );
            byte[] out = new byte[ count ];
            System.arraycopy( array, j0, out, 0, count );
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static String[] slice( String[] array, int i0, int i1 ) {
        if ( array != null ) {
            int leng = array.length;
            int j0 = effectiveIndex( i0, leng );
            int j1 = effectiveIndex( i1, leng );
            int count = Math.max( 0, j1 - j0 );
            String[] out = new String[ count ];
            System.arraycopy( array, j0, out, 0, count );
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static Object[] slice( Object[] array, int i0, int i1 ) {
        if ( array != null ) {
            int leng = array.length;
            int j0 = effectiveIndex( i0, leng );
            int j1 = effectiveIndex( i1, leng );
            int count = Math.max( 0, j1 - j0 );
            Object[] out = new Object[ count ];
            System.arraycopy( array, j0, out, 0, count );
            return out;
        }
        else {
            return null;
        }
    }

    /**
     * Returns a selection of elements from a given array.
     *
     * <p>The output array consists of one element selected from the
     * input array for each of the supplied index values.
     * If a negative value is supplied for an index value,
     * it is added to the input array length, so that -1 indicates the
     * last element of the input array.
     * If the input array is null, null is returned.
     * If any of the index values is out of the range of the extent of
     * the input array, an error results.
     *
     * <p><strong>Note:</strong>
     * This documents the double-precision version of the routine.
     * Corresponding routines exist for other data types
     * (<code>float</code>, <code>long</code>, <code>int</code>,
     * <code>short</code>, <code>byte</code>, <code>String</code>,
     * <code>Object</code>).
     *
     * @example  <code>pick(array(10,11,12,13), 0, 3) = [10, 13]</code>
     * @example  <code>pick(array(10,11,12,13), -1, -2, -3)
     *                 = [13, 12, 11]</code>
     *
     * @param  array  input array
     * @param  indices   one or more index into the input array
     *                   (may be negative to count back from the end)
     * @return   array giving the elements specified by <code>indices</code>
     */
    public static double[] pick( double[] array, int... indices ) {
        if ( array != null ) {
            int leng = array.length;
            int n = indices.length;
            double[] out = new double[ n ];
            for ( int i = 0; i < n; i++ ) {
                int ix = indices[ i ];
                int jx = ix >= 0 ? ix : leng + ix;
                out[ i ] = array[ jx ];
            }
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static float[] pick( float[] array, int... indices ) {
        if ( array != null ) {
            int leng = array.length;
            int n = indices.length;
            float[] out = new float[ n ];
            for ( int i = 0; i < n; i++ ) {
                int ix = indices[ i ];
                int jx = ix >= 0 ? ix : leng + ix;
                out[ i ] = array[ jx ];
            }
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static long[] pick( long[] array, int... indices ) {
        if ( array != null ) {
            int leng = array.length;
            int n = indices.length;
            long[] out = new long[ n ];
            for ( int i = 0; i < n; i++ ) {
                int ix = indices[ i ];
                int jx = ix >= 0 ? ix : leng + ix;
                out[ i ] = array[ jx ];
            }
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static int[] pick( int[] array, int... indices ) {
        if ( array != null ) {
            int leng = array.length;
            int n = indices.length;
            int[] out = new int[ n ];
            for ( int i = 0; i < n; i++ ) {
                int ix = indices[ i ];
                int jx = ix >= 0 ? ix : leng + ix;
                out[ i ] = array[ jx ];
            }
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static short[] pick( short[] array, int... indices ) {
        if ( array != null ) {
            int leng = array.length;
            int n = indices.length;
            short[] out = new short[ n ];
            for ( int i = 0; i < n; i++ ) {
                int ix = indices[ i ];
                int jx = ix >= 0 ? ix : leng + ix;
                out[ i ] = array[ jx ];
            }
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static byte[] pick( byte[] array, int... indices ) {
        if ( array != null ) {
            int leng = array.length;
            int n = indices.length;
            byte[] out = new byte[ n ];
            for ( int i = 0; i < n; i++ ) {
                int ix = indices[ i ];
                int jx = ix >= 0 ? ix : leng + ix;
                out[ i ] = array[ jx ];
            }
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static String[] pick( String[] array, int... indices ) {
        if ( array != null ) {
            int leng = array.length;
            int n = indices.length;
            String[] out = new String[ n ];
            for ( int i = 0; i < n; i++ ) {
                int ix = indices[ i ];
                int jx = ix >= 0 ? ix : leng + ix;
                out[ i ] = array[ jx ];
            }
            return out;
        }
        else {
            return null;
        }
    }

    @HideDoc
    public static Object[] pick( Object[] array, int... indices ) {
        if ( array != null ) {
            int leng = array.length;
            int n = indices.length;
            Object[] out = new Object[ n ];
            for ( int i = 0; i < n; i++ ) {
                int ix = indices[ i ];
                int jx = ix >= 0 ? ix : leng + ix;
                out[ i ] = array[ jx ];
            }
            return out;
        }
        else {
            return null;
        }
    }

    /**
     * Returns a floating-point array resulting from applying a given
     * function expression element-by-element to an input array.
     * The output array is the same length as the input array.
     *
     * <p>The supplied expression can use the variable "<code>x</code>"
     * to refer to the corresponding element of the input array, and
     * "<code>i</code>"
     * to refer to its (zero-based) index.
     * The various functions and operators from the expression language
     * can all be used, but it is currently <strong>not</strong> possible
     * to reference other table column values.
     *
     * <p>If there is an error in the expression, a blank value
     * (not an array) will be returned.
     *
     * @example <code>arrayFunc("3*x",array(0,1,2,3,NaN))
     *              = [0, 3, 6, 9, NaN]</code>
     * @example <code>arrayFunc("pow(2,i)+x", array(0.5,0.5,0.5,0.5))
     *              = [1.5, 2.5, 4.5, 8.5]</code>
     *
     * @param  expr  expression mapping input to output array values
     * @param  inArray   input array
     * @return   floating point array with the same number of elements as
     *           <code>inArray</code>, or null for a bad <code>expr</code>
     */
    public static double[] arrayFunc( String expr, Object inArray ) {
        return arrayFunc( expr, inArray, double[].class );
    }

    /**
     * Returns an integer array resulting from applying a given
     * function expression element-by-element to an input array.
     * The output array is the same length as the input array.
     *
     * <p>The supplied expression can use the variable "<code>x</code>"
     * to refer to the corresponding element of the input array, and
     * "<code>i</code>"
     * to refer to its (zero-based) index.
     * The various functions and operators from the expression language
     * can all be used, but it is currently <strong>not</strong> possible
     * to reference other table column values.
     *
     * <p>If there is an error in the expression, a blank value
     * (not an array) will be returned.
     *
     * @example <code>intArrayFunc("-x",sequence(5))
     *              = [0, -1, -2, -3, -4]</code>
     *
     * @param  expr  expression mapping input to output array values
     * @param  inArray   input array
     * @return   floating point array with the same number of elements as
     *           <code>inArray</code>, or null for a bad <code>expr</code>
     */
    public static int[] intArrayFunc( String expr, Object inArray ) {
        return arrayFunc( expr, inArray, int[].class );
    }

    /**
     * Returns an untyped array resulting from applying a given
     * function expression element-by-element to an input array.
     * The runtime type of the output value is determined by the
     * compiled type of the supplied expression, but since this
     * method only has type Object, it has to be cast manually to
     * the actual type before anything much can be done with it.
     * For that reason it's not publicised in the user documentation.
     *
     * @param  expr  expression mapping input to output array values
     * @param  inArray   input array
     * @return   untyped array with the same number of elements as
     *           <code>inArray</code>, or null for a bad <code>expr</code>
     */
    @HideDoc
    public static Object untypedArrayFunc( String expr, Object inArray ) {
        return arrayFunc( expr, inArray, Object.class );
    }

    /**
     * Returns an array of a specified type that results from applying a given
     * function expression element-by-element to an input array.
     *
     * @param  expr  expression mapping input to output array values
     * @param  inArray   input array
     * @return   array with the same number of elements as
     *           <code>inArray</code>, or null for a bad <code>expr</code>
     */
    private static <I,O> O arrayFunc( String expr, I inArray,
                                      Class<O> outClazz ) {
        if ( inArray == null || expr == null ) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Class<I> inClazz = (Class<I>) inArray.getClass();
        JELArrayFunction<I,O> afunc =
             getArrayFunction( ARRAY_INDEX_VARNAME, ARRAY_ELEMENT_VARNAME,
                               expr, inClazz, outClazz );
        return afunc == null
             ? null
             : afunc.evaluate( inArray );
    }

    /**
     * Returns an array function with specified characteristics that is
     * safe for use in the current thread.
     *
     * @param   iname  name of the array index variable
     * @param   xname  name of the array element variable
     * @param   fexpr  text of expression giving the function value
     * @param   inClazz  type of input array; must be an array type of
     *                   primitive or object elements
     * @param   outClazz  type of output array; if not known, Object.class
     *                    may be given, and the output type will be determined
     *                    from the expression
     */
    private static <I,O>
             JELArrayFunction<I,O> getArrayFunction( String iname, String xname,
                                                     String fexpr,
                                                     Class<I> inClazz,
                                                     Class<O> outClazz ) {
         return afuncsThreadLocal_
               .get()
               .getArrayFunction( iname, xname, fexpr, inClazz, outClazz );
    }

    /**
     * Returns the position in a supplied array at which a given item appears.
     * The result is zero-based, so if the supplied <code>item</code>
     * is the first entry in the <code>array</code>, the return value
     * will be zero. 
     *
     * <p>If the item does not appear in the array, -1 is returned. 
     * If it appears multiple times, the index of its first appearance
     * is returned.
     *
     * <p>If <code>indexOf(array, item)==n</code>, then
     * <code>array[n]</code> is equal to <code>item</code>.
     *
     * <p><strong>Note:</strong>
     * This documents the <code>Object</code> version of the routine.
     * Corresponding routines exist for other data types
     * (<code>double</code>, <code>float</code>, <code>long</code>,
     * <code>int</code>, <code>short</code>).
     *
     * @example <code>indexOf(stringArray("QSO", "BCG", "SNR"), "BCG")
     *                = 1</code>
     * @example <code>indexOf(stringArray("QSO", "BCG", "SNR"), "TLA")
     *                = -1</code>
     *
     * @param  array  array which may contain the supplied item
     * @param  item   entry to look for in the array
     * @return   the index of <code>item</code> in <code>array</code>,
     *           or -1
     */
    public static int indexOf( Object[] array, Object item ) {
        if ( array != null ) {
            int n = array.length;
            if ( item == null ) {
                for ( int i = 0; i < n; i++ ) {
                    if ( array[ i ] == null ) {
                        return i;
                    }
                }
            }
            else {
                for ( int i = 0; i < n; i++ ) {
                    if ( item.equals( array[ i ] ) ) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    @HideDoc
    public static int indexOf( double[] array, double item ) {
        if ( array != null ) {
            int n = array.length;
            if ( Double.isNaN( item ) ) {
                for ( int i = 0; i < n; i++ ) {
                    if ( Double.isNaN( array[ i ] ) ) {
                        return i;
                    }
                }
            }
            else {
                for ( int i = 0; i < n; i++ ) {
                    if ( array[ i ] == item ) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    @HideDoc
    public static int indexOf( float[] array, double item ) {
        if ( array != null ) {
            int n = array.length;
            if ( Double.isNaN( item ) ) {
                for ( int i = 0; i < n; i++ ) {
                    if ( Float.isNaN( array[ i ] ) ) {
                        return i;
                    }
                }
            }
            else {
                float fitem = (float) item;
                for ( int i = 0; i < n; i++ ) {
                    if ( array[ i ] == fitem ) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    @HideDoc
    public static int indexOf( long[] array, long item ) {
        if ( array != null ) {
            int n = array.length;
            for ( int i = 0; i < n; i++ ) {
                if ( array[ i ] == item ) {
                    return i;
                }
            }
        }
        return -1;
    }

    @HideDoc
    public static int indexOf( int[] array, int item ) {
        if ( array != null ) {
            int n = array.length;
            for ( int i = 0; i < n; i++ ) {
                if ( array[ i ] == item ) {
                    return i;
                }
            }
        }
        return -1;
    }

    @HideDoc
    public static int indexOf( short[] array, int item ) {
        short sitem = (short) item;
        if ( array != null && sitem == item ) {
            int n = array.length;
            for ( int i = 0; i < n; i++ ) {
                if ( array[ i ] == item ) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Returns an integer array of a given length with the values
     * 0, 1, 2, ....
     *
     * @example <code>sequence(4) = (0, 1, 2, 3)</code>
     *
     * @param  n  length of array
     * @return   <code>n</code>-element array,
     *           (0, 1, 2, ... <code>n</code>-1)
     */
    public static int[] sequence( int n ) {
        int[] seq = new int[ n ];
        for ( int i = 0; i < n; i++ ) {
            seq[ i ] = i;
        }
        return seq;
    }

    /**
     * Returns a floating point numeric array built from the given arguments.
     *
     * @param   values   one or more array elements
     * @return  array
     */
    public static double[] array( double... values ) {
        return values;
    }

    /**
     * Returns an integer numeric array built from the given arguments.
     *
     * @param   values   one or more array elements
     * @return  array
     */
    public static int[] intArray( int... values ) {
        return values;
    }

    /**
     * Returns a String array built from the given arguments.
     *
     * @param   values   one or more array elements
     * @return  array
     */
    public static String[] stringArray( String... values ) {
        return values;
    }

    /**
     * Returns the length of a primitive numeric array.
     * If the supplied object is not a primitive numeric array,
     * -1 will be returned.
     *
     * @param   array   object
     * @return   length of array, or -1
     */
    private static int getNumericArrayLength( Object array ) {
        return ( array instanceof byte[] 
              || array instanceof short[]
              || array instanceof int[]
              || array instanceof long[]
              || array instanceof float[]
              || array instanceof double[] )
            ? Array.getLength( array )
            : -1;
    }

    /**
     * Returns the effective index indicated by a user array index
     * specification.  This uses python-like semantics, where a negative
     * value is added to the array length to count backwards from the end.
     * The output is also capped so that it does not fall outside the range
     * of legal array elements.
     *
     * @param  index  supplied index specification
     * @param  leng   array length
     * @return  effective index
     */
    private static int effectiveIndex( int index, int leng ) {
        return index >= 0 ? Math.min( leng, index )
                          : Math.max( 0, leng + index );
    }

    /**
     * Class to cache compiled array functions.
     */
    private static class ArrayFuncMap {
        private final Map<Key<?,?>,JELArrayFunction<?,?>> map_;

        ArrayFuncMap() {
            map_ = new HashMap<>();
        }

        /**
         * Returns a lazily constructed, possibly cached, array function.
         * The returned object not safe for concurrent use within
         * multiple threads.
         *
         * @param   iname  name of the array index variable
         * @param   xname  name of the input array element variable
         * @param   fexpr  expression for the output array element
         * @param   inClazz  array class for the input array
         * @param   outClazz  array class for the output array
         * @return   function that maps input to output array objects,
         *           or null in the case of a compilation exception
         */
        <I,O> JELArrayFunction<I,O> getArrayFunction( String iname,
                                                      String xname,
                                                      String fexpr,
                                                      Class<I> inClazz,
                                                      Class<O> outClazz ) {
            Key<I,O> key = new Key<>( iname, xname, fexpr, inClazz, outClazz );
            JELArrayFunction<I,O> afunc;
            if ( map_.containsKey( key ) ) {
                @SuppressWarnings("unchecked")
                JELArrayFunction<I,O> afunc0 =
                    (JELArrayFunction<I,O>) map_.get( key );
                afunc = afunc0;
            }
            else {
                try {
                    afunc = new JELArrayFunction<I,O>( iname, xname, fexpr,
                                                       inClazz, outClazz );
                }
                catch ( CompilationException e ) {
                    logger_.log( Level.WARNING,
                                 "Bad expresssion \"" + fexpr + "\": " + e, e );
                    afunc = null;
                }
                map_.put( key, afunc );
            }
            return afunc;
        }

        /**
         * Map key to identify array function indices.
         */
        private static class Key<I,O> {
            final String iname_;
            final String xname_;
            final String fexpr_;
            final Class<I> inClazz_;
            final Class<O> outClazz_;
            Key( String iname, String xname, String fexpr,
                 Class<I> inClazz, Class<O> outClazz ) {
                iname_ = iname;
                xname_ = xname;
                fexpr_ = fexpr;
                inClazz_ = inClazz;
                outClazz_ = outClazz;
            }
            @Override
            public int hashCode() {
                int code = -9971;
                code = 23 * code + iname_.hashCode();
                code = 23 * code + xname_.hashCode();
                code = 23 * code + fexpr_.hashCode();
                code = 23 * code + inClazz_.hashCode();
                code = 23 * code + outClazz_.hashCode();
                return code;
            }
            @Override
            public boolean equals( Object o ) {
                if ( o instanceof Key ) {
                    Key<?,?> other = (Key<?,?>) o;
                    return this.fexpr_.equals( other.fexpr_ )
                        && this.iname_.equals( other.iname_ )
                        && this.xname_.equals( other.xname_ )
                        && this.inClazz_.equals( other.inClazz_ )
                        && this.outClazz_.equals( other.outClazz_ );
                }
                else {
                    return false;
                }
            }
        }
    }
}
