package uk.ac.starlink.topcat.plot;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import uk.ac.starlink.topcat.AuxWindow;
import uk.ac.starlink.util.gui.ShrinkWrapper;
import uk.ac.starlink.util.gui.ValueButtonGroup;

/**
 * StyleEditor implementation for editing {@link MarkStyle} objects.
 *
 * @author   Mark Taylor
 * @since    10 Jan 2005
 */
public class MarkStyleEditor extends StyleEditor {

    private final JCheckBox markFlagger_;
    private final JComboBox shapeSelector_;
    private final JComboBox sizeSelector_;
    private final ColorComboBox colorSelector_;
    private final LogSlider opaqueSlider_;
    private final ThicknessComboBox thickSelector_;
    private final DashComboBox dashSelector_;
    private final ValueButtonGroup lineSelector_;
    private final JLabel corrLabel_;
    private final Map statMap_;
    private final String helpId_;

    private static final int MAX_SIZE = 5;
    private static final int MAX_THICK = 10;
    private static final MarkShape[] SHAPES = new MarkShape[] {
        MarkShape.FILLED_CIRCLE,
        MarkShape.OPEN_CIRCLE,
        MarkShape.CROSS,
        MarkShape.CROXX,
        MarkShape.OPEN_SQUARE,
        MarkShape.OPEN_DIAMOND,
        MarkShape.OPEN_TRIANGLE_UP,
        MarkShape.OPEN_TRIANGLE_DOWN,
        MarkShape.FILLED_SQUARE,
        MarkShape.FILLED_DIAMOND,
        MarkShape.FILLED_TRIANGLE_UP,
        MarkShape.FILLED_TRIANGLE_DOWN,
    };

