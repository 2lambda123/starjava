package uk.ac.starlink.topcat.plot;

import Acme.JPM.Encoders.GifEncoder;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.Icon;
import javax.swing.ListModel;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;
import org.jibble.epsgraphics.EpsGraphics2D;
import uk.ac.starlink.table.ColumnInfo;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.gui.StarTableColumn;
import uk.ac.starlink.topcat.AuxWindow;
import uk.ac.starlink.topcat.BasicAction;
import uk.ac.starlink.topcat.BitsRowSubset;
import uk.ac.starlink.topcat.ControlWindow;
import uk.ac.starlink.topcat.OptionsListModel;
import uk.ac.starlink.topcat.ResourceIcon;
import uk.ac.starlink.topcat.RowSubset;
import uk.ac.starlink.topcat.SuffixFileFilter;
import uk.ac.starlink.topcat.ToggleButtonModel;
import uk.ac.starlink.topcat.TopcatModel;
import uk.ac.starlink.topcat.TopcatUtils;
import uk.ac.starlink.ttools.convert.ValueConverter;
import uk.ac.starlink.util.gui.ErrorDialog;

/**
 * Abstract superclass for windows doing N-dimensional plots of table data.
 *
 * <p>The basic way that plotting works is as follows.  Almost all the
 * controls visible on the GraphicsWindow do nothing except trigger 
 * the replot action {@link #getReplotListener} when their state changes,
 * which schedules a replot to occur later on the event dispatch thread. 
 * When the replot is executed, the {@link #getPlotState} method is 
 * called which goes through all the controls and assembles a 
 * {@link PlotState} object (of a class which is probably specific to
 * the window implementation).  <em>Only if</em> this PlotState differs
 * from the last gathered PlotState will any actual plotting action take
 * place.  This means that we don't worry about triggering loads of
 * replot actions - as long as the state doesn't change materially 
 * between one and the next, they're cheap.  If the state does change
 * materially, then a new plot is required.  The work done for plotting
 * depends on the details of how the PlotState has changed - in some cases
 * new data will be acquired (<code>PointSelection.readPoints</code> 
 * is called - possibly expensive),
 * but if the data is the same as before, the plot just
 * needs to be redrawn (usually quite fast, since the various plotting
 * classes are written as efficiently as possible).
 * It is therefore very important for performance reasons that you can
 * tell whether one plot state differs from the last one.  Since the
 * PlotState is a newly created object each time, its <code>equals()</code>
 * method is used - so <code>PlotState.equals()</code> must be written 
 * with great care.  There's an assertion in this class which tests that
 * two PlotStates gathered at the same time are equal, so you should find
 * out if your equals() method is calling two equal states unequal.
 * If it's calling two unequal states equal, then you'll find that the
 * plot doesn't get updated when state changes.
 *
 * @author   Mark Taylor
 * @since    26 Oct 2005
 */
public abstract class GraphicsWindow extends AuxWindow {

    private final int ndim_;
    private final PointSelectorSet pointSelectors_;

    private final ReplotListener replotListener_;
    private final Action replotAction_;
    private final Action axisEditAction_;
    private final Action rescaleAction_;
    private final String[] axisNames_;
    private final ToggleButtonModel gridModel_;
    private final ToggleButtonModel[] flipModels_;
    private final ToggleButtonModel[] logModels_;
    private final JMenu exportMenu_;

    private StyleSet styleSet_;
    private BitSet usedStyles_;
    private Points points_;
    private PointSelection lastPointSelection_;
    private PlotState lastState_;
    private Box statusBox_;
    private boolean initialised_;
    private int guidePointCount_;
    private AxisWindow axisWindow_;
    private Range[] dataRanges_;
    private Range[] viewRanges_;
    private boolean forceReread_;
    private double padRatio_ = 0.02;

    private static JFileChooser exportSaver_;
    private static FileFilter psFilter_ =
        new SuffixFileFilter( new String[] { ".ps", ".eps", } );
    private static FileFilter gifFilter_ =
        new SuffixFileFilter( new String[] { ".gif" } );
    private static final Logger logger_ =
        Logger.getLogger( "uk.ac.starlink.topcat.plot" );
        
