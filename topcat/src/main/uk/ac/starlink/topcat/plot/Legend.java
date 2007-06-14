package uk.ac.starlink.topcat.plot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.Icon;
import javax.swing.JComponent;

/**
 * Draws the legend for identifying points on a plot.
 *
 * @author   Mark Taylor
 * @since    4 Jan 2006
 */
public class Legend extends JComponent {

    private String[] labels_;
    private Icon[] icons_;
    private int iconWidth_;
    private int lineHeight_;
    private int prefWidth_;
    private int prefHeight_;
    private boolean active_;

    private final static int IL_PAD = 8;

    /**
     * Constructor.
     */
    public Legend() {
        setOpaque( false );
        icons_ = new Icon[ 0 ];
        labels_ = new String[ 0 ];
    }

    /**
     * Sets the plot styles and their associated text labels.
     * The two arrays must have the same length.
     *
     * @param   styles   style array
     * @param   labels   label array
     */
    public void setStyles( Style[] styles, String[] labels ) {

        /* Validate and store state. */
        int nstyle = styles.length;
        if ( labels.length != nstyle ) {
            throw new IllegalArgumentException();
        }
        labels_ = new String[ nstyle ];
        icons_ = new Icon[ nstyle ];
        for ( int is = 0; is < nstyle; is++ ) {
            icons_[ is ] = styles[ is ].getLegendIcon();
            labels_[ is ] = labels[ is ];
        }

        /* Ensure active if there is more than one item to display. */
        if ( nstyle > 1 ) {
            setActive( true );
        }

        /* Calculate geometry. */
        int ixmax = 0;
        int iymax = 0;
        int sxmax = 0;
        int height = 0;
        int width = 0;
        FontMetrics fm = getFontMetrics( getFont() );
        lineHeight_ = (int) ( 1.2 * Math.max( fm.getHeight(), iymax ) );
        for ( int is = 0; is < nstyle; is++ ) {
            ixmax = Math.max( ixmax, icons_[ is ].getIconWidth() );
            iymax = Math.max( iymax, icons_[ is ].getIconHeight() );
            sxmax = Math.max( sxmax, fm.stringWidth( labels[ is ] ) + 1 );
            height += lineHeight_;
        }
        iconWidth_ = ixmax;
        width = iconWidth_ + IL_PAD + sxmax;
        Insets insets = getInsets();
        int xpad = insets.left + insets.right;
        int ypad = insets.top + insets.bottom;

        /* Store new preferred geometry.  Width never decreases - this is
         * here simply so that the plotting in TOPCAT is smoother when 
         * flicking between subset selections. */
        prefWidth_ =
            Math.max( width + insets.left + insets.right, prefWidth_ );
        prefHeight_ = height + insets.top + insets.bottom;

        /* Ensure updates take place. */
        revalidate();
        repaint();
    }

    /**
     * Determines whether this component will have non-zero size and 
     * visible display.  The default state is inactive until the first
     * time that more than one legend item is requested.
     *
     * @param   active  active flag
     */
    public void setActive( boolean active ) {
        active_ = active;
    }

    protected void paintComponent( Graphics g ) {

        /* JComponent boilerplate. */
        if ( isOpaque() ) {
            Color col = g.getColor();
            g.setColor( getBackground() );
            g.fillRect( 0, 0, getWidth(), getHeight() );
            g.setColor( col ); 
        }

        /* Draw icons and labels. */
        int nstyle = labels_.length;
        Insets insets = getInsets();
        FontMetrics fm = g.getFontMetrics();
        if ( active_ ) {
            int xoff = insets.left;
            int ys = lineHeight_ / 2 + fm.getHeight() / 2;
            int xs = xoff + iconWidth_ + IL_PAD;
            for ( int is = 0; is < nstyle; is++ ) {
                int yoff = insets.top + lineHeight_ * is;
                Icon icon = icons_[ is ];
                int yi = yoff + ( lineHeight_ - icon.getIconHeight() ) / 2;
                int xi = xoff + ( iconWidth_ - icon.getIconWidth() ) / 2;
                icon.paintIcon( this, g, xi, yi );
                g.drawString( labels_[ is ], xs, ys + yoff );
            }
        }
    }

    public Dimension getPreferredSize() {
        return active_ ? new Dimension( prefWidth_, prefHeight_ )
                       : new Dimension( 0, 0 );
    }

    public Dimension getMaximumSize() {
        return new Dimension( Integer.MAX_VALUE, prefHeight_ );
    }

    public Dimension getMinimumSize() {
        return getPreferredSize();
    }
}
