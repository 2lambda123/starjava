package uk.ac.starlink.tfcat;

import ari.ucidy.UCD;
import ari.ucidy.UCDParser;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import uk.me.nxg.unity.OneUnit;
import uk.me.nxg.unity.Syntax;
import uk.me.nxg.unity.UnitExpr;
import uk.me.nxg.unity.UnitParser;
import uk.me.nxg.unity.UnitParserException;

/**
 * Utilities for use with TFCat classes.
 *
 * @author   Mark Taylor
 * @since    9 Feb 2022
 */
public class TfcatUtil {

    private static final TfcatObject[] NO_TFCATS = new TfcatObject[ 0 ];
    private static final Logger logger_ =
        Logger.getLogger( "uk.ac.starlink.tfcat" );
    private static final Logger ucidyLogger_ = Logger.getLogger( "ari.ucidy" );
    private static WordChecker ucdChecker_;
    private static WordChecker unitChecker_;

    /**
     * Private sole constructor prevents instantiation.
     */
    private TfcatUtil() {
    }

    /**
     * Performs a validating parse of a given TFCat text.
     *
     * @param  jsonTxt  TFCat text
     * @param  reporter   error message destination
     * @return   TFCat object, or null in case of fatal parse error
     */
    public static TfcatObject parseTfcat( String jsonTxt, Reporter reporter ) {
        if ( jsonTxt == null ) {
            return null;
        }
        if ( reporter == null ) {
            reporter = DummyReporter.INSTANCE;
        }
        if ( getFirstChar( jsonTxt ) != '{' || getLastChar( jsonTxt ) != '}' ) {
            reporter.report( "Not JSON object" );
        }
        final JSONObject json;
        try {
            json = new JSONObject( jsonTxt );
        }
        catch ( JSONException e ) {
            reporter.report( "Bad JSON: " + e.getMessage() );
            return null;
        }
        TfcatObject tfcat = Decoders.TFCAT.decode( reporter, json, null );
        if ( tfcat != null && reporter != DummyReporter.INSTANCE ) {
            checkBoundingBoxes( reporter, tfcat );
            checkCrs( reporter, tfcat );
        }
        return tfcat;
    }

    /**
     * Returns any direct children of a TfcatObject that are themselves
     * TfcatObjects.  The relationship depends a bit on the object type,
     * but traversing the tree in this way will get you all the TfcatObjects
     * it contains.
     *
     * @param  tfcat  parent
     * @return   children
     */
    public static TfcatObject[] getChildren( TfcatObject tfcat ) {
        if ( tfcat instanceof FeatureCollection ) {
            return ((FeatureCollection) tfcat).getFeatures();
        }
        else if ( tfcat instanceof Feature ) {
            return new TfcatObject[] { ((Feature) tfcat).getGeometry() };
        }
        else if ( tfcat instanceof Geometry<?> ) {
            Object shape = ((Geometry<?>) tfcat).getShape();
            return shape instanceof Geometry<?>[]
                 ? (Geometry<?>[]) shape
                 : NO_TFCATS;
        }
        else {
            assert false;
            return NO_TFCATS;
        }
    }

    /**
     * Returns all the Geometries that are descendents of a given TFCat object,
     * including itself if applicable.
     *
     * @param  tfcat  root
     * @return   all geometries in tree
     */
    public static List<Geometry<?>> getAllGeometries( TfcatObject tfcat ) {
        if ( tfcat instanceof Geometry<?> ) {
            Geometry<?> geom = (Geometry<?>) tfcat;
            Object shape = geom.getShape();
            if ( shape instanceof Geometry<?>[] ) {
                List<Geometry<?>> list = new ArrayList<>();
                for ( Geometry<?> g : (Geometry<?>[]) shape ) {
                    list.addAll( getAllGeometries( g ) );
                }
                return list;
            }
            else {
                return Collections.singletonList( geom );
            }
        }
        else if ( tfcat instanceof FeatureCollection ) {
            List<Geometry<?>> list = new ArrayList<>();
            for ( Feature feat : ((FeatureCollection) tfcat).getFeatures() ) {
                list.addAll( getAllGeometries( feat.getGeometry() ) );
            }
            return list;
        }
        else if ( tfcat instanceof Feature ) {
            return getAllGeometries( ((Feature) tfcat).getGeometry() );
        }
        else {
            assert false;
            return Collections.emptyList();
        }
    }

