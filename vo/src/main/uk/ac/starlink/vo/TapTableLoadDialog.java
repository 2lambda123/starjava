package uk.ac.starlink.vo;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import uk.ac.starlink.table.DescribedValue;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.StarTableFactory;
import uk.ac.starlink.table.TableSequence;
import uk.ac.starlink.table.Tables;
import uk.ac.starlink.table.gui.TableLoader;
import uk.ac.starlink.util.gui.ErrorDialog;
import uk.ac.starlink.util.gui.ShrinkWrapper;

/**
 * Load dialogue for TAP services.
 *
 * @author   Mark Taylor
 * @since    18 Jan 2011
 * @see <a href="http://www.ivoa.net/Documents/TAP/">IVOA TAP Recommendation</a>
 */
public class TapTableLoadDialog extends DalTableLoadDialog {

    private final Map<String,TapQueryPanel> tqMap_;
    private JTabbedPane tabber_;
    private JComponent tqContainer_;
    private TapQueryPanel tqPanel_;
    private UwsJobListPanel jobsPanel_;
    private ResumeTapQueryPanel resumePanel_;
    private CaretListener adqlListener_;
    private Action reloadAct_;
    private ProxyAction[] proxyActs_;
    private ComboBoxModel runModeModel_;
    private TapMetaPolicy metaPolicy_;
    private String ofmtName_;
    private StarTableFactory tfact_;
    private int tqTabIndex_;
    private int jobsTabIndex_;
    private int resumeTabIndex_;
    private int iseq_;

    // This is an expression designed to pick up things that the user might
    // have entered as an upload table identifier.  It intentionally includes
    // illegal TAP upload strings, so that the getUploadTable method
    // has a chance to emit a helpful error message.
    private static final Pattern UPLOAD_REGEX =
        Pattern.compile( "TAP_UPLOAD\\.([^ ()*+-,/;<=>&?|\t\n\r]*)",
                         Pattern.CASE_INSENSITIVE );

    // Pattern for locating table names in ADQL, used for generating terse
    // query summaries; doesn't need to be very good.  Could be improved
    // by using the ADQL parser if required.
    private static final Pattern TNAME_REGEX =
        Pattern.compile( "(FROM|JOIN)\\s+(\\S*)",
                         Pattern.CASE_INSENSITIVE );

    // Maximum Number of requests for table metadata to queue in LIFO.
    private static final int META_QUEUE_LIMIT = 10;

    private static final Logger logger_ =
        Logger.getLogger( "uk.ac.starlink.vo" );

    /**
     * Constructor.
     */
    public TapTableLoadDialog() {
        super( "Table Access Protocol (TAP) Query", "TAP",
               "Query remote databases using SQL-like language",
               Capability.TAP, false, false );
        tqMap_ = new HashMap<String,TapQueryPanel>();
        metaPolicy_ = TapMetaPolicy.getDefaultInstance();
        setIconUrl( TapTableLoadDialog.class.getResource( "tap.gif" ) );
    }

    @Override
    public void configure( StarTableFactory tfact, Action submitAct ) {
        submitAct.putValue( Action.NAME, "Run Query" );
        tfact_ = tfact;
        super.configure( tfact, submitAct );
    }

