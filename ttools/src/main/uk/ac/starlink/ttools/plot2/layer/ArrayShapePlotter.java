package uk.ac.starlink.ttools.plot2.layer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import uk.ac.starlink.ttools.plot.Range;
import uk.ac.starlink.ttools.plot2.DataGeom;
import uk.ac.starlink.ttools.plot2.PlotLayer;
import uk.ac.starlink.ttools.plot2.RangeCollector;
import uk.ac.starlink.ttools.plot2.data.Coord;
import uk.ac.starlink.ttools.plot2.data.CoordGroup;
import uk.ac.starlink.ttools.plot2.data.DataSpec;
import uk.ac.starlink.ttools.plot2.data.DataStore;
import uk.ac.starlink.ttools.plot2.data.FloatingArrayCoord;
import uk.ac.starlink.ttools.plot2.data.Input;
import uk.ac.starlink.ttools.plot2.data.TupleSequence;

/**
 * ShapePlotter subclass that plots multiple shapes for each row,
 * based on array-valued coordinates.
 * This class provides some additional functionality
 * specific to array-valued positions.
 *
 * <p>This plotter does not report positions and point clouds in the
 * usual way, since each row typically corresponds to a large region
 * of the plot surface, and reporting a single point is not very helpful.
 * Instead, the PlotLayers it supplies are doctored to adjust the
 * coordinate ranges to cover the whole of the relevant area for
 * the plotted rows.
 *
 * @author   Mark Taylor
 * @since    27 Jan 2021
 */
public class ArrayShapePlotter extends ShapePlotter {

    private final ShapeForm form_;
    private final FloatingArrayCoord xsCoord_;
    private final FloatingArrayCoord ysCoord_;
    private final int icXs_;
    private final int icYs_;

    /**
     * Constructor.
     *
     * @param   name  plotter name
     * @param   form  multiple shape determiner
     * @param   mode  colour determiner
     */
    public ArrayShapePlotter( String name, ShapeForm form, ShapeMode mode ) {
        super( name, form, mode, createArrayCoordGroup( form, mode ) );
        form_ = form;
        xsCoord_ = FloatingArrayCoord.X;
        ysCoord_ = FloatingArrayCoord.Y;
        CoordGroup cgrp = getCoordGroup();
        icXs_ = 0;
        icYs_ = 1;
    }

    @Override
    public int getModeCoordsIndex( DataGeom geom ) {
        return 2 + form_.getExtraCoords().length;
    }

    @Override
    public PlotLayer createLayer( DataGeom pointDataGeom,
                                  final DataSpec dataSpec,
                                  ShapeStyle style ) {
        final PlotLayer baseLayer =
            super.createLayer( pointDataGeom, dataSpec, style );
        return new WrapperPlotLayer( baseLayer ) {
            @Override
            public void extendCoordinateRanges( Range[] ranges,
                                                boolean[] logFlags,
                                                DataStore dataStore ) {
                super.extendCoordinateRanges( ranges, logFlags, dataStore );
                RangeCollector<TupleSequence> rangeCollector =
                        new RangeCollector<TupleSequence>( 2 ) {
                    public void accumulate( TupleSequence tseq,
                                            Range[] ranges ) {
                        extendRanges( tseq, ranges[ 0 ], ranges[ 1 ] );
                    }
                };
                Range[] arrayRanges =
                    dataStore
                   .getTupleRunner()
                   .collect( rangeCollector,
                             () -> dataStore.getTupleSequence( dataSpec ) );
                rangeCollector.mergeRanges( ranges, arrayRanges );
            }
        };
    }

    /**
     * Creates an array of ArrayShapePlotters, using all combinations of the
     * specified list of ShapeForms and ShapeModes.
     * Since these implement the {@link ModePlotter} interface,
     * other parts of the UI may be able to group them.
     *
     * @param  forms  array of shape forms
     * @param  modes  array of shape modes
     * @return <code>forms.length*modes.length</code>-element array of plotters
     */
    public static ArrayShapePlotter[]
            createArrayShapePlotters( ShapeForm[] forms, ShapeMode[] modes ) {
        int nf = forms.length;
        int nm = modes.length;
        ArrayShapePlotter[] plotters = new ArrayShapePlotter[ nf * nm ];
        int ip = 0;
        for ( ShapeMode mode : modes ) {
            for ( ShapeForm form : forms ) {
                String name = form.getFormName() + "-" + mode.getModeName();
                plotters[ ip++ ] = new ArrayShapePlotter( name, form, mode );
            }
        }
        assert ip == plotters.length;
        return plotters;
    }

    /**
     * Utility method that identifies whether an Input corresponds to a
     * named axis.  This is an ad hoc method put in place to
     * assist in working out how to annotate axes on which array plots
     * are represented.
     * 
     * @param  axName  geometric axis name, e.g. "X"
     * @param  input   coordinate input specification
     * @return   true iff the input corresponds to an array value specifier
     *           intended for the named axis
     */
    public static boolean matchesAxis( String axName, Input input ) {
        return ( "X".equals( axName ) &&
                 input.getMeta().getLongName()
                .equals( FloatingArrayCoord.X
                        .getInput().getMeta().getLongName() ) )
            || ( "Y".equals( axName ) &&
                 input.getMeta().getLongName()
                .equals( FloatingArrayCoord.Y
                        .getInput().getMeta().getLongName() ) );
    }

    /**
     * Extends X and Y coordinate ranges to cover all the positions
     * represented by array-valued X and Y coordinates in a TupleSequence.
     *
     * @param  tseq   tuple sequence
     * @param  xRange   X range to adjust
     * @param  yRange   Y range to adjust
     */
    private void extendRanges( TupleSequence tseq, Range xRange, Range yRange ){
        while ( tseq.next() ) {
            int np = xsCoord_.getArrayCoordLength( tseq, icXs_ );
            if ( np > 0 && np == ysCoord_.getArrayCoordLength( tseq, icYs_ ) ) {
                 double[] xs = xsCoord_.readArrayCoord( tseq, icXs_ );
                 double[] ys = ysCoord_.readArrayCoord( tseq, icYs_ );
                for ( int ip = 0; ip < np; ip++ ) {
                    xRange.submit( xs[ ip ] );
                    yRange.submit( ys[ ip ] );
                }
            }
        }
    }

    /**
     * Prepares a CoordGroup suitable for use with an ArrayShapePlotter
     * based on an array-consuming ShapeForm and a ShapeMode.
     *
     * @param  form  shape form that uses X and Y array-valued coordinates
     * @param  mode  shading mode
     * @return  coord group
     */
    private static CoordGroup createArrayCoordGroup( ShapeForm form,
                                                     ShapeMode mode ) {
        List<Coord> clist = new ArrayList<>();
        clist.add( FloatingArrayCoord.X );
        clist.add( FloatingArrayCoord.Y );
        clist.addAll( Arrays.asList( form.getExtraCoords() ) );
        clist.addAll( Arrays.asList( mode.getExtraCoords() ) );
        Coord[] coords = clist.toArray( new Coord[ 0 ] );
        boolean[] rangeCoordFlags = new boolean[ coords.length ];
        rangeCoordFlags[ 0 ] = true;
        rangeCoordFlags[ 1 ] = true;
        return CoordGroup.createPartialCoordGroup( coords, rangeCoordFlags );
    }
}
