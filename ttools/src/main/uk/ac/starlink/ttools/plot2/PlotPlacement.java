package uk.ac.starlink.ttools.plot2;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;

/**
 * Aggregates a Surface and the Rectangle that it is placed within.
 * It may also store decorations to be painted on top of the plot.
 * Class instances themselves may be compared for equality, but don't
 * do much else.  Several static methods however are provided to assist
 * in creating instances, in particular doing the non-trivial work to
 * determine how much external space is required for legends etc.
 *
 * <p>Note instances of this class are not immutable, since the decoration
 * list may be changed.
 *
 * @author   Mark Taylor
 * @since    12 Feb 2013
 */
@Equality
public class PlotPlacement {

    private final Rectangle bounds_;
    private final Surface surface_;
    private final List<Decoration> decorations_;
    private static final int EXTERNAL_LEGEND_GAP = 10;
    private static final int MIN_DIM = 24;
    private static final int PAD = 2;

    /**
     * Constructs a placement with no decorations.
     *
     * @param   bounds   external bounds within which plot is to be placed
     * @param   surface  plot surface
     */
    public PlotPlacement( Rectangle bounds, Surface surface ) {
        this( bounds, surface, new Decoration[ 0 ] );
    }

    /**
     * Constructs a placement with an initial list of decorations.
     *
     * @param   bounds   external bounds within which plot is to be placed
     * @param   surface  plot surface
     * @param   decorations  initial list of decorations;
     *          note more can be added later
     */
    public PlotPlacement( Rectangle bounds, Surface surface,
                          Decoration[] decorations ) {
        bounds_ = bounds;
        surface_ = surface;
        decorations_ =
            new ArrayList<Decoration>( Arrays.asList( decorations ) );
    }

    /**
     * Returns the external bounds of this placement.
     *
     * @return   bounds
     */
    public Rectangle getBounds() {
        return bounds_;
    }

    /**
     * Returns the plot surface.
     *
     * @return  surface
     */
    public Surface getSurface() {
        return surface_;
    }

    /**
     * Returns a list of decorations to be painted over the finished plot.
     * This list may be modified to add or remove decoration items.
     *
     * @return   modifiable list of decoration objects
     */
    public List<Decoration> getDecorations() {
        return decorations_;
    }

    /**
     * Takes an icon containing plot background and layers,
     * and turns it into one positioned in an external rectangle
     * with surface foreground (axes) and other decorations.
     *
     * @param  dataIcon  icon as generated by
     *       {@link uk.ac.starlink.ttools.plot2.paper.PaperType#createDataIcon}
     * @return  final plot icon to be drawn at the graphics origin
     */
    public Icon createPlotIcon( final Icon dataIcon ) {
        final Rectangle plotBounds = surface_.getPlotBounds();
        return new Icon() {
            public int getIconWidth() {
                return bounds_.width;
            }
            public int getIconHeight() {
                return bounds_.height;
            }
            public void paintIcon( Component c, Graphics g, int x, int y ) {
                int xoff = x - bounds_.x;
                int yoff = y - bounds_.y;
                g.translate( xoff, yoff );
                paintPlot( c, g );
                g.translate( -xoff, -yoff );
            }
            private void paintPlot( Component c, Graphics g ) {
                Shape clip0 = g.getClip();
                g.clipRect( plotBounds.x, plotBounds.y,
                            plotBounds.width, plotBounds.height );
                dataIcon.paintIcon( c, g, plotBounds.x, plotBounds.y );
                g.setClip( clip0 );
                surface_.paintForeground( g );
                for ( Decoration dec : decorations_ ) {
                    dec.paintDecoration( g );
                }
            }
        };
    }

    @Override
    public boolean equals( Object o ) {
        if ( o instanceof PlotPlacement ) {
            PlotPlacement other = (PlotPlacement) o;
            return this.bounds_.equals( other.bounds_ )
                && this.surface_.equals( other.surface_ )
                && this.decorations_.equals( other.decorations_ );
        }
        else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int code = 239991;
        code = 23 * code + bounds_.hashCode();
        code = 23 * code + surface_.hashCode();
        code = 23 * code + decorations_.hashCode();
        return code;
    }

