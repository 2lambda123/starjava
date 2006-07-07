package uk.ac.starlink.plastic;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.ListModel;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;

/**
 * Minimal window for displaying a JList of PLASTIC applications.
 *
 * @author  Mark Taylor
 * @since   10 Apr 2006
 */
class ListWindow extends JFrame {

    /**
     * Constructor.
     *
     * @param   model  model to display
     */
    public ListWindow( ListModel model ) {
        JList list = new JList( model );
        JScrollPane scroller = new JScrollPane( list );
        scroller.setPreferredSize( new Dimension( 200, 150 ) );
        getContentPane().setLayout( new BorderLayout() );
        getContentPane().add( scroller, BorderLayout.CENTER );
        JComponent heading = new JLabel( "PLASTIC registered applications" );
        ((JComponent) getContentPane())
                     .setBorder( BorderFactory
                                .createEmptyBorder( 5, 5, 5, 5 ) );
        heading.setBorder( BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) );
        getContentPane().add( heading, BorderLayout.NORTH );
    }
}
