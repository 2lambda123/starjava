/*
 * Copyright (C) 2005 Central Laboratory of the Research Councils
 *
 *  History:
 *    20-JUN-2005 (Peter W. Draper):
 *       Original version.
 */
package uk.ac.starlink.splat.iface;

import java.awt.Color;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import java.util.Iterator;

import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumnModel;

import uk.ac.starlink.splat.plot.PlotControl;
import uk.ac.starlink.splat.util.Utilities;
import uk.ac.starlink.table.gui.StarJTable;
import uk.ac.starlink.util.AsciiFileParser;

/**
 * StatsRangesView extends XGraphicsRangesView for use with the
 * StatsRangesModel and StatsRange classes.
 *
 * @author Peter W. Draper
 * @version $Id$
 */
public class StatsRangesView
    extends XGraphicsRangesView
{
    private PlotControl control = null;
    private boolean tableInit = true;

    /**
     * Create an instance with default colour and constraint.
     *
     * @param plot the DivaPlot that we're drawing into.
     * @param menu a menu for adding the ranges actions (can be null).
     * @param model the model used to populate the table, if null a default
     *              instance of XGraphicsRangesModel will be used.
     */
    public StatsRangesView( PlotControl control, JMenu menu, 
                            StatsRangesModel model )
    {
        this( control, menu, Color.green, true, model );
        configureColumnWidths();
    }

    /**
     * Create an instance with a given colour and constained property and
     * StatsRangesModel instance.
     *
     * @param plot the DivaPlot that we're drawing into.
     * @param menu a menu for adding the ranges actions (can be null).
     * @param colour the colour that any figures should be drawn using.
     * @param constrain whether the figure moves just X and show a full range
     *                  in Y or not.
     * @param model the model used to populate the table, if null a default
     *              instance of XGraphicsRangesModel will be used.
     */
    protected StatsRangesView( PlotControl control, JMenu menu, Color colour, 
                               boolean constrain, StatsRangesModel model )
    {
        super( control.getPlot(), menu, colour, constrain, model );
        this.control = control;
        configureColumnWidths();
    }

    /**
     * Create a new region and arrange to have it added to the model, when
     * drawn.
     */
    protected void createRange()
    {
        //  Raise the plot to indicate that an interaction should begin.
        SwingUtilities.getWindowAncestor( plot ).toFront();

        StatsRange xRange = new StatsRange( control, (StatsRangesModel) model, 
                                            colour, constrain, null );
    }

    /**
     * Create a new region with the given coordinate range.
     *
     * @param range an array of two world coordinates (not graphics) that
     *      define the extent of the range to be created.
     */
    protected void createRange( double[] range )
    {
        StatsRange xRange = new StatsRange( control, (StatsRangesModel) model, 
                                            colour, constrain, range );
    }

    private void configureColumnWidths()
    {
        TableColumnModel tcm = table.getColumnModel();
        tcm.getColumn( 0 ).setPreferredWidth( 25 );
        tcm.getColumn( 1 ).setPreferredWidth( 100 );
        tcm.getColumn( 2 ).setPreferredWidth( 100 );
        tcm.getColumn( 3 ).setPreferredWidth( 100 );
        tcm.getColumn( 4 ).setPreferredWidth( 100 );
        tcm.getColumn( 5 ).setPreferredWidth( 100 );
        tcm.getColumn( 6 ).setPreferredWidth( 100 );
        table.setAutoResizeMode( JTable.AUTO_RESIZE_OFF );
    }

    /**
     * Read a set of ranges from a file. These are added to the existing
     * ranges. The file should be simple and have at least two fields,
     * separated by whitespace or commas. Comments are indicated by lines
     * starting with a hash (#) and are ignored.
     *
     * @param file reference to the file.
     */
    public void readRangesFromFile( File file )
    {
        //  Check file exists. Note this is more or-less a copy of the method
        //  from the superclass so we can losen the constraint requiring only
        //  two fields.
        if ( ! file.exists() && file.canRead() && file.isFile() ) {
            return;
        }
        AsciiFileParser parser = new AsciiFileParser( file );
        if ( parser.getNFields() < 2 ) {
            JOptionPane.showMessageDialog
                ( this,
                  "The format of ranges file requires at least two fields (" +
                  parser.getNFields() + " were found)",
                  "Error reading " + file.getName(),
                  JOptionPane.ERROR_MESSAGE );
            return;
        }

        int nrows = parser.getNRows();
        double[] range = new double[2];
        for ( int i = 0; i < nrows; i++ ) {
            for ( int j = 0; j < 2; j++ ) {
                range[j] = parser.getDoubleField( i, j );
            }

            //  Create the new range. Ignores any statistical parts these will
            //  be re-generated.
            createRange( range );
        }
    }

    /**
     * Write the current ranges to a simple text file.
     *
     * @param file reference to the file.
     */
    public void writeRangesToFile( File file )
    {
        //  Get a BufferedWriter to write the file line-by-line.
        FileOutputStream f = null;
        BufferedWriter r = null;
        try {
            f = new FileOutputStream( file );
            r = new BufferedWriter( new OutputStreamWriter( f ) );
        }
        catch ( Exception e ) {
            e.printStackTrace();
            return;
        }

        // Add a header to the file.
        try {
            r.write( "# File created by "+ Utilities.getReleaseName() +"\n" );
        }
        catch ( Exception e ) {
            e.printStackTrace();
        }

        // Now write the data.
        if ( model instanceof StatsRangesModel ) {
            
        }

        Iterator i = model.rangeIterator();
        StatsRange s = null;
        double[] range = null;
        while ( i.hasNext() ) {
            s = (StatsRange) i.next();
            range = s.getRange();
            try {
                r.write( range[0] + " " + range[1] + " " +
                         s.getMean() + " " + s.getStandardDeviation() + " " +
                         s.getMin() + " " + s.getMax() + "\n" );
            }
            catch ( Exception e ) {
                e.printStackTrace();
            }
        }
        try {
            r.newLine();
            r.close();
            f.close();
        }
        catch ( Exception e ) {
            //  Do nothing.
        }
    }
}