    /**
     * Convenience method to create a plot placement given various inputs.
     * In particular it works out how much space is required for
     * decorations like axis annotations, legend etc.
     *
     * @param   extBounds  external bounds of plot placement
     * @param   padding   requirements for outer padding, or null
     * @param   surfFact  surface factory
     * @param   profile  factory-specific surface profile
     * @param   aspect   factory-specific surface aspect
     * @param   withScroll  true if the placement should work well
     *                      with future scrolling
     * @param   legend   legend icon if required, or null
     * @param   legPos  legend position if intenal legend is required;
     *                  2-element (x,y) array, each element in range 0-1
     * @param   title   title text, or null
     * @param   shadeAxis  shader axis if required, or null
     * @return   new plot placement
     */
    public static <P,A> PlotPlacement
            createPlacement( Rectangle extBounds, Padding padding,
                             SurfaceFactory<P,A> surfFact,
                             P profile, A aspect, boolean withScroll,
                             Icon legend, float[] legPos, String title,
                             ShadeAxis shadeAxis ) {
        Rectangle dataBounds =
            calculateDataBounds( extBounds, padding, surfFact, profile, aspect,
                                 withScroll, legend, legPos, title, shadeAxis );
        Surface surf = surfFact.createSurface( dataBounds, profile, aspect );
        Decoration[] decs =
            createPlotDecorations( surf, legend, legPos, title, shadeAxis );
        return new PlotPlacement( extBounds, surf, decs );
    }

    /**
     * Determines the required insets for a plot to accommodate
     * axis annotations etc.
     *
     * @param   extBounds  external bounds of plot placement
     * @param   surfFact  surface factory
     * @param   profile  factory-specific surface profile
     * @param   aspect   factory-specific surface aspect
     * @param   withScroll  true if the placement should work well
     *                      with future scrolling
     * @param   legend   legend icon if required, or null
     * @param   legPos  legend position if intenal legend is required;
     *                  2-element (x,y) array, each element in range 0-1
     * @param   title   title text, or null
     * @param   shadeAxis  shader axis if required, or null
     * @param   pad   extra padding in pixels around the outside
     * @return  data bounds rectangle
     */
    public static <P,A> Insets
            calculateDataInsets( Rectangle extBounds,
                                 SurfaceFactory<P,A> surfFact, P profile,
                                 A aspect, boolean withScroll, Icon legend,
                                 float[] legPos, String title,
                                 ShadeAxis shadeAxis, int pad ) {

        /* This implementation currently places the legend in the top
         * right corner of the plot surface's requested insets,
         * which assumes that this requested inset space is not used.
         * That's true for existing surface implementations (that space
         * is just padding to make room for overflowing X axis labels)
         * but might not be for future implementations (e.g. right-hand
         * axis labels).  Adjust the implementation if that happens. */
        boolean hasExtLegend = legend != null && legPos == null;

        /* Calculate insets required for decorations that
         * do not depend (much) on the size of the plot bounds. */
        Insets decInsets = new Insets( 0, 0, 0, 0 );
        if ( hasExtLegend ) {
            decInsets.right =
                Math.max( decInsets.right,
                          legend.getIconWidth() + EXTERNAL_LEGEND_GAP );
            decInsets.right = Math.min( decInsets.right,
                                        extBounds.width / 2 );
        }
        if ( shadeAxis != null ) {
            Rectangle rampBox =
                new Rectangle( 0, 0, shadeAxis.getRampWidth(),
                               extBounds.height );
            decInsets.right =
                Math.max( decInsets.right,
                          rampBox.width
                          + shadeAxis.getRampInsets( rampBox ).right
                          + EXTERNAL_LEGEND_GAP );
            int yShadePad = shadeAxis.getEndPadding();
            if ( ! hasExtLegend ) {
                decInsets.top = Math.max( decInsets.top, yShadePad );
            }
            decInsets.bottom = Math.max( decInsets.bottom, yShadePad );
        }
        if ( title != null ) {

            /* Slightly annoying that we have to create a surface just to
             * get the captioner, but it should be cheap. */
            Captioner captioner =
                surfFact.createSurface( extBounds, profile, aspect )
                        .getCaptioner();
            Caption caption = Caption.createCaption( title );
            decInsets.top =
                Math.max( decInsets.top,
                          captioner.getCaptionBounds( caption ).height
                          + captioner.getPad() );
        }

        /* Insets for padding outside space that is actually painted on
         * by the axes and decorations. */
        Insets padInsets = new Insets( PAD, PAD, PAD, PAD );

        /* Now work out the insets that do depend on the actual size
         * of the plot.  Since the size of the plot depends on the insets, 
         * which is what we're trying to work out, it's not straightforward
         * to get the answer.  We have to supply a guess for the insets,
         * work out the size of the internal plotting surface based on that,
         * and then ask the surface how much space is required for axis
         * annotations given that.  But the positions of the labels might
         * be different in the plot surface if the resulting insets are used,
         * so really it's necessary to iterate.
         * Iterating until stability is a bad idea, since it might not
         * become stable.  So do it a few times and return the insets
         * representing the biggest required space.  This still isn't
         * bulletproof, but there's a pretty good chance it will give
         * enough space for the labels.

        /* The initial guess is the insets required for size-independent
         * decorations. */
        Insets insets = (Insets) decInsets.clone();

        /* Assign the number of required iterations.
         * If scrolling is being accounted for, it's likely
         * (though not certain) that just one iteration will do it,
         * since that allocates extra space on the assumptions that
         * the labels might move around.  But for withScroll=false,
         * the change in the position of the labels is more likely to
         * affect the result, so do more iterations.
         * withScroll=false is also likely to be for generating an
         * image for output (not interactive) so the extra time it takes
         * (shouldn't be very expensive in any case) is not so much of
         * an issue. */
        int nit = withScroll ? 1 : 4;

        /* Iterate. */
        for ( int i = 0; i < nit; i++ ) {
            Rectangle plotBounds =
                PlotUtil
               .subtractInsets( PlotUtil.subtractInsets( extBounds, insets ),
                                padInsets );
            plotBounds.width = Math.max( plotBounds.width, MIN_DIM );
            plotBounds.height = Math.max( plotBounds.height, MIN_DIM );
            Surface surf =
                surfFact.createSurface( plotBounds, profile, aspect );
            Insets axisInsets = surf.getPlotInsets( withScroll );
            insets.top = Math.max( insets.top, axisInsets.top );
            insets.left = Math.max( insets.left, axisInsets.left );
            insets.bottom = Math.max( insets.bottom, axisInsets.bottom );
            insets.right = Math.max( insets.right, axisInsets.right );
        }

        /* Add fixed padding and return. */
        insets.top += padInsets.top;
        insets.left += padInsets.left;
        insets.bottom += padInsets.bottom;
        insets.right += padInsets.right;
        return insets;
    }