    protected Component createQueryComponent() {

        /* Prepare a panel to search the registry for TAP services. */
        final Component searchPanel = super.createQueryComponent();

        /* Prepare a panel for monitoring running jobs. */
        jobsPanel_ = new UwsJobListPanel() {
            public void addJob( UwsJob job, boolean select ) {
                super.addJob( job, select );
                tabber_.setEnabledAt( jobsTabIndex_, true );
            }
            public void removeJob( UwsJob job ) {
                super.removeJob( job );
                if ( getJobs().length == 0 ) {
                    tabber_.setEnabledAt( jobsTabIndex_, false );
                    tabber_.setSelectedIndex( tqTabIndex_ );
                }
            }
        };

        /* Prepare a panel for resuming previously started jobs. */
        resumePanel_ = new ResumeTapQueryPanel( this );

        /* Prepare a tabbed panel to contain the components. */
        tabber_ = new JTabbedPane();
        tabber_.add( "Select Service", searchPanel );
        tqContainer_ = new JPanel( new BorderLayout() );
        String tqTitle = "Enter Query";
        tabber_.add( tqTitle, tqContainer_ );
        tqTabIndex_ = tabber_.getTabCount() - 1;
        tabber_.add( "Resume Job", resumePanel_ );
        resumeTabIndex_ = tabber_.getTabCount() - 1;
        tabber_.add( "Running Jobs", jobsPanel_ );
        jobsTabIndex_ = tabber_.getTabCount() - 1;

        /* Provide a button to move to the query tab.
         * Placing it near the service selector makes it more obvious that
         * that is what you need to do after selecting a TAP service. */
        final Action tqAct = new AbstractAction( tqTitle ) {
            public void actionPerformed( ActionEvent evt ) {
                tabber_.setSelectedIndex( tqTabIndex_ );
            }
        };
        tqAct.putValue( Action.SHORT_DESCRIPTION,
                        "Go to " + tqTitle
                      + " tab to prepare and execute TAP query" );
        Box buttLine = Box.createHorizontalBox();
        buttLine.add( Box.createHorizontalGlue() );
        buttLine.add( new JButton( tqAct ) );
        getControlBox().add( buttLine );

        /* Set up TAP run modes. */
        runModeModel_ = new DefaultComboBoxModel( createRunModes() );

        /* Only enable the query tab if a valid service URL has been
         * selected. */
        tqAct.setEnabled( false );
        tabber_.setEnabledAt( tqTabIndex_, false );
        tabber_.setEnabledAt( jobsTabIndex_, false );
        getServiceUrlField().addCaretListener( new CaretListener() {
            public void caretUpdate( CaretEvent evt ) {
                boolean hasUrl;
                try {
                    checkUrl( getServiceUrl() );
                    hasUrl = true;
                }
                catch ( RuntimeException e ) {
                    hasUrl = false;
                }
                tabber_.setEnabledAt( tqTabIndex_, hasUrl );
                tqAct.setEnabled( hasUrl );
            }
        } );

        /* Arrange for the table query panel to get updated when it becomes
         * the visible tab. */
        tabber_.addChangeListener( new ChangeListener() {
            public void stateChanged( ChangeEvent evt ) {
                if ( tabber_.getSelectedIndex() == tqTabIndex_ ) {
                    setSelectedService( createServiceKit() );
                }
                updateReady();
            }
        } );

        /* Arrange that the TAP query submit action's enabledness status
         * can be sensitive to the content of the ADQL entry field. */
        adqlListener_ = new CaretListener() {
            public void caretUpdate( CaretEvent evt ) {
                updateReady();
            }
        };

        /* Reload action. */
        reloadAct_ = new AbstractAction( "Reload" ) {
            public void actionPerformed( ActionEvent evt ) {
                int itab = tabber_.getSelectedIndex();
                if ( itab == tqTabIndex_ ) {
                    tqPanel_.setServiceKit( createServiceKit() );
                }
                else if ( itab == resumeTabIndex_ ) {
                    resumePanel_.reload();
                }
                else if ( itab == jobsTabIndex_ ) {
                    jobsPanel_.reload();
                }
            }
        };
        ChangeListener reloadEnabler = new ChangeListener() {
            public void stateChanged( ChangeEvent evt ) {
                int itab = tabber_.getSelectedIndex();
                reloadAct_.setEnabled( itab == tqTabIndex_
                                    || itab == resumeTabIndex_
                                    || itab == jobsTabIndex_ );
            }
        };
        tabber_.addChangeListener( reloadEnabler );
        reloadEnabler.stateChanged( null );
        reloadAct_.putValue( Action.SMALL_ICON, 
                             new ImageIcon( TapTableLoadDialog.class
                                           .getResource( "reload.gif" ) ) );
        reloadAct_.putValue( Action.SHORT_DESCRIPTION,
              "Reload information displayed in this panel from the server; "
            + "exact behaviour depends on which panel is visible" );

        /* Set up an Edit menu. */
        List<JMenu> menuList =
            new ArrayList<JMenu>( Arrays.asList( super.getMenus() ) );
        JMenu editMenu = new JMenu( "Edit" );
        editMenu.setMnemonic( KeyEvent.VK_E );
        menuList.add( editMenu );
        setMenus( menuList.toArray( new JMenu[ 0 ] ) );

        /* Add actions from the query panel to this dialogue's Edit menu.
         * However, the query panel object will change over the
         * lifetime of this component, so populate the menu with
         * proxy actions that will delegate to the corresponding action
         * of whatever query panel is currently visible. */
        List<ProxyAction> pacts = new ArrayList<ProxyAction>();
        for ( Action templateAct : createTapQueryPanel().getEditActions() ) {
            ProxyAction proxyAct = new ProxyAction( templateAct );
            pacts.add( proxyAct );
            editMenu.add( proxyAct );
        }
        proxyActs_ = pacts.toArray( new ProxyAction[ 0 ] );
        updateQueryPanel();

        /* Set toolbar actions. */
        List<Action> actList =
            new ArrayList<Action>( Arrays.asList( super.getToolbarActions() ) );
        actList.add( reloadAct_ );
        setToolbarActions( actList.toArray( new Action[ 0 ] ) );

        /* Adjust message. */
        RegistryPanel regPanel = getRegistryPanel();
        regPanel.displayAdviceMessage( new String[] {
            "Query registry for TAP services:",
            "Enter search terms in Keywords field or leave it blank,",
            "then click "
            + regPanel.getSubmitQueryAction().getValue( Action.NAME ) + ".",
            " ",
            "Alternatively, enter TAP URL in field below.",
        } );

        /* It's big. */
        tabber_.setPreferredSize( new Dimension( 700, 650 ) );

        /* Return the tabbed pane which is the main query component. */
        return tabber_;
    }