    /**
     * Constructor.
     *
     * @param   viewName  name of the view window
     * @param   axisNames  array of labels by which each axis is known;
     *          the length of this array defines the dimensionality of the plot
     * @param   parent   parent window - may be used for positioning
     */
    public GraphicsWindow( String viewName, String[] axisNames,
                           Component parent ) {
        super( viewName, parent );
        axisNames_ = axisNames;
        ndim_ = axisNames.length;
        replotListener_ = new ReplotListener();

        /* Axis flags. */
        flipModels_ = new ToggleButtonModel[ ndim_ ];
        logModels_ = new ToggleButtonModel[ ndim_ ];
        for ( int i = 0; i < ndim_; i++ ) {
            String ax = axisNames[ i ];
            flipModels_[ i ] = new ToggleButtonModel( "Flip " + ax + " Axis",
                null, "Reverse the sense of the " + axisNames[ i ] + " axis" );
            logModels_[ i ] = new ToggleButtonModel( "Log " + ax + " Axis",
                null, "Logarithmic scale for the " + axisNames[ i ] + " axis" );
            flipModels_[ i ].addActionListener( replotListener_ );
            logModels_[ i ].addActionListener( replotListener_ );
        }
        if ( ndim_ > 0 ) {
            flipModels_[ 0 ].setIcon( ResourceIcon.XFLIP );
            logModels_[ 0 ].setIcon( ResourceIcon.XLOG );
            if ( ndim_ > 1 ) {
                flipModels_[ 1 ].setIcon( ResourceIcon.YFLIP );
                logModels_[ 1 ].setIcon( ResourceIcon.YLOG );
            }
        }

        /* Set up point selector component. */
        pointSelectors_ = new PointSelectorSet() {
            protected PointSelector createSelector() {
                return GraphicsWindow.this.createPointSelector();
            }
            protected StyleEditor createStyleEditor() {
                return GraphicsWindow.this.createStyleEditor();
            }
        };
        getControlPanel().setLayout( new BoxLayout( getControlPanel(),
                                                    BoxLayout.Y_AXIS ) );
        getControlPanel().add( new SizeWrapper( pointSelectors_ ) );

        /* Ensure that changes to the point selection trigger a replot. */
        pointSelectors_.addActionListener( replotListener_ );

         /* Actions for exporting the plot. */
        Action gifAction = new ExportAction( "GIF", ResourceIcon.IMAGE,
                                             "Save plot as a GIF file",
                                             gifFilter_ ) {
            public void exportTo( OutputStream out ) throws IOException {
                exportGif( out );
            }
        };
        Action epsAction = new ExportAction( "EPS", ResourceIcon.PRINT,
                                             "Export to Encapsulated " +
                                             "Postscript file", psFilter_ ) {
            public void exportTo( OutputStream out ) throws IOException {
                exportEPS( out );
            }
        };
        getToolBar().add( epsAction );
        getToolBar().add( gifAction );
        exportMenu_ = new JMenu( "Export" );
        exportMenu_.setMnemonic( KeyEvent.VK_E );
        exportMenu_.add( epsAction );
        exportMenu_.add( gifAction );
        getJMenuBar().add( exportMenu_ );

        /* Other actions. */
        replotAction_ =
            new GraphicsAction( "Replot", ResourceIcon.REDO,
                                "Redraw the plot" );
        rescaleAction_ =
            new GraphicsAction( "Rescale", ResourceIcon.RESIZE,
                                "Rescale the plot to show all points" );

        /* Action for showing grid. */
        gridModel_ = new ToggleButtonModel( "Show Grid", ResourceIcon.GRID_ON,
                                            "Select whether grid lines are " +
                                            "drawn" );
        gridModel_.setSelected( true );
        gridModel_.addActionListener( replotListener_ );

        /* Action for performing user configuration of axes. */
        axisEditAction_ = new GraphicsAction( "Configure Axes",
                                              ResourceIcon.AXIS_EDIT,
                                              "Set axis labels and ranges" );
    }

    public void setVisible( boolean visible ) {
        if ( visible ) {
            ensureInitialised();
            if ( lastState_ == null ) {
                lastState_ = getPlotState();
                lastState_.setValid( false );
            }
        }
        super.setVisible( visible );
    }

    /**
     * Check that initialisations have been performed.
     */
    private void ensureInitialised() {
        if ( ! initialised_ ) {
            init();
            initialised_ = true;
        }
    }
    
