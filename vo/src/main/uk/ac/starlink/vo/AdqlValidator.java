package uk.ac.starlink.vo;

import adql.db.DBChecker;
import adql.db.DBColumn;
import adql.db.DBIdentifier;
import adql.db.DBTable;
import adql.db.DefaultDBColumn;
import adql.db.DefaultDBTable;
import adql.db.FunctionDef;
import adql.parser.ADQLParser;
import adql.parser.ADQLQueryFactory;
import adql.parser.QueryChecker;
import adql.parser.feature.FeatureSet;
import adql.parser.feature.LanguageFeature;
import adql.parser.grammar.ParseException;
import adql.parser.grammar.TokenMgrError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import uk.ac.starlink.util.ContentCoding;

/**
 * Handles validation of ADQL queries.
 * In the current implementation the heavy lifting is done by 
 * Gregory Mantelet's ADQL parser.
 *
 * @author   Mark Taylor
 * @since    3 Oct 2011
 */
public class AdqlValidator {

    private final ADQLParser parser_;
    private AdqlVersion version_;
    private boolean isAllowAnyUdf_;

    private static final Logger logger_ =
        Logger.getLogger( "uk.ac.starlink.vo" );

    static final String ADQLGEO_FEATURE_TYPE_VOLLT =
        // Correct, but only in later ADQL 2.1 versions
        TapCapability.TAPREGEXT_STD_URI + "#features-adqlgeo";

    static final String ADQLGEO_FEATURE_TYPE_VARIANT =
        // Incorrect, but appeared in earlier ADQL 2.1 draft
        TapCapability.TAPREGEXT_STD_URI + "#features-adql-geo";

    /**
     * Constructor.
     * Note empty arrays restrict possibilities (to none), but null values
     * allow anything.
     *
     * @param  version  ADQL language version, or null for default
     * @param  featureSet  ADQL supported feature set, or null for all features
     * @param  vtables  table metadata for database to be checked against,
     *                  or null for no checking
     */
    private AdqlValidator( ADQLParser.ADQLVersion version,
                           FeatureSet featureSet, ValidatorTable[] vtables ) {
        if ( version == null ) {
            version = ADQLParser.ADQLVersion.V2_0;
        }
        if ( featureSet == null ) {
            featureSet = new FeatureSet( true );
        }
        QueryChecker checker = vtables == null
                             ? null
                             : new DBChecker( Arrays.stream( vtables )
                                             .map( ValidatorDBTable::new )
                                             .collect( Collectors.toList() ) );
        ADQLQueryFactory qfact = new ADQLQueryFactory();
        parser_ = new ADQLParser( version, checker, qfact, featureSet );
    }

    /**
     * Configures whether the parser will allow undeclared functions.
     *
     * @param  isAllowed  true to allow undeclared functions, false to reject
     */
    public void setAllowAnyUdf( boolean isAllowed ) {
        parser_.allowAnyUdf( isAllowed );
    }

    /**
     * Validates an ADQL string.
     * Any throwable returned hopefully includes useful information about
     * the location and nature of the parse error, but that depends on the
     * implementation.
     *
     * @param  query   ADQL query string
     * @throws  ParseException   if the string is not valid ADQL
     * @throws  TokenMgrError   if something has gone wrong with the parsing
     */
    public void validate( String query ) throws ParseException, TokenMgrError {
        parser_.parseQuery( query );
    }

    /**
     * Attempts to fix common errors in a submitted query.
     * If some changes can be made that would make the query more correct,
     * the fixed query is returned.  If no such changes can be made for
     * whatever reason, null is returned.
     *
     * @param   query   input ADQL
     * @return   EITHER ADQL which resembles, but is not identical to,
     *           the input but which has a better chance of being correct;
     *           OR null
     */
    public String fixup( String query ) {
        if ( query != null && query.trim().length() > 0 ) {
            try {
                String fixQuery = parser_.tryQuickFix( query );
                return query.equals( fixQuery ) ? null : fixQuery;
            }
            catch ( ParseException e ) {
                return null;
            }
        }
        else {
            return null;
        }
    }

    /**
     * Returns a basic validator instance.
     *
     * @return  new vanilla validator
     */
    public static AdqlValidator createValidator() {
        return new AdqlValidator( (ADQLParser.ADQLVersion) null,
                                  (FeatureSet) null, (ValidatorTable[]) null );
    }

    /**
     * Returns a validator instance that knows about available tables.
     *
     * @param  vtables  table metadata for database to be checked against
     * @return  vanilla validator
     */
    public static AdqlValidator createValidator( ValidatorTable[] vtables ) {
        return new AdqlValidator( (ADQLParser.ADQLVersion) null,
                                  (FeatureSet) null, vtables );
    }