    /**
     * Returns a table named by an upload specifier in an ADQL query.
     * The TapTableLoadDialog implementation of this throws an exception,
     * but subclasses may override this if they are capable of providing
     * uploadable tables.
     * If no table named by the given label is available, it is good
     * practice to throw an IllegalArgumentException with an informative
     * message, though returning null is also acceptable.
     *
     * @param  upLabel  name part of an uploaded table specification,
     *                  that is the part following the "TAP_UPLOAD." part
     * @return  table named by <code>upLabel</code>
     */
    protected StarTable getUploadTable( String upLabel ) {
        throw new IllegalArgumentException( "Upload tables not supported" );
    }

    public TableLoader createTableLoader() {
        int itab = tabber_.getSelectedIndex();
        if ( itab == tqTabIndex_ ) {
            return createQueryPanelLoader();
        }
        else if ( itab == resumeTabIndex_ ) {
            return resumePanel_.createTableLoader();
        }
        else {
            return null;
        }
    }

    /**
     * Adds a running TAP query to the list of queries this dialogue
     * is currently aware of.
     *
     * @param  tapJob  UWS job representing TAP query
     */
    public void addRunningQuery( UwsJob tapJob ) {
        jobsPanel_.addJob( tapJob, true );
        tabber_.setSelectedIndex( jobsTabIndex_ );
    }

    /**
     * Sets the policy used for TAP service metadata acquisition.
     * As currently implemented, a change in policy triggers a reload
     * of the metadata for the currently displayed service metadata,
     * though not for other cached ones.
     *
     * @param  metaPolicy   new metadata acquisition policy
     */
    public void setMetaPolicy( TapMetaPolicy metaPolicy ) {
        if ( metaPolicy_ != metaPolicy ) {
            metaPolicy_ = metaPolicy;
            if ( tqPanel_ != null ) {
                TapServiceKit serviceKit = createServiceKit();
                if ( serviceKit != null ) {
                    tqPanel_.setServiceKit( serviceKit );
                }
            }
        }
    }

    /**
     * Sets the preferred format in which the service is to provide the
     * table output.  This had better represent some form of VOTable.
     * The supplied name may be a MIME type, an alias, or an ivo-id
     * as described in sec 2.4 of TAPRegExt v1.0.
     * If the supplied name is null, or if no output format has been
     * declared by the service corresponding to the supplied name,
     * then the service's default output format will be used.
     *
     * @param  ofmtName  output format MIME type, alias or ivo-id
     */
    public void setPreferredOutputFormat( String ofmtName ) {
        ofmtName_ = ofmtName;
    }

    /**
     * Returns a panel-specific reload action.
     * When enabled, this performs some kind of update action
     * relevant to the currently visible tab.
     *
     * @return  reload action
     */
    public Action getReloadAction() {
        return reloadAct_;
    }