    /**
     * Perform initialisation which can't be done in the constructor
     * (typically because it calls potentially overridden methods).
     */
    private void init() {

        /* Add a starter point selector. */
        PointSelector mainSel = createPointSelector();
        pointSelectors_.addNewSelector( mainSel );
        pointSelectors_.revalidate();

        /* Add axis editors and corresponding data and view range arrays. */
        AxisEditor[] axeds = mainSel.createAxisEditors();
        int nax = axeds.length;
        dataRanges_ = new Range[ nax ];
        viewRanges_ = new Range[ nax ];
        for ( int i = 0; i < nax; i++ ) {
            viewRanges_[ i ] = new Range();
            axeds[ i ].addMaintainedRange( viewRanges_[ i ] );
            axeds[ i ].addActionListener( replotListener_ );
        }
        axisWindow_ = new AxisWindow( this, axeds );
        axisWindow_.addActionListener( replotListener_ );

        /* Set a suitable default style set. */
        long npoint = 0;
        if ( guidePointCount_ > 0 ) {
            npoint = guidePointCount_;
        }
        else {
            TopcatModel selectedTable =
                getPointSelectors().getMainSelector().getTable();
            if ( selectedTable != null ) {
                npoint = selectedTable.getDataModel().getRowCount();
            }
            else {
                ListModel tablesList = ControlWindow.getInstance()
                                                    .getTablesListModel();
                npoint = 10000;
                for ( int i = 0; i < tablesList.getSize(); i++ ) {
                    npoint = Math.min( npoint,
                                       ((TopcatModel)
                                        tablesList.getElementAt( i ))
                                      .getDataModel().getRowCount() );
                }
            }
        }
        setStyles( getDefaultStyles( (int) Math.min( npoint,
                                                     Integer.MAX_VALUE ) ) );
        mainSel.setStyles( getStyles() );
    }

    /**
     * Provides a hint to this window how many points it's likely to be
     * plotting.  This should be called before the window is first 
     * displayed, and may influence the default plotting style.
     *
     * @param   npoint  approximate number of data points that may be plotted
     */
    public void setGuidePointCount( int npoint ) {
        guidePointCount_ = npoint;
    }

    /**
     * Sets the ratio by which the data ranges calculated by the
     * GraphicsWindow implementation of {@link #calculateRanges} are
     * padded.
     *
     * @param  pad  padding ratio (typically a few percent)
     */
    public void setPadRatio( double pad ) {
        padRatio_ = pad;
    }

    /**
     * Returns the ratio by which the data ranges calculated by the
     * GraphicsWindow implememetation of {@link #calculateRanges} are
     * padded.
     *
     * @return  padding ratio (by default a few percent)
     */
    public double getPadRatio() {
        return padRatio_;
    }

    /**
     * Returns the menu which contains export actions.
     *
     * @return  export menu
     */
    public JMenu getExportMenu() {
        return exportMenu_;
    }

    /**
     * Returns the PointSelectorSet component used by this window.
     *
     * @return  point selector set
     */
    public PointSelectorSet getPointSelectors() {
        return pointSelectors_;
    }

    /**
     * Returns the most recently calculated data range objects.
     * These were calculated by invocation of {@link #calculateRanges},
     * which probably occurred during the last data read or rescale
     * operation.  They describe the natural ranges of the data,
     * which typically means that they defibe an N-dimensional region
     * into which all the current data points fall.
     * 
     * @return  array of data ranges, one for each axis
     */
    public Range[] getDataRanges() {
        return dataRanges_;
    }

    /**
     * Returns an array of ranges which may be set to determine the
     * actual range of visible data displayed by this plot.  The 
     * dimensions should generally match those returned by 
     * {@link #getDataRanges}.  The actual range of visible data will
     * generally be got by combining the data range with the visible
     * range on each axis.  Elements of the returned array may have
     * their states altered, but should not be replaced, since 
     * these elements are kept up to date by the editors in the axis window.
     *
     * @return   array of visible ranges, one for each axis
     */
    public Range[] getViewRanges() {
        return viewRanges_;
    }

    /**
     * Returns an array of button models representing the inversion state
     * for each axis.  Selected state for each model indicates that that
     * axis has been flipped.
     *
     * @return   button models for flip state
     */
    public ToggleButtonModel[] getFlipModels() {
        return flipModels_;
    }

    /**
     * Returns an array of button models representing the log/linear state
     * for each axis.  Selected state for each model indicates that that
     * axis is logarithmic, unselected means linear.
     *
     * @return  button models for log state
     */
    public ToggleButtonModel[] getLogModels() {
        return logModels_;
    }

    /**
     * Returns the most recently read Points object.
     *
     * @return  points object
     */
    public Points getPoints() {
        return points_;
    }

