package uk.ac.starlink.votable.dom;

import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
//DOM3 import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.Element;
import org.w3c.dom.Entity;
import org.w3c.dom.EntityReference;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Notation;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

/**
 * DOM Document implementation which delegates its operations to a
 * base Document instance.  This is the basic class which must be
 * used and extended when using the delegate DOM package.
 *
 * <p>In order to specialise the nodes which appear in a DOM, 
 * you have to override the protected <tt>createDelegating*</tt> 
 * methods to return your own custom DelegatingNode subclasses.
 *
 * @author   Mark Taylor (Starlink)
 * @since    14 Sep 2004
 */
public class DelegatingDocument extends DelegatingNode implements Document {

    private final Document base_;
    private final Map delegates_ = new HashMap();
 
    /**
     * Constructs a new document which delegates its behaviour to a
     * <tt>Document</tt> from another DOM.
     *
     * @param   base  delegate document node
     */
    public DelegatingDocument( Document base ) {
        super( base );
        setDocument( this );
        if ( base == null ) {
            throw new NullPointerException();
        }
        base_ = base;
    }

    /**
     * Constructs a new empty document based on a new empty document
     * got from the default DOM implementation.
     * This can theoretically result in a ParserConfigurationException, 
     * but shouldn't do for any sensibly set up JVM -
     * any such condition is rethrown as a RuntimeException.
     */
    public DelegatingDocument() {
        this( makeEmptyDocument() );
    }

    /**
     * Returns the node in this document which delegates to (is based on)
     * a given node in the base document.  If this node has not previously
     * been encountered, it will be created here.
     * 
     * @param  baseNode  node in the base document
     * @return   corresponding node in this document
     */
    protected DelegatingNode getDelegator( Node baseNode ) {
        DelegatingNode delegator;
        if ( baseNode == null ) {
            delegator = null;
        }
        else {
            delegator = (DelegatingNode) delegates_.get( baseNode );
            if ( delegator == null ) {
                delegator = createDelegator( baseNode );
                delegates_.put( baseNode, delegator );
            }
        }
        return delegator;
    }

    /**
     * Returns the base document (the one to which this delegates).
     *
     * @return   base document
     */
    protected Document getBaseDocument() {
        return base_;
    }