    /**
     * Returns the run modes provided by this dialogue.
     *
     * @return   run mode options
     */
    protected TapRunMode[] createRunModes() {
        return new TapRunMode[] {
            TapRunMode.SYNC, TapRunMode.ASYNC, TapRunMode.LOOK,
        };
    }

    /**
     * Returns a new query TableLoader for the case when the QueryPanel
     * is the currently visible tab.
     *
     * @return   new loader
     */
    private TableLoader createQueryPanelLoader() {
        final URL serviceUrl = checkUrl( getServiceUrl() );
        final String adql = tqPanel_.getAdql();
        final Map<String,StarTable> uploadMap =
            new LinkedHashMap<String,StarTable>();
        final String summary = createLoadLabel( adql );
        TapCapabilityPanel tcapPanel = tqPanel_.getCapabilityPanel();
        long rowUploadLimit = tcapPanel.getUploadLimit( TapLimit.ROWS );
        final long byteUploadLimit = tcapPanel.getUploadLimit( TapLimit.BYTES );
        Set<String> uploadLabels = getUploadLabels( adql );
        for ( String upLabel : uploadLabels ) {
            StarTable upTable = getUploadTable( upLabel );
            if ( upTable != null ) {
                long nrow = upTable.getRowCount();
                if ( rowUploadLimit >= 0 && nrow > rowUploadLimit ) {
                    throw new IllegalArgumentException(
                        "Table " + upLabel + " too many rows for upload "
                      + " (" + nrow + ">" + rowUploadLimit + ")" );
                }
                uploadMap.put( upLabel, upTable );
            }
            else {
                throw new IllegalArgumentException( "No known table \"" 
                                                  + upLabel + "\" for upload" );
            }
        }
        final Map<String,String> extraParams =
            new LinkedHashMap<String,String>();
        String language = tcapPanel.getQueryLanguageName();
        if ( language != null && language.trim().length() > 0 ) {
            extraParams.put( "LANG", language );
        }
        long maxrec = tcapPanel.getMaxrec();
        if ( maxrec > 0 ) {
            extraParams.put( "MAXREC", Long.toString( maxrec ) );
        }
        TapCapability tcap = tcapPanel.getCapability();
        if ( tcap != null ) {
            String ofmtSpec =
                getOutputFormatSpecifier( ofmtName_, tcap.getOutputFormats() );
            if ( ofmtSpec != null ) {
                extraParams.put( "FORMAT", ofmtSpec );
            }
        }
        List<DescribedValue> metaList = new ArrayList<DescribedValue>();
        metaList.addAll( Arrays.asList( getResourceMetadata( serviceUrl
                                                            .toString() ) ) );
        final DescribedValue[] metas =
            metaList.toArray( new DescribedValue[ 0 ] );
        final TapRunMode runMode = (TapRunMode) runModeModel_.getSelectedItem();
        TapQuery tq0;
        try {
            tq0 = new TapQuery( serviceUrl, adql, extraParams, uploadMap,
                                byteUploadLimit, null );
        }
        catch ( IOException e ) {
            ErrorDialog.showError( getQueryComponent(), "Query Construction",
                                   e );
            return null;
        }
        final TapQuery tq = tq0;
        if ( runMode.isLoader_ ) {
            return new TableLoader() {
                public TableSequence loadTables( StarTableFactory tfact )
                        throws IOException {
                    if ( runMode.isSync_ ) {
                        assert runMode == TapRunMode.SYNC;
                        StarTable table =
                            tq.executeSync( tfact.getStoragePolicy() );
                        table.getParameters().addAll( Arrays.asList( metas ) );
                        return Tables.singleTableSequence( table );
                    }
                    else {
                        assert runMode == TapRunMode.ASYNC;
                        final UwsJob tapJob = tq.submitAsync();
                        SwingUtilities.invokeLater( new Runnable() {
                            public void run() {
                                addRunningQuery( tapJob );
                            }
                        } );
                        return createTableSequence( tfact, tapJob, metas );
                    }
                }
                public String getLabel() {
                    return summary;
                }
            };
        }
        else {
            assert runMode == TapRunMode.LOOK;
            assert runMode.isSync_;
            QuickLookWindow qlw = new QuickLookWindow( tq, tfact_ );
            qlw.setVisible( true );
            qlw.executeQuery();
            return null;
        }
    }

