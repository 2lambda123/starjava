package uk.ac.starlink.votable.dom;

import org.w3c.dom.Text;

public class DelegatingText extends DelegatingCharacterData implements Text {

    private final Text base_;
    private final DelegatingDocument doc_;

    protected DelegatingText( Text base, DelegatingDocument doc ) {
        super( base, doc );
        base_ = base;
        doc_ = doc;
    }

    public Text splitText( int offset ) {
        return (Text) doc_.getDelegator( base_.splitText( offset ) );
    }
}
