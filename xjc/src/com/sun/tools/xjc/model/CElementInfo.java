package com.sun.tools.xjc.model;

import static com.sun.tools.xjc.model.CElementPropertyInfo.CollectionMode.REPEATED_VALUE;
import static com.sun.tools.xjc.model.CElementPropertyInfo.CollectionMode.NOT_REPEATED;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.namespace.QName;

import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.model.nav.NClass;
import com.sun.tools.xjc.model.nav.NType;
import com.sun.tools.xjc.model.nav.NavigatorImpl;
import com.sun.tools.xjc.outline.Aspect;
import com.sun.tools.xjc.outline.Outline;
import com.sun.xml.bind.v2.model.core.ElementInfo;

import org.xml.sax.Locator;

/**
 * {@link ElementInfo} implementation for the compile-time model.
 *
 * <p>
 * As an NType, it represents the Java representation of this element
 * (either JAXBElement&lt;T> or Foo).
 *
 * @author Kohsuke Kawaguchi
 */
public final class CElementInfo extends AbstractCTypeInfoImpl implements ElementInfo<NType,NClass>, CElement, CTypeInfo, NType {

    private final QName tagName;

    /**
     * Represents {@code JAXBElement&lt;ContentType>}.
     */
    private final NType type;

    /**
     * If this element produces its own class, the short name of that class.
     * Otherwise null.
     */
    private String className;

    /**
     * If this element is global, the element info is considered to be
     * package-level, and this points to the package in which this element
     * lives in.
     *
     * <p>
     * For local elements, this points to the parent {@link CClassInfo}.
     */
    public final CClassInfoParent parent;

    /**
     * The location in the source file where this class was declared.
     */
    @XmlTransient
    public final Locator location;

    private boolean isAbstract;

    private CElementInfo substitutionHead;

    /**
     * Lazily computed.
     */
    private Set<CElementInfo> substitutionMembers;

    /**
     * {@link Model} that owns this object.
     */
    private final Model model;

    private final CElementPropertyInfo property;

    /**
     * Creates an element in the given package.
     */
    public CElementInfo(Model model,QName tagName, JPackage _package, TypeUse contentType, String defaultValue, List<CPluginCustomization> customizations, Locator location ) {
        this(model,tagName,model.getPackage(_package),contentType,defaultValue, customizations, location);
    }

    /**
     * Creates an element in the given parent.
     */
    public CElementInfo(Model model,QName tagName, CClassInfoParent parent, TypeUse contentType, String defaultValue, List<CPluginCustomization> customizations, Locator location ) {
        super(customizations);
        this.tagName = tagName;
        this.model = model;
        this.parent = parent;
        this.property = new CElementPropertyInfo("Value",
                contentType.isCollection()?REPEATED_VALUE:NOT_REPEATED,
                contentType.idUse(),null,location,true);
        this.property.setAdapter(contentType.getAdapterUse());
        property.getTypes().add(new CTypeRef((CNonElement)contentType.getInfo(),tagName,true,defaultValue));
        this.type = NavigatorImpl.createParameterizedType(
            NavigatorImpl.theInstance.ref(JAXBElement.class),
            getContentInMemoryType() );
        this.location = location;

        model.add(this);
    }

    /**
     * Creates an element with a class in th given parent.
     */
    public CElementInfo(Model model,QName tagName, CClassInfoParent parent, String className, TypeUse contentType, String defaultValue, List<CPluginCustomization> customizations, Locator location ) {
        this(model,tagName,parent,contentType,defaultValue,customizations,location);
        this.className = className;
    }

    public final String getDefaultValue() {
        return getProperty().getTypes().get(0).getDefaultValue();
    }

    public final JPackage _package() {
        return parent.getOwnerPackage();
    }

    public CNonElement getContentType() {
        return getProperty().ref().get(0);
    }

    public NType getContentInMemoryType() {
        if(getProperty().getAdapter()==null) {
            NType itemType = getContentType().getType();
            if(!property.isCollection())
                return itemType;

            return NavigatorImpl.createParameterizedType(List.class,itemType);
        } else {
            return getProperty().getAdapter().customType;
        }
    }

    public CElementPropertyInfo getProperty() {
        return property;
    }

    public CClassInfo getScope() {
        if(parent instanceof CClassInfo)
            return (CClassInfo)parent;
        return null;
    }

    public NType getType() {
        return this;
    }

    public QName getElementName() {
        return tagName;
    }

    public JType toType(Outline o, Aspect aspect) {
        if(className==null)
            return type.toType(o,aspect);
        else
            return o.getElement(this).implClass;
    }

    /**
     * Returns the "squeezed name" of this element.
     *
     * @see CClassInfo#getSqueezedName()
     */
    @XmlElement
    public String getSqueezedName() {
        StringBuilder b = new StringBuilder();
        CClassInfo s = getScope();
        if(s!=null)
            b.append(s.getSqueezedName());
        b.append( model.getNameConverter().toClassName(tagName.getLocalPart()));
        return b.toString();
    }

    public void setAbstract() {
        isAbstract = true;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public CElementInfo getSubstitutionHead() {
        return substitutionHead;
    }

    public Collection<CElementInfo> getSubstitutionMembers() {
        if(substitutionMembers==null)
            return Collections.emptyList();
        else
            return substitutionMembers;
    }

    public void setSubstitutionHead(CElementInfo substitutionHead) {
        // don't set it twice
        assert this.substitutionHead==null;
        assert substitutionHead!=null;
        this.substitutionHead = substitutionHead;

        if(substitutionHead.substitutionMembers==null)
            substitutionHead.substitutionMembers = new HashSet<CElementInfo>();
        substitutionHead.substitutionMembers.add(this);
    }

    public boolean isBoxedType() {
        return false;
    }

    public String fullName() {
        if(className==null)
            return type.fullName();
        else {
            String r = parent.fullName();
            if(r.length()==0)   return className;
            else                return r+'.'+className;
        }
    }

    public String shortName() {
        return className;
    }

    /**
     * True if this element has its own class
     * (as opposed to be represented as an instance of {@link JAXBElement}.
     */
    public boolean hasClass() {
        return className!=null;
    }
}
