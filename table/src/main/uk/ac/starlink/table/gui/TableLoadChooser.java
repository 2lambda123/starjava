package uk.ac.starlink.table.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import uk.ac.starlink.connect.FilestoreChooser;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.StarTableFactory;
import uk.ac.starlink.table.TableBuilder;
import uk.ac.starlink.table.jdbc.SwingAuthenticator;
import uk.ac.starlink.util.DataSource;
import uk.ac.starlink.util.Loader;

/**
 * Window which permits the user to select an existing {@link StarTable}
 * from file browsers or other external sources.
 * The most straightforward way to use this is to invoke the
 * {@link #showTableDialog} method.  
 *
 * <p>As well as a text field in which the user may type the location of a
 * table, a number of buttons are offered which pop up additional dialogues,
 * for instance a normal file browser and a dialogue for posing an 
 * SQL query.  This list is extensible at run time; if you wish to 
 * provide an additional table acquisition dialogue, then you must 
 * provide an implementation of the {@link TableLoadDialog} interface.
 * This can be made known to the chooser either by passing a list of
 * additional dialogues to the constructor,
 * or by specifying the class names as the value
 * of the system property with the name {@link #LOAD_DIALOGS_PROPERTY}
 * (multiple classnames may be separated by colons).
 * In the latter case the implementing class(es) must have a 
 * no-arg constructor.
 *
 * <p>By default, if the required classes are present, only the 
 * {@link FilestoreTableLoadDialog} and {@link SystemTableLoadDialog}
 * handlers are installed.
 * As well as ones you might implement yourself, a number of other 
 * useful {@link TableLoadDialog} implementations are available in
 * the Starlink java set; see the "Known Implementing Classes" section
 * of the TableLoadDialog javadocs.
 * These can be installed if desired as explained above.
 *
 * <p>If you want to make more customised use of this component than is
 * offered by <tt>showTableDialog</tt> it is possible, but these javadocs
 * don't go out of their way to explain how.  Take a look at the 
 * implementation of <tt>showTableDialog</tt>.
 * 
 * @author   Mark Taylor (Starlink)
 * @since    26 Nov 2004
 */
public class TableLoadChooser extends JPanel {

    private final JTextField locField_;
    private final Action locAction_;
    private final TableLoadDialog[] dialogs_;
    private final String[] extraDialogNames_;
    private TableLoadDialog[] knownDialogs_;
    private final Component[] activeComponents_;
    private StarTableFactory tableFactory_;
    private JComboBox formatSelector_;
    private TransferHandler transferHandler_;
    private Icon queryIcon_;
    private TableConsumer tableConsumer_;

    private final static Logger logger_ =
        Logger.getLogger( "uk.ac.starlink.table" );

    /**
     * List of classnames for {@link TableLoadDialog} 
     * implementations used by default.
     */
    public static String[] STANDARD_DIALOG_CLASSES = new String[] {
        FilestoreTableLoadDialog.class.getName(),
        SystemTableLoadDialog.class.getName(),
    };

    /**
     * Name of the system property which can be used to specify the class
     * names of additional {@link TableLoadDialog} implementations.  
     * Each must have a no-arg constructor.  Multiple classnames should be
     * separated by colons.
     */
    public static final String LOAD_DIALOGS_PROPERTY = "startable.load.dialogs";

    /**
     * Constructs a new chooser window with default characteristics.
     * A new default table factory and 
     * a default set of load dialogue options, as supplied by 
     * {@link #makeDefaultLoadDialogs}, are used.
     */
    public TableLoadChooser() {
        this( new StarTableFactory() );
        SwingAuthenticator auth = new SwingAuthenticator();
        auth.setParentComponent( this );
        tableFactory_.getJDBCHandler().setAuthenticator( auth );
    }

    /**
     * Constructs a new chooser window with a specified table factory.
     * A default set of load dialogue options, as supplied by 
     * {@link #makeDefaultLoadDialogs}, is used.
     *
     * @param  factory  factory to use for creating tables
     */
    public TableLoadChooser( StarTableFactory factory ) {
        this( factory, makeDefaultLoadDialogs(), new String[ 0 ] );
    }

