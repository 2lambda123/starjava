package uk.ac.starlink.ttools.plot2.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.ac.starlink.table.Domain;
import uk.ac.starlink.table.DomainMapper;
import uk.ac.starlink.table.Tables;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.util.DoubleList;

/**
 * Domain representing two-dimensional regions on a common surface.
 * The surface may be a plane or the surface of a sphere.
 *
 * <p>The sole instance of this singleton class is available as the
 * {@link #INSTANCE} static member.
 *
 * @author   Mark Taylor
 * @since    14 Apr 2020
 */
public class AreaDomain implements Domain<AreaMapper> {

    /** Singleton instance. */
    public static final AreaDomain INSTANCE = new AreaDomain();

    /** STC-S - see TAP 1.0 section 6. */
    public static final AreaMapper STCS_MAPPER = createStcsMapper();

    /** Mapper for (x,y,r) circles - see DALI 1.1 section 3.3.6. */
    public static final AreaMapper CIRCLE_MAPPER =
        createDaliMapper( Area.Type.CIRCLE,
                          "3-element array "
                        + "(<code>x</code>,<code>y</code>,<code>r</code>)" );

    /** Mapper for (xi,yi,...) polygons - see DALI 1.1 section 3.3.7. */
    public static final AreaMapper POLYGON_MAPPER =
        createDaliMapper( Area.Type.POLYGON,
                          "2n-element array "
                        + "(<code>x1</code>,<code>y1</code>,"
                        + " <code>x2</code>,<code>y2</code>,...);\n"
                        + "a <code>NaN</code>,<code>NaN</code> pair "
                        + "can be used to delimit distinct polygons." );

    /** Mapper for (x,y) points - see DALI 1.1 section 3.3.5. */
    public static final AreaMapper POINT_MAPPER =
        createDaliMapper( Area.Type.POINT,
                          "2-element array (<code>x</code>,<code>y</code>)" );

    private static final String WORDS_REGEX =
        "\\s*([A-Za-z]+)\\s+([A-Za-z][A-Za-z0-9]*\\s+)*";
    private static final String NUMBER_REGEX =
        "\\s*([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)";
    private static final Pattern WORDS_PATTERN =
        Pattern.compile( WORDS_REGEX );
    private static final Pattern NUMBER_PATTERN =
        Pattern.compile( NUMBER_REGEX );
    private static final Pattern PARENTHESIS_PATTERN =
        Pattern.compile( "\\s*\\((.*)\\)\\s*" );
    private static final Pattern TERM_PATTERN =
        Pattern.compile( "(" + WORDS_REGEX + ")+" + "(" + NUMBER_REGEX + ")+" );

    /**
     * Private sole constructor prevents external instantiation.
     */
    private AreaDomain() {
    }

    public String getDomainName() {
        return "Area";
    }

    public AreaMapper[] getMappers() {
        return new AreaMapper[] {
            STCS_MAPPER,
            POLYGON_MAPPER,
            CIRCLE_MAPPER,
            POINT_MAPPER,
        };
    }