    /**
     * Determines the bounds for the data part of a plot given its
     * external dimensions and other information about it.
     * It does this by assessing how much space will be required for
     * axis annotations etc.
     *
     * @param   extBounds  external bounds of plot placement
     * @param   padding   preferences for outer padding, or null
     * @param   surfFact  surface factory
     * @param   profile  factory-specific surface profile
     * @param   aspect   factory-specific surface aspect
     * @param   withScroll  true if the placement should work well
     *                      with future scrolling
     * @param   legend   legend icon if required, or null
     * @param   legPos  legend position if intenal legend is required;
     *                  2-element (x,y) array, each element in range 0-1
     * @param   title   title text, or null
     * @param   shadeAxis  shader axis if required, or null
     * @return  data bounds rectangle
     */
    public static <P,A> Rectangle
            calculateDataBounds( Rectangle extBounds, Padding padding,
                                 SurfaceFactory<P,A> surfFact, P profile,
                                 A aspect, boolean withScroll, Icon legend,
                                 float[] legPos, String title,
                                 ShadeAxis shadeAxis ) {
        padding = padding == null ? new Padding() : padding;
        final Insets insets;
        if ( padding.isDefinite() ) {
            insets = padding.toDefiniteInsets();
        }
        else {
            Insets dataInsets =
                calculateDataInsets( extBounds, surfFact, profile, aspect,
                                     withScroll, legend, legPos,
                                     title, shadeAxis, PAD );
            insets = padding.overrideInsets( dataInsets );
        }
        return PlotUtil.subtractInsets( extBounds, insets );
    }