    /**
     * Returns all the Position objects contained in a TFCat object and
     * its descendents.
     *
     * @param  tfcat  TFCat object
     * @return   all contained positions
     */
    public static List<Position> getAllPositions( TfcatObject tfcat ) {
        List<Position> list = new ArrayList<>();
        for ( Geometry<?> geom : getAllGeometries( tfcat ) ) {
            list.addAll( getPositionsFromShape( geom.getShape() ) );
        }
        return list;
    }

    /**
     * Performs a global check for a given TfcatObject that any positions
     * listed are within the bounding boxes in whose scope they appear.
     *
     * @param  reporter   error message destination
     * @param  tfcat  TFCat object
     */
    public static void checkBoundingBoxes( Reporter reporter,
                                           TfcatObject tfcat ) {
        Reporter tReporter = reporter.createReporter( tfcat.getType() );
        for ( TfcatObject subObj : TfcatUtil.getChildren( tfcat ) ) {
            checkBoundingBoxes( tReporter, subObj );
        }
        Bbox bbox = tfcat.getBbox();
        if ( bbox != null ) {
            for ( Position pos : TfcatUtil.getAllPositions( tfcat ) ) {
                if ( ! bbox.isInside( pos ) ) {
                    tReporter.report( "bounding box violation"
                                    + " (" + pos + " outside " + bbox + ")" );
                    return;
                }
            }
        }
    }

    /**
     * Returns a CRS object applying to the given TFCat object.
     * This may exist on the object itself or on one of its ancestors.
     * The return value may be null, but probably ought not to be for a
     * legal TFCat object.
     *
     * @param tfcat  target object
     * @return  CRS object, may be null
     */
    public static Crs getCrsInScope( TfcatObject tfcat ) {
        return getMemberInScope( tfcat, TfcatObject::getCrs );
    }

    /**
     * Returns a Bounding Box objecct applying to the given TFCat object.
     * This may exist on the object itself or on one of its ancestors.
     *
     * @param tfcat  target object
     * @return  Bbox object, may be null
     */
    public static Bbox getBboxInScope( TfcatObject tfcat ) {
        return getMemberInScope( tfcat, TfcatObject::getBbox );
    }

    /**
     * Returns an attribute that may exist on a TFCat object or one of
     * its ancestors.  The first non-null value working backwards is returned.
     *
     * @param tfcat  target object
     * @param get   acquires typed result from a TFCat object
     * @return  typed result, may be null if null for all self-or-ancestors
     */
    private static <T> T getMemberInScope( TfcatObject tfcat,
                                           Function<TfcatObject,T> get ) {
        T member0 = get.apply( tfcat );
        if ( member0 != null ) {
            return member0;
        }
        TfcatObject parent = tfcat.getParent();
        return parent == null ? null : getMemberInScope( parent, get );
    }

    /**
     * Ensure that CRS objects are in place for the tree rooted at the
     * given TFCat Object.
     * Usually that will mean that the given object has a CRS object of its
     * own, but as long as all of its Geometry descendants have CRS objects
     * in scope, it's OK.
     * If CRS objects are missing, a message is written to the reporter.
     *
     * @param  reporter   message destination
     * @param  tfcat     object to check
     */
    public static void checkCrs( Reporter reporter, TfcatObject tfcat ) {

        /* Usually, the CRS will be defined in the top-level object.
         * If that's the case, then all descendants will have an ancestor
         * CRS, so everything is in order. */
        if ( getCrsInScope( tfcat ) != null ) {
            return;
        }

        /* Other arrangements are possible.  Test every Geometry to see
         * whether it has an ancestor CRS.  There may be more efficient
         * ways of doing this, but it's not likely to be a hugely expensive
         * or very common operation. */
        if ( ! allGeometriesHaveCrs( tfcat ) ) {
            reporter.report( "No top-level CRS, and "
                           + "at least one Geometry lacks CRS" );
        }
    }

    /**
     * Checks whether a given token is in a supplied list of valid options.
     * No special handling is performed for null values.
     *
     * @param   reporter  destination for reports if token is not valid
     * @param   token   token to test
     * @param   options  valid token values
     */
    public static void checkOption( Reporter reporter, String token,
                                    Collection<String> options ) {
        if ( ! options.contains( token ) ) {
            reporter.report( "Disallowed value \"" + token + "\""
                           + " (not in " + options + ")" );
        }
    }

    /**
     * Returns a syntax checker for UCDs.
     *
     * @return  UCD checker
     */
    public static WordChecker getUcdChecker() {
        if ( ucdChecker_ == null ) {
            ucdChecker_ = createUcdChecker();
        }
        return ucdChecker_;
    }