    /**
     * Returns a table sequence constructed from a given TAP query.
     * This method marks each TapQuery for deletion on JVM shutdown.
     * Subclass implementations may override this method to perform
     * different job deletion behaviour.
     *
     * @param   tfact  table factory
     * @param   tapJob   UWS job representing async TAP query
     * @param   tapMeta  metadata describing the query suitable for
     *          decorating the resulting table
     * @return  table sequence suitable for a successful return from
     *          this dialog's TableLoader
     */
    protected TableSequence createTableSequence( StarTableFactory tfact,
                                                 UwsJob tapJob,
                                                 DescribedValue[] tapMeta )
            throws IOException {
        tapJob.setDeleteOnExit( true );
        tapJob.start();
        StarTable table;
        try {
            table = TapQuery
                   .waitForResult( tapJob, tfact.getStoragePolicy(), 4000 );
        }
        catch ( InterruptedException e ) {
            throw (IOException)
                  new InterruptedIOException( "Interrupted" )
                 .initCause( e );
        }
        table.getParameters().addAll( Arrays.asList( tapMeta ) );
        return Tables.singleTableSequence( table );
    }

    /**
     * Creates a new TapQueryPanel.  This is called when a new TAP service
     * is selected.  The default implementation constructs one with a basic
     * set of examples, but it can be overridden for more specialised
     * behaviour.
     *
     * @return  new query panel
     */
    protected TapQueryPanel createTapQueryPanel() {
        return new TapQueryPanel( new UrlHandler() {
            public void clickUrl( URL url ) {
                logger_.warning( "Click :" + url );
            }
        } );
    }

    public boolean isReady() {
        if ( tqPanel_ == null || tabber_.getSelectedIndex() != tqTabIndex_ ) {
            return false;
        }
        else {
            String adql = tqPanel_.getAdql();
            return super.isReady() && adql != null && adql.trim().length() > 0;
        }
    }

    /**
     * Generates a short summary string to use as a loading label for a table
     * loaded by TAP.  An half-hearted attempt is made to construct the
     * string by parsing the provided ADQL.
     *
     * @param  adql  ADQL text
     * @return  summary string
     */
    private String createLoadLabel( String adql ) {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append( "TAP_" )
            .append( ++iseq_ );
        if ( adql != null ) {
            Matcher matcher = TNAME_REGEX.matcher( adql );
            for ( boolean more = false; matcher.find(); more = true ) {
                String tname = matcher.group( 2 );

                /* Do some mangling, since at present the label
                 * can be truncated elsewhere in the application by
                 * assuming "/" characters represent pathnames for which
                 * the earlier parts can get discarded. */
                tname = tname.replaceAll( "/+", "_" )
                             .replaceAll( "\"", "" );
                sbuf.append( more ? ',' : '_' )
                    .append( tname );
            }
        }
        return sbuf.toString();
    }

    /**
     * Returns a set of table identifers which are required for upload.
     * This is the set of any [identifier]s in the query of the form
     * TAP_UPLOAD.[identifier].
     *
     * @param   adql  ADQL/S text
     * @return   collection of upload identifiers
     */
    private static Set<String> getUploadLabels( String adql ) {

        /* Use of a regex for this partial parse is not bulletproof,
         * but you would have to have quite contrived ADQL to get a
         * false match here. */
        Set<String> labelSet = new HashSet<String>();
        Matcher matcher = UPLOAD_REGEX.matcher( adql );
        while ( matcher.find() ) {
            labelSet.add( matcher.group( 1 ) );
        }
        return labelSet;
    }

    /**
     * Returns a service kit based on the current state of this dialogue.
     *
     * @return  service kit, may be null if not ready
     */
    private TapServiceKit createServiceKit() {
        String surl = getServiceUrl();
        if ( surl == null || metaPolicy_ == null ) {
            return null;
        }
        URL serviceUrl;
        try {
            serviceUrl = new URL( surl );
        }
        catch ( MalformedURLException e ) {
            return null;
        }
        return new TapServiceKit( serviceUrl, getIvoid( serviceUrl ),
                                  metaPolicy_, META_QUEUE_LIMIT );
    }

