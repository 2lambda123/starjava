package uk.ac.starlink.votable;

import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.util.ArrayDataInput;
import nom.tam.util.BufferedDataInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import uk.ac.starlink.fits.BintableStarTable;
import uk.ac.starlink.fits.FitsConstants;
import uk.ac.starlink.fits.FitsTableBuilder;
import uk.ac.starlink.table.ColumnInfo;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.StoragePolicy;
import uk.ac.starlink.table.TableBuilder;
import uk.ac.starlink.table.TableFormatException;
import uk.ac.starlink.table.TableSink;
import uk.ac.starlink.util.DOMUtils;
import uk.ac.starlink.util.DataSource;
import uk.ac.starlink.util.IOUtils;

/**
 * Table builder which can read files in 'fits-plus' format (as written
 * by {@link FitsPlusTableWriter}).  This looks for a primary header
 * in the FITS file which contains the VOTMETA header (in fact it is
 * quite inflexible about what it recognises as this format - 
 * see {@link #isMagic}) and tries to interpret the data array as a 
 * 1-d array of bytes representing the XML of a VOTable document.
 * This VOTable document should have one TABLE element with no DATA
 * content - the table data is got from the first extension HDU,
 * which must be a BINTABLE extension matching the metadata described
 * by the VOTable.
 *
 * <p>The point of all this is so that you can store a VOTable in
 * the efficient FITS format (it can be mapped if it's on local disk,
 * which makes table creation practically instantaneous, even for
 * random access) without sacrificing any of the metadata that you
 * want to encode in VOTable format.
 *
 * @author   Mark Taylor (Starlink)
 * @since    27 Aug 2004
 * @see      FitsPlusTableWriter
 */
public class FitsPlusTableBuilder implements TableBuilder {

    private static Logger logger = Logger.getLogger( "uk.ac.starlink.votable" );

    public String getFormatName() {
        return "FITS-plus";
    }

    public StarTable makeStarTable( DataSource datsrc, boolean wantRandom,
                                    StoragePolicy storagePolicy )
            throws IOException {

        /* If the data source has a position, then we're being directed
         * to a particular HDU - not for us. */
        if ( datsrc.getPosition() != null ) {
            throw new TableFormatException( "Can't locate numbered HDU" );
        }

        /* See if this looks like a fits-plus table. */
        if ( ! isMagic( datsrc.getIntro() ) ) {
            throw new TableFormatException( 
                "Doesn't look like a FITS-plus file" );
        }

        /* Get an input stream. */
        ArrayDataInput strm = FitsConstants.getInputStreamStart( datsrc );
        try {
            long[] pos = new long[ 1 ]; 
            TableElement tabel = readMetadata( strm, pos );

            /* Now get the StarTable from the next HDU. */
            StarTable starTable =
                FitsTableBuilder.attemptReadTable( strm, wantRandom,
                                                   datsrc, pos );
            if ( starTable == null ) {
                throw new TableFormatException( "No BINTABLE HDU found" );
            }

            /* Turn it into a TabularData element associated it with its
             * TABLE DOM element as if the DOM builder had found the table
             * data in a DATA element within the TABLE element. */
            tabel.setData( new TableBodies.StarTableTabularData( starTable ) );

            /* Now create and return a StarTable based on the TABLE element; 
             * its metadata comes from the VOTable, but its data comes from 
             * the FITS table we've just read. */
            VOStarTable startab = new VOStarTable( tabel );

            /* Ensure column type consistency.  There can occasionally by 
             * some nasty issues with Character/String types. */
            int ncol = starTable.getColumnCount();
            assert ncol == startab.getColumnCount();
            for ( int icol = 0; icol < ncol; icol++ ) {
                ColumnInfo fInfo = starTable.getColumnInfo( icol );
                ColumnInfo vInfo = startab.getColumnInfo( icol );
                if ( ! vInfo.getContentClass()
                            .isAssignableFrom( fInfo.getContentClass() ) ) {
                    vInfo.setContentClass( fInfo.getContentClass() );
                }
            }

            /* Return the table with FITS data and VOTable metadata. */
            return startab;
        }
        catch ( FitsException e ) {
            throw new TableFormatException( e.getMessage(), e );
        }
        catch ( NullPointerException e ) {
            throw new TableFormatException( "Table not quite in " +
                                            "fits-plus format", e );
        }
    }

    public void streamStarTable( InputStream in, final TableSink sink,
                                 String pos )
            throws IOException {

        /* If we're being directed to a numbered HDU, it's not for us. */
        if ( pos != null && pos.trim().length() > 0 ) {
            throw new TableFormatException( "Can't locate numbered HDU" );
        }

        /* Read the metadata from the primary HDU. */
        ArrayDataInput strm = new BufferedDataInputStream( in );
        try {
            TableElement tabel = readMetadata( strm, new long[ 1 ] );

            /* Prepare a modified sink which behaves like the one we were
             * given but will pass on the VOTable metadata rather than that 
             * from the BINTABLE extension. */
            final StarTable voMeta = new VOStarTable( tabel );
            TableSink wsink = new TableSink() {
                public void acceptMetadata( StarTable fitsMeta )
                        throws TableFormatException {
                    sink.acceptMetadata( voMeta );
                }
                public void acceptRow( Object[] row ) throws IOException {
                    sink.acceptRow( row );
                }
                public void endRows() throws IOException {
                    sink.endRows();
                }
            };

            /* Write the table data from the upcoming BINTABLE element to the
             * sink. */
            Header hdr = new Header();
            FitsConstants.readHeader( hdr, strm );
            BintableStarTable.streamStarTable( hdr, strm, wsink );
        }
        catch ( FitsException e ) {
            throw new TableFormatException( e.getMessage(), e );
        }
        finally {
            strm.close();
        }
    }

