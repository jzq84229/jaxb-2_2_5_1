package com.sun.xml.bind.v2.model.impl;

import java.util.Collection;

import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;

import com.sun.xml.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.bind.v2.model.annotation.Locatable;
import com.sun.xml.bind.v2.model.core.AccessibleProperty;
import com.sun.xml.bind.v2.model.core.Adapter;
import com.sun.xml.bind.v2.model.core.ID;
import com.sun.xml.bind.v2.model.core.PropertyInfo;
import com.sun.xml.bind.v2.model.core.TypeInfo;
import com.sun.xml.bind.v2.model.core.TypeInfoSet;
import com.sun.xml.bind.v2.model.nav.Navigator;
import com.sun.xml.bind.v2.runtime.Location;

/**
 * Default partial implementation for {@link PropertyInfo}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class PropertyInfoImpl<TypeT,ClassDeclT,FieldT,MethodT>
    implements PropertyInfo<TypeT,ClassDeclT>, AccessibleProperty, Locatable {

    /**
     * Object that reads annotations.
     */
    protected final PropertySeed<TypeT,ClassDeclT,FieldT,MethodT> seed;

    /**
     * Lazily computed.
     * @see #isCollection()
     */
    private Boolean isCollection;

    private final ID id;

    /**
     * Computed lazily.
     *
     * @see {@link #getType()}.
     */
    private TypeInfo<TypeT,ClassDeclT> type;

    protected final ClassInfoImpl<TypeT,ClassDeclT,FieldT,MethodT> parent;

    protected PropertyInfoImpl(ClassInfoImpl<TypeT,ClassDeclT,FieldT,MethodT> parent, PropertySeed<TypeT,ClassDeclT,FieldT,MethodT> spi) {
        this.seed = spi;
        this.parent = parent;
        this.id = calcId();
    }

    public ClassInfoImpl<TypeT,ClassDeclT,FieldT,MethodT> parent() {
        return parent;
    }

    protected final Navigator<TypeT,ClassDeclT,FieldT,MethodT> nav() {
        return parent.nav();
    }
    protected final AnnotationReader<TypeT,ClassDeclT,FieldT,MethodT> reader() {
        return parent.reader();
    }


    public final String getName() {
        return seed.getName();
    }

    public Adapter<TypeT,ClassDeclT> getAdapter() {
        if(seed instanceof AdaptedPropertySeed)
            return ((AdaptedPropertySeed<TypeT,ClassDeclT,FieldT,MethodT>)seed).adapter;
        else
            return null;
    }


    public final String displayName() {
        return nav().getClassName(parent.getClazz())+'#'+getName();
    }

    public final ID id() {
        return id;
    }

    private ID calcId() {
        XmlID ida = seed.readAnnotation(XmlID.class);
        if(ida!=null) {
            return ID.ID;
        } else
        if(seed.readAnnotation(XmlIDREF.class)!=null) {
            return ID.IDREF;
        } else {
            return ID.NONE;
        }
    }


    public TypeInfo<TypeT,ClassDeclT>  getType() {
        if(type==null) {
            assert parent.builder!=null : "this method must be called during the build stage";
            type = parent.builder.getTypeInfo(_getType(),this);
        }
        return type;
    }

    /**
     * Returns the type of the property computed from the signature.
     *
     * <p>
     * When the property is a collection, this method returns
     * the type of the collection item, not that of the collection itself.
     */
    protected final TypeT _getType() {
        TypeT rt = seed.getRawType();
        if(!isCollection())
            return rt;
        else {
            if(nav().isArrayButNotByteArray(rt)) {
                return nav().getComponentType(rt);
            } else {
                // look for the type parameter
                TypeT t = nav().getBaseClass(rt,nav().asDecl(Collection.class));
                if(nav().isParameterizedType(t))
                    return nav().getTypeArgument(t,0);
                else
                    // default
                    return nav().ref(Object.class);
            }
        }
    }

    public final boolean isCollection() {
        if(isCollection==null) {
            TypeT t = seed.getRawType();
            if(nav().isSubClassOf(t,nav().ref(Collection.class))
            || nav().isArrayButNotByteArray(t))
                isCollection = true;
            else
                isCollection = false;
        }
        return isCollection;
    }

    public final String generateSetValue(String $bean, String $var, String uniqueName) {
        return seed.generateSetValue($bean,$var);
    }

    public final String generateGetValue(String $bean, String $var, String uniqueName) {
        return $var + '=' + seed.generateGetValue($bean) + ';';
    }

    /**
     * Called after all the {@link TypeInfo}s are collected into the governing {@link TypeInfoSet}.
     *
     * Derived class can do additional actions to complete the model.
     */
    protected void link() {
    
    }

    /**
     * A {@link PropertyInfoImpl} is always referenced by its enclosing class,
     * so return that as the upstream.
     */
    public Locatable getUpstream() {
        return parent;
    }

    public Location getLocation() {
        return seed.getLocation();
    }
}