    /**
     * Returns the IVORN apparently corresponding to a selected service URL.
     *
     * @param   serviceUrl  service URL
     * @return   corresponding IVORN, or null
     */
    private String getIvoid( URL serviceUrl ) {
        RegResource[] resources = getRegistryPanel().getSelectedResources();
        if ( resources == null || serviceUrl == null ) {
            return null;
        }
        String surl = serviceUrl.toString();
        for ( RegResource res : resources ) {
            for ( RegCapabilityInterface cap : res.getCapabilities() ) {
                if ( surl.equals( cap.getAccessUrl() ) ) {
                    return res.getIdentifier();
                }
            }
        }
        return null;
    }

    /**
     * Configure this dialogue to use a given TAP service.
     *
     * @param  serviceKit   TAP service metadata access kit
     */
    private void setSelectedService( TapServiceKit serviceKit ) {

        /* We have to install a TapQueryPanel for this service in the 
         * appropriate tab of the tabbed pane.
         * First remove any previously installed query panel. */
        if ( tqPanel_ != null ) {
            tqContainer_.remove( tqPanel_ );
            tqPanel_.removeCaretListener( adqlListener_ );
            tqPanel_ = null;
        }
        if ( serviceKit != null ) {
            String serviceUrl = serviceKit.getServiceUrl().toString();

            /* Construct, configure and cache a suitable query panel
             * if we haven't seen this service URL before now. */
            if ( ! tqMap_.containsKey( serviceUrl ) ) {
                TapQueryPanel tqPanel = createTapQueryPanel();
                if ( runModeModel_ != null && runModeModel_.getSize() > 1 ) {
                    JComponent modeLine = Box.createHorizontalBox();
                    modeLine.add( new JLabel( "Mode: " ) );
                    modeLine.add( new ShrinkWrapper(
                                      new JComboBox( runModeModel_ ) ) );
                    modeLine.add( Box.createHorizontalStrut( 5 ) );
                    tqPanel.addControl( modeLine );
                }
                tqPanel.setServiceKit( serviceKit );
                tqMap_.put( serviceUrl, tqPanel );
            }

            /* Get the panel from the cache, now guaranteed present. */
            tqPanel_ = tqMap_.get( serviceUrl );

            /* Install ready for use. */
            tqPanel_.addCaretListener( adqlListener_ );
            tqContainer_.add( tqPanel_, BorderLayout.CENTER );
        }
        updateQueryPanel();
        updateReady();
    }

    /**
     * Invoked to update the GUI if the identity of the TapQueryPanel
     * on display may have changed.
     */
    private void updateQueryPanel() {

        /* Reconfigure all known proxy actions so that they delegate to the
         * corresponding actions from the curently visible TapQueryPanel. */
        Map<String,Action> actMap = new HashMap<String,Action>();
        if ( tqPanel_ != null ) {
            for ( Action baseAct : tqPanel_.getEditActions() ) {
                actMap.put( (String) baseAct.getValue( Action.NAME ), baseAct );
            }
        }
        for ( ProxyAction act : proxyActs_ ) {
            act.setTarget( actMap.get( act.getValue( Action.NAME ) ) );
        }
    }

    /**
     * Returns a specification string suitable for use with the TAP
     * FORMAT request parameter that indicates preference for a particular
     * table output format.
     *
     * @param  ofmtName  preferred output format MIME type, alias or ivo-id
     * @param  ofmts   available output formats
     * @return  FORMAT value to specify preferred output format,
     *          or none if no suitable format is present in the supplied list
     */
    private static String getOutputFormatSpecifier( String ofmtName,
                                                    OutputFormat[] ofmts ) {
        if ( ofmtName != null && ofmts != null ) {
            for ( OutputFormat ofmt : ofmts ) {
                String[] aliases = ofmt.getAliases();
                aliases = aliases == null ? new String[ 0 ] : aliases;
                if ( ofmtName.equalsIgnoreCase( ofmt.getIvoid() ) ||
                     ofmtName.equalsIgnoreCase( ofmt.getMime() ) ||
                     Arrays.asList( aliases ).indexOf( ofmtName ) >= 0 ) {
                    return aliases.length > 0 ? aliases[ 0 ] : ofmt.getMime();
                }
            }
        }
        return null;
    }