    /**
     * Creates a delegator node from a base node, by invoking one of the
     * <tt>createDelegating*</tt> mehtods.
     *
     * @param  baseNode   base node 
     * @return  a new node in this model which delegates to <tt>baseNode</tt>
     */
    private DelegatingNode createDelegator( Node baseNode ) {
        if ( baseNode == null ) {
            throw new NullPointerException();
        }
        else if ( baseNode instanceof CDATASection ) {
            return createDelegatingCDATASection( (CDATASection) baseNode );
        }
        else if ( baseNode instanceof Comment ) {
            return createDelegatingComment( (Comment) baseNode );
        }
        else if ( baseNode instanceof Text ) {
            return createDelegatingText( (Text) baseNode );
        }
        else if ( baseNode instanceof CharacterData ) {
            return createDelegatingCharacterData( (CharacterData) baseNode );
        }
        else if ( baseNode instanceof Attr ) {
            return createDelegatingAttr( (Attr) baseNode );
        }
        else if ( baseNode instanceof DocumentFragment ) {
            return createDelegatingDocumentFragment( (DocumentFragment)
                                                   baseNode );
        }
        else if ( baseNode instanceof DocumentType ) {
            return createDelegatingDocumentType( (DocumentType) baseNode );
        }
        else if ( baseNode instanceof Element ) {
            return createDelegatingElement( (Element) baseNode );
        }
        else if ( baseNode instanceof Entity ) {
            return createDelegatingEntity( (Entity) baseNode );
        }
        else if ( baseNode instanceof EntityReference ) {
            return createDelegatingEntityReference( (EntityReference) 
                                                    baseNode );
        }
        else if ( baseNode instanceof Notation ) {
            return createDelegatingNotation( (Notation) baseNode );
        }
        else if ( baseNode instanceof ProcessingInstruction ) {
            return createDelegatingProcessingInstruction( 
                       (ProcessingInstruction) baseNode );
        }
        else {
            return createDelegatingSimpleNode( baseNode );
        }
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.Node} (not one of its subclasses) 
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingNode createDelegatingSimpleNode( Node baseNode ) {
        return new DelegatingNode( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.Attr}
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingAttr createDelegatingAttr( Attr baseNode ) {
        return new DelegatingAttr( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.CDATASection}
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingCDATASection 
              createDelegatingCDATASection( CDATASection baseNode ) {
        return new DelegatingCDATASection( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.CharacterData} (not one of its subclasses)
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingCharacterData 
              createDelegatingCharacterData( CharacterData baseNode ) {
        return new DelegatingCharacterData( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.Comment}
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingComment createDelegatingComment( Comment baseNode ) {
        return new DelegatingComment( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.DocumentFragment}
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingDocumentFragment 
              createDelegatingDocumentFragment( DocumentFragment baseNode ) {
        return new DelegatingDocumentFragment( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.DocumentType}
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingDocumentType
              createDelegatingDocumentType( DocumentType baseNode ) {
        return new DelegatingDocumentType( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.Element}
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingElement createDelegatingElement( Element baseNode ) {
        return new DelegatingElement( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.Entity}
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingEntity createDelegatingEntity( Entity baseNode ) {
        return new DelegatingEntity( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.EntityReference}
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingEntityReference 
              createDelegatingEntityReference( EntityReference baseNode ) {
        return new DelegatingEntityReference( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.Notation}
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingNotation createDelegatingNotation( Notation baseNode ) {
        return new DelegatingNotation( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.ProcessingInstruction}
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingProcessingInstruction
             createDelegatingProcessingInstruction( 
                 ProcessingInstruction baseNode ) {
        return new DelegatingProcessingInstruction( baseNode, this );
    }

    /**
     * Creates a new node in this document that delegates to an object
     * of class {@link org.w3c.dom.Text} (not one of its subclasses)
     * in the base model.  This may be overridden to create specialised
     * node types.
     * 
     * @param   baseNode  delegate node
     * @return  new node in this model based on <tt>baseNode</tt>
     */
    protected DelegatingText createDelegatingText( Text baseNode ) {
        return new DelegatingText( baseNode, this );
    }

    /**
     * Returns a NodeList which will dispense nodes in this document.
     *
     * @param  baseList  node list which dispenses nodes in the base document
     * @return  equivalent of <tt>baseList</tt> in this document
     */
    protected NodeList createDelegatingNodeList( NodeList baseList ) {
        return new DelegatingNodeList( baseList, this );
    }

    /**
     * Returns a NamedNodeMap which will dispense nodes in this document.
     *
     * @param  baseMap  node map which dispenses nodes in the base document
     * @return  equivalent of <tt>baseMap</tt> in this document
     */
    protected NamedNodeMap 
            createDelegatingNamedNodeMap( NamedNodeMap baseMap ) {
        return new DelegatingNamedNodeMap( baseMap, this );
    }

    /**
     * Constructs a new empty document from JAXP's default DOM implementation.
     *
     * @return  new Document
     */
    private static Document makeEmptyDocument() {
        try {
            return DocumentBuilderFactory
                  .newInstance()
                  .newDocumentBuilder()
                  .newDocument();
        }
        catch ( ParserConfigurationException e ) {
            throw (RuntimeException) 
                  new IllegalStateException( "Can't create a new Document" )
                 .initCause( e );
        }
    }

    //
    // Level 2 implementation.
    //

    /**
     * Returns the implementation of the base document.
     */
    public DOMImplementation getImplementation() {
        return base_.getImplementation();
    }

    public DocumentType getDoctype() {
        return (DocumentType) getDelegator( base_.getDoctype() );
    }

    public Element getDocumentElement() {
        return (Element) getDelegator( base_.getDocumentElement() );
    }

    public Element createElement( String tagName ) {
        return (Element) getDelegator( base_.createElement( tagName ) );
    }

    public DocumentFragment createDocumentFragment() {
        return (DocumentFragment) 
               getDelegator( base_.createDocumentFragment() );
    }

    public Text createTextNode( String data ) {
        return (Text) getDelegator( base_.createTextNode( data ) );
    }

    public Comment createComment( String data ) {
        return (Comment) getDelegator( base_.createComment( data ) );
    }

    public CDATASection createCDATASection( String data ) {
        return (CDATASection) getDelegator( base_.createCDATASection( data ) );
    }

    public ProcessingInstruction createProcessingInstruction( String target,
                                                              String data ) {
        return (ProcessingInstruction) 
              getDelegator( base_.createProcessingInstruction( target, data ) );
    }

    public Attr createAttribute( String name ) {
        return (Attr) getDelegator( base_.createAttribute( name ) );
    }

    public EntityReference createEntityReference( String name ) {
        return (EntityReference) 
               getDelegator( base_.createEntityReference( name ) );
    }

    public NodeList getElementsByTagName( String tagname ) {
        return createDelegatingNodeList( base_
                                        .getElementsByTagName( tagname ) );
    }

    public Node importNode( Node importedNode, boolean deep ) {
        return getDelegator( base_.importNode( importedNode, deep ) );
    }

    public Element createElementNS( String namespaceURI, 
                                    String qualifiedName ) {
        return (Element) getDelegator( base_.createElementNS( namespaceURI,
                                                              qualifiedName ) );
    }

    public Attr createAttributeNS( String namespaceURI, String qualifiedName ) {
        return (Attr) getDelegator( base_.createAttributeNS( namespaceURI,
                                                             qualifiedName ) );
    }

    public NodeList getElementsByTagNameNS( String namespaceURI,
                                            String localName ) {
        return (NodeList) base_.getElementsByTagNameNS( namespaceURI,
                                                        localName );
    }

    public Element getElementById( String elementId ) {
        return (Element) getDelegator( base_.getElementById( elementId ) );
    }

//DOM3     //
//DOM3     // Level 3 implementation.
//DOM3     //
//DOM3 
//DOM3     public String getInputEncoding() {
//DOM3         return base_.getInputEncoding();
//DOM3     }
//DOM3 
//DOM3     public String getXmlEncoding() {
//DOM3         return base_.getXmlEncoding();
//DOM3     }
//DOM3 
//DOM3     public boolean getXmlStandalone() {
//DOM3         return base_.getXmlStandalone();
//DOM3     }
//DOM3 
//DOM3     public void setXmlStandalone( boolean xmlStandalone ) {
//DOM3         base_.setXmlStandalone( xmlStandalone );
//DOM3     }
//DOM3 
//DOM3     public String getXmlVersion() {
//DOM3         return base_.getXmlVersion();
//DOM3     }
//DOM3 
//DOM3     public void setXmlVersion( String xmlVersion ) {
//DOM3         base_.setXmlVersion( xmlVersion );
//DOM3     }
//DOM3 
//DOM3     public boolean getStrictErrorChecking() {
//DOM3         return base_.getStrictErrorChecking();
//DOM3     }
//DOM3 
//DOM3     public void setStrictErrorChecking( boolean strictErrorChecking ) {
//DOM3         base_.setStrictErrorChecking( strictErrorChecking );
//DOM3     }
//DOM3 
//DOM3     public String getDocumentURI() {
//DOM3         return base_.getDocumentURI();
//DOM3     }
//DOM3 
//DOM3     public void setDocumentURI( String documentURI ) {
//DOM3         base_.setDocumentURI( documentURI );
//DOM3     }
//DOM3 
//DOM3     public Node adoptNode( Node source ) {
//DOM3 
//DOM3         /* We are permitted to refuse to do this, so do that.
//DOM3          * Doing it properly would raise some minor problems. */
//DOM3         return null;
//DOM3     }
//DOM3 
//DOM3     public DOMConfiguration getDomConfig() {
//DOM3         return base_.getDomConfig();
//DOM3     }
//DOM3 
//DOM3     public void normalizeDocument() {
//DOM3         base_.normalizeDocument();
//DOM3     }
//DOM3 
//DOM3     public Node renameNode( Node node, String namespaceURI, 
//DOM3                             String qualifiedName ) {
//DOM3         return getDelegator(
//DOM3             base_.renameNode( DelegatingNode.getBaseNode( node, this ),
//DOM3                               namespaceURI, qualifiedName ) );
//DOM3     }
}
