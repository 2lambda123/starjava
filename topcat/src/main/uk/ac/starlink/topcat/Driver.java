package uk.ac.starlink.topcat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.astrogrid.samp.JSamp;
import org.astrogrid.samp.httpd.UtilServer;
import uk.ac.starlink.plastic.PlasticHub;
import uk.ac.starlink.plastic.PlasticUtils;
import uk.ac.starlink.table.DefaultValueInfo;
import uk.ac.starlink.table.DescribedValue;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.StarTableFactory;
import uk.ac.starlink.table.StoragePolicy;
import uk.ac.starlink.table.TableBuilder;
import uk.ac.starlink.table.TableFormatException;
import uk.ac.starlink.table.Tables;
import uk.ac.starlink.table.WrapperStarTable;
import uk.ac.starlink.table.gui.TableLoadChooser;
import uk.ac.starlink.table.gui.TableLoadDialog;
import uk.ac.starlink.table.gui.SQLReadDialog;
import uk.ac.starlink.table.jdbc.TextModelsAuthenticator;
import uk.ac.starlink.topcat.interop.TopcatCommunicator;
import uk.ac.starlink.topcat.soap.TopcatSOAPServer;
import uk.ac.starlink.ttools.Stilts;
import uk.ac.starlink.util.gui.ErrorDialog;
import uk.ac.starlink.util.DataSource;
import uk.ac.starlink.util.Loader;
import uk.ac.starlink.util.URLDataSource;
import uk.ac.starlink.util.URLUtils;
import uk.ac.starlink.votable.VOElementFactory;

/**
 * Main class for invoking the TOPCAT application from scratch.
 * Contains some useful static configuration-type methods as well
 * as the {@link #main} method itself.
 *
 * @author   Mark Taylor (Starlink)
 * @since    9 Mar 2004
 */
public class Driver {

    private static boolean standalone = false;
    private static boolean securityChecked;
    private static Boolean canRead;
    private static Boolean canWrite;
    private static StarTable[] demoTables;
    private static Logger logger = Logger.getLogger( "uk.ac.starlink.topcat" );
    private static StarTableFactory tabfact;
    private static ControlWindow control;
    private static String[] extraLoaders;
    private static final ValueInfo DEMOLOC_INFO = 
        new DefaultValueInfo( "DemoLoc", String.class, "Demo file location" );
    private static final int DEFAULT_SERVER_PORT = 2525;
    private static String[] KNOWN_DIALOGS = new String[] {
        "uk.ac.starlink.table.gui.FileChooserLoader",
        "uk.ac.starlink.datanode.tree.TreeTableLoadDialog",
        SQLReadDialog.class.getName(),
        "uk.ac.starlink.vo.ConeSearchDialog",
        "uk.ac.starlink.vo.SiapTableLoadDialog",
        "uk.ac.starlink.vo.SsapTableLoadDialog",
        "uk.ac.starlink.vo.RegistryTableLoadDialog",
        "uk.ac.starlink.topcat.contrib.cds.VizierTableLoadDialog",
        "uk.ac.starlink.topcat.contrib.gavo.GavoTableLoadDialog",
    };

    /**
     * Determines whether TableViewers associated with this class should
     * act as a standalone application.  If <tt>standalone</tt> is set
     * true, then it will be possible to exit the JVM using menu items
     * etc in the viewer.  Otherwise, no normal activity within the
     * TableViewer GUI will cause a JVM exit.
     *
     * @param  standalone  whether this class should act as a standalone
     *         application
     */
    public static void setStandalone( boolean standalone ) {
        Driver.standalone = standalone;
    }

    /**
     * Indicates whether the TableViewer application is standalone or not.
     *
     * @return  whether this should act as a standalone application.
     */
    public static boolean isStandalone() {
        return standalone;
    }

    /**
     * Indicates whether the security context will permit reads from local
     * disk.
     *
     * @return  true iff reads are permitted
     */
    public static boolean canRead() {
        checkSecurity();
        return canRead.booleanValue();
    }