    public AreaMapper getProbableMapper( ValueInfo info ) {
        if ( info == null ) {
            return null;
        }
        for ( DomainMapper mapper : info.getDomainMappers() ) {
            if ( mapper instanceof AreaMapper ) {
                return (AreaMapper) mapper;
            }
        }
        Class<?> clazz = info.getContentClass();
        String name = info.getName();
        String xtype = Tables.getXtype( info );
        String ucd = info.getUCD();
        if ( clazz.equals( String.class ) ) {
            if ( "s_region".equals( name ) ||
                 "pos.outline;obs.field".equals( ucd ) ||
                 ( ucd != null && ucd.startsWith( "pos.outline" ) ) ) {
                return STCS_MAPPER;
            }
            else {
                return null;
            }
        }
        else if ( isNumArray( clazz ) ) {
            if ( "circle".equalsIgnoreCase( xtype ) ) {
                return CIRCLE_MAPPER;
            }
            else if ( "polygon".equalsIgnoreCase( xtype ) ) {
                return POLYGON_MAPPER;
            }
            else if ( "point".equalsIgnoreCase( xtype ) ) {
                return POINT_MAPPER;
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }

    public AreaMapper getPossibleMapper( ValueInfo info ) {
        if ( info == null ) {
            return null;
        }
        for ( DomainMapper mapper : info.getDomainMappers() ) {
            if ( mapper instanceof AreaMapper ) {
                return (AreaMapper) mapper;
            }
        }
        Class<?> clazz = info.getContentClass();
        if ( String.class.equals( clazz ) ) {
            return STCS_MAPPER;
        }
        else if ( isNumArray( clazz ) ) {
            int[] shape = info.getShape();
            if ( shape != null && shape.length == 1 ) {
                int nel = shape[ 0 ];
                if ( nel == 3 ) {
                    return CIRCLE_MAPPER;
                }
                else if ( nel == 2 ) {
                    return POINT_MAPPER;
                }
                else if ( nel % 2 == 0 && nel >= 6 || nel < 0 ) {
                    return POLYGON_MAPPER;
                }
                else {
                    return null;
                }
            }
            else if ( shape == null ) {
                return POLYGON_MAPPER;
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }

    /**
     * Indicates whether a given class is a numerical array of the sort
     * that the mappers implemented within this class can use.
     *
     * @param   clazz  source value class
     * @param   true iff clazz is a double[] or float[] array
     */
    private static boolean isNumArray( Class<?> clazz ) {
        return double[].class.equals( clazz )
            || float[].class.equals( clazz );
    }

    /**
     * Returns a mapper that can turn STC-S Strings into areas.
     *
     * @return  area mapper instance
     */
    private static AreaMapper createStcsMapper() {
        String descrip = new StringBuffer()
            .append( "Region description using STC-S syntax;\n" )
            .append( "see <webref " )
            .append( "url='http://www.ivoa.net/documents/TAP/20100327/'>" )
            .append( "TAP 1.0</webref>, section 6.\n" )
            .append( "Note there are some restrictions:\n" )
            .append( "<code>&lt;frame&gt;</code>, " )
            .append( "<code>&lt;refpos&gt;</code> and " )
            .append( "<code>&lt;flavor&gt;</code> metadata are ignored,\n" )
            .append( "polygon winding direction is ignored " )
            .append( "(small polygons are assumed)\n" )
            .append( "and the <code>INTERSECTION</code> and <code>NOT</code> " )
            .append( "constructions are not supported." )
            .toString();
        return new AreaMapper( "STC-S", descrip, String.class ) {
            public Function<Object,Area> areaFunction( Class<?> clazz ) {
                if ( String.class.equals( clazz ) ) {
                    return obj -> obj instanceof String
                                ? stcsArea( (String) obj, true )
                                : null;
                }
                else {
                    return null;
                }
            }
        };
    }

    /**
     * Returns a mapper that can turn a particular type of area represented
     * as a numerical array as described in DALI 1.1. section 3.3
     * into an area object.
     *
     * @param   areaType   area type
     * @param   descrip    description of mapping type
     */
    private static AreaMapper createDaliMapper( final Area.Type areaType,
                                                String descrip ) {
        return new AreaMapper( areaType.toString(), descrip, Object.class ) {
            public Function<Object,Area> areaFunction( Class<?> clazz ) {
                if ( double[].class.equals( clazz ) ) {
                    return obj -> {
                        if ( obj instanceof double[] ) {
                            double[] data = (double[]) obj;
                            if ( areaType.isLegalArrayLength( data.length ) ) {
                                return new Area( areaType, data );
                            }
                        }
                        return null;
                    };
                }
                else if ( float[].class.equals( clazz ) ) {
                    return obj -> {
                        if ( obj instanceof float[] ) {
                            float[] fdata = (float[]) obj;
                            int nd = fdata.length;
                            if ( areaType.isLegalArrayLength( nd ) ) {
                                double[] ddata = new double[ nd ];
                                for ( int i = 0; i < nd; i++ ) {
                                    ddata[ i ] = fdata[ i ];
                                }
                                return new Area( areaType, ddata );
                            }
                        }
                        return null;
                    };
                }
                else {
                    return null;
                }
            }
        };
    }

    /**
     * Decodes an STC-S string as an Area object.
     * Support is not complete: no INTERSECTION, no NOT;
     * frame, refpos and flavor are ignored;
     * polygon winding direction is ignored (small polygon is assumed).
     *
     * @param   stcs   STC-S string
     * @param   allowPoint  true if POINT type is acceptable
     * @return   area specified, or null
     */
    private static Area stcsArea( CharSequence stcs, boolean allowPoint ) {
        Matcher w0matcher = WORDS_PATTERN.matcher( stcs );
        if ( w0matcher.lookingAt() ) {
            String word0 = w0matcher.group( 1 ).toUpperCase();
            CharSequence remainder =
                stcs.subSequence( w0matcher.end(), stcs.length() );
            if ( "CIRCLE".equals( word0 ) ) {
                Area.Type circleType = Area.Type.CIRCLE;
                double[] numbers = getNumbers( remainder );
                return circleType.isLegalArrayLength( numbers.length )
                     ? new Area( circleType, numbers )
                     : null;
            }
            else if ( "POLYGON".equals( word0 ) ) {
                Area.Type polygonType = Area.Type.POLYGON;
                double[] numbers = getNumbers( remainder );
                return polygonType.isLegalArrayLength( numbers.length )
                     ? new Area( polygonType, numbers )
                     : null;
            }
            else if ( "BOX".equals( word0 ) ) {
                Area.Type polygonType = Area.Type.POLYGON;
                double[] numbers = getNumbers( remainder );
                return numbers.length == 4
                     ? new Area( polygonType,
                                 new double[] {
                                     numbers[ 0 ],
                                     numbers[ 1 ],
                                     numbers[ 0 ] + numbers[ 2 ],
                                     numbers[ 1 ] + numbers[ 3 ],
                                 } )
                     : null;
            }
            else if ( allowPoint && "POSITION".equals( word0 ) ) {
                Area.Type pointType = Area.Type.POINT;
                double[] numbers = getNumbers( remainder );
                return pointType.isLegalArrayLength( numbers.length )
                     ? new Area( pointType, numbers )
                     : null;
            }
            else if ( "BOX".equals( word0 ) ) {
                Area.Type polygonType = Area.Type.POLYGON;
                double[] numbers = getNumbers( remainder );
                return numbers.length == 4
                     ? new Area( polygonType,
                                 new double[] {
                                     numbers[ 0 ],
                                     numbers[ 1 ],
                                     numbers[ 0 ] + numbers[ 2 ],
                                     numbers[ 1 ] + numbers[ 3 ],
                                 } )
                     : null;
            }
            else if ( allowPoint && "POSITION".equals( word0 ) ) {
                Area.Type pointType = Area.Type.POINT;
                double[] numbers = getNumbers( remainder );
                return pointType.isLegalArrayLength( numbers.length )
                     ? new Area( pointType, numbers )
                     : null;
            }
            else if ( "UNION".equals( word0 ) ) {
                Matcher parenMatcher = PARENTHESIS_PATTERN.matcher( remainder );
                if ( ! parenMatcher.matches() ) {
                    return null;
                }
                Matcher termMatcher =
                    TERM_PATTERN.matcher( parenMatcher.group( 1 ) );
                List<Area> plist = new ArrayList<Area>();
                while ( termMatcher.find() ) {
                    Area termArea = stcsArea( termMatcher.group(), allowPoint );
                    if ( termArea != null &&
                         termArea.getType() == Area.Type.POLYGON ) {
                        plist.add( termArea );
                    }
                    else {
                        return null;
                    }
                }
                DoubleList dlist = new DoubleList();
                for ( Area poly : plist ) {
                    if ( dlist.size() > 0 ) {
                        dlist.add( Double.NaN );
                        dlist.add( Double.NaN );
                    }
                    dlist.addAll( poly.getDataArray() );
                }
                return new Area( Area.Type.POLYGON, dlist.toDoubleArray() );
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }

    /**
     * Parses a whitespace-separated list of floating-point values.
     *
     * @param   cseq   input text 
     * @return   array of numeric values parsed from input
     */
    private static double[] getNumbers( CharSequence cseq ) {
        Matcher matcher = NUMBER_PATTERN.matcher( cseq );
        DoubleList dlist = new DoubleList(); 
        while ( matcher.find() ) {
            dlist.add( Double.parseDouble( matcher.group( 1 ) ) );
        }
        return dlist.toDoubleArray(); 
    }
}