    /**
     * Creates an instance given a set of table metadata and a TapLanguage
     * description object.
     * The language object's TapLanguageFeature map is examined to determine
     * what UDFs and other optional features are supported.
     * In the case that no features are declared, no restrictions are imposed.
     *
     * @param  vtables  table metadata
     * @param  lang   language specifics
     */
    public static AdqlValidator createValidator( ValidatorTable[] vtables,
                                                 TapLanguage lang ) {

        /* Prepare to extract from the TapLanguage object a list of
         * LanguageFeatures that can be passed to the parser. */
        Map<String,TapLanguageFeature[]> featMap =
            lang == null ? null : new LinkedHashMap<>( lang.getFeaturesMap() );
        if ( featMap == null ) {
            featMap = new HashMap<String,TapLanguageFeature[]>();
        }
        List<LanguageFeature> features = new ArrayList<>();

        /* Doctor map for ADQL geo features; there has been confusion in
         * standards about the correct form, so accept either and tag them
         * as the form the ADQL parser library is currently accepting. */
        TapLanguageFeature[] variantGeoFeats =
            featMap.remove( ADQLGEO_FEATURE_TYPE_VARIANT );
        if ( variantGeoFeats != null ) {
            featMap.put( ADQLGEO_FEATURE_TYPE_VOLLT, variantGeoFeats );
        }

        /* Treat UDFs specially. */
        TapLanguageFeature[] udfFeats =
            featMap.remove( TapCapability.UDF_FEATURE_TYPE );
        if ( udfFeats != null ) {
            for ( TapLanguageFeature udfFeat : udfFeats ) {
                String form = udfFeat.getForm();
                try {
                    FunctionDef udf = FunctionDef.parse( form );
                    features.add( udf.toLanguageFeature() );
                }
                catch ( ParseException e ) {

                    /* Arguably this should be a WARNING, but at time of
                     * writing the ADQL library fails to parse many
                     * reasonable UDFs, since it doesn't know about
                     * ADQL 2.1 types.  So demote it to an INFO for now
                     * to reduce logging noise. */
                    logger_.log( Level.INFO,
                                 "Failed to parse UDF def \"" + form + "\"",
                                 e );
                }
            }
        }

        /* Now add all the other language features. */
        for ( Map.Entry<String,TapLanguageFeature[]> entry :
              featMap.entrySet() ) {
            String type = entry.getKey();
            assert !TapCapability.UDF_FEATURE_TYPE.equals( type ) &&
                   !ADQLGEO_FEATURE_TYPE_VARIANT.equals( type );
            for ( TapLanguageFeature feat : entry.getValue() ) {
                features.add( new LanguageFeature( type, feat.getForm(), true,
                                                   feat.getDescription() ) );
            }
        }

        /* If any features have been defined, mark them as supported in
         * the FeatureSet.  If none have, assume that somebody is too lazy
         * to do feature declaration and treat all features as supported. */
        final FeatureSet featureSet;
        if ( features.size() > 0 ) {
            featureSet = new FeatureSet( false );
            for ( LanguageFeature f : features ) {
                featureSet.support( f );
            }
        }
        else {
            featureSet = new FeatureSet( true );
        }

        /* Construct a suitable validator object. */
        ADQLParser.ADQLVersion version = null;
        return new AdqlValidator( version, featureSet, vtables );
    }

    /**
     * Tests parser.  Use <code>-h</code> for usage.
     */
    public static void main( String[] args )
            throws ParseException,
            java.io.IOException, org.xml.sax.SAXException {
        String usage = "\n   Usage: " + AdqlValidator.class.getName()
                     + " [-meta <tmeta-url>]"
                     + " <query>"
                     + "\n";
        SchemaMeta[] schMetas = null;
        ArrayList<String> argList =
        new ArrayList<String>( java.util.Arrays.asList( args ) );
        for ( Iterator<String> it = argList.iterator(); it.hasNext(); ) {
            String arg = it.next();
            if ( arg.startsWith( "-h" ) ) {
                System.out.println( usage );
                return;
            }
            else if ( arg.equals( "-meta" ) && it.hasNext() ) {
                it.remove();
                String loc = it.next();
                it.remove();
                schMetas = TableSetSaxHandler
                          .readTableSet( new java.net.URL( loc ),
                                         ContentCoding.GZIP );
            }
        }
        if ( argList.size() != 1 ) {
            System.err.println( usage );
            System.exit( 1 );
            return;
        }
        String query = argList.remove( 0 );
        final ValidatorTable[] vtables;
        if ( schMetas != null ) {
            List<ValidatorTable> vtList = new ArrayList<ValidatorTable>();
            for ( SchemaMeta schMeta : schMetas ) {
                final String sname = schMeta.getName();
                for ( TableMeta tmeta : schMeta.getTables() ) {
                    final String tname = tmeta.getName();
                    final Collection<String> colNames;
                    ColumnMeta[] cmetas = tmeta.getColumns();
                    if ( cmetas == null ) {
                        colNames = null;
                    }
                    else {
                        colNames = new ArrayList<String>();
                        for ( ColumnMeta cmeta : cmetas ) {
                            colNames.add( cmeta.getName() );
                        }
                    }
                    vtList.add( new ValidatorTable() {
                        public String getSchemaName() {
                            return sname;
                        }
                        public String getTableName() {
                            return tname;
                        }
                        public Collection<String> getColumnNames() {
                            return colNames;
                        }
                    } );
                }
            }
            vtables = vtList.toArray( new ValidatorTable[ 0 ] );
        }
        else {
            vtables = null;
        }
        createValidator( vtables ).validate( query );
    }

