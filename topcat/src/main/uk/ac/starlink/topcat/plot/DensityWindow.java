package uk.ac.starlink.topcat.plot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.filechooser.FileFilter;
import nom.tam.fits.BasicHDU;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import org.votech.plastic.PlasticHubListener;
import uk.ac.starlink.fits.FitsConstants;
import uk.ac.starlink.plastic.PlasticUtils;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.topcat.BasicAction;
import uk.ac.starlink.topcat.ControlWindow;
import uk.ac.starlink.topcat.ResourceIcon;
import uk.ac.starlink.topcat.SuffixFileFilter;
import uk.ac.starlink.topcat.ToggleButtonModel;
import uk.ac.starlink.topcat.TopcatPlasticListener;
import uk.ac.starlink.topcat.TopcatUtils;
import uk.ac.starlink.ttools.func.Maths;
import uk.ac.starlink.ttools.func.Times;
import uk.ac.starlink.util.gui.ErrorDialog;

/**
 * Graphics window which displays a density plot, that is a 2-dimensional
 * histogram.  Each screen pixel corresponds to a bin of the 2-d histogram,
 * and is coloured according to how many items fall into it.
 *
 * @author   Mark Taylor
 * @since    1 Dec 2005
 */
public class DensityWindow extends GraphicsWindow {

    private final DensityPlot plot_;
    private final BlobPanel blobPanel_;
    private final Action blobAction_;
    private final CountsLabel plotStatus_;
    private final ToggleButtonModel rgbModel_;
    private final ToggleButtonModel zLogModel_;
    private final CutChooser cutter_;
    private final JLabel cutLabel_;
    private final PixelSizeAction pixIncAction_;
    private final PixelSizeAction pixDecAction_;
    private final Action fitsAction_;
    private final DensityStyle[] styles_;
    private final List rgbRepaintList_;
    private int pixelSize_ = 1;

    private static FileFilter fitsFilter_ =
        new SuffixFileFilter( new String[] { ".fits", ".fit", ".fts", } );
    private static FileFilter jpegFilter_ =
        new SuffixFileFilter( new String[] { ".jpeg", ".jpg", } );

    private static final URI MSG_LOADIMG =
        PlasticUtils.createURI( "ivo://votech.org/fits/image/loadFromURL" );

