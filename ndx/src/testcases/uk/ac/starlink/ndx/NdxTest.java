package uk.ac.starlink.ndx;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.net.MalformedURLException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import uk.ac.starlink.array.AccessMode;
import uk.ac.starlink.array.ArrayImpl;
import uk.ac.starlink.array.BadHandler;
import uk.ac.starlink.array.BridgeNDArray;
import uk.ac.starlink.array.ConvertArrayImpl;
import uk.ac.starlink.array.Converter;
import uk.ac.starlink.array.DeterministicArrayImpl;
import uk.ac.starlink.array.Function;
import uk.ac.starlink.array.NDArray;
import uk.ac.starlink.array.NDShape;
import uk.ac.starlink.array.Type;
import uk.ac.starlink.array.TypeConverter;
import uk.ac.starlink.util.TestCase;

public class NdxTest extends TestCase {

    private NdxIO ndxio;
    private boolean hdsPresent = false;
    private boolean fitsPresent = false;
    private String ndxname;
    private URL remoteNDX;
    private String rname;

    public NdxTest( String name ) {
        super( name );
    }

    public void setUp() throws MalformedURLException {
        ndxio = new NdxIO();
        ndxname = System.getProperty( "java.io.tmpdir" )
                + File.separatorChar
                + "vndx";
        remoteNDX = 
            new URL( "http://andromeda.star.bris.ac.uk/~mbt/data/m31.sdf" );
        rname = System.getProperty( "java.io.tmpdir" )
              + File.separatorChar
              + "m31-from-network";
        try {
            Class.forName( "uk.ac.starlink.hds.NDFNdxHandler" );
            hdsPresent = true;
        }
        catch ( ClassNotFoundException e ) {
        }
        try {
            Class.forName( "uk.ac.starlink.fits.FitsNdxHandler" );
            fitsPresent = true;
        }
        catch ( ClassNotFoundException e ) {
        }
    }

    public void testNdx() throws IOException {

        /* Construct a virtual NDX. */
        NDShape shape = new NDShape( new long[] { 50, 40 },
                                     new long[] { 100, 200 } );
        Type type = Type.FLOAT;
        final NDArray vimage = 
            new BridgeNDArray( new DeterministicArrayImpl( shape, type ) );

        BadHandler bh = vimage.getBadHandler();
        Function sqfunc = new Function() {
            public double forward( double x ) { return x * x; }
            public double inverse( double y ) { return Math.sqrt( y ); }
        };
        Converter sconv = new TypeConverter( type, bh, type, bh, sqfunc );
        final NDArray vvariance = 
            new BridgeNDArray( new ConvertArrayImpl( vimage, sconv ) );
        final NDArray vquality = null;

        final String etcText = 
              "<etc>"
            + "<favouriteFood>Fish cakes</favouriteFood>"
            + "<pets>"
            + "<hedgehog/>"
            + "<herd>"
            + "<cow name='daisy'/>"
            + "<cow name='dobbin' colour='brown'/>"
            + "</herd>"
            + "</pets>"
            + "</etc>";

        NdxImpl vimpl = new NdxImpl() {
            public BulkDataImpl getBulkData() {
                return new ArraysBulkDataImpl( vimage, vvariance, vquality );
            }
            public byte getBadBits() { return (byte) 0; }
            public boolean hasTitle() { return true; }
            public String getTitle() { return "Mark's first test NDX"; }
            public boolean hasWCS() { return false; }
            public Object getWCS() { return null; }
            public boolean hasEtc() { return true; }
            public Source getEtc() { 
                return new StreamSource( new StringReader( etcText ) ); }
        };
        Ndx vndx = new BridgeNdx( vimpl );

        /* Write it to various output types. */
        ndxio.outputNdx( ndxname + ".xml", vndx );

        if ( fitsPresent ) {
            String fname = ndxname + ".fits";
            ndxio.outputNdx( fname, vndx );
            Ndx fndx = ndxio.makeNdx( fname );
            ndxio.outputNdx( ndxname + "-fits.xml", fndx );
        }

        if ( hdsPresent ) {
            String hname = ndxname + ".sdf";
            ndxio.outputNdx( hname, vndx );
            Ndx hndx = ndxio.makeNdx( hname );
            ndxio.outputNdx( ndxname + "-hds.xml", hndx );
        }

        /* Have a go at data across the network. */
        Ndx rndx = ndxio.makeNdx( remoteNDX );
        ndxio.outputNdx( rname + ".sdf", rndx );
 
    }
}