    /**
     * Returns a line suitable for putting status information into.
     *
     * @return  status  component
     */
    public Box getStatusBox() {
        if ( statusBox_ == null ) {
            statusBox_ = Box.createHorizontalBox();
            getControlPanel().add( Box.createVerticalStrut( 5 ) );
            getControlPanel().add( statusBox_ );
        }
        return statusBox_;
    }

    /**
     * Returns the component containing the graphics output of this 
     * window.  This is the component which is exported or printed etc,
     * so should contain only the output data, not any user interface
     * decoration.
     *
     * @return   plot component
     */
    protected abstract JComponent getPlot();

    /**
     * Performs an actual plot.  Concrete subclasses should implement this
     * to paint the component according to the given <code>state</code>
     * and <code>points</code>.  
     * Probably a {@link java.awt.Component#repaint()} will be required
     * at the end.
     *
     * @param  state  plot state determining details of plot configuration
     * @param  points  data to plot
     */
    protected abstract void doReplot( PlotState state, Points points );

    /**
     * Returns a new PointSelector instance to be used for selecting
     * points to be plotted.
     *
     * @return   new point selector component
     */
    protected PointSelector createPointSelector() {
        DefaultPointSelector.ToggleSet[] toggleSets = 
            new DefaultPointSelector.ToggleSet[] {
                new DefaultPointSelector.ToggleSet( "Log", logModels_ ),
                new DefaultPointSelector.ToggleSet( "Flip", flipModels_ ),
            };
        return new DefaultPointSelector( getStyles(), axisNames_, toggleSets );
    };

    /**
     * Creates a style editor suitable for this window.
     *
     * @return   new style editor
     */
    protected abstract StyleEditor createStyleEditor();

    /**
     * Returns a StyleSet which can supply markers.
     * The <code>npoint</code> may be used as a hint for how many 
     * points are expected to be drawn with it.
     *
     * @param    npoint  approximate number of points - use -1 for unknown
     * @return   style factory
     */
    public abstract StyleSet getDefaultStyles( int npoint );

    /**
     * Sets the style set to use for this window.
     * 
     * @param   styleSet  new style set
     */
    public void setStyles( StyleSet styleSet ) {
        styleSet_ = styleSet;
        usedStyles_ = new BitSet();
        int nsel = pointSelectors_.getSelectorCount();
        for ( int isel = 0; isel < nsel; isel++ ) {
            pointSelectors_.getSelector( isel ).setStyles( getStyles() );
        }
    }

    /**
     * Returns a style set suitable for use with a new PointSelector.
     * Note this is not the same object as was set by {@link #setStyles},
     * but it is based on it - it will dispense styles from the same set,
     * but avoid styles already dispensed to other selectors.
     *
     * @return   style set suitable for a new selector
     */
    public MutableStyleSet getStyles() {
        return new PoolStyleSet( styleSet_, usedStyles_ );
    }

    /**
     * Constructs a new PlotState.  This is called by {@link #getPlotState}
     * prior to the PlotState configuration done there.  Thus if a 
     * subclass wants to provide and configure a particular state
     * (for instance one of a specialised subclass of PlotState) it can
     * override this method to do so.
     * The default implementation just invokes <code>new PlotState()</code>.
     *
     * @return   returns a new PlotState object ready for generic
     *           configuration
     */
    protected PlotState createPlotState() {
        return new PlotState();
    }