    /**
     * Defines table metadata for tables known to the validator.
     */
    public interface ValidatorTable {

        /**
         * Returns the fully-qualified name of this table,
         * which may include a schema part.
         *
         * @return  table name
         */
        String getTableName();

        /**
         * Returns the name of the schema to which this table belongs,
         * if known.  In practice, it only seems to be necessary if
         * the table name does not include a schema part.
         *
         * @return  schema name
         */
        String getSchemaName();

        /**
         * Returns a collection of column names associated with this table.
         * A null return value means that the list of column names is
         * not known.
         *
         * <p>The return value of this call may change over the lifetime of
         * this object.
         *
         * @return  column array, or null
         */
        Collection<String> getColumnNames();
    }

    /**
     * DBTable implementation that adapts a ValidatorTable instance.
     * Some of the implementation was done with reference to the
     * source code of the adql.db.DefaultDBTable class.
     */
    private static class ValidatorDBTable implements DBTable {
        private final ValidatorTable vtable_;
        private String name_;
        private String schemaName_;
        private String catalogName_;
        private AdqlSyntax syntax_;
  
        /**
         * Constructor.
         *
         * @param    vtable  validator table supplying behaviour
         */
        ValidatorDBTable( ValidatorTable vtable ) {
            vtable_ = vtable;
            syntax_ = AdqlSyntax.getInstance();
            String[] names =
                syntax_.getCatalogSchemaTable( vtable.getTableName() );

            /* I'm still not certain I'm assigning the schema name here
             * correctly, or exactly what it's used for. */
            if ( names != null ) {
                name_ = names[ 2 ];
                schemaName_ = vtable.getSchemaName();
                catalogName_ = names[ 0 ];
            }
            else {
                name_ = vtable.getTableName();
                schemaName_ = vtable.getSchemaName();
                catalogName_ = null;
            }
        }

        public boolean isCaseSensitive() {
            return false;
        }

        public String getADQLName() {
            return syntax_.unquote( name_ );
        }

        public String getDBName() {
            return name_;
        }

        public String getADQLSchemaName() {
            return syntax_.unquote( schemaName_ );
        }

        public String getDBSchemaName() {
            return schemaName_;
        }

        public String getADQLCatalogName() {
            return syntax_.unquote( catalogName_ );
        }

        public String getDBCatalogName() {
            return catalogName_;
        }

        public DBColumn getColumn( String colName, boolean isAdqlName ) {

            /* Note the value of vtable.getColumnNames may change over the
             * lifetime of the vtable object, so don't cache the result. */
            Collection<String> cnames = vtable_.getColumnNames();
            if ( cnames == null ) {
                return createDBColumn( colName );
            }
            else {
                for ( String cn : cnames ) {
                    if ( colName.equals( isAdqlName ? syntax_.unquote( cn )
                                                    : cn ) ) {
                        return createDBColumn( cn );
                    }
                }
                return null;
            }
        }

        public Iterator<DBColumn> iterator() {

            /* Note the value of vtable.getColumnNames may change over the
             * lifetime of the vtable object, so don't cache the result. */
            Collection<String> cnames = vtable_.getColumnNames();
            if ( cnames == null ) {
                return new ArrayList<DBColumn>().iterator();
            }
            else {
                final Iterator<String> it = cnames.iterator();
                return new Iterator<DBColumn>() {
                    public boolean hasNext() {
                        return it.hasNext();
                    }
                    public DBColumn next() {
                        return createDBColumn( it.next() );
                    }
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        }

        public DBTable copy( String dbName, String adqlName ) {
            DefaultDBTable copy =
                new DefaultDBTable( getADQLCatalogName(), catalogName_,
                                    getADQLSchemaName(), schemaName_,
                                    adqlName, dbName );
            copy.setCaseSensitive( isCaseSensitive() );
            for ( DBColumn col : this ) {
                copy.addColumn( col.copy( col.getDBName(), col.getADQLName(),
                                          copy ) );
            }
            return copy;
        }

        private DBColumn createDBColumn( String colName ) {
            String rawColName = syntax_.unquote( colName );
            return new DefaultDBColumn( colName, rawColName, this );
        }
    }
}