    /**
     * Indicates whether the security context will permit writes to local
     * disk.
     *
     * @return  true iff writes are permitted
     */
    public static boolean canWrite() {
        checkSecurity();
        return canWrite.booleanValue();
    }

    /**
     * Talks to the installed security manager to find out what is and
     * is not permitted.
     */
    private static void checkSecurity() {
        if ( ! securityChecked ) {
            SecurityManager sman = System.getSecurityManager();
            if ( sman == null ) {
                canRead = Boolean.TRUE;
                canWrite = Boolean.TRUE;
            }
            else {
                String readDir;
                String writeDir;
                try { 
                    readDir = System.getProperty( "user.home" );
                }
                catch ( SecurityException e ) {
                    readDir = ".";
                }
                try {
                    writeDir = System.getProperty( "java.io.tmpdir" );
                }
                catch ( SecurityException e ) {
                    writeDir = ".";
                }
                try {
                    sman.checkRead( readDir );
                    canRead = Boolean.TRUE;
                }
                catch ( SecurityException e ) {
                    canRead = Boolean.FALSE;
                }
                try {
                    sman.checkWrite( new File( writeDir, "tOpCTeSt.tmp" )
                                    .toString() );
                    canWrite = Boolean.TRUE;
                }
                catch ( SecurityException e ) {
                    canWrite = Boolean.FALSE;
                }
            }
            assert canRead != null;
            assert canWrite != null;
        }
    }

    /**
     * Main method for TOPCAT invocation.
     * Under normal circumstances this will pop up a ControlWindow and
     * populate it with tables named in the arguments.
     *
     * @param  args  list of flags and table specifications
     */
    public static void main( String[] args ) {
        try {
            Loader.checkJ2se();
        }
        catch ( ClassNotFoundException e ) {
            for ( int i = 0; i < args.length; i++ ) {
                if ( args[ i ].toLowerCase().startsWith( "-debug" ) ) {
                    e.printStackTrace( System.err );
                }
            }
            System.err.println( e.getMessage() );
            return;
        }
        runMain( args );
    }

