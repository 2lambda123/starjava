package uk.ac.starlink.tptask;

import uk.ac.starlink.task.Environment;
import uk.ac.starlink.task.TaskException;

/**
 * Parameter for selecting line dash types.
 *
 * @author   Mark Taylor
 * @since    15 Aug 2008
 * @see      {@link java.awt.BasicStroke#getDashArray}
 */
public class DashParameter extends NamedObjectParameter {

    /**
     * Constructor.
     *
     * @param   name   parameter name
     */
    public DashParameter( String name ) {
        super( name );
        addOption( "dot", new float[] { 1f, 2f, } );
        addOption( "dash", new float[] { 4f, 2f, } );
        addOption( "longdash", new float[] { 8f, 4f, } );
        addOption( "dotdash", new float[] { 12f, 3f, 3f, 3f, } );
        setNullPermitted( true );
    }

    /**
     * Returns an XML string, suitable for inclusion into a parameter 
     * description, which explains the format of values accepted by this
     * parameter.
     * The returned string is not encosed in a &lt;p&gt; element.
     *
     * @return  XML format description
     */
    public String getFormatDescription() {
        return new StringBuffer()
            .append( "To generate a dashed line the value may be\n" )
            .append( "one of the named dash types:\n" )
            .append( getOptionList() )
            .append( "\n" )
            .append( "or may be a comma-separated string of on/off length " )
            .append( "values such as\n" )
            .append( "\"<code>4,2,8,2</code>\".\n" )
            .append( "A <code>null</code> value indicates a solid line." )
            .toString();
    }

    /**
     * Returns the value of this parameter as a dash array.
     *
     * @return   float array giving dash type
     */
    public float[] dashValue( Environment env ) throws TaskException {
        return (float[]) objectValue( env );
    }

    public String toString( Object obj ) {
        float[] dash = (float[]) obj;
        StringBuffer sbuf = new StringBuffer();
        for ( int i = 0; i < dash.length; i++ ) {
            if ( i > 0 ) {
                sbuf.append( ',' );
            }
            float f = dash[ i ];
            sbuf.append( f == (int) f ? Integer.toString( (int) f )
                                      : Float.toString( f ) );
        }
        return sbuf.toString();
    }

    public Object fromString( String name ) {
        String[] parts = name.split( "," );
        int np = parts.length;
        float[] fs = new float[ np ];
        for ( int i = 0; i < np; i++ ) {
            fs[ i ] = Float.parseFloat( parts[ i ] );
        }
        return fs;
    }
}
