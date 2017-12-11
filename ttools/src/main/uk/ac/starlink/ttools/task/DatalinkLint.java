package uk.ac.starlink.ttools.task;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import uk.ac.starlink.task.Environment;
import uk.ac.starlink.task.Executable;
import uk.ac.starlink.task.Parameter;
import uk.ac.starlink.task.ParameterValueException;
import uk.ac.starlink.task.StringParameter;
import uk.ac.starlink.task.Task;
import uk.ac.starlink.task.TaskException;
import uk.ac.starlink.ttools.Stilts;
import uk.ac.starlink.ttools.taplint.DatalinkValidator;
import uk.ac.starlink.ttools.taplint.OutputReporter;
import uk.ac.starlink.util.URLUtils;

/**
 * DataLink validator task.
 *
 * @author   Mark Taylor
 * @since    28 Nov 2017
 */
public class DatalinkLint implements Task {

    private final Parameter<String> locParam_;
    private final OutputReporterParameter reporterParam_;
    private final Parameter[] params_;

    /**
     * Constructor.
     */
    public DatalinkLint() {
        List<Parameter> paramList = new ArrayList<Parameter>();

        locParam_ = new StringParameter( "votable" );
        locParam_.setPosition( 1 );
        locParam_.setPrompt( "Location of DataLink VOTable" );
        locParam_.setUsage( "<filename>|<URL>|-" );
        locParam_.setDescription( new String[] {
            "<p>Location of the DataLink VOTable document to check.",
            "This may be a URL, or a filename, or the special value",
            "\"<code>-</code>\" to indicate standard input.",
            "</p>",
        } );
        paramList.add( locParam_ );

        reporterParam_ = new OutputReporterParameter( "format" );
        paramList.add( reporterParam_ );
        paramList.addAll( Arrays.asList( reporterParam_
                                        .getReporterParameters() ) );

        params_ = paramList.toArray( new Parameter[ 0 ] );
    }

    public String getPurpose() {
        return "Validates DataLink documents";
    }

    public Parameter[] getParameters() {
        return params_;
    }

    public Executable createExecutable( Environment env ) throws TaskException {
        String loc = locParam_.stringValue( env );
        final OutputReporter reporter = reporterParam_.objectValue( env );
        final DatalinkValidator validator = new DatalinkValidator( reporter );
        final Runnable runner;
        if ( "-".equals( loc ) ) {
            runner = new Runnable() {
                public void run() {
                    InputStream in = new BufferedInputStream( System.in );
                    validator.validateDatalink( in );
                }
            };
        }
        else {
            final URL url = toUrl( loc );
            if ( url == null ) {
                throw new ParameterValueException( locParam_,
                                                   "No such file or URL: "
                                                 + loc );
            }
            String proto = url.getProtocol();
            final boolean isDatalinkService = proto.equalsIgnoreCase( "http" )
                                           || proto.equalsIgnoreCase( "https" );
            final boolean mustSucceed = true;
            runner = new Runnable() {
                public void run() {
                    validator.validateDatalink( url, isDatalinkService,
                                                mustSucceed );
                }
            };
        }
        final String[] announcements = getAnnouncements();
        return new Executable() {
            public void execute() {
                reporter.start( announcements );
                runner.run();
                reporter.summariseUnreportedMessages( null );
                reporter.end();
            }
        };
    }

    /**
     * Returns the initial text to output through the reporter.
     *
     * @return  announcement text lines
     */
    private String[] getAnnouncements() {
        return new String[] {
            new StringBuffer()
                .append( "This is the STILTS DataLink validator, " )
                .append( Stilts.getVersion() )
                .append( "/" )
                .append( Stilts.getStarjavaRevision() )
                .toString(),
        };
    }

    /**
     * Attempts to extract a URL from a location string.
     * If it doesn't appear to refer to a location, null is returned.
     *
     * @param  loc  location string
     * @return  URL or null
     */
    private static URL toUrl( String loc ) {
        try {
            return new URL( loc );
        }
        catch ( MalformedURLException e ) {
        }
        File f = new File( loc );
        if ( f.exists() ) {
            return URLUtils.makeFileURL( f );
        }
        return null;
    }
}
