/*
 * Copyright (C) 2000-2002 Central Laboratory of the Research Councils
 *
 *  History:
 *     28-NOV-2000 (Peter W. Draper):
 *       Original version.
 */
package uk.ac.starlink.ast.gui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * GraphicsHintsControls creates a "page" of widgets that are a view
 * of a GraphicsHints object. They provide the ability to configure
 * all the properties of the object (i.e. whether the displayed lines
 * and text are antialiased).
 *
 * @author Peter W. Draper
 * @version $Id$
 *
 * @see GraphicsHints
 * @see PlotConfigurator
 */
public class GraphicsHintsControls extends JPanel 
    implements PlotControls, ChangeListener
{
    /**
     * GraphicsHints model for current state.
     */
    protected GraphicsHints hints = null;

    /**
     * Whether the text is to be antialiased.
     */
    protected JCheckBox textAntialiased = new JCheckBox();

    /**
     * Whether everything is to be antialiased.
     */
    protected JCheckBox allAntialiased = new JCheckBox();

    /**
     * GridBagConstraints object.
     */
    protected GridBagConstraints gbc = new GridBagConstraints();

    /**
     * Label Insets.
     */
    protected Insets labelInsets = new Insets( 10, 5, 5, 10 );

    /**
     * The default title for these controls.
     */
    protected static String defaultTitle = "Graphics Rendering Hints:";

    /**
     * The default short name for these controls.
     */
    protected static String defaultName = "Rendering";

    /**
     * Create an instance.
     */
    public GraphicsHintsControls( AbstractPlotControlsModel hints )
    {
        initUI();
        setGraphicsHints( (GraphicsHints) hints );
    }

    /**
     * Create and initialise the user interface.
     */
    protected void initUI() {

        setLayout( new GridBagLayout() );

        textAntialiased.addActionListener( new ActionListener() {
                public void actionPerformed( ActionEvent e ) {
                    matchTextState();
                }
            });

        allAntialiased.addActionListener( new ActionListener() {
                public void actionPerformed( ActionEvent e ) {
                    matchAllState();
                }
            });

        //  Add labels for all fields.
        addLabel( "Text:", 0 );
        addLabel( "Everything:", 1 );

        gbc.insets = new Insets( 0, 0, 0, 0 );
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weighty = 0.0;
        gbc.weightx = 1.0;
        gbc.gridx = 1;

        //  Current row for adding components.
        int row = 0;

        //  Text antialiased.
        gbc.gridy = row++;
        gbc.fill = GridBagConstraints.NONE;
        add( textAntialiased, gbc );

        //  Everything antialiased.
        gbc.gridy = row++;
        gbc.fill = GridBagConstraints.NONE;
        add( allAntialiased, gbc );

        //  Eat up all spare vertical space (pushes widgets to top).
        Component filly = Box.createVerticalStrut( 5 );
        gbc.gridy = row++;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        add( filly, gbc );

        //  Set tooltips.
        textAntialiased.setToolTipText( 
           "Render text using antialiasing" );
        allAntialiased.setToolTipText( 
           "Render everthing using antialiasing (can be slow)" );
    }

    /**
     * Set the GraphicsHints object (only after UI is initiliased).
     */
    public void setGraphicsHints( GraphicsHints hints ) 
    {
        this.hints = hints;
        hints.addChangeListener( this );
        updateFromGraphicsHints();
    }

    /**
     * Update interface to reflect values of GraphicsHints object.
     */
    protected void updateFromGraphicsHints() 
    {
        hints.removeChangeListener( this );
        textAntialiased.setSelected( hints.isTextAntialiased() );
        allAntialiased.setSelected( hints.isAllAntialiased() );
        hints.addChangeListener( this );
    }

    /**
     * Get copy of reference to current GraphicsHints
     */
    public GraphicsHints getGraphicsHints() {
        return hints;
    }

    /**
     * Add a new UI description label. This is added to the front of
     * the given row.
     */
    private void addLabel( String text, int row ) {
        JLabel label = new JLabel( text );
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.insets = labelInsets;
        add( label, gbc );
    }

    /**
     * Match whether to antialias text.
     */
    protected void matchTextState() {
        hints.setTextAntialiased( textAntialiased.isSelected() );
    }

    /**
     * Match whether to antialias everything.
     */
    protected void matchAllState() {
        hints.setAllAntialiased( allAntialiased.isSelected() );
    }

//
// Implement the PlotControls interface
//
    /**
     * Return a title for these controls (for the border).
     */
    public String getControlsTitle()
    {
        return defaultTitle;
    }

    /**
     * Return a short name for these controls (for the tab).
     */
    public String getControlsName()
    {
        return defaultName;
    }

    /**
     * Reset controls to defaults.
     */
    public void reset() 
    {
        hints.setDefaults();
        updateFromGraphicsHints();
    }

    /**
     * Return a reference to the JComponent sub-class that will be
     * displayed (normally a reference to this).
     */
    public JComponent getControlsComponent()
    {
        return this;
    }

    /**
     * Return reference to the AbstractPlotControlsModel. This defines
     * the actual state of the controls and stores the current values.
     */
    public AbstractPlotControlsModel getControlsModel()
    {
        return hints;
    }

    /**
     * Return the class of object that we expect as our model.
     */
    public static Class getControlsModelClass()
    {
        return GraphicsHints.class;
    }

//
// Implement the ChangeListener interface
//
    /**
     * If the AstGrid object changes then we need to update the
     * interface.
     */
    public void stateChanged( ChangeEvent e )
    {
        updateFromGraphicsHints();
    }
}