    /**
     * Reads the primary HDU of a FITS stream, checking it is of the 
     * correct FITS-plus format, and returns the VOTable TABLE element
     * which is encoded in it.  On successful exit, the stream will 
     * be positioned at the start of the first non-primary HDU 
     * (which should contain a BINTABLE).
     *
     * @param   strm  stream holding the data (positioned at the start)
     * @param   pos   1-element array for returning the number of bytes read
     *                into the stream
     * @return  TABLE element in the primary HDU
     */
    private TableElement readMetadata( ArrayDataInput strm, long[] pos )
            throws IOException {

        /* Read the first FITS block from the stream into a buffer. 
         * This should contain the entire header of the primary HDU. */
       
        byte[] headBuf = new byte[ 2880 ];
        strm.readFully( headBuf );

        /* Check it seems to have the right form. */
        if ( ! isMagic( headBuf ) ) {
            throw new TableFormatException( "Primary header not FITS-plus" );
        }
        try {

            /* Turn it into a header and find out the length of the 
             * data unit. */
            Header hdr = new Header();
            ArrayDataInput hstrm = 
                new BufferedDataInputStream(
                    new ByteArrayInputStream( headBuf ) );
            int headsize = FitsConstants.readHeader( hdr, hstrm );
            int datasize = (int) FitsConstants.getDataSize( hdr );
            pos[ 0 ] = headsize + datasize;
            assert headsize == 2880;
            assert hdr.getIntValue( "NAXIS" ) == 1;
            assert hdr.getIntValue( "BITPIX" ) == 8;
            int nbyte = hdr.getIntValue( "NAXIS1" );

            /* Read the data from the primary HDU into a byte buffer. */
            byte[] vobuf = new byte[ nbyte ];
            strm.readFully( vobuf );

            /* Advance to the end of the primary HDU. */
            int pad = datasize - nbyte;
            IOUtils.skipBytes( strm, pad );

            /* Read XML from the byte buffer, performing a custom
             * parse to DOM. */
            VOElementFactory vofact = new VOElementFactory();
            DOMSource domsrc =
                vofact.transformToDOM( 
                    new StreamSource( new ByteArrayInputStream( vobuf ) ),
                                      false );

            /* Obtain the TABLE element, which ought to be empty. */
            VODocument doc = (VODocument) domsrc.getNode();
            VOElement topel = (VOElement) doc.getDocumentElement();
            VOElement resel = topel.getChildByName( "RESOURCE" );
            if ( resel == null ) {
                throw new TableFormatException( 
                    "Embedded VOTable document has no RESOURCE element" );
            }
            TableElement tabel = (TableElement) resel.getChildByName( "TABLE" );
            if ( tabel == null ) {
                throw new TableFormatException(
                    "Embedded VOTable document has no TABLE element" );
            }
            if ( tabel.getChildByName( "DATA" ) != null ) {
                throw new TableFormatException(
                    "Embedded VOTable document has unexpected DATA element" );
            }
            return tabel;
        }
        catch ( FitsException e ) {
            throw new TableFormatException( e.getMessage(), e );
        }
        catch ( SAXException e ) {
            throw new TableFormatException( e.getMessage(), e );
        }
    }

    /**
     * Returns <tt>true</tt> for a flavor with the MIME type "application/fits".
     */
    public boolean canImport( DataFlavor flavor ) {
        if ( flavor.getPrimaryType().equals( "application" ) &&
             flavor.getSubType().equals( "fits" ) ) {
            return true;
        }
        return false;
    }

    /**
     * Tests whether a given buffer contains bytes which might be the
     * first few bytes of a FitsPlus table.
     * The criterion is that it looks like the start of a FITS header, 
     * and the first few cards look roughly like this:
     * <pre>
     *     SIMPLE  =              T
     *     BITPIX  =              8
     *     NAXIS   =              1
     *     NAXIS1  =            ???
     *     VOTMETA =              T
     * </pre>
     *
     * @param  buffer  byte buffer containing leading few bytes of data
     * @return  true  if it looks like a FitsPlus file
     */
    public static boolean isMagic( byte[] buffer ) {
        final int ntest = 5;
        int pos = 0;
        int ncard = 0;
        boolean ok = true;
        for ( int il = 0; ok && il < ntest; il++ ) {
            if ( buffer.length > pos + 80 ) {
                char[] cbuf = new char[ 80 ];
                for ( int ic = 0; ic < 80; ic++ ) {
                    cbuf[ ic ] = (char) ( buffer[ pos++ ] & 0xff );
                }
                try {
                    HeaderCard card = new HeaderCard( new String( cbuf ) );
                    ok = ok && cardOK( il, card );
                }
                catch ( FitsException e ) {
                    ok = false;
                }
            }
        }
        return ok;
    }

    /**
     * Checks whether the i'th card looks like it should do for the file
     * to be readable by this handler.
     *
     * @param  icard  card index
     * @param  card   header card
     * @return  true  if <tt>card</tt> looks like the <tt>icard</tt>'th
     *          header card of a FitsPlus primary header should do
     */
    private static boolean cardOK( int icard, HeaderCard card )
            throws FitsException {
        String key = card.getKey();
        String value = card.getValue();
        switch ( icard ) {
            case 0:
                return "SIMPLE".equals( key ) && "T".equals( value );
            case 1:
                return "BITPIX".equals( key ) && "8".equals( value );
            case 2:
                return "NAXIS".equals( key ) && "1".equals( value );
            case 3:
                return "NAXIS1".equals( key );
            case 4:
                return "VOTMETA".equals( key ) && "T".equals( value );
            default:
                return true;
        }
    }
}