    /**
     * Constructs a new chooser window with a specified table factory
     * and specification of what load dialogues to use.
     * The <tt>dialogs</tt> argument specifies the main list of actual
     * subdialogues which can be used for loading tables.
     * The <tt>extraDialogNames</tt> argument gives an additional list of
     * dialogue class names which will be instantiated if possible,
     * and perhaps presented in a menu or something.  It may or may not
     * contain some of the same names as the classes in <tt>dialogs</tt>
     * (any duplicates will be weeded out).
     *
     * @param  factory  table factory
     * @param  dialogs  main list of load dialogues
     * @param  extraDialogNames  names of additional classes which implement
     *         {@link TableLoadDialog}
     */
    public TableLoadChooser( StarTableFactory factory, 
                             TableLoadDialog[] dialogs,
                             String[] extraDialogNames ) {
        dialogs_ = dialogs;
        extraDialogNames_ = extraDialogNames;
        Border emptyBorder = BorderFactory.createEmptyBorder( 5, 5, 5, 5 );
        Box actionBox = Box.createVerticalBox();
        actionBox.setBorder( emptyBorder );
        setLayout( new BorderLayout() );
        add( actionBox, BorderLayout.CENTER );

        /* Prepare a list of components which can be enabled/disabled. */
        List activeList = new ArrayList();
        
        /* Construct and place format selector. */
        JComponent formatBox = Box.createHorizontalBox();
        formatBox.add( new JLabel( "Format: " ) );
        formatBox.add( Box.createHorizontalStrut( 5 ) );
        formatSelector_ = new JComboBox() {
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        formatBox.add( formatSelector_ );
        activeList.add( formatSelector_ );
        formatBox.setAlignmentX( LEFT_ALIGNMENT );
        actionBox.add( formatBox );
        setStarTableFactory( factory );

        /* Construct and place location text entry field. */
        locAction_ = new AbstractAction( "OK" ) {
            public void actionPerformed( ActionEvent evt ) {
                submitLocation( locField_.getText() );
            }
        };
        JComponent locBox = Box.createHorizontalBox();
        locField_ = new JTextField( 25 );
        locField_.addActionListener( locAction_ );
        locBox.add( new JLabel( "Location: " ) );
        locBox.add( Box.createHorizontalStrut( 5 ) );
        locBox.add( locField_ );
        activeList.add( locField_ );
        Dimension locSize = locBox.getPreferredSize();
        locSize.width = 1024;
        locBox.setMaximumSize( locSize );
        locBox.setAlignmentX( LEFT_ALIGNMENT );
        locBox.add( Box.createHorizontalStrut( 5 ) );
        locBox.add( new JButton( locAction_ ) );
        actionBox.add( Box.createVerticalStrut( 5 ) );
        actionBox.add( locBox );
        
        /* Create buttons for each of the pluggable dialog options. */
        List buttList = new ArrayList();
        for ( int i = 0; i < dialogs_.length; i++ ) {
            if ( dialogs_[ i ].isAvailable() ) {
                JButton butt = new JButton( makeAction( dialogs_[ i ] ) );
                activeList.add( butt );
                buttList.add( butt );
            }
        }
        JButton[] buttons = (JButton[]) buttList.toArray( new JButton[ 0 ] );
        int nopt = buttons.length;

        /* Position buttons. */
        int buttw = 0;
        for ( int i = 0; i < nopt; i++ ) {
            buttw = Math.max( buttw, buttons[ i ].getPreferredSize().width );
        }
        JComponent dialogBox = Box.createVerticalBox();
        for ( int i = 0; i < nopt; i++ ) {
            Dimension max = buttons[ i ].getMaximumSize();
            max.width = buttw;
            buttons[ i ].setMaximumSize( max );
            if ( i > 0 ) {
                dialogBox.add( Box.createVerticalStrut( 5 ) );
            }
            dialogBox.add( buttons[ i ] );
        }
        JPanel dialogLine = 
            new JPanel( new FlowLayout( FlowLayout.RIGHT, 0, 0 ) );
        dialogLine.add( dialogBox );
        dialogLine.setAlignmentX( LEFT_ALIGNMENT );
        actionBox.add( Box.createVerticalStrut( 10 ) );
        actionBox.add( dialogLine );

        /* Configure drag'n'drop operation. */
        transferHandler_ = new LoadTransferHandler();
        setTransferHandler( transferHandler_ );

        /* Store list of disablable components. */
        activeComponents_ = (Component[]) 
                            activeList.toArray( new Component[ 0 ] );
    }

    /**
     * Pops up a modal dialogue which invites the user to select a table.
     * If the user selects a valid <tt>StarTable</tt> it is returned,
     * if he declines, then <tt>null</tt> will be returned.
     * The user will be informed of any errors and asked to reconsider
     * (so this method should not normally be invoked in a loop).
     *
     * @param  parent  the parent component, used for window positioning etc
     * @return  a selected table, or <tt>null</tt>
     */
    public StarTable showTableDialog( Component parent ) {

        /* Create the dialogue. */
        final JProgressBar progBar = new JProgressBar();
        final JDialog dialog = createDialog( parent, progBar );

        /* Create and install a table consumer which can return 
         * the loaded table. */
        final StarTable[] result = new StarTable[ 1 ];
        final BasicTableConsumer tc = new BasicTableConsumer( dialog ) {
            protected void setLoading( boolean isLoading ) {
                super.setLoading( isLoading );
                setEnabled( ! isLoading );
                progBar.setIndeterminate( isLoading );
            }
            protected boolean tableLoaded( StarTable table ) {
                assert table != null;
                result[ 0 ] = table;
                dialog.dispose();
                return true;
            }
        };
        setTableConsumer( tc );

        /* Ensure that if the dialogue is closed for any reason (this may
         * happen as the result of the user hitting the Cancel button)
         * then the table consumer stops listening. */
        dialog.addWindowListener( new WindowAdapter() {
            public void windowClosed( WindowEvent evt ) {
                tc.cancel();
            }
        } );

        /* Pop up the dialogue.  Since it is modal, this will block until
         * (a) tableLoaded is called on the TableConsumer above because
         * a successful table load has completed or (b) the dialog is
         * disposed with a cancel or close action of some kind. */
        setEnabled( true );
        dialog.setVisible( true );
        return result[ 0 ];
    }

    
    /**
     * Synonym for {@link #showTableDialog(java.awt.Component)}.
     *
     * @deprecated  use <tt>showTableDialog</tt> instead
     */
    public StarTable getTable( Component parent ) {
        return showTableDialog( parent );
    }

    /**
     * Sets the object which does something with tables that the user
     * selects to load.
     *
     * @param   eater  table consumer
     */
    public void setTableConsumer( TableConsumer eater ) {
        tableConsumer_ = eater;
    }

    /**
     * Returns the object which does something with tables that the user
     * selects to load.
     *
     * @return  table consumer
     */
    public TableConsumer getTableConsumer() {
        return tableConsumer_;
    }

    /**
     * Returns the factory object which this chooser
     * uses to resolve files into <tt>StarTable</tt>s.
     *
     * @return  the factory
     */
    public StarTableFactory getStarTableFactory() {
        return tableFactory_;
    }

    /**
     * Sets the factory object which this chooser
     * uses to resove files into <tt>StarTable</tt>s.
     *
     * @param  factory  the factory
     */
    public void setStarTableFactory( StarTableFactory factory ) {
        tableFactory_ = factory;
        formatSelector_.setModel( makeFormatBoxModel( factory ) );
    }

    /**
     * Returns the FilestoreChooser used by this loader, if any.
     *
     * @return   filestore chooser
     */
    FilestoreChooser getFilestoreChooser() {
        for ( int i = 0; i < dialogs_.length; i++ ) {
            if ( dialogs_[ i ] instanceof FilestoreTableLoadDialog ) {
                return ((FilestoreTableLoadDialog) dialogs_[ i ]).getChooser();
            }
        }
        return null;
    }

    /**
     * Sets the configuration of this loader to match that of a
     * saver widget.  This will typically involve things like making 
     * sure they are viewing the same directory.
     *
     * @param  saver  saver
     */
    public void configureFromSaver( TableSaveChooser saver ) {
        FilestoreChooser loadChooser = getFilestoreChooser();
        FilestoreChooser saveChooser = saver.getFilestoreChooser();
        if ( loadChooser != null && saveChooser != null ) {
            loadChooser.setModel( saveChooser.getModel() );
        }
    }

    /**
     * Returns the format selected with which to interpret the table.
     *
     * @return  the selected format name (or <tt>null</tt>)
     */
    public String getFormatName() {
        return (String) formatSelector_.getSelectedItem();
    }

    /**
     * Returns an array of all dialogues known by this chooser.
     * This may be larger than the usually presented list, and may 
     * incur some additional cost (for instance classloading) when it is
     * called.  It is designed for presenting a more exhaustive list
     * in a menu, for instance.
     *
     * @return  list of actions corresponding to all known subdialogues
     */
    public TableLoadDialog[] getKnownDialogs() {
        if ( knownDialogs_ == null ) {
            Set classes = new HashSet();
            List tldList = new ArrayList();

            /* Add all the standard subdialogues. */
            for ( int i = 0; i < dialogs_.length; i++ ) {
                TableLoadDialog tld = dialogs_[ i ];
                classes.add( tld.getClass().getName() );
                tldList.add( tld );
            }

            /* Now go through the extra ones and try to instantiate each one
             * whose class hasn't already been added. */
            for ( int i = 0; i < extraDialogNames_.length; i++ ) {
                String cname = extraDialogNames_[ i ];
                if ( ! classes.contains( cname ) ) {
                    classes.add( cname );
                    try {
                        TableLoadDialog tld = (TableLoadDialog)
                            getClass().forName( cname ).newInstance();
                        tldList.add( tld );
                    }
                    catch ( Throwable th ) {
                        logger_.log( Level.INFO,
                                     "Error instantiating load dialogue " +
                                      cname, th );
                    }
                }
            }

            /* Create and return an array from the list. */
            knownDialogs_ = (TableLoadDialog[]) 
                            tldList.toArray( new TableLoadDialog[ 0 ] );
        }
        return knownDialogs_;
    }

    /**
     * Creates a menu containing actions for popping up modal dialogues
     * corresponding to all the known load dialogue classes
     * (as reported by {@link #getKnownDialogs}.  Some of these may be
     * inactive if the requisite classes are not present etc.
     * 
     * @param  menuName   name of the menu.  A default will be used if 
     *                    <tt>null</tt> is supplied
     */
    public JMenu makeKnownDialogsMenu( String menuName ) {
        final JMenu menu = new JMenu( menuName == null ? "DataSources"
                                                       : menuName );
        menu.addMenuListener( new MenuListener() {
            boolean done;
            public void menuSelected( MenuEvent evt ) {
                if ( ! done ) {
                    done = true;
                    TableLoadDialog[] tlds = getKnownDialogs();
                    for ( int i = 0; i < tlds.length; i++ ) {
                        menu.add( makeAction( tlds[ i ] ) );
                    }
                }
            }
            public void menuDeselected( MenuEvent evt ) {}
            public void menuCanceled( MenuEvent evt ) {}
        } );
        return menu;
    }

    public void setEnabled( boolean isEnabled ) {
        if ( isEnabled != isEnabled() ) {
            for ( int i = 0; i < activeComponents_.length; i++ ) {
                activeComponents_[ i ].setEnabled( isEnabled );
            }
            locAction_.setEnabled( isEnabled );
        }
        super.setEnabled( isEnabled );
    }

    /**
     * Constructs a modal dialogue containing this window which can
     * be presented to the user.
     *
     * @param   parent   parent window
     * @param   progBar  progress bar used to indicate load progress
     */
    public JDialog createDialog( Component parent, JProgressBar progBar ) {

        /* Locate parent's frame. */
        Frame frame = null;
        if ( parent != null ) {
            frame = parent instanceof Frame 
                  ? (Frame) parent
                  : (Frame) SwingUtilities.getAncestorOfClass( Frame.class,
                                                               parent );
        }

        /* Create a new dialogue. */
        final JDialog dialog = new JDialog( frame, "Load Table", true );
        dialog.setDefaultCloseOperation( JDialog.DISPOSE_ON_CLOSE );
        Container pane = new JPanel( new BorderLayout() );
        dialog.getContentPane().setLayout( new BorderLayout() );
        dialog.getContentPane().add( pane, BorderLayout.CENTER );
        dialog.getContentPane().add( progBar, BorderLayout.SOUTH );

        /* Place this component. */
        pane.setLayout( new BorderLayout() );
        pane.add( this, BorderLayout.CENTER );

        /* Place a little icon. */
        Box iconBox = Box.createVerticalBox();
        iconBox.add( Box.createVerticalGlue() );
        iconBox.add( new JLabel( getQueryIcon() ) );
        iconBox.add( Box.createVerticalGlue() );
        iconBox.setBorder( BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) );
        pane.add( iconBox, BorderLayout.WEST );

        /* Place a cancel button. */
        Action cancelAction = new AbstractAction( "Cancel" ) {
            public void actionPerformed( ActionEvent evt ) {
                dialog.dispose();
            }
        };
        Box cancelBox = Box.createHorizontalBox();
        cancelBox.add( Box.createHorizontalGlue() );
        cancelBox.add( new JButton( cancelAction ) );
        cancelBox.setBorder( BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) );
        pane.add( cancelBox, BorderLayout.SOUTH );

