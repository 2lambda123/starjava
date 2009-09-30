package uk.ac.starlink.topcat.join;

import uk.ac.starlink.table.StarTableFactory;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.topcat.ColumnSelector;
import uk.ac.starlink.topcat.TopcatModel;
import uk.ac.starlink.ttools.cone.ConeSearcher;
import uk.ac.starlink.vo.Capability;

/**
 * Defines service-type-specific aspects of how to do a multiple query 
 * against a positional (cone-like) DAL service.
 *
 * @author   Mark Taylor
 * @since    30 Sep 2009
 */
public interface DalMultiService {

    /**
     * Returns the name of this service type.
     *
     * @return  short name
     */
    String getName();

    /**
     * Returns a short label for this service type.
     *
     * @return  short label - no spaces, just a few lower case characters
     */
    String getLabel();

    /**
     * Returns the capability defining this service type.
     *
     * @return  capapbility type
     */
    Capability getCapability();

    /**
     * Returns metadata describing the search radius (or diameter, or whatever)
     * parameter used by this query.
     *
     * @return   search size metadata
     */
    ValueInfo getSizeInfo();

    /**
     * Configures the column selector representing search radius 
     * (or diameter, or whatever) to some sensible default value.
     *
     * @param  sizeSelector   search size value selector component
     */
    void setSizeDefault( ColumnSelector sizeSelector );

    /**
     * Constructs a cone searcher object for this service type.
     *
     * @param   url  service URL
     * @param   tfact  table factory
     */
    ConeSearcher createSearcher( String url, StarTableFactory tfact );
}