    /**
     * Does the work for the <code>main</code> method, but may throw
     * throwables.
     *
     * @param  args  list of table specifications
     */
    private static void runMain( String[] args ) {
        tabfact = new StarTableFactory( true );
        String cmdname;
        try {
            Loader.loadProperties();
            cmdname = System.getProperty( "uk.ac.starlink.topcat.cmdname" );
        }
        catch ( SecurityException e ) {
            // never mind
            cmdname = null;
        }
        Loader.tweakGuiForMac();
        Loader.setHttpAgent( TopcatUtils.getApplicationName() + "/"
                           + TopcatUtils.getVersion() );

        /* Use sloppy interpretation of VOTables. */
        VOElementFactory.STRICT_DEFAULT = false;

        /* Fine tune the logging - we don't need HDS or AST here, so 
         * stop them complaining when they can't be loaded. */
        try {
            Logger.getLogger( "uk.ac.starlink.hds" ).setLevel( Level.OFF );
            Logger.getLogger( "uk.ac.starlink.ast" ).setLevel( Level.OFF );
        }
        catch ( SecurityException e ) {
            // If running in a sandbox, this may be blocked - never mind.
        }

        /* Prepare basic usage message. */
        if ( cmdname == null ) {
            cmdname = "topcat";
        }
        String pre = "Usage: " + cmdname;
        String pad = pre.replaceAll( ".", " " );
        String usage = 
              pre + " [-help] [-version] [-stilts <stilts-args>]"
                  + " [-jsamp <jsamp-args>]\n"
            + pad + " [-verbose] [-demo] [-disk]\n"
            + pad + " [-hub] [-exthub] [-samp] [-plastic] [-soap] [-noserv]\n"
            + pad + " [-tree] [-file] [-sql] [-cone] [-vizier] [-gavo]"
                  + " [-registry] [-sia] [-ssa]\n"
            + pad + " [[-f <format>] table ...]";

        /* Standalone execution (e.g. System.exit() may be called). */
        setStandalone( true );

        /* Process flags. */
        List argList = new ArrayList( Arrays.asList( args ) );
        List loaderList = new ArrayList();
        boolean demo = false;
        int verbosity = 0;
        boolean soapServe = false;
        boolean interopServe = true;
        boolean internalHub = false;
        boolean externalHub = false;
        boolean stiltsMode = false;
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.equals( "-h" ) || arg.equals( "-help" ) ) {
                System.out.println( getHelp( cmdname ) );
                return;
            }
            else if ( arg.equals( "-version" ) ) {
                it.remove();
                Logger.getLogger( "uk.ac.starlink.topcat" )
                      .setLevel( Level.WARNING );
                String[] about = TopcatUtils.getAbout();
                System.out.println();
                for ( int i = 0; i < about.length; i++ ) {
                    System.out.println( "    " + about[ i ] );
                }
                System.out.println();
                return;
            }
            else if ( arg.equals( "-stilts" ) ) {
                it.remove();
                Stilts.main( (String[]) argList.toArray( new String[ 0 ] ) );
                return;
            }
            else if ( arg.equals( "-jsamp" ) ) {
                it.remove();
                JSamp.main( (String[]) argList.toArray( new String[ 0 ] ) );
                return;
            }
            else if ( arg.equals( "-v" ) || arg.equals( "-verbose" ) ) {
                it.remove();
                verbosity++;
            }
            else if ( arg.equals( "-demo" ) ) {
                it.remove();
                demo = true;
            }
            else if ( arg.equals( "-disk" ) ) {
                it.remove();
                tabfact.setStoragePolicy( StoragePolicy.PREFER_DISK );
            }
            else if ( arg.equals( "-hub" ) ) {
                it.remove();
                internalHub = true;
            }
            else if ( arg.equals( "-exthub" ) ) {
                it.remove();
                externalHub = true;
            }
            else if ( arg.equals( "-samp" ) ) {
                it.remove();
                interopServe = true;
                ControlWindow.interopType_ = "samp";
            }
            else if ( arg.equals( "-plastic" ) ) {
                it.remove();
                interopServe = true;
                ControlWindow.interopType_ = "plastic";
            }
            else if ( arg.equals( "-noplastic" ) ) { // deprecated
                it.remove();
                interopServe = false;
            }
            else if ( arg.equals( "-soap" ) ) {
                it.remove();
                soapServe = true;
            }
            else if ( arg.equals( "-nosoap" ) ) {
                it.remove();
                soapServe = false;
            }
            else if ( arg.startsWith( "-noserv" ) ) {
                it.remove();
                soapServe = false;
                interopServe = false;
                ControlWindow.interopType_ = "none";
            }
            else if ( arg.equals( "-f" ) || arg.equals( "-format" ) ) {
                // leave this for this later
            }
            else if ( arg.equals( "-tree" ) ) {
                it.remove();
                loaderList.add( "uk.ac.starlink.datanode.tree." +
                                "TreeTableLoadDialog" );
            }
            else if ( arg.equals( "-file" ) ) {
                it.remove();
                loaderList.add( "uk.ac.starlink.table.gui.FileChooserLoader" );
            }
            else if ( arg.equals( "-sql" ) ) {
                it.remove();
                loaderList.add( SQLReadDialog.class.getName() );
            }
            else if ( arg.equals( "-cone" ) ) {
                it.remove();
                loaderList.add( "uk.ac.starlink.vo.ConeSearchDialog" );
            }
            else if ( arg.equals( "-vizier" ) ) {
                it.remove();
                loaderList.add( "uk.ac.starlink.topcat.contrib.cds."
                              + "VizierTableLoadDialog" );
            }
            else if ( arg.equals( "-gavo" ) ) {
                it.remove();
                loaderList.add( "uk.ac.starlink.topcat.contrib.gavo."
                              + "GavoTableLoadDialog" );
            }
            else if ( arg.equals( "-registry" ) ) {
                it.remove();
                loaderList.add( "uk.ac.starlink.vo.RegistryTableLoadDialog" );
            }
            else if ( arg.equals( "-sia" ) || arg.equals( "-siap" ) ) {
                it.remove();
                loaderList.add( "uk.ac.starlink.vo.SiapTableLoadDialog" );
            }
            else if ( arg.equals( "-ssa" ) || arg.equals( "-ssap" ) ) {
                it.remove();
                loaderList.add( "uk.ac.starlink.vo.SsapTableLoadDialog" );
            }
            else if ( arg.startsWith( "-" ) && arg.length() > 1 ) {
                System.err.println( usage );
                System.exit( 1 );
            }
        }
        extraLoaders = (String[]) loaderList.toArray( new String[ 0 ] );

        /* Configure logging. */
        configureLogging( verbosity );

        /* Check JRE vendor and report on concerns. */
        Loader.checkJ2seVendor();

        /* Configure default port number for SAMP-related services. */
        try {
            String portnum = System.getProperty( UtilServer.PORT_PROP );
            if ( portnum == null || portnum.trim().length() == 0 ) {
                System.setProperty( UtilServer.PORT_PROP,
                                    Integer.toString( DEFAULT_SERVER_PORT ) );
            }
        }
        catch ( SecurityException e ) {
            // Never mind.
        }

        /* Configure factory. */
        tabfact.getJDBCHandler()
               .setAuthenticator( new TextModelsAuthenticator() );

        /* Install custom URL handlers. */
        URLUtils.installCustomHandlers();

        /* Assemble pairs of (table name, handler name) to be loaded. */
        List names = new ArrayList();
        List handlers = new ArrayList();
        String handler = null;
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.equals( "-f" ) || arg.equals( "-format" ) ) {
                if ( it.hasNext() ) {
                    handler = (String) it.next();
                    if ( handler.equals( "auto" ) ) {
                        handler = null;
                    }
                }
                else {
                    System.err.println( usage );
                    System.exit( 1 );
                }
            }
            else if ( arg.startsWith( "-" ) && arg.length() > 1 ) {
                System.err.println( usage );
                System.exit( 1 );
            }
            else {
                names.add( arg );
                handlers.add( handler );
            }
        }
        int nload = names.size();
        assert nload == handlers.size();

        /* Start up the GUI now. */
        final ControlWindow control = getControlWindow();

        /* Start up with demo data if requested. */
        if ( demo ) {
            StarTable[] demoTables = getDemoTables();
            for ( int i = 0; i < demoTables.length; i++ ) {
                StarTable table = demoTables[ i ];
                if ( table != null ) {
                    String loc = table
                                .getParameterByName( DEMOLOC_INFO.getName() )
                                .getValue().toString();
                    addTableLater( table, "[Demo]:" + loc );
                }
            }
        }

        /* Load the requested tables. */
        for ( int i = 0; i < nload; i++ ) {
            final String name = (String) names.get( i );
            String hand = (String) handlers.get( i );
            final LoadingToken token = new LoadingToken( name );
            control.addLoadingToken( token );
            try {
                StarTable[] startabs =
                    tabfact.makeStarTables( DataSource.makeDataSource( name ),
                                            hand );
                for ( int j = 0; j < startabs.length; j++ ) {
                    String tName = startabs.length == 1
                                 ? name
                                 : name + "-" + ( j + 1 );
                    addTableLater( tabfact.randomTable( startabs[ i ] ),
                                   tName );
                }
            }
            catch ( OutOfMemoryError e ) {
                TopcatUtils.memoryError( e );
            }
            catch ( final Throwable e ) {
                System.err.println( e.getMessage() );
                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        if ( e instanceof TableFormatException ) {
                           ErrorDialog.showError( getControlWindow(),
                                                  "Load Error", e );
                        }
                        else if ( e instanceof FileNotFoundException ) {
                           ErrorDialog.showError( getControlWindow(),
                                                  "Load Error", e,
                                                  "No such file: " + name );
                        }
                        else {
                            ErrorDialog.showError( getControlWindow(),
                                                   "Load Error", e,
                                                   "Can't open table " + name );
                        }
                    }
                } );
            }
            finally {
                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        control.removeLoadingToken( token );
                    }
                } );
            }
        }

        /* Downgrade this thread's priority now; anything done after this 
         * point is done at a lower priority so as not to impact GUI setup
         * too much. */
        Thread here = Thread.currentThread();
        try {
            here.setPriority( ( here.getPriority() + Thread.MIN_PRIORITY )
                              / 2 );
        }
        catch ( SecurityException e ) {
            // never mind.
        }
       
        /* Start up remote services.  Do it in a separate, low-priority
         * thread to avoid impact on startup time. */
        if ( soapServe ) {
            try {
                TopcatSOAPServer.initServices( getControlWindow() );
            }
            catch ( Throwable e ) {
                logger.warning( "No SOAP server: " + e );
            }
        }
        if ( internalHub || externalHub ) {
            TopcatCommunicator communicator =
                getControlWindow().getCommunicator();
            boolean isExternal = externalHub;
            try {
                communicator.startHub( isExternal );
            }
            catch ( IOException e ) {
                logger.warning( "Can't start " + communicator.getProtocolName()
                              + " hub: " + e );
            }
        }
        if ( interopServe ) {
            TopcatCommunicator communicator =
                getControlWindow().getCommunicator();
            boolean isReg = communicator.setActive();
            if ( isReg ) {
                logger.info( "Registered as " + communicator.getProtocolName()
                           + " client" );
            }
            else {
                logger.info( communicator.getProtocolName()
                           + " registration atttempt failed" );
            }
        }
    }

    /**
     * Returns the ControlWindow used by this application.  It is
     * constructed lazily, which means if it's never needed (say if 
     * we're just printing a usage message), the GUI
     * never has to start up.
     *
     * @return  control window
     */
    private static ControlWindow getControlWindow() {
        if ( control == null ) {
            TableLoadChooser chooser = makeLoadChooser();
            control = ControlWindow.getInstance();
            control.setTableFactory( tabfact );
            control.setLoadChooser( chooser );
        }
        return control;
    }

    /**
     * Schedules a table for posting to the Control Window in the event
     * dispatch thread.  
     *
     * @param  table  the table to add
     * @param  location  location string indicating the provenance of
     *         <tt>table</tt> - preferably a URL or filename or something
     */
    private static void addTableLater( final StarTable table,
                                       final String location ) {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                getControlWindow().addTable( table, location, false );
            }
        } );
    }

    /**
     * Creates a TableLoadChooser suitable for use by the application.
     *
     * @return  new chooser
     */
    private static TableLoadChooser makeLoadChooser() {

        /* If we have been requested to use any extra load dialogues,
         * install them into the chooser here.  It would be more
         * straightforward to do this using the system property mechanism
         * designed for this (set TableLoadChooser.LOAD_DIALOGS_PROPERTY),
         * but this would fail with a SecurityException under some 
         * circumstances (unsigned WebStart). */
        List dList = new ArrayList();
        dList.addAll( Arrays.asList( TableLoadChooser
                                    .makeDefaultLoadDialogs() ) );
        List nameList = new ArrayList();
        for ( Iterator it = dList.iterator(); it.hasNext(); ) {
            nameList.add( it.next().getClass().getName() );
        }
        for ( int i = 0; i < extraLoaders.length; i++ ) {
            String cname = extraLoaders[ i ];
            if ( ! nameList.contains( cname ) ) {
                try {
                    TableLoadDialog tld =
                        (TableLoadDialog) 
                        Driver.class.forName( extraLoaders[ i ] ).newInstance();
                    dList.add( tld );
                }
                catch ( Throwable th ) {
                    System.err.println( "Class loading error for optional " +
                                        "loader:" );
                    th.printStackTrace( System.err );
                    System.exit( 1 );
                }
            }
        }
        TableLoadDialog[] dialogs = (TableLoadDialog[]) 
                                    dList.toArray( new TableLoadDialog[ 0 ] );
        return new TableLoadChooser( tabfact, dialogs, KNOWN_DIALOGS );
    }

    /**
     * Returns a set of example StarTables suitable for demonstration
     * purposes.  They will all have random access.
     * If one of the demo tables can't be created for some
     * reason (e.g. the required resource is missing) the corresponding
     * element in the returned array will be <tt>null</tt>.
     *
     * @return  array of demo tables
     */
    static StarTable[] getDemoTables() {
        String base = TopcatUtils.DEMO_LOCATION + '/';
        String[] demoNames = new String[] {
            "6dfgs_mini.xml.bz2",
            // "863sub.fits",
            // "vizier.xml.gz#6",
            "messier.xml",
            // "tables.fit.gz#2",
        };
        int ntab = demoNames.length;
        if ( demoTables == null ) {
            demoTables = new StarTable[ ntab ];
            StarTableFactory demoFactory = new StarTableFactory( true );
            for ( int i = 0; i < ntab; i++ ) {
                final String demoName = demoNames[ i ];
                try {
                    int fragIndex = demoName.indexOf( '#' );
                    String name; 
                    String frag;
                    if ( fragIndex > 0 ) {
                        name = demoName.substring( 0, fragIndex );
                        frag = demoName.substring( fragIndex + 1 );
                    }
                    else {
                        name = demoName;
                        frag = null;
                    }
                    URL url = Driver.class.getClassLoader()
                                    .getResource( base + name );
                    if ( url != null ) {
                        DataSource datsrc = 
                            DataSource.makeDataSource( url.toString() );
                        if ( frag != null ) {
                            datsrc.setPosition( frag );
                        }
                        StarTable table = demoFactory.makeStarTable( datsrc );
                        table.getParameters()
                             .add( new DescribedValue( DEMOLOC_INFO,
                                                       demoName ) );
                        demoTables[ i ] = demoFactory.randomTable( table );
                    }
                    else {
                        logger.warning( "Demo table resource not located: " +
                                        base + demoName );
                    }
                }
                catch ( IOException e ) {
                    logger.warning( "Demo table " + demoName + " not loaded: "
                                  + e.toString() );
                }
            }
        }
        return demoTables;
    }

    /**
     * Returns a full help message.
     *
     * @param  cmdname  command name for this application
     * @return help
     */
    private static String getHelp( String cmdname ) {
        StringBuffer buf = new StringBuffer();
        String p1 = "\n\n    ";
        String p2 = "\n        ";

        /* Basic usage. */
        buf.append( "Usage: " )
           .append( cmdname )
           .append( " <flags> [[-f <format>] <table> ...]" );

        /* General flags. */
        buf.append( p1 + "General flags:" )
           .append( p2 + "-help          print this message and exit" )
           .append( p2 + "-version       print component versions etc "
                                         + "and exit" )
           .append( p2 + "-verbose       increase verbosity of "
                                         + "reports to console" )
           .append( p2 + "-demo          start with demo data" )
           .append( p2 + "-disk          use disk backing store for "
                                         + "large tables" ) 
           .append( p2 + "-samp          use SAMP for tool interoperability" )
           .append( p2 + "-plastic       use PLASTIC for "
                                         + "tool interoperability" )
           .append( p2 + "-hub           run internal SAMP/PLASTIC hub" )
           .append( p2 + "-exthub        run external SAMP/PLASTIC hub" )
           .append( p2 + "-soap          start SOAP services" )
           .append( p2 + "-noserv        don't run any services"
                                         + " (PLASTIC, SAMP or SOAP)" )
           .append( p2 + "-stilts <args> run STILTS not TOPCAT" )
           .append( p2 + "-jsamp <args>  run JSAMP not TOPCAT" );

        /* Load dialogues. */
        buf.append( p1 + "Optional load dialogue flags:" )
           .append( p2 + "-tree      hierarchy browser" )
           .append( p2 + "-file      basic file browser" )
           .append( p2 + "-sql       SQL query on relational database" )
           .append( p2 + "-cone      cone search dialogue" )
           .append( p2 + "-vizier    VizieR catalogue service query" )
           .append( p2 + "-gavo      GAVO Millennium run database query" )
           .append( p2 + "-registry  VO registry query" )
           .append( p2 + "-sia       Simple Image Access Protocol queries" )
           .append( p2 + "-ssa       Simple Spectrum Access Protocol queries" );

        /* Java flags. */
        buf.append( p1 + "Useful Java flags:" )
           .append( p2 )
           .append( "-classpath jar1:jar2..  specify additional classes" )
           .append( p2 )
           .append( "-XmxnnnM                use nnn megabytes of memory" )
           .append( p2 )
           .append( "-Dname=value            set system property" );

        /* Auto-detected formats. */
        buf.append( p1 + "Auto-detected formats: " )
           .append( p2 );
        for ( Iterator it = tabfact.getDefaultBuilders().iterator();
              it.hasNext(); ) {
            buf.append( ((TableBuilder) it.next()).getFormatName()
                                                  .toLowerCase() );
            if ( it.hasNext() ) {
                buf.append( ", " );
            }
        }

        /* All known formats. */
        buf.append( p1 + "All known formats:" )
           .append( p2 );
        for ( Iterator it = tabfact.getKnownFormats().iterator();
              it.hasNext(); ) {
            buf.append( ((String) it.next()).toLowerCase() );
            if ( it.hasNext() ) {
                buf.append( ", " );
            }
        }

        /* System properties. */
        buf.append( p1 + "Useful system properties " 
                       + "(-Dname=value - lists are colon-separated):" )
           .append( p2 )
           .append( "java.io.tmpdir          temporary filespace directory" )
           .append( p2 )
           .append( "jdbc.drivers            JDBC driver classes" )
           .append( p2 )
           .append( "jel.classes             " +
                    "custom algebraic function classes" )
           .append( p2 )
           .append( "jel.classes.activation  custom action function classes" )
           .append( p2 )
           .append( "star.connectors         custom remote filestore classes" )
           .append( p2 )
           .append( "startable.load.dialogs  custom load dialogue classes" )
           .append( p2 )
           .append( "startable.readers       custom table input handlers" )
           .append( p2 )
           .append( "startable.writers       custom table output handlers" )
           .append( p2 )
           .append( "startable.storage       default storage policy" )
           .append( p2 )
           .append( "mark.workaround         work around mark/reset bug" )
           .append( p2 )
           .append( "myspace.cache           MySpace performance workaround" )
           .append( p2 )
           .append( "    (see topcat -jsamp -help for more)" )
           .append( "" );

        /* Return. */
        return "\n" + buf.toString() + "\n";
    }

    /**
     * Sets up the logging system.
     *
     * @param  verbosity  number of levels greater than default to set
     */
    private static void configureLogging( int verbosity ) {

        /* Try to acquire a custom log handler - may fail for security
         * reasons. */
        LogHandler customHandler = LogHandler.getInstance();
	if ( customHandler == null ) {
            return;
        }
        try {

            /* Add a custom log handler. */
            Logger rootLogger = Logger.getLogger( "" );
            rootLogger.addHandler( customHandler );

            /* Work out the logging level to which the requested verbosity 
             * corresponds. */
            int verbInt = Math.max( Level.ALL.intValue(),
                                    Level.WARNING.intValue()
                                    - verbosity * 
                                      ( Level.WARNING.intValue() -
                                        Level.INFO.intValue() ) );
            Level verbLevel = Level.parse( Integer.toString( verbInt ) );

            /* Get the root logger's console handler.  By default
             * it has one of these; if it doesn't then some custom 
             * logging is in place and we won't mess about with it. */
            Handler[] rootHandlers = rootLogger.getHandlers();
            if ( rootHandlers.length > 0 &&
                 rootHandlers[ 0 ] instanceof ConsoleHandler ) {
                rootHandlers[ 0 ].setLevel( verbLevel );
                rootHandlers[ 0 ].setFormatter( new LineFormatter() );
            }
            rootLogger.setLevel( verbLevel );

            /* Filter out an annoying message that Axis issues. */
            Logger.getLogger( "org.apache.axis.utils.JavaUtils" )
                  .setLevel( Level.SEVERE );
        }

        /* I don't think this should happen, since the earlier test should
         * already have failed if we don't have permission to muck about
         * with logging configuration.  However, I don't fully understand
         * the logging security model, so maybe it could. */
        catch ( SecurityException e ) {
            logger.warning( "Logging configuration failed" +
                            " - security exception" );
        }
    }
}
