/*
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.sun.xml.bind.validator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.helpers.NotIdentifiableEventImpl;
import javax.xml.bind.helpers.ValidationEventImpl;
import javax.xml.bind.helpers.ValidationEventLocatorImpl;

import org.xml.sax.SAXException;

import com.sun.xml.bind.serializer.*;
import com.sun.xml.bind.serializer.NamespaceContextImpl;
import com.sun.xml.bind.serializer.XMLSerializable;

/**
 * Maintains information that needs to be stored across
 * validations of multiple objects.
 * 
 * Specifically, this object is responsible for:
 * 
 * <ol>
 *   <li>detecting a cycle in a content tree by keeping track of
 *       objects that were validated.
 * 
 *   <li>keeping an instance of NamespaceContextImpl, which is
 *       shared by all MSVValidators.
 * 
 *   <li>keeping a reference to {@link ValidationErrorHandler}.
 *       MSVValidators should use this error handler to report any error.
 * </ol>
 * @since JAXB1.0
 * @deprecated in JAXB1.0.1
 */
class ValidationContext
{
    /**
     * @param validateID
     *      if true, ID/IDREF validation will be performed.
     */
    ValidationContext( ValidationEventHandler _eventHandler, boolean validateID ) {
        this.eventHandler = _eventHandler;
        this.validateID = validateID;
    }
    

    
//
//
// detecting cycles.
//
//
    
    /** Set of all validated objects. Used to detect a cycle. */
    private final HashSet validatedObjects = new HashSet();
    
    /**
     * Validates the sub-tree rooted at <code>vo</code> and reports
     * any errors/warnings to the error handler.
     * 
     * @return
     *      true if there was no error. false otherwise.
     */
    public void validate( ValidatableObject vo ) throws SAXException, AbortSerializationException {
        if( validatedObjects.add(vo) ) {
            // setup a new validator for this object and validate it.
            MSVValidator.validate(this,vo);
        } else {
            // this object has already been validated once.
            reportEvent( vo, Messages.format( Messages.CYCLE_DETECTED ) );
        }
    }

    
//
//
// Keeping track of namespace bindings.
//
//
    
    /** namespace context. */
    private final NamespaceContextImpl nsContext = new NamespaceContextImpl();
    
    public NamespaceContextImpl getNamespaceContext() { return nsContext; }
    

//
//
// ID/IDREF validation
//
//
    /** ID/IDREF validation is done only when this flag is true. */
    private final boolean validateID;
    
    private final HashSet IDs = new HashSet();
    private final HashMap IDREFs = new HashMap();
    
    public String onID( XMLSerializable owner, String value ) throws SAXException {
            
        if(!validateID) return value;
        
        if(!IDs.add(value)) {
            // this ID value has already been used.
            reportEvent((ValidatableObject)owner,
                Messages.format(Messages.DUPLICATE_ID,value));
        }
        
        return value;
    }
    public String onIDREF( XMLSerializable referer, String value ) throws SAXException {
        if(!validateID) return value;
        
        if(IDs.contains(value))
            return value; // this IDREF has the corresponding ID.
        
        // remember the value to check the value later.
        IDREFs.put(value,referer);
        
        return value;
    }
    /** Tests if all IDREFs have corresponding IDs. */
    protected void reconcileIDs() throws SAXException {
        if(!validateID) return;
        
        for (Iterator itr = IDREFs.entrySet().iterator(); itr.hasNext();) {
            Map.Entry e = (Map.Entry) itr.next();
            
            if(IDs.contains(e.getKey()))
                continue;   // OK.
            
            // ID was not found.
            ValidatableObject source = (ValidatableObject)e.getValue();
            reportEvent(
                source,                
                new NotIdentifiableEventImpl( 
                    ValidationEvent.ERROR,
                    Messages.format( Messages.ID_NOT_FOUND, e.getKey() ),
                    new ValidationEventLocatorImpl( source ) ) );
        }
        
        IDREFs.clear();
    }

    
//
//
// Keeping track of ValidationErrorHandler
//
//
    private final ValidationEventHandler eventHandler;
    
    /**
     * Reports an error to the application.
     */
    public void reportEvent(ValidatableObject source, String formattedMessage) throws AbortSerializationException {
        reportEvent( 
            source, 
            new ValidationEventImpl( ValidationEvent.ERROR, 
                                     formattedMessage,
                                     new ValidationEventLocatorImpl(source) ) );
    }
    
    /**
     * Reports an error to the client.
     * This version should be used when an exception is thrown from sub-modules.
     */
    public void reportEvent(ValidatableObject source, Exception nestedException ) throws AbortSerializationException {
        reportEvent( 
            source, 
            new ValidationEventImpl( ValidationEvent.ERROR, 
                                     nestedException.toString(),
                                     new ValidationEventLocatorImpl(source),
                                     nestedException ) );
    }
    
    public void reportEvent( ValidatableObject source, ValidationEvent event ) throws AbortSerializationException {
        boolean r;
        
        try {
            r = eventHandler.handleEvent( event );
        } catch( RuntimeException re ) {
            // if the client event handler causes a RuntimeException, then
            // we have to return false.
            r = false;
        }
        
        if(!r) {
            // throw an exception to abort validation
            throw new AbortSerializationException( event.getMessage() );
        }
    }
        
    

}