    /**
     * Returns an object which characterises the choices the user has
     * made in the GUI to indicate the plot that s/he wants to see.
     *
     * <p>The <code>GraphicsWindow</code> implementation of this method
     * as well as populating the state with standard information
     * also calls {@link PointSelection#readPoints}
     * and {@link #calculateRanges} if necessary.
     *
     * @return  snapshot of the currently-selected plot request
     */
    public PlotState getPlotState() {

        /* Create a plot state as delegated to the current instance. */
        PlotState state = createPlotState();

        /* Can't plot, won't plot. */
        if ( ! pointSelectors_.getMainSelector().isValid() ) {
            state.setValid( false );
            return state;
        }

        /* Set per-axis characteristics. */
        StarTable mainData = pointSelectors_.getMainSelector().getData();
        ColumnInfo[] axinfos = new ColumnInfo[ ndim_ ];
        boolean[] flipFlags = new boolean[ ndim_ ];
        boolean[] logFlags = new boolean[ ndim_ ];
        ValueConverter[] converters = new ValueConverter[ ndim_ ];
        for ( int i = 0; i < ndim_; i++ ) {
            ColumnInfo cinfo = mainData.getColumnInfo( i );
            axinfos[ i ] = cinfo;
            converters[ i ] =
                (ValueConverter)
                cinfo.getAuxDatumValue( TopcatUtils.NUMERIC_CONVERTER_INFO,
                                        ValueConverter.class );
            flipFlags[ i ] = flipModels_[ i ].isSelected();
            logFlags[ i ] = logModels_[ i ].isSelected();
        }
        state.setAxes( axinfos );
        state.setConverters( converters );
        state.setLogFlags( logFlags );
        state.setFlipFlags( flipFlags );

        /* Set grid status. */
        state.setGrid( gridModel_.isSelected() );

        /* Set point selection, reading the points data if necessary
         * (that is if the point selection has changed since last time
         * it was read). */
        PointSelection pointSelection = pointSelectors_.getPointSelection();
        state.setPointSelection( pointSelection );
        boolean sameData = pointSelection.sameData( lastPointSelection_ );
        if ( ( ! sameData ) || forceReread_ ) {
            forceReread_ = false;
            try {

                /* Read the data if required. */
                points_ = pointSelection.readPoints();

                /* Calculate new data ranges corresponding to the new data. */
                dataRanges_ = calculateRanges( pointSelection, points_ );

                /* If we're looking at what is effectively a new graph,
                 * reset the viewing limits to null, so the visible range
                 * will be defined only by the data. */
                if ( ! sameData ) {
                    for ( int i = 0; i < viewRanges_.length; i++ ) {
                        viewRanges_[ i ].clear();
                    }
                }
            }

            /* In case of trouble inform the user. */
            catch ( IOException e ) {
                ErrorDialog.showError( GraphicsWindow.this,
                                       "Error reading table data", e );
                points_ = null;
                state.setValid( false );
                return state;
            }

            /* Remember point selection for comparison next time. */
            lastPointSelection_ = pointSelection;
        }

        /* Set axis labels configured in the axis editor window. */
        AxisEditor[] eds = axisWindow_.getEditors();
        int nax = eds.length;
        String[] labels = new String[ nax ];
        for ( int i = 0; i < eds.length; i++ ) {
            AxisEditor ed = eds[ i ];
            labels[ i ] = ed.getLabel();
        }
        state.setAxisLabels( labels );

        /* Set visible ranges, based on data range and ranges explicitly
         * selected by the user. */
        double[][] bounds = new double[ dataRanges_.length ][];
        for ( int i = 0; i < dataRanges_.length; i++ ) {
            Range range = new Range( dataRanges_[ i ] );
            range.limit( viewRanges_[ i ] );
            bounds[ i ] = range.getFiniteBounds( logFlags[ i ] );
        }
        state.setRanges( bounds );

        /* Return the configured state for use. */
        state.setValid( true );
        return state;
    }

    /**
     * Returns the button model used to select whether a grid will be
     * drawn or not.
     *
     * @return   grid toggle model
     */
    public ToggleButtonModel getGridModel() {
        return gridModel_;
    }

    /**
     * Returns an action which can be used to force a replot of the plot.
     *
     * @return   replot action
     */
    public Action getReplotAction() {
        return replotAction_;
    }

    /**
     * Returns an action which will recalculate data ranges, clear 
     * view ranges, and replot the data.
     *
     * @return  rescale action
     */
    public Action getRescaleAction() {
        return rescaleAction_;
    }

    /**
     * Returns an action which can be used to configure axes manually.
     *
     * @return   axis configuration action
     */
    public Action getAxisEditAction() {
        return axisEditAction_;
    }

    /**
     * Sets the main table in the point selector component.
     *
     * @param  tcModel  new table
     */
    public void setMainTable( TopcatModel tcModel ) {
        PointSelector mainSel = pointSelectors_.getMainSelector();
        mainSel.setTable( tcModel, true );
    }

