package uk.ac.starlink.table.gui;

import java.io.IOException;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import uk.ac.starlink.table.RowSequence;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.WrapperRowSequence;
import uk.ac.starlink.table.WrapperStarTable;

/**
 * A WrapperStarTable which behaves the same as its base, except that 
 * UI intervention is permitted on any RowSequence which is taken out
 * on it.  For one thing, the RowSequence will update a supplied
 * <tt>JProgressBar</tt> component as it iterates, and for another thing
 * {@link java.lang.Thread#interrupt}ing the thread in which the RowSequence
 * is being iterated over will cause the <tt>next</tt> method 
 * to throw an <tt>IOException</tt>.
 * Said <tt>RowSequence</tt>s may be used from any thread, that is 
 * they are not restricted to use from the AWT event dispatcher thread.
 * <strong>However</strong> you don't want to be using two such 
 * row sequences simultaneously or the progress bar will be getting two
 * sets of updates at once.
 *
 * <p>You might think this should be based on a 
 * {@link javax.swing.BoundedRangeModel} (JProgressBar's model) instead,
 * but unfortunately that doesn't allow you use of 
 * indeterminate progress states.
 *
 * @author   Mark Taylor (Starlink)
 */
public class ProgressBarStarTable extends WrapperStarTable {

    private JProgressBar progBar_;

    public ProgressBarStarTable( StarTable baseTable ) {
        this( baseTable, new JProgressBar() );
    }

    public ProgressBarStarTable( StarTable baseTable, JProgressBar progBar ) {
        super( baseTable );
        setProgressBar( progBar );
        setZero();
    }

    public void setProgressBar( JProgressBar progBar ) {
        progBar_ = progBar;
    }

    public JProgressBar getProgressBar() {
        return progBar_;
    }

    public RowSequence getRowSequence() throws IOException {
        long nrow = getRowCount();
        final long every = nrow > 0 ? nrow / 200L : 256; 
        return new WrapperRowSequence( baseTable.getRowSequence() ) {
            long counter;
            int irow;
            Runnable updater = new Runnable() {
                public void run() {
                    progBar_.setValue( irow );
                }
            };
            public boolean next() throws IOException {
                irow++;
                if ( --counter < 0 ) {
                    SwingUtilities.invokeLater( updater );
                    counter = every;
                }
                if ( Thread.interrupted() ) {
                    throw new IOException( "Operation interrupted" );
                }
                return super.next();
            }
            public void close() throws IOException {
                setZero();
                super.close();
            }
        };
    }

    private void setZero() {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                progBar_.setMinimum( 0 );
                progBar_.setValue( 0 );
                long nrow = getRowCount();
                boolean determinate = nrow > 0 && nrow < Integer.MAX_VALUE;
                progBar_.setIndeterminate( ! determinate );
                if ( determinate ) {
                    progBar_.setMaximum( (int) nrow );
                }
            }
        } );
    }
}