    /**
     * Constructor.
     */
    public MarkStyleEditor( boolean withLines, boolean withTransparency ) {
        super();
        statMap_ = new HashMap();
        helpId_ = withLines ? "MarkStyleEditor" : "MarkStyleEditorNoLines";

        /* Shape selector. */
        shapeSelector_ = createShapeSelector();
        shapeSelector_.addActionListener( this );

        /* Size selector. */
        sizeSelector_ = createSizeSelector();
        sizeSelector_.addActionListener( this );

        /* Colour selector. */
        colorSelector_ = new ColorComboBox();
        colorSelector_.addActionListener( this );

        /* Opacity limit slider. */
        opaqueSlider_ = new LogSlider( 1000 );
        final JLabel opaqueLabel = new JLabel( "", SwingConstants.RIGHT );
        opaqueSlider_.addChangeListener( new ChangeListener() {
            Dimension size;;
            public void stateChanged( ChangeEvent evt ) {
                if ( size == null ) {
                    opaqueLabel.setText( "9999" );
                    size = opaqueLabel.getPreferredSize();
                }
                opaqueLabel.setText( Integer.toString( opaqueSlider_
                                                      .getValue1() ) );
                opaqueLabel.setPreferredSize( size );
                if ( ! opaqueSlider_.getValueIsAdjusting() ) {
                    MarkStyleEditor.this.stateChanged( evt );
                }
            }
        } );

        /* Marker hiding selector. */
        markFlagger_ = new JCheckBox( "Hide Markers" );
        markFlagger_.setSelected( false );
        markFlagger_.addActionListener( this );

        /* Line thickness selector. */
        thickSelector_ = new ThicknessComboBox( MAX_THICK );
        thickSelector_.addActionListener( this );

        /* Line dash selector. */
        dashSelector_ = new DashComboBox();
        dashSelector_.addActionListener( this );

        /* Line type selector. */
        JRadioButton noneButton = new JRadioButton( "None", true );
        JRadioButton dotsButton = new JRadioButton( "Dot to Dot" );
        JRadioButton corrButton = new JRadioButton( "Linear Correlation" );
        lineSelector_ = new ValueButtonGroup();
        lineSelector_.add( noneButton, null );
        lineSelector_.add( dotsButton, MarkStyle.DOT_TO_DOT );
        lineSelector_.add( corrButton, MarkStyle.LINEAR );
        lineSelector_.addChangeListener( this );
        corrLabel_ = new JLabel();

        /* Place marker selection components. */
        JComponent formBox = Box.createHorizontalBox();
        formBox.add( new JLabel( "Shape: " ) );
        formBox.add( new ShrinkWrapper( shapeSelector_ ) );
        formBox.add( Box.createHorizontalStrut( 5 ) );
        formBox.add( new ComboBoxBumper( shapeSelector_ ) );
        formBox.add( Box.createHorizontalStrut( 10 ) );
        formBox.add( new JLabel( "Size: " ) );
        formBox.add( new ShrinkWrapper( sizeSelector_ ) );
        formBox.add( Box.createHorizontalStrut( 5 ) );
        formBox.add( new ShrinkWrapper( new ComboBoxBumper( sizeSelector_ ) ) );
        formBox.add( Box.createHorizontalStrut( 5 ) );
        formBox.add( Box.createHorizontalGlue() );

        JComponent colorBox = Box.createHorizontalBox();
        colorBox.add( new JLabel( "Colour: " ) );
        colorBox.add( new ShrinkWrapper( colorSelector_ ) );
        colorBox.add( Box.createHorizontalStrut( 5 ) );
        colorBox.add( new ComboBoxBumper( colorSelector_ ) );
        colorBox.add( Box.createHorizontalStrut( 5 ) );
        colorBox.add( Box.createHorizontalGlue() );

        JComponent opaqueBox = Box.createHorizontalBox();
        opaqueBox.add( new JLabel( "Transparency: " ) );
        opaqueBox.add( opaqueSlider_ );
        opaqueBox.add( Box.createHorizontalStrut( 5 ) );
        opaqueBox.add( opaqueLabel );
        opaqueBox.add( Box.createHorizontalStrut( 5 ) );
        opaqueBox.add( Box.createHorizontalGlue() );

        JComponent hideBox = Box.createHorizontalBox();
        hideBox.add( markFlagger_ );
        hideBox.add( Box.createHorizontalGlue() );

        JComponent markBox = Box.createVerticalBox();
        markBox.add( formBox );
        markBox.add( Box.createVerticalStrut( 5 ) );
        markBox.add( colorBox );
        if ( withTransparency ) {
            markBox.add( Box.createVerticalStrut( 5 ) );
            markBox.add( opaqueBox );
        }
        if ( withLines ) {
            markBox.add( Box.createVerticalStrut( 5 ) );
            markBox.add( hideBox );
        }
        markBox.setBorder( AuxWindow.makeTitledBorder( "Marker" ) );
        add( markBox );

        /* Place line selection components if required. */
        if ( withLines ) {
            Box lineStyleBox = Box.createHorizontalBox();
            lineStyleBox.add( new JLabel( "Thickness: " ) );
            lineStyleBox.add( new ShrinkWrapper( thickSelector_ ) );
            lineStyleBox.add( Box.createHorizontalStrut( 5 ) );
            lineStyleBox.add( new ComboBoxBumper( thickSelector_ ) );
            lineStyleBox.add( Box.createHorizontalStrut( 10 ) );
            lineStyleBox.add( new JLabel( "Dash: " ) );
            lineStyleBox.add( new ShrinkWrapper( dashSelector_ ) );
            lineStyleBox.add( Box.createHorizontalStrut( 5 ) );
            lineStyleBox.add( new ComboBoxBumper( dashSelector_ ) );
            lineStyleBox.add( Box.createHorizontalStrut( 5 ) );
            lineStyleBox.add( Box.createHorizontalGlue() );

            Box noneLineBox = Box.createHorizontalBox();
            noneLineBox.add( noneButton );
            noneLineBox.add( Box.createHorizontalGlue() );
            Box dotsLineBox = Box.createHorizontalBox();
            dotsLineBox.add( dotsButton );
            dotsLineBox.add( Box.createHorizontalGlue() );
            Box corrLineBox = Box.createHorizontalBox();
            corrLineBox.add( corrButton );
            corrLineBox.add( corrLabel_ );
            corrLineBox.add( Box.createHorizontalGlue() );

            Box lineBox = Box.createVerticalBox();
            lineBox.add( lineStyleBox );
            lineBox.add( noneLineBox );
            lineBox.add( dotsLineBox );
            lineBox.add( corrLineBox );
            lineBox.setBorder( AuxWindow.makeTitledBorder( "Line" ) );
            add( lineBox );
        }
    }

    public void setStyle( Style style ) {
        MarkStyle mstyle = (MarkStyle) style;
        shapeSelector_.setSelectedItem( mstyle.getShapeId() );
        sizeSelector_.setSelectedIndex( mstyle.getSize() );
        colorSelector_.setSelectedItem( mstyle.getColor() );
        int opaqueLimit = mstyle.getOpaqueLimit();
        opaqueSlider_.setValue1( opaqueLimit );
        thickSelector_.setSelectedThickness( mstyle.getLineWidth() );
        dashSelector_.setSelectedDash( mstyle.getDash() );
        lineSelector_.setValue( mstyle.getLine() );
        markFlagger_.setSelected( mstyle.getHidePoints() );
    }

    public Style getStyle() {
        return getStyle( (MarkShape) shapeSelector_.getSelectedItem(),
                         sizeSelector_.getSelectedIndex(),
                         colorSelector_.getSelectedColor(),
                         opaqueSlider_.getValue1(),
                         markFlagger_.isEnabled() && markFlagger_.isSelected(),
                         (MarkStyle.Line) lineSelector_.getValue(),
                         thickSelector_.getSelectedThickness(),
                         dashSelector_.getSelectedDash() );
    }

    public String getHelpID() {
        return helpId_;
    }