        /* Position. */
        dialog.pack();
        dialog.setLocationRelativeTo( parent );
        return dialog;
    }

    /**
     * Constructs and returns a new action suitable for invoking a
     * TableLoadDialog within this chooser.  This is called when constructing
     * the buttons for display.
     *
     * @param  tld   loader dialogue supplier
     * @return   action which calls the <tt>showLoadDialog</tt> method of
     *           <tt>tld</tt>
     */
    public Action makeAction( final TableLoadDialog tld ) {
        Action act = new AbstractAction( tld.getName() ) {
            public void actionPerformed( ActionEvent evt ) {
                boolean status =
                    tld.showLoadDialog( TableLoadChooser.this, tableFactory_,
                                        formatSelector_.getModel(),
                                        getTableConsumer() );
            }
        };
        act.putValue( Action.SHORT_DESCRIPTION, tld.getDescription() );
        act.putValue( Action.SMALL_ICON, tld.getIcon() );
        act.setEnabled( tld.isAvailable() );
        return act;   
    }

    /**
     * Returns a transfer handler which will accept a table dropped on it
     * as a selected one.  This handler is installed by default on this
     * window.
     *
     * @return  table drop target transfer handler
     */
    public TransferHandler getTableImportTransferHandler() {
        return transferHandler_;
    }

    /**
     * Attempts to make and select a table from a location string.
     *
     * @param  location
     */
    public void submitLocation( final String location ) {
        final StarTableFactory factory = tableFactory_;
        final String format = getFormatName();
        new LoadWorker( getTableConsumer(), location ) {
            public StarTable attemptLoad() throws IOException {
                return factory.makeStarTable( location, format );
            }
        }.invoke();
    }

    /**
     * Returns the action used when the location text is submitted.
     *
     * @return  action
     */
    public Action getSubmitAction() {
        return locAction_;
    }

    /**
     * Returns a default list of sub-dialogs which can be invoked to 
     * load a table.  This consists of those named in the 
     * {@link #STANDARD_DIALOG_CLASSES} variable as well as any named
     * by the contents of the {@link #LOAD_DIALOGS_PROPERTY} property
     * (as long as the requisite classes can be loaded and instantiated).
     *
     * @return   an array of {@link TableLoadDialog} objects
     */
    public static TableLoadDialog[] makeDefaultLoadDialogs() {
        return (TableLoadDialog[]) 
               Loader.getClassInstances( STANDARD_DIALOG_CLASSES,
                                         LOAD_DIALOGS_PROPERTY, 
                                         TableLoadDialog.class )
              .toArray( new TableLoadDialog[ 0 ] );
    }

    /**
     * Creates and returns a ComboBoxModel suitable for use in a JComboBox
     * which the user can use to choose the format of tables to be loaded.
     * Each element of the returned model is a String.
     *
     * @return   ComboBoxModel with entries for each of the known formats,
     *           as well as an AUTO option
     */
    public static ComboBoxModel makeFormatBoxModel( StarTableFactory factory ) {
        DefaultComboBoxModel fmodel = new DefaultComboBoxModel();
        fmodel.addElement( StarTableFactory.AUTO_HANDLER );
        for ( Iterator it = factory.getKnownBuilders().iterator();
              it.hasNext(); ) {
            TableBuilder handler = (TableBuilder) it.next();
            fmodel.addElement( handler.getFormatName() );
        }
        return fmodel;
    }

    /**
     * Return an icon used to indicate a query dialogue.
     *
     * @return icon
     */
    private Icon getQueryIcon() {
        if ( queryIcon_ == null ) {
            queryIcon_ = UIManager.getIcon( "OptionPane.questionIcon" );
        }
        return queryIcon_;
    }

    /**
     * Transfer handler for this window, which will treat a drop of
     * a suitable dragged object as equivalent to typing something in
     * the dialog box.
     */
    private class LoadTransferHandler extends TransferHandler {

         public boolean canImport( JComponent comp, DataFlavor[] flavors ) {
             return tableFactory_.canImport( flavors );
         }

         public boolean importData( JComponent comp, 
                                    final Transferable trans ) {
             final StarTableFactory factory = tableFactory_;
             TableConsumer eater = getTableConsumer();

             /* The table has to be loaded in line here, i.e. on the event
              * dispatch thread, since otherwise the weird IPC magic
              * which provides the inputstream from the Transferable
              * will go away.  This is unfortunate, since it might be
              * slow, but I don't *think* there's any alternative. */
             eater.loadStarted( "Dropped Table" );
             try {
                 return eater.loadSucceeded( factory.makeStarTable( trans ) );
             }
             catch ( Throwable th ) {
                 eater.loadFailed( th );
                 return false;
             }
        }

        public int getSourceActions( JComponent comp ) {
            return NONE;
        }
    }
}