    /**
     * Enum for TAP run modes.
     */
    public enum TapRunMode {

        /** Synchronous load into application. */
        SYNC( true, true,
               "Synchronous",
               "Execute query in TAP synchronous mode"
             + " and load result into application" ),

        /** Asynchronous load into application. */
        ASYNC( true, false,
               "Asynchronous",
               "Execute query in TAP asynchronous mode"
             + " and load result into application when complete"  ),

        /** Quick look in popup window. */
        LOOK( false, true,
              "Quick Look",
              "Execute query in TAP synchronous mode"
            + " and display the result in a popup window" );

        private final boolean isLoader_;
        private final boolean isSync_;
        private final String name_;
        private final String description_;

        /**
         * Constructor.
         *
         * @param  isLoader  true if this mode results in loading into
         *                   the host application; false if it will be
         *                   disposed of some other way
         * @param  isSync    true for synchronous, false for asynchronous
         * @param  name    mode name
         * @param  description  mode description
         */
        TapRunMode( boolean isLoader, boolean isSync,
                    String name, String description ) {
            isLoader_ = isLoader;
            isSync_ = isSync;
            name_ = name;
            description_ = description;
        }

        @Override
        public String toString() {
            return name_;
        }
    }

    /**
     * Action which is based on a given template action,
     * but can delegate behaviour to a dynamically supplied target action.
     * Properties are taken from the template, but enabledness and the
     * actionPerformed method are delegated to the target.
     * If the target is null, the action is disabled.
     */
    private static class ProxyAction extends AbstractAction {
        private final Action template_;
        private final PropertyChangeListener propForwarder_;
        private Action target_;

        /**
         * Constructor.
         *
         * @param  template  template action providing all fixed properties
         *                   apart from enabled status
         */
        ProxyAction( Action template ) {
            template_ = template;
            propForwarder_ = new PropertyChangeListener() {
                public void propertyChange( PropertyChangeEvent evt ) {
                    for ( PropertyChangeListener l :
                          getPropertyChangeListeners() ) {
                        l.propertyChange( evt );
                    }
                }
            };
        }

        /**
         * Sets the target action.
         *
         * @param  action providing enabledness and actionPerformed
         */
        public void setTarget( Action target ) {
            if ( target_ != null ) {
                target_.removePropertyChangeListener( propForwarder_ );
            }
            target_ = target;
            if ( target_ != null ) {
                target_.addPropertyChangeListener( propForwarder_ );
            }
            setEnabled( isEnabled() );
        }

        public void actionPerformed( ActionEvent evt ) {
            if ( target_ != null ) {
                target_.actionPerformed( evt );
            }
        }

        @Override
        public boolean isEnabled() {
            return target_ != null && target_.isEnabled();
        }

        @Override
        public Object getValue( String key ) {
            return template_.getValue( key );
        }
    }

    /**
     * Main method pops up an instance of this dialog.
     * An initial TAP URL may be given on the command line.
     */
    public static void main( String[] args ) {
        final String tapUrl = args.length > 0 ? args[ 0 ] : null;
        final TapTableLoadDialog tld = new TapTableLoadDialog() {
            protected TapRunMode[] createRunModes() {
                return new TapRunMode[] { TapRunMode.LOOK };
            }
        };
        final StarTableFactory tfact = new StarTableFactory();
        tld.configure( tfact, new AbstractAction() {
            public void actionPerformed( ActionEvent evt ) {
                tld.createTableLoader();
            }
        } );
        Component qcomp = tld.getQueryComponent();
        if ( tapUrl != null ) {
            tld.getServiceUrlField().setText( tapUrl );
        }
        javax.swing.JFrame frm = new javax.swing.JFrame();
        frm.setJMenuBar( new javax.swing.JMenuBar() );
        for ( JMenu menu : tld.getMenus() ) {
            frm.getJMenuBar().add( menu );
        }
        frm.getContentPane().add( qcomp, BorderLayout.CENTER );
        frm.getContentPane().add( new JButton( tld.getSubmitAction() ),
                                  BorderLayout.SOUTH );
        frm.pack();
        frm.setVisible( true );
    }
}