    /**
     * Returns a MarkStyle described by its attributes.
     *
     * @param  shape  marker shape
     * @param  size   marker size
     * @param  color  marker colour
     * @param  hidePoints  whether markers are invisible
     * @param  line   line type
     * @param  thick  line thickness
     * @return  marker
     */
    private static MarkStyle getStyle( MarkShape shape, int size, Color color,
                                       int opaqueLimit, boolean hidePoints,
                                       MarkStyle.Line line,
                                       int thick, float[] dash ) {
        MarkStyle style = size == 0 ? MarkShape.POINT.getStyle( color, 0 )
                                    : shape.getStyle( color, size );
        style.setOpaqueLimit( opaqueLimit );
        style.setLine( line );
        style.setHidePoints( hidePoints );
        style.setLineWidth( thick );
        style.setDash( dash );
        return style;
    }

    /**
     * Sets the known statistical information about a list of plottable sets.
     * This represents information about calculated linear regression
     * coefficients.
     * 
     * @param  setIds  set identifiers for the statistics objects provided
     * @param  stats   statistics objects themselves, one per element of 
     *                 <code>setIds</code>
     */
    public void setStats( SetId[] setIds, XYStats[] stats ) {
        statMap_.clear();
        if ( setIds.length != stats.length ) {
            throw new IllegalArgumentException();
        }
        for ( int i = 0; i < setIds.length; i++ ) {
            statMap_.put( setIds[ i ], stats[ i ] );
        }
        refreshState();
    }

    protected void refreshState() {
        super.refreshState();
        SetId setId = getSetId();

        /* Ensure that information about linear correlations is up to date. */
        String statText;
        XYStats stats = (XYStats) statMap_.get( setId );
        if ( stats != null ) {
            statText = new StringBuffer()
                .append( " - " )
                .append( "m=" )
                .append( (float) stats.getLinearCoefficients()[ 1 ] )
                .append( "; c=" )
                .append( (float) stats.getLinearCoefficients()[ 0 ] )
                .append( "; r=" )
                .append( (float) stats.getCorrelation() )
                .toString();
        }
        else {
            statText = "";
        }
        corrLabel_.setText( statText );

        /* Make sure that marker presence control is only enabled if 
         * a line is being plotted. */
        markFlagger_.setEnabled( lineSelector_.getValue() != null );
    }

    /**
     * Returns a new JComboBox which will contain a standard set of 
     * MarkShape objects.
     *
     * @return  new shape selection combo box
     */
    public static JComboBox createShapeSelector() {
        final JComboBox selector = new JComboBox( SHAPES );
        selector.setRenderer( new MarkRenderer() {
            public MarkShape getMarkShape( int index ) {
                return (MarkShape) selector.getItemAt( index );
            }
            public MarkShape getMarkShape() {
                return (MarkShape) selector.getSelectedItem();
            }
            public Color getMarkColor() {
                return Color.BLACK;
            }
            public int getMarkSize() {
                return 5;
            }
        } );
        return selector;
    }

    /**
     * Returns a new JComboBox which will contain a standard set of integers
     * for specifying marker size (0..MAX_SIZE).
     *
     * @return  new size selection combo box
     */
    public static JComboBox createSizeSelector() {
        final JComboBox selector = 
            new JComboBox( createNumberedModel( MAX_SIZE + 1 ) );
        selector.setRenderer( new MarkRenderer( true ) {
            public int getMarkSize( int index ) {
                return index;
            }
            public int getMarkSize() {
                return selector.getSelectedIndex();
            }
            public Color getMarkColor() {
                return Color.BLACK;
            }
            public MarkShape getMarkShape() {
                return MarkShape.OPEN_SQUARE;
            }
        } );
        return selector;
    }

    /**
     * ComboBoxRenderer class suitable for rendering MarkStyles.
     */
    private static abstract class MarkRenderer extends BasicComboBoxRenderer {
        private boolean useText_;
        MarkRenderer() {
            this( false );
        }
        MarkRenderer( boolean useText ) {
            useText_ = useText;
        }
        MarkShape getMarkShape( int itemIndex ) {
            return getMarkShape();
        }
        abstract MarkShape getMarkShape();
        int getMarkSize( int itemIndex ) {
            return getMarkSize();
        }
        abstract int getMarkSize();
        Color getMarkColor( int itemIndex ) {
            return getMarkColor();
        }
        abstract Color getMarkColor();
        public Component getListCellRendererComponent( JList list, Object value,
                                                       int index,
                                                       boolean isSelected,
                                                       boolean hasFocus ) {
            Component c =
                super.getListCellRendererComponent( list, value, index,
                                                    isSelected, hasFocus );
            if ( c instanceof JLabel ) {
                JLabel label = (JLabel) c;
                if ( ! useText_ ) {
                    setText( null );
                }
                MarkStyle style = index >= 0
                                ? getStyle( getMarkShape( index ),
                                            getMarkSize( index ),
                                            getMarkColor( index ),
                                            1, false, null, 1, null )
                                : getStyle( getMarkShape(),
                                            getMarkSize(),
                                            getMarkColor(),
                                            1, false, null, 1, null );
                label.setIcon( style.getLegendIcon() );
            }
            return c;
        }
    }
}