    /**
     * Constructs a new DensityWindow.
     *
     * @param   parent   parent component (may be used for positioning)
     */
    public DensityWindow( Component parent ) {
        super( "Density Plot", new String[] { "X", "Y" }, parent );

        /* There's only one style set it makes sense to use for this window.
         * Construct it here. */
        styles_ = new DensityStyle[] {
            new DStyle( DensityStyle.RED ),
            new DStyle( DensityStyle.GREEN ),
            new DStyle( DensityStyle.BLUE ),
        };

        /* Construct a plotting surface to receive the graphics. */
        final PlotSurface surface = new PtPlotSurface( this );
        ((PtPlotSurface) surface).setPadPixels( 0 );

        /* No grid.  There are currently problems with displaying it
         * over the top of the plot. */
        getGridModel().setSelected( false );

        /* Construct and populate the plot panel with the 2d histogram
         * itself and a transparent layer for doodling blobs on. */
        plot_ = new DensityPlot( surface ) {
            protected void reportCounts( int nPoint, int nInc, int nVis ) {
                plotStatus_.setValues( new int[] { nPoint, nInc, nVis } );
            }
            protected void reportCuts( int[] loCuts, int[] hiCuts ) {
                StringBuffer sbuf = new StringBuffer();
                for ( int i = 0; loCuts != null && i < loCuts.length; i++ ) {
                    if ( i > 0 ) {
                        sbuf.append( ";  " );
                    }
                    sbuf.append( loCuts[ i ] )
                        .append( " \u2014 " )
                        .append( hiCuts[ i ] );
                }
                cutLabel_.setText( sbuf.toString() );
            }
        };
        JPanel plotPanel = new JPanel();
        blobPanel_ = new BlobPanel() {
            protected void blobCompleted( Shape blob ) {
                addNewSubsets( plot_.getContainedMask( blob ) );
            }
        };
        blobPanel_.setColors( new Color( 0x80a0a0a0, true ),
                              new Color( 0xc0a0a0a0, true ) );
        blobAction_ = blobPanel_.getBlobAction();
        plotPanel.setLayout( new OverlayLayout( plotPanel ) );
        plotPanel.add( blobPanel_ );
        plotPanel.add( plot_ );
        getMainArea().add( plotPanel, BorderLayout.CENTER );

        /* Construct and add a status line. */
        plotStatus_ = new CountsLabel( new String[] {
            "Potential", "Included", "Visible",
        } );
        PositionLabel posStatus = new PositionLabel( surface );
        posStatus.setMaximumSize( new Dimension( Integer.MAX_VALUE,
                                                 posStatus.getMaximumSize()
                                                          .height ) );
        getStatusBox().add( plotStatus_ );
        getStatusBox().add( Box.createHorizontalStrut( 5 ) );
        getStatusBox().add( posStatus );

        /* Action for resizing the plot. */
        Action resizeAction = new BasicAction( "Rescale", ResourceIcon.RESIZE,
                                               "Rescale the plot to show " +
                                               "all data" ) {
            public void actionPerformed( ActionEvent evt ) {
                getAxisWindow().clearRanges();
                plot_.rescale();
                forceReplot();
            }
        };

        /* Action for rgb/greyscale toggle. */
        rgbModel_ = new ToggleButtonModel( "Colour", ResourceIcon.COLOR,
                                           "Select red/green/blue or " +
                                           "greyscale rendering" );
        rgbModel_.setSelected( true );
        rgbModel_.addActionListener( getReplotListener() );
        rgbRepaintList_ = new ArrayList();
        rgbModel_.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent evt ) {
                for ( Iterator it = rgbRepaintList_.iterator();
                      it.hasNext(); ) {
                    ((JComponent) it.next()).repaint();
                }
            }
        } );

        /* Action for linear/log scale for colour map. */
        zLogModel_ = new ToggleButtonModel( "Log Intensity",
                                            ResourceIcon.COLOR_LOG,
                                            "Pixel colours represent log of " +
                                            "counts" );
        zLogModel_.setSelected( false );
        zLogModel_.addActionListener( getReplotListener() );

        /* Actions for altering pixel size. */
        pixIncAction_ =
            new PixelSizeAction( "Bigger Pixels", ResourceIcon.ROUGH,
                                 "Increase number of screen pixels per bin",
                                 +1 );
        pixDecAction_ =
            new PixelSizeAction( "Smaller Pixels", ResourceIcon.FINE,
                                 "Decrease number of screen pixels per bin",
                                 -1 );

        /* Action for exporting FITS file. */
        fitsAction_ = new ExportAction( "FITS", ResourceIcon.FITS, 
                                        "Save image as FITS array",
                                        fitsFilter_ ) {
            public void exportTo( OutputStream out ) throws IOException {
                try {
                    exportFits( out );
                }
                catch ( FitsException e ) {
                    throw (IOException) new IOException( e.getMessage() )
                                       .initCause( e );
                }
            }
        };

        /* Action for exporting image as JPEG. */
        Action jpegAction = new ImageIOExportAction( "JPEG", jpegFilter_ );

        /* Action for broadcasting image via PLASTIC. */
        Action broadcastAction = new BasicAction( "Broadcast Image",
                                                  ResourceIcon.BROADCAST,
                                                  "Broadcast image as FITS "
                                                + "via PLASTIC" ) {
            public void actionPerformed( ActionEvent evt ) {
                try {
                    broadcastFits();
                }
                catch ( IOException e ) {
                    ErrorDialog.showError( DensityWindow.this, "PLASTIC Error",
                                           e );
                }
            }
        };

        getExportMenu().add( fitsAction_ );
        getExportMenu().add( jpegAction );
        getExportMenu().add( broadcastAction );

        /* Cut level adjuster widgets. */
        cutter_ = new CutChooser(); 
        cutter_.setLowValue( 0.1 );
        cutter_.setHighValue( 0.9 );
        cutter_.addChangeListener( getReplotListener() );
        cutLabel_ = new JLabel();
        JComponent cutBox = Box.createVerticalBox();
        cutBox.setBorder( makeTitledBorder( "Cut Percentile Levels" ) );
        JComponent clbox = Box.createHorizontalBox();
        clbox.add( Box.createHorizontalGlue() );
        clbox.add( cutLabel_ );
        clbox.add( Box.createHorizontalGlue() );
        cutBox.add( cutter_ );
        cutBox.add( clbox ); 
        getMainArea().add( cutBox, BorderLayout.SOUTH );

        /* General plot operation menu. */
        JMenu plotMenu = new JMenu( "Plot" );
        plotMenu.setMnemonic( KeyEvent.VK_P );
        plotMenu.add( resizeAction );
        plotMenu.add( getAxisEditAction() );
        plotMenu.add( getReplotAction() );
        getJMenuBar().add( plotMenu );

        /* Axis operation menu. */
        JMenu axisMenu = new JMenu( "Axes" );
        axisMenu.setMnemonic( KeyEvent.VK_A );
        axisMenu.add( getFlipModels()[ 0 ].createMenuItem() );
        axisMenu.add( getFlipModels()[ 1 ].createMenuItem() );
        axisMenu.addSeparator();
        axisMenu.add( getLogModels()[ 0 ].createMenuItem() );
        axisMenu.add( getLogModels()[ 1 ].createMenuItem() );
        axisMenu.add( zLogModel_.createMenuItem() );
        getJMenuBar().add( axisMenu );

        /* View menu. */
        JMenu viewMenu = new JMenu( "View" );
        viewMenu.setMnemonic( KeyEvent.VK_V );
        axisMenu.add( rgbModel_.createMenuItem() );
        axisMenu.add( pixIncAction_ );
        axisMenu.add( pixDecAction_ );

        /* Subset operation menu. */
        JMenu subsetMenu = new JMenu( "Subsets" );
        subsetMenu.setMnemonic( KeyEvent.VK_S );
        Action fromVisibleAction = new BasicAction( "New subset from visible",
                                                    ResourceIcon.VISIBLE_SUBSET,
                                                    "Define a new row subset " +
                                                    "containing only " +
                                                    "currently visible data" ) {
            public void actionPerformed( ActionEvent evt ) {
                addNewSubsets( plot_.getVisibleMask() );
            }
        };
        subsetMenu.add( blobAction_ );
        subsetMenu.add( fromVisibleAction );
        getJMenuBar().add( subsetMenu );

        /* Add actions to the toolbar. */
        getToolBar().add( fitsAction_ );
        getToolBar().add( resizeAction );
        getToolBar().add( getAxisEditAction() );
        getToolBar().add( zLogModel_.createToolbarButton() );
        getToolBar().add( getReplotAction() );
        getToolBar().add( rgbModel_.createToolbarButton() );
        getToolBar().add( pixIncAction_ );
        getToolBar().add( pixDecAction_ );
        getToolBar().add( blobAction_ );
        getToolBar().add( fromVisibleAction );
        getToolBar().addSeparator();

        /* Add standard help actions. */
        addHelp( "DensityWindow" );

        /* Perform an initial plot. */
        replot();
    }

    protected JComponent getPlot() {
        return plot_;
    }

    protected StyleEditor createStyleEditor() {
        final StyleEditor editor = new DensityStyleEditor( styles_ );
        rgbRepaintList_.add( editor );
        return editor;
    }

    protected PointSelector createPointSelector() {
        PointSelector psel = super.createPointSelector();
        rgbRepaintList_.add( psel );
        return psel;
    }

    protected PlotState createPlotState() {
        return new DensityPlotState();
    }

    public PlotState getPlotState() {
        DensityPlotState state = (DensityPlotState) super.getPlotState();
        boolean valid = state != null && state.getValid();
        state.setRgb( rgbModel_.isSelected() );
        state.setLogZ( zLogModel_.isSelected() );
        state.setLoCut( cutter_.getLowValue() );
        state.setHiCut( cutter_.getHighValue() );
        state.setPixelSize( pixelSize_ );
        pixIncAction_.configureEnabledness();
        pixDecAction_.configureEnabledness();
        return state;
    }

    protected void doReplot( PlotState state, Points points ) {

        /* Cancel any current blob drawing. */
        blobPanel_.setActive( false );

        /* Send the plot component the most up to date plotting state. */
        PlotState lastState = plot_.getState();
        plot_.setPoints( points );
        plot_.setState( state );

        /* If the axes are different from the last time we plotted, 
         * rescale so that all the points are visible. */
        if ( ! state.sameAxes( lastState ) || ! state.sameData( lastState ) ) {
            plotStatus_.setValues( null );
            plot_.rescale();
        }

        /* Schedule for repainting so changes can take effect. */
        plot_.repaint();
    }

    public StyleSet getDefaultStyles( int npoint ) {
        return new StyleSet() {
            public String getName() {
                return "RGB";
            }
            public Style getStyle( int index ) {
                return styles_[ index % styles_.length ];
            }
        };
    }

    /**
     * Broadcasts the currently plotted image as a FITS file to PLASTIC
     * listeners.
     */
    private void broadcastFits() throws IOException {

        /* Get the hub and ID. */
        TopcatPlasticListener pserv =
            ControlWindow.getInstance().getPlasticServer();
        pserv.register();
        final PlasticHubListener hub = pserv.getHub();
        final URI plasticId = pserv.getRegisteredId();

        /* Write the data as a FITS image to a temporary file preparatory
         * to broadcast. */
        final File tmpfile = File.createTempFile( "plastic", ".fits" );
        final String tmpUrl = tmpfile.toURL().toString();
        tmpfile.deleteOnExit();
        OutputStream ostrm = 
            new BufferedOutputStream( new FileOutputStream( tmpfile ) );
        try {
            exportFits( ostrm );
        }
        catch ( IOException e ) {
            tmpfile.delete();
            throw e;
        }
        catch ( FitsException e ) {
            tmpfile.delete();
            throw (IOException) new IOException( e.getMessage() )
                               .initCause( e );
        }
        finally {
            ostrm.close();
        }

        /* Do the broadcast, synchronously so that we don't delete the
         * temporary file to early, but in another thread so we don't block
         * the GUI. */
        new Thread( "FITS broadcast" ) {
            public void run() {
                Map responses =
                    hub.request( plasticId, MSG_LOADIMG,
                                 Arrays.asList( new Object[] { tmpUrl } ) );
                tmpfile.delete();
            }
        }.start();
    }

    /**
     * Exports the grids currently displayed in the plot as a FITS image
     * (primary HDU).
     *
     * @param   ostrm   output stream
     */
    private void exportFits( OutputStream ostrm )
            throws IOException, FitsException {
        final DataOutputStream out = new DataOutputStream( ostrm );

        BinGrid[] grids = plot_.getBinnedData();
        int ngrid = grids.length;

        /* Set up an object that can write the data of this grid using
         * an appropriate datatype. */
        int max = 0;
        for ( int i = 0; i < ngrid; i++ ) {
            max = Math.max( max, grids[ i ].getMaxCount() );
        }
        int bitpix;
        abstract class IntWriter {
            abstract void writeInt( int value ) throws IOException;
            abstract int size();
        };
        IntWriter intWriter;
        if ( max < Math.pow( 2, 7 ) ) {
            bitpix = BasicHDU.BITPIX_BYTE;
            intWriter = new IntWriter() {
                void writeInt( int value ) throws IOException {
                    out.writeByte( value );
                }
                int size() {
                    return 1;
                }
            };
        }
        else if ( max < Math.pow( 2, 15 ) ) {
            bitpix = BasicHDU.BITPIX_SHORT;
            intWriter = new IntWriter() {
                void writeInt( int value ) throws IOException {
                    out.writeShort( value );
                }
                int size() {
                    return 2;
                }
            };
        }
        else {
            bitpix = BasicHDU.BITPIX_INT;
            intWriter = new IntWriter() {
                void writeInt( int value ) throws IOException {
                    out.writeInt( value );
                }
                int size() {
                    return 4;
                }
            };
        }

        /* Assemble FITS header.  The output is a 2d array if there is a
         * single channel displayed, or a 3d array if there is more than
         * one channel. */
        int nx = grids[ 0 ].getSizeX();
        int ny = grids[ 0 ].getSizeY();
        for ( int i = 0; i < ngrid; i++ ) {
            assert grids[ i ].getSizeX() == nx;
            assert grids[ i ].getSizeY() == ny;
        }
        DensityPlotState state = (DensityPlotState) plot_.getState();
        int psize = state.getPixelSize();
        ValueInfo[] axes = state.getAxes();
        String name1 = axes[ 0 ].getName();
        String name2 = axes[ 1 ].getName();
        boolean log1 = state.getLogFlags()[ 0 ];
        boolean log2 = state.getLogFlags()[ 1 ];
        if ( state.getLogFlags()[ 0 ] ) {
            name1 = "log(" + name1 + ")";
        }
        if ( state.getLogFlags()[ 1 ] ) {
            name2 = "log(" + name2 + ")";
        }
        PlotSurface surface = plot_.getSurface();
        Rectangle bbox = surface.getClip().getBounds();
        int x0 = bbox.x;
        int y0 = bbox.y;
        double[] p0 = surface.graphicsToData( x0, y0, false );
        double[] p1 = surface.graphicsToData( x0 + psize, y0 + psize, false );
        Header hdr = new Header();
        hdr.addValue( "SIMPLE", true, "" );
        hdr.addValue( "BITPIX", bitpix, "Data type" );
        hdr.addValue( "NAXIS", ngrid == 1 ? 2 : 3, "Number of axes" );
        hdr.addValue( "NAXIS1", nx, "X dimension" );
        hdr.addValue( "NAXIS2", ny, "Y dimension" );
        if ( ngrid > 1 ) {
            hdr.addValue( "NAXIS3", ngrid, "Number of channels" );
        }
        hdr.addValue( "DATE", Times.mjdToIso( Times.unixMillisToMjd( 
                                           System.currentTimeMillis() ) ),
                      "HDU creation date" );
        hdr.addValue( "CTYPE1", name1, axes[ 0 ].getDescription() );
        hdr.addValue( "CTYPE2", name2, axes[ 1 ].getDescription() );
        if ( ngrid > 1 ) {
            hdr.addValue( "CTYPE3", "Channel index",
                          "Separate histograms stored in different planes" );
        }
        hdr.addValue( "BUNIT", "COUNTS", "Number of points per pixel (bin)" );
        hdr.addValue( "DATAMIN", 0.0, "Minimum value" );
        hdr.addValue( "DATAMAX", (double) max, "Maximum value" );
        hdr.addValue( "CRPIX1", 0.0, "Reference pixel X index" );
        hdr.addValue( "CRPIX2", (double) ny, "Reference pixel Y index" );
        if ( ngrid > 1 ) {
            hdr.addValue( "CRPIX3", 0.0, "Reference pixel plane index" );
        }
        hdr.addValue( "CRVAL1", log1 ? Maths.log10( p0[ 0 ] ) : p0[ 0 ],
                                "Reference pixel X position" );
        hdr.addValue( "CRVAL2", log2 ? Maths.log10( p0[ 1 ] ) : p0[ 1 ],
                                "Reference pixel Y position" );
        if ( ngrid > 1 ) {
            hdr.addValue( "CRVAL3", 0.0,
                          "Reference pixel plane index position" );
        }
        hdr.addValue( "CDELT1", log1 ? Maths.log10( p1[ 0 ] / p0[ 0 ] )
                                     : ( p1[ 0 ] - p0[ 0 ] ),
                                "X extent of reference pixel" );
        hdr.addValue( "CDELT2", log2 ? Maths.log10( p0[ 1 ] / p1[ 1 ] )
                                     : ( p0[ 1 ] - p1[ 1 ] ),
                                "Y extent of reference pixel" );
        if ( ngrid > 1 ) {
            hdr.addValue( "CDELT3", 1.0,
                          "Plane index extent of reference pixel" );
        }
        hdr.addValue( "ORIGIN", "TOPCAT " + TopcatUtils.getVersion() + 
                      " (" + getClass().getName() + ")", null );

        /* Write the FITS header. */
        FitsConstants.writeHeader( out, hdr );

        /* Write the data. */
        for ( int i = 0; i < ngrid; i++ ) {
            int[] data = grids[ i ].getCounts();
            for ( int iy = 0; iy < ny; iy++ ) {
                int yoff = ( ny - 1 - iy ) * nx;
                for ( int ix = 0; ix < nx; ix++ ) {
                    intWriter.writeInt( data[ yoff + ix ] );
                }
            }
        }

        /* Write padding to an integral number of FITS block sizes. */
        int nbyte = nx * ny * ngrid * intWriter.size();
        int over = nbyte % FitsConstants.FITS_BLOCK;
        if ( over > 0 ) {
            out.write( new byte[ FitsConstants.FITS_BLOCK - over ] );
        }

        /* Flush. */
        out.flush();
    }

    /**
     * Style used by density window.  Most of this class is defined
     * by the abstract DensityStyle class, but we have to fill in one
     * method (isRGB) here since behaviour is dependent on the current
     * state of this window.
     */
    private class DStyle extends DensityStyle {
        DStyle( DensityStyle.Channel channel ) {
            super( channel );
        }
        protected boolean isRGB() {
            return rgbModel_.isSelected();
        }
    }

    /**
     * Action for incrementing the grid pixel size.
     */
    private class PixelSizeAction extends BasicAction {
        final int MAX_SIZE = 20;
        final int MIN_SIZE = 1;
        final int inc_;

        /**
         * Constructs a new PixelSizeAction.
         *
         * @param  name  action name
         * @param  icon  action icon
         * @param  desc  short description (tool tip)
         * @param  inc   amount to increment the pixsize when the action is
         *               invoked
         */
        PixelSizeAction( String name, Icon icon, String desc, int inc ) {
            super( name, icon, desc );
            inc_ = inc;
        }

        public void actionPerformed( ActionEvent evt ) {
            pixelSize_ = Math.min( Math.max( pixelSize_ + inc_, MIN_SIZE ),
                                             MAX_SIZE );
            configureEnabledness();
            replot();
        }

        /**
         * Configures this action according to whether it would have any
         * effect or not.
         */
        void configureEnabledness() {
            setEnabled( pixelSize_ + inc_ >= MIN_SIZE &&
                        pixelSize_ + inc_ <= MAX_SIZE );
        }
    }
}