    /**
     * Returns a syntax checker for VOUnits.
     *
     * @return  unit checker
     */
    public static WordChecker getUnitChecker() {
        if ( unitChecker_ == null ) {
            unitChecker_ = createUnitChecker();
        }
        return unitChecker_;
    }

    /**
     * Creates a checker for UCDs.
     *
     * @return new UCD checker
     */
    private static WordChecker createUcdChecker() {
        try {
            UCDParser.class.toString();
        }
        catch ( NoClassDefFoundError e ) {
            logger_.warning( "Ucidy library unavailable: no UCD checking" );
            return ucd -> null;
        }
        ucidyLogger_.setLevel( Level.OFF );
        return ucd -> {
            UCD pucd = UCDParser.defaultParser.parse( ucd );
            Iterator<String> errit = pucd.getErrors();
            return errit.hasNext() ? errit.next() : null;
        };
    }

    /**
     * Creates a checker for VOUnits.
     *
     * @return  new unit checker
     */
    private static WordChecker createUnitChecker() {
        try {
            UnitParser.class.toString();
        }
        catch ( NoClassDefFoundError e ) {
            logger_.warning( "Unity library unavailable: no unit checking" );
            return unit -> null;
        }
        return unit -> {
            Syntax syntax = Syntax.VOUNITS;
            UnitExpr punit;
            try {
                punit = new UnitParser( syntax, unit ).getParsed();
            }
            catch ( Exception e ) {
                return "bad unit \"" + unit + "\" (" + e.getMessage() + ")";
            }
            if ( punit.isFullyConformant( syntax ) ) {
                return null;
            }
            else {
                for ( OneUnit word : punit ) {
                    if ( ! word.isRecognisedUnit( syntax ) ) {
                        return "unrecognised unit \"" + word + "\"";
                    }
                    else if ( ! word.isRecommendedUnit( syntax ) ) {
                        return "deprecated unit \"" + word + "\"";
                    }
                }
                return "unidentified problem with unit \"" + unit + "\"";
            }
        };
    }

    /**
     * Returns all the positions contained in a geometry's shape.
     *
     * @param  shape  Position, LinearRing, or single/multi-dimensional
     *                array of same
     * @return  all positions in shape and descendents
     */
    private static List<Position> getPositionsFromShape( Object shape ) {
        if ( shape == null ) {
            return Collections.emptyList();
        }
        else if ( shape instanceof Position ) {
            return Collections.singletonList( (Position) shape );
        }
        else if ( shape instanceof LinearRing ) {
            return getPositionsFromShape( ((LinearRing) shape)
                                         .getDistinctPositions() );
        }
        else if ( shape.getClass().isArray() ) {
            List<Position> list = new ArrayList<>();
            for ( int i = 0; i < Array.getLength( shape ); i++ ) {
                list.addAll( getPositionsFromShape( Array.get( shape, i ) ) );
            }
            return list;
        }
        else {
            assert false;
            return Collections.emptyList();
        }
    }

    /**
     * Tests whether all the TFCat objects with the given root that are
     * of type Geometry (and not GeometryCollection) have a CRS object
     * in scope.
     */
    private static boolean allGeometriesHaveCrs( TfcatObject tfcat ) {
        if ( tfcat instanceof Geometry &&
             ! ( tfcat instanceof Geometry.GeometryCollection ) &&
             getCrsInScope( tfcat ) == null ) {
            return false;
        }
        else {
            for ( TfcatObject subObj : getChildren( tfcat ) ) {
                if ( ! allGeometriesHaveCrs( subObj ) ) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Returns the first non-blank character in a given string.
     *
     * @param  txt  string
     * @return  first non-blank character, if any
     */
    private static char getFirstChar( String txt ) {
        int leng = txt.length();
        for ( int ic = 0; ic < leng; ic++ ) {
            char c = txt.charAt( ic );
            switch ( c ) {
                case ' ':
                case '\n':
                case '\r':
                case '\t':
                    break;
                default:
                    return c;
            }
        }
        return ' ';
    }

    /**
     * Returns the last non-blank character in a given string.
     *
     * @param  txt  string
     * @return   last non-blank character, if any
     */
    private static char getLastChar( String txt ) {
        for ( int ic = txt.length() - 1; ic >= 0; ic-- ) {
            char c = txt.charAt( ic );
            switch ( c ) {
                case ' ':
                case '\n':
                case '\r':
                case '\t':
                    break;
                default:
                    return c;
            }
        }
        return ' ';
    }
}