    /**
     * Returns a list of plot decorations for things like the legend
     * and shade colour ramp.
     *
     * @param  surf  plot surface
     * @param   legend   legend icon if required, or null
     * @param   legPos  legend position if intenal legend is required;
     *                  2-element (x,y) array, each element in range 0-1
     * @param   title   title text, or null
     * @param   shadeAxis  shader axis if required, or null
     * @return   list of decorations (may have zero elements)
     */
    public static Decoration[] createPlotDecorations( Surface surf,
                                                      Icon legend,
                                                      float[] legPos,
                                                      String title,
                                                      ShadeAxis shadeAxis ) {
        Rectangle dataBounds = surf.getPlotBounds();
        Insets insets = surf.getPlotInsets( false );
        List<Decoration> decList = new ArrayList<Decoration>();
        int gxlo = dataBounds.x;
        int gylo = dataBounds.y;
        int gxhi = dataBounds.x + dataBounds.width;
        int gyhi = dataBounds.y + dataBounds.height;
        
        /* Work out legend position. */
        if ( legend != null ) {
            final int lx;
            final int ly;
            if ( legPos == null ) {
                lx = gxhi + EXTERNAL_LEGEND_GAP;
                ly = gylo;
            }
            else {
                lx = gxlo + Math.round( ( gxhi - gxlo - legend.getIconWidth() )
                                      * legPos[ 0 ] );

                /* Invert the sense of the y component so that up is positive,
                 * like for data coordinates. */
                ly = gylo + Math.round( ( gyhi - gylo - legend.getIconHeight() )
                                      * ( 1f - legPos[ 1 ] ) );
            }
            decList.add( new Decoration( legend, lx, ly ) );
        }

        /* Work out shader axis position. */
        if ( shadeAxis != null ) {
            int sx = gxhi + EXTERNAL_LEGEND_GAP;
            boolean hasExtLegend = legend != null && legPos == null;
            int sy = gylo;
            if ( hasExtLegend ) {
                sy += legend.getIconHeight()
                    + Math.max( shadeAxis.getEndPadding() + PAD,
                                EXTERNAL_LEGEND_GAP );
            }
            Rectangle rampBox =
                new Rectangle( sx, sy, shadeAxis.getRampWidth(), gyhi - sy );
            Icon shadeIcon = shadeAxis.createAxisIcon( rampBox );
            decList.add( new Decoration( shadeIcon, sx, sy ) );
        }

        /* Position title. */
        if ( title != null ) {
            Captioner captioner = surf.getCaptioner();
            Icon titleIcon = new CaptionIcon( title, captioner );
            int px = dataBounds.x
                   + dataBounds.width / 2
                   - titleIcon.getIconWidth() / 2;
            int py = dataBounds.y - titleIcon.getIconHeight()
                                  - captioner.getPad();
            decList.add( new Decoration( titleIcon, px, py ) );
        }

        return decList.toArray( new Decoration[ 0 ] );
    }

    /**
     * Icon which draws a caption.
     */
    @Equality
    private static class CaptionIcon implements Icon {
        private final Caption caption_;
        private final Captioner captioner_;
        private final int width_;
        private final int height_;
        private final int x_;
        private final int y_;

        /**
         * Constructor.
         *
         * @param  text  caption text
         * @param  captioner   caption painter
         */ 
        CaptionIcon( String text, Captioner captioner ) {
            caption_ = Caption.createCaption( text );
            captioner_ = captioner;
            Rectangle bounds = captioner.getCaptionBounds( caption_ );
            x_ = 0;
            y_ = - bounds.y;
            width_ = bounds.width;
            height_ = bounds.height;
        }

        public int getIconWidth() {
            return width_;
        }

        public int getIconHeight() {
            return height_;
        }

        public void paintIcon( Component c, Graphics g, int x, int y ) {
            int xoff = x + x_;
            int yoff = y + y_;
            Color color0 = g.getColor();
            g.setColor( Color.BLACK );
            g.translate( xoff, yoff );
            captioner_.drawCaption( caption_, g );
            g.translate( -xoff, -yoff );
            g.setColor( color0 );
        }

        @Override
        public int hashCode() {
            int code = 4432;
            code = 23 * caption_.hashCode();
            code = 23 * captioner_.hashCode();
            return code;
        }

        @Override
        public boolean equals( Object o ) {
            if ( o instanceof CaptionIcon ) {
                CaptionIcon other = (CaptionIcon) o;
                return this.caption_.equals( other.caption_ )
                    && this.captioner_.equals( other.captioner_ );
            }
            else {
                return false;
            }
        }
    }
}