    /**
     * Redraws the plot if any of the characteristics indicated by the
     * currently-requested plot state have changed since the last time
     * it was done.  If no changes have been made, no work is done, in
     * which case it will be cheap to call.
     *
     * <p>This method schedules a replot on the event dispatch thread,
     * so it may be called from any thread.
     */
    public void replot() {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                performReplot();
            }
        } );
    }                              

    /**
     * Redraws the plot in-thread, perhaps taking account of whether 
     * the plot state has changed since last time it was done.
     */
    private void performReplot() {

        /* If called before this window is ready, do nothing. */
        if ( ! initialised_ ) {
            return;
        }

        /* Interrogate this window for the details of the plot to be done. */
        PlotState state = getPlotState();

        /* Check that two plot state objects obtained one after another
         * satisfy the equals() relationship.  This is not required for 
         * correctness, but it is important for performance.  If you're
         * getting an assertion error here, find out why the two 
         * PlotStates are unequal and fix it (probably by providing 
         * suitable equals() implementations for plotstate constituent
         * objects).  getPlotState() itself ought to be cheap, so this 
         * assertion should not take much time. */
        assert state.equals( getPlotState() ) : state.compare( getPlotState() );

        /* Only if the current plot state materially differs from its 
         * value last time we did this, do the actual drawing. */
        if ( ! state.equals( lastState_ ) ) {
            doReplot( state, points_ );
            lastState_ = state;
        }
    }

    /**
     * Returns a listener which will perform a replot when any event occurs.
     *
     * @return   replot listener
     */
    protected ReplotListener getReplotListener() {
        return replotListener_;
    }

    /**
     * Returns the axis configuration window associated with this window.
     *
     * @return  axis editor dialogue
     */
    public AxisWindow getAxisWindow() {
        return axisWindow_;
    }

    /**
     * Adds a new row subset to tables associated with this window as
     * appropriate.  The subset is based on a bit vector
     * representing the points in this window's Points object.
     *
     * @param  pointsMask  bit vector giving included points
     */
    protected void addNewSubsets( BitSet pointsMask ) {

        /* If the subset is empty, just warn the user and return. */
        if ( pointsMask.cardinality() == 0 ) {
            JOptionPane.showMessageDialog( this, "Empty subset",
                                           "Blank Selection",
                                           JOptionPane.ERROR_MESSAGE );
            return;                                  
        }
  
        /* Get the name for the new subset(s). */
        String name = enquireSubsetName();
        if ( name == null ) {
            return;
        }

        /* For the given mask, which corresponds to all the plotted points,
         * deconvolve it into individual masks for any of the tables
         * that our point selection is currently dealing with. */
        PointSelection.TableMask[] tableMasks =
            lastState_.getPointSelection().getTableMasks( pointsMask );

        /* Handle each of the affected tables separately. */
        for ( int i = 0; i < tableMasks.length; i++ ) {
            TopcatModel tcModel = tableMasks[ i ].getTable();
            BitSet tmask = tableMasks[ i ].getMask();

            /* Try adding a new subset to the table. */
            if ( tmask.cardinality() > 0 ) {
                OptionsListModel subsets = tcModel.getSubsets();
                int inew = subsets.size();
                assert tmask.length() <= tcModel.getDataModel().getRowCount();
                subsets.add( new BitsRowSubset( name, tmask ) );

                /* Then make sure that the newly added subset is selected
                 * in each of the point selectors. */
                PointSelectorSet pointSelectors = getPointSelectors();
                for ( int ips = 0; ips < pointSelectors.getSelectorCount();
                      ips++ ) {
                    PointSelector psel = pointSelectors.getSelector( ips );
                    if ( psel.getTable() == tcModel ) {
                        boolean[] flags = psel.getSubsetSelection();
                        assert flags.length == inew + 1;
                        flags[ inew ] = true;
                        psel.setSubsetSelection( flags );
                    }
                }
            }
        }
    }

    /**
     * Calculates data ranges for a given data set.
     * The returned Range array is the one which will be returned from
     * future calls of {@link #getDataRanges}.
     * Subclasses may override this method to alter its behaviour.
     *
     * @param  pointSelection  point selection for the plot
     * @param  points  point data for the plot
     */
    public Range[] calculateRanges( PointSelection pointSelection,
                                    Points points ) {

        /* Set up blank range objects. */
        int ndim = points.getNdim();
        Range[] ranges = new Range[ ndim ];
        for ( int idim = 0; idim < ndim; idim++ ) {
            ranges[ idim ] = new Range();
        }

        /* Submit each data point which will be plotted to the ranges. */
        RowSubset[] sets = pointSelection.getSubsets();
        int nset = sets.length;
        int npoint = points.getCount();
        double[] coords = new double[ ndim ];
        for ( int ip = 0; ip < npoint; ip++ ) {
            points.getCoords( ip, coords );
            boolean isValid = true;
            for ( int idim = 0; idim < ndim && isValid; idim++ ) {
                isValid = isValid && ( ! Double.isNaN( coords[ idim ] ) &&
                                       ! Double.isInfinite( coords[ idim ] ) );
            }
            if ( isValid ) {
                boolean isUsed = false;
                for ( int iset = 0; iset < nset && ! isUsed; iset++ ) {
                    isUsed = isUsed || sets[ iset ].isIncluded( ip );
                }
                if ( isUsed ) {
                    for ( int idim = 0; idim < ndim; idim++ ) {
                        ranges[ idim ].submit( coords[ idim ] );
                    }
                }
            }
        }

        /* Add some padding at each end. */
        for ( int idim = 0; idim < ndim; idim++ ) {
            ranges[ idim ].pad( getPadRatio() );
        }

        /* Return the range array. */
        return ranges;
    }

    /**
     * Rescales the data acoording to the most recently read data and current
     * point selection.  The data ranges are recalculated and the view
     * ranges are cleared.
     */
    private void rescale() {
        PlotState state = getPlotState();
        Points points = getPoints();
        if ( state.getValid() && points != null ) {
            getAxisWindow().clearRanges();
            dataRanges_ = calculateRanges( state.getPointSelection(), points ); 
            for ( int i = 0; i < viewRanges_.length; i++ ) {
                viewRanges_[ i ].clear();
            }
        }
    }

    /**
     * Exports the currently displayed plot to encapsulated postscript.
     *
     * @param  ostrm  destination stream for the EPS
     */
    private void exportEPS( OutputStream ostrm ) throws IOException {

        /* Construct a graphics object which will write postscript
         * down this stream. */
        JComponent plot = getPlot();
        Rectangle bounds = plot.getBounds();
        EpsGraphics2D g2 = new EpsGraphics2D( getTitle(), ostrm,
                                              bounds.x, bounds.y,
                                              bounds.x + bounds.width,
                                              bounds.y + bounds.height );

        /* Do the drawing. */
        plot.print( g2 );

        /* Note this close call *must* be made, otherwise the
         * eps file is not flushed or correctly terminated.
         * This closes the output stream too. */
        g2.close();
    }

    /**
     * Exports the currently displayed plot to GIF format.
     *
     * <p>There's something wrong with this - it ought to produce a
     * transparent background, but it doesn't.  I'm not sure why, or
     * even whether it's to do with the plot or the encoder.
     *
     * @param  ostrm  destination stream for the gif
     */
    private void exportGif( OutputStream ostrm ) throws IOException {

        /* Get the component which will be plotted and its dimensions. */
        JComponent plot = getPlot();
        int w = plot.getWidth();
        int h = plot.getHeight();

        /* Draw it onto a new BufferedImage. */
        BufferedImage image =
            new BufferedImage( w, h, BufferedImage.TYPE_4BYTE_ABGR );
        plot.paint( image.getGraphics() );

        /* Count the number of colours represented in the resulting image. */
        Set colors = new HashSet();
        for ( int ix = 0; ix < w; ix++ ) {
            for ( int iy = 0; iy < h; iy++ ) {
                colors.add( new Integer( image.getRGB( ix, iy ) ) );
            }
        }

        /* If there are too many, redraw the image into an indexed image
         * instead.  This is necessary since the GIF encoder we're using
         * here just gives up if there are too many. */
        if ( colors.size() > 254 ) {
            image = new BufferedImage( w, h, BufferedImage.TYPE_BYTE_INDEXED );
            plot.paint( image.getGraphics() );
        }

        /* Write the image as a gif down the provided stream. */
        new GifEncoder( image, ostrm ).encode();
    }

    /**
     * Returns a file chooser widget with which the user can select a
     * file to output the currently plotted graph to in some serialized form.
     *
     * @return   a file chooser
     */
    private JFileChooser getExportSaver() {
        if ( exportSaver_ == null ) {
            exportSaver_ = new JFileChooser( "." );
            exportSaver_.setAcceptAllFileFilterUsed( true );
        }
        return exportSaver_;
    }

    /**
     * Returns the index in the TableModel (not the TableColumnModel) of
     * the given TableColumn.
     *
     * @param   tcol   the column whose index is to be found
     * @return  the index of <tt>tcol</tt> in the table model
     */
    public int getColumnIndex( TableColumn tcol ) {
        return tcol.getModelIndex();
    }

    public void dispose() {
        super.dispose();

        /* Configure all the point selectors to use a new, dummy TopcatModel
         * instead of the one they were using before.  The main purpose of 
         * this is to give the selectors a chance to unregister themselves
         * as listeners to the old TopcatModel.  This is important so that
         * no references exist in listener lists to this window, so that
         * it can be garbage collected (once disposed, this window can't
         * become visible again). */
        TopcatModel dummyModel = TopcatModel.createDummyModel();
        PointSelectorSet psels = getPointSelectors();
        for ( int i = 0; i < psels.getSelectorCount(); i++ ) {
            psels.getSelector( i ).configureForTable( dummyModel );
        }
    }

