/*
 * Copyright (C) 2003 Central Laboratory of the Research Councils
 *
 *  History:
 *     29-SEP-2000 (Peter W. Draper):
 *       Original version.
 */
package uk.ac.starlink.splat.iface;

import javax.swing.AbstractListModel;
import javax.swing.ListSelectionModel;

import uk.ac.starlink.splat.data.SpecData;

/**
 * SplatListModel is an implementation of the ListModel interface for
 * mediating between the GlobalSpecPlotList object and the main view of
 * available spectra.
 *
 * @version $Id$
 * @author Peter W. Draper
 * @copyright Copyright (C) 2000 Central Laboratory of the Research Councils
 *
 */
public class SpecListModel 
    extends AbstractListModel 
    implements SpecListener 
{
    /**
     * Reference to the GlobalSpecPlotList object. This is an
     * interface to all information about spectra availability (and
     * views etc). 
     */
    protected GlobalSpecPlotList globalList = 
        GlobalSpecPlotList.getReference();

    /**
     * Reference to the ListSelectionModel that is used for the JList.
     */
    protected ListSelectionModel selectionModel;

    /**
     *  Create an instance of this class.
     */
    public SpecListModel( ListSelectionModel selectionModel ) 
    {
        // Register ourselves as interested in changes in the number
        // of spectra in the GlobalSpecPlotList;
        globalList.addSpecListener( this );

        //  The selection model is used to deal with spectrum changes
        //  that require that the selection is also changed.
        this.selectionModel = selectionModel;
    }
    public void finalize() throws Throwable
    { 
        globalList.removeSpecListener( this );
        super.finalize();
    }

    /**
     *  Return the spectrum at a given position.
     */
    public SpecData getSpectrum( int index ) 
    {
        return globalList.getSpectrum( index );
    }

//
//  Implement rest of ListModel interface (listeners are free from
//  AbstractListModel)
//
    /**
     *  Return the length of the list (i.e. the number of spectra).
     */
    public int getSize() 
    {
        return globalList.specCount();
    }

    /**
     *  Return the name of the spectrum at a given position.
     */
    public Object getElementAt( int index ) 
    {
        return globalList.getShortName( index );
    }

//
//  Implement the SpecListener interface.
//    
    /**
     *  React to a new spectrum being added.
     *
     *  @param index the index at which the new spectrum has been added.
     */
    public void spectrumAdded( SpecChangedEvent e ) 
    {
        int index = e.getIndex();
        fireIntervalAdded( this, index, index );
    }

    /**
     *  React to a spectrum being removed.
     */
    public void spectrumRemoved( SpecChangedEvent e ) 
    {
        int index = e.getIndex();
        fireIntervalRemoved( this, index, index );
    }

    /**
     *  React to a spectrum being changed. Could be the short name so
     *  we need to update.
     */
    public void spectrumChanged( SpecChangedEvent e ) 
    {
        int index = e.getIndex();
        fireContentsChanged( this, index, index );
    }

    /**
     *  React to the "current" spectrum being changed. The reaction
     *  chosen is to make this the selected spectrum.
     */
    public void spectrumCurrent( SpecChangedEvent e ) 
    {
        int index = e.getIndex();
        selectionModel.clearSelection();
        selectionModel.setSelectionInterval( index, index );
    }
}