//  public void finalize() throws Throwable {
//      super.finalize();
//      logger_.fine( "Finalize " + this.getClass().getName() );
//  }

    /**
     * Actions for exporting the plot to a file.
     */
    protected abstract class ExportAction extends BasicAction {
        final String formatName_;
        final FileFilter filter_;

        /**
         * Constructs an export action.
         * 
         * @param   formatName  short name for format
         * @param   icon   icon for action
         * @param   descrip  description for action
         * @param   filter   file filter appropriate for export files
         */
        ExportAction( String formatName, Icon icon, String desc, 
                      FileFilter filter ) {
            super( "Export as " + formatName, icon, desc );
            formatName_ = formatName;
            filter_ = filter;
        }

        /**
         * Performs the export by writing bytes to a given stream.
         * Implementations should not close the stream after writing.
         *
         * @param  out  destination stream
         */
        public abstract void exportTo( OutputStream out ) throws IOException;

        public void actionPerformed( ActionEvent evt ) {
            Component parent = GraphicsWindow.this;

            /* Acquire and configure the file chooser. */
            JFileChooser chooser = getExportSaver();
            chooser.setDialogTitle( "Export Plot As " + formatName_ );
            chooser.setFileFilter( filter_ );

            /* Prompt the user to select a file for output. */
            if ( chooser.showDialog( parent, "Write " + formatName_ ) ==
                 JFileChooser.APPROVE_OPTION ) {
                OutputStream ostrm = null;
                try {

                    /* Construct the output stream. */
                    File file = chooser.getSelectedFile();
                    ostrm = new BufferedOutputStream(
                                    new FileOutputStream( file ) );

                    /* Write output to it. */
                    exportTo( ostrm );
                }
                catch ( IOException e ) {
                    ErrorDialog.showError( parent, "Write Error", e );
                }
                finally {
                    if ( ostrm != null ) {
                        try {
                            ostrm.close();
                        }
                        catch ( IOException e ) {
                            // no action
                        }
                    }
                }
            }
        }
    }

    /**
     * ExportAction which uses the java ImageIO framework to do the export.
     */
    protected class ImageIOExportAction extends ExportAction {

        private final boolean ok_;
        private final String formatName_;

        public ImageIOExportAction( String formatName, FileFilter filter ) {
            super( formatName, ResourceIcon.IMAGE,
                   "Save plot as a " + formatName + " file", filter );
            ok_ = ImageIO.getImageWritersByFormatName( formatName ).hasNext();
            formatName_ = formatName;
        }

        public boolean isEnabled() {
            return ok_ && super.isEnabled();
        }

        public void exportTo( OutputStream out ) throws IOException {
            JComponent plot = getPlot();
            int w = plot.getWidth();
            int h = plot.getHeight();
            BufferedImage image = 
                new BufferedImage( w, h, BufferedImage.TYPE_INT_RGB );
            plot.paint( image.getGraphics() );
            boolean done = ImageIO.write( image, formatName_, out );
            out.flush();
            if ( ! done ) {
                throw new IOException( "No handler for format " + formatName_ +
                                       " (surprising - thought there was)" );
            }
        }
    }

    /**
     * Miscellaneous actions.
     */
    private class GraphicsAction extends BasicAction {
        GraphicsAction( String name, Icon icon, String desc ) {
            super( name, icon, desc );
        }
        public void actionPerformed( ActionEvent evt ) {
            if ( this == replotAction_ ) {

                /* Force a re-read of the data. */
                forceReread_ = true;
                lastState_ = null;
                replot();
            }
            else if ( this == axisEditAction_ ) {
                axisWindow_.show();
            }
            else if ( this == rescaleAction_ ) {
                rescale();
                replot();
            }
        }
    }

    /**
     * General purpose listener which replots given an event.
     */
    protected class ReplotListener implements ActionListener, ItemListener,
                                              ListSelectionListener,
                                              ChangeListener {
        public void actionPerformed( ActionEvent evt ) {
            replot();
        }
        public void itemStateChanged( ItemEvent evt ) {
            replot();
        }
        public void valueChanged( ListSelectionEvent evt ) {
            replot();
        }
        public void stateChanged( ChangeEvent evt ) {
            replot();
        }
    }

}
