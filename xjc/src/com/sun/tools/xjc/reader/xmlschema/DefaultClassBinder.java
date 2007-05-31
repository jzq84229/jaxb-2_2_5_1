/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.tools.xjc.reader.xmlschema;
import static com.sun.tools.xjc.reader.xmlschema.BGMBuilder.getName;

import java.util.Set;

import javax.xml.namespace.QName;

import com.sun.codemodel.JJavaName;
import com.sun.codemodel.JPackage;
import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.tools.xjc.ErrorReceiver;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CClassInfoParent;
import com.sun.tools.xjc.model.CClassRef;
import com.sun.tools.xjc.model.CCustomizations;
import com.sun.tools.xjc.model.CElement;
import com.sun.tools.xjc.model.CElementInfo;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.reader.Ring;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BIClass;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BIGlobalBinding;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BISchemaBinding;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BindInfo;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BIXSubstitutable;
import com.sun.tools.xjc.reader.xmlschema.ct.ComplexTypeFieldBuilder;
import com.sun.tools.xjc.reader.xmlschema.ct.ComplexTypeBindingMode;
import com.sun.xml.xsom.XSAnnotation;
import com.sun.xml.xsom.XSAttGroupDecl;
import com.sun.xml.xsom.XSAttributeDecl;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSContentType;
import com.sun.xml.xsom.XSDeclaration;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSIdentityConstraint;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSModelGroupDecl;
import com.sun.xml.xsom.XSNotation;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.XSType;
import com.sun.xml.xsom.XSWildcard;
import com.sun.xml.xsom.XSXPath;

import org.xml.sax.Locator;

/**
 * Default classBinder implementation. Honors &lt;jaxb:class> customizations
 * and default bindings.
 */
final class DefaultClassBinder implements ClassBinder
{
    private final SimpleTypeBuilder stb = Ring.get(SimpleTypeBuilder.class);
    private final Model model = Ring.get(Model.class);

    protected final BGMBuilder builder = Ring.get(BGMBuilder.class);
    protected final ClassSelector selector = Ring.get(ClassSelector.class);


    public CElement attGroupDecl(XSAttGroupDecl decl) {
        return allow(decl,decl.getName());
    }

    public CElement attributeDecl(XSAttributeDecl decl) {
        return allow(decl,decl.getName());
    }

    public CElement modelGroup(XSModelGroup mgroup) {
        return never();
    }

    public CElement modelGroupDecl(XSModelGroupDecl decl) {
        return never();
    }


    public CElement complexType(XSComplexType type) {
        CElement ci = allow(type,type.getName());
        if(ci!=null)    return ci;

        // no customization is given -- do as the default binding.

        BindInfo bi = builder.getBindInfo(type);

        if(type.isGlobal()) {
            QName tagName = null;
            String className = deriveName(type);
            Locator loc = type.getLocator();

            if(getGlobalBinding().isSimpleMode()) {
                // in the simple mode, we may optimize it away
                XSElementDecl referer = getSoleElementReferer(type);
                if(referer!=null && isCollapsable(referer)) {
                    // if a global element contains
                    // a collpsable complex type, we bind this element to a named one
                    // and collapses element and complex type.
                    tagName = getName(referer);
                    className = deriveName(referer);
                    loc = referer.getLocator();
                }
            }

            // by default, global ones get their own classes.

            JPackage pkg = selector.getPackage(type.getTargetNamespace());

            return new CClassInfo(model,pkg,className, loc,getTypeName(type),tagName,type,bi.toCustomizationList());
        } else {
            XSElementDecl element = type.getScope();

            if( element.isGlobal() && isCollapsable(element)) {
                if(builder.getBindInfo(element).get(BIClass.class)!=null)
                    // the parent element was bound to a class. Don't bind this again to
                    // cause unnecessary wrapping
                    return null;

                // generate one class from element and complex type together.
                // this needs to be done before selector.isBound to avoid infinite recursion.

                // but avoid doing so when the element is mapped to a class,
                // which creates unnecessary classes
                return new CClassInfo( model, selector.getClassScope(),
                    deriveName(element), element.getLocator(), null,
                    getName(element), element, bi.toCustomizationList() );
            }


            CElement parentType = selector.isBound(element,type);

            String className;
            CClassInfoParent scope;


            if( parentType!=null
             && parentType instanceof CElementInfo
             && ((CElementInfo)parentType).hasClass() ) {
                // special case where we put a nested 'Type' element
                scope = (CElementInfo)parentType;
                className = "Type";
            } else {
                // since the parent element isn't bound to a type, merge the customizations associated to it, too.
//                custs = CCustomizations.merge( custs, builder.getBindInfo(type.getScope()).toCustomizationList());
                className = builder.getNameConverter().toClassName(element.getName());

                BISchemaBinding sb = builder.getBindInfo(
                    type.getOwnerSchema() ).get(BISchemaBinding.class);
                if(sb!=null)    className = sb.mangleAnonymousTypeClassName(className);
                scope = selector.getClassScope();
            }

            return new CClassInfo(model, scope, className, type.getLocator(), null, null, type, bi.toCustomizationList() );
        }
    }

    private QName getTypeName(XSComplexType type) {
        if(type.getRedefinedBy()!=null)
            return null;
        else
            return getName(type);
    }

    /**
     * Returns true if the complex type of the given element can be "optimized away"
     * and unified with its parent element decl to form a single class.
     */
    private boolean isCollapsable(XSElementDecl decl) {
        XSType type = decl.getType();

        if(!type.isComplexType())
            return false;   // not a complex type

        if(decl.getSubstitutables().size()>1 || decl.getSubstAffiliation()!=null)
            // because element substitution calls for a proper JAXBElement hierarchy
            return false;

        if(decl.isNillable())
            // because nillable needs JAXBElement to represent correctly
            return false;

        BIXSubstitutable bixSubstitutable = builder.getBindInfo(decl).get(BIXSubstitutable.class);
        if(bixSubstitutable !=null) {
            // see https://jaxb.dev.java.net/issues/show_bug.cgi?id=289
            // this customization forces non-collapsing behavior.
            bixSubstitutable.markAsAcknowledged();
            return false;
        }

        if( getGlobalBinding().isSimpleMode() && decl.isGlobal()) {
            // in the simple mode, we do more aggressive optimization, and get rid of
            // a complex type class if it's only used once from a global element
            XSElementDecl referer = getSoleElementReferer(decl.getType());
            if(referer!=null) {
                assert referer==decl;  // I must be the sole referer
                return true;
            }
        }

        if(!type.isLocal() || !type.isComplexType())
            return false;

        return true;
    }

    /**
     * If only one global {@link XSElementDecl} is refering to {@link XSType},
     * return that element, otherwise null.
     */
    private @Nullable XSElementDecl getSoleElementReferer(@NotNull XSType t) {
        Set<XSComponent> referer = builder.getReferer(t);

        XSElementDecl sole = null;
        for (XSComponent r : referer) {
            if(r instanceof XSElementDecl) {
                XSElementDecl x = (XSElementDecl) r;
                if(!x.isGlobal())
                    // local element references can be ignored, as their names are either given
                    // by the property, or by the JAXBElement (for things like mixed contents)
                    continue;
                if(sole==null)  sole=x;
                else            return null;    // more than one
            } else {
                // if another type refers to this type, that means
                // this type has a sub-type, so type substitution is possible now.
                return null;
            }
        }

        return sole;
    }

    public CElement elementDecl(XSElementDecl decl) {
        CElement r = allow(decl,decl.getName());

        if(r==null) {
            QName tagName = getName(decl);
            CCustomizations custs = builder.getBindInfo(decl).toCustomizationList();

            if(decl.isGlobal()) {
                if(isCollapsable(decl)) {
                    // we want the returned type to be built as a complex type,
                    // so the binding cannot be delayed.
                    return selector.bindToType(decl.getType().asComplexType(),decl,true);
                } else {
                    String className = null;
                    if(getGlobalBinding().isGenerateElementClass())
                        className = deriveName(decl);

                    // otherwise map global elements to JAXBElement
                    CElementInfo cei = new CElementInfo(
                        model, tagName, selector.getClassScope(), className, custs, decl.getLocator());
                    selector.boundElements.put(decl,cei);

                    stb.refererStack.push(decl);    // referer is element
                    cei.initContentType( selector.bindToType(decl.getType(),decl), decl, decl.getDefaultValue() );
                    stb.refererStack.pop();
                    r = cei;
                }
            }
        }

        // have the substitution member derive from the substitution head
        XSElementDecl top = decl.getSubstAffiliation();
        if(top!=null) {
            CElement topci = selector.bindToType(top,decl);

            if(r instanceof CClassInfo && topci instanceof CClassInfo)
                ((CClassInfo)r).setBaseClass((CClassInfo)topci);
            if (r instanceof CElementInfo && topci instanceof CElementInfo)
                ((CElementInfo)r).setSubstitutionHead((CElementInfo)topci);
        }

        return r;
    }

    public CClassInfo empty( XSContentType ct ) { return null; }

    public CClassInfo identityConstraint(XSIdentityConstraint xsIdentityConstraint) {
        return never();
    }

    public CClassInfo xpath(XSXPath xsxPath) {
        return never();
    }

    public CClassInfo attributeUse(XSAttributeUse use) {
        return never();
    }

    public CElement simpleType(XSSimpleType type) {
        CElement c = allow(type,type.getName());
        if(c!=null) return c;

        if(getGlobalBinding().isSimpleTypeSubstitution() && type.isGlobal()) {
            return new CClassInfo(model,selector.getClassScope(),
                    deriveName(type), type.getLocator(), getName(type), null, type, null );
        }

        return never();
    }

    public CClassInfo particle(XSParticle particle) {
        return never();
    }

    public CClassInfo wildcard(XSWildcard wc) {
        return never();
    }


    // these methods won't be used
    public CClassInfo annotation(XSAnnotation annon) {
        assert false;
        return null;
    }

    public CClassInfo notation(XSNotation not) {
        assert false;
        return null;
    }

    public CClassInfo facet(XSFacet decl) {
        assert false;
        return null;
    }
    public CClassInfo schema(XSSchema schema) {
        assert false;
        return null;
    }





    /**
     * Makes sure that the component doesn't carry a {@link BIClass}
     * customization.
     *
     * @return
     *      return value is unused. Since most of the caller needs to
     *      return null, to make the code a little bit shorter, this
     *      method always return null (so that the caller can always
     *      say <code>return never(sc);</code>.
     */
    private CClassInfo never() {
        // all we need to do here is just not to acknowledge
        // any class customization. Then this class customization
        // will be reported as an error later when we check all
        // unacknowledged customizations.


//        BIDeclaration cust=owner.getBindInfo(component).get(BIClass.NAME);
//        if(cust!=null) {
//            // error
//            owner.errorReporter.error(
//                cust.getLocation(),
//                "test {0}", NameGetter.get(component) );
//        }
        return null;
    }

    /**
     * Checks if a component carries a customization to map it to a class.
     * If so, make it a class.
     *
     * @param defaultBaseName
     *      The token which will be used as the basis of the class name
     *      if the class name is not specified in the customization.
     *      This is usually the name of an element declaration, and so on.
     *
     *      This parameter can be null, in that case it would be an error
     *      if a name is not given by the customization.
     */
    private CElement allow( XSComponent component, String defaultBaseName ) {
        BindInfo bindInfo = builder.getBindInfo(component);
        BIClass decl=bindInfo.get(BIClass.class);
        if(decl==null)  return null;

        decl.markAsAcknowledged();

        // first consider binding to the class reference.
        String ref = decl.getExistingClassRef();
        if(ref!=null) {
            if(!JJavaName.isFullyQualifiedClassName(ref)) {
                Ring.get(ErrorReceiver.class).error( decl.getLocation(),
                    Messages.format(Messages.ERR_INCORRECT_CLASS_NAME,ref) );
                // recover by ignoring @ref
            } else {
                if(component instanceof XSComplexType) {
                    // UGLY UGLY UGLY
                    // since we are not going to bind this complex type, we need to figure out
                    // its binding mode without actually binding it (and also expose this otherwise
                    // hidden mechanism into this part of the code.)
                    //
                    // this code is potentially dangerous as the base class might have been bound
                    // in different ways. To be correct, we need to figure out how the content type
                    // would have been bound, from the schema.
                    Ring.get(ComplexTypeFieldBuilder.class).recordBindingMode(
                        (XSComplexType)component, ComplexTypeBindingMode.NORMAL
                    );
                }
                return new CClassRef(model, component, decl, bindInfo.toCustomizationList() );
            }
        }

        String clsName = decl.getClassName();
        if(clsName==null) {
            // if the customiztion doesn't give us a name, derive one
            // from the current component.
            if( defaultBaseName==null ) {
                Ring.get(ErrorReceiver.class).error( decl.getLocation(),
                    Messages.format(Messages.ERR_CLASS_NAME_IS_REQUIRED) );

                // recover by generating a pseudo-random name
                defaultBaseName = "undefined"+component.hashCode();
            }
            clsName = builder.deriveName( defaultBaseName, component );
        } else {
            if( !JJavaName.isJavaIdentifier(clsName) ) {
                // not a valid Java class name
                Ring.get(ErrorReceiver.class).error( decl.getLocation(),
                    Messages.format( Messages.ERR_INCORRECT_CLASS_NAME, clsName ));
                // recover by a dummy name
                clsName = "Undefined"+component.hashCode();
            }
        }

        QName typeName = null;
        QName elementName = null;

        if(component instanceof XSType) {
            XSType t = (XSType) component;
            typeName = getName(t);
        }

        if (component instanceof XSElementDecl) {
            XSElementDecl e = (XSElementDecl) component;
            elementName = getName(e);
        }

        if (component instanceof XSElementDecl && !isCollapsable((XSElementDecl)component)) {
            XSElementDecl e = ((XSElementDecl)component);

            CElementInfo cei = new CElementInfo(model, elementName,
                    selector.getClassScope(), clsName,
                    bindInfo.toCustomizationList(), decl.getLocation() );
            selector.boundElements.put(e,cei);

            stb.refererStack.push(component);    // referer is element
            cei.initContentType(
                selector.bindToType(e.getType(),e),
                e,e.getDefaultValue());
            stb.refererStack.pop();
            return cei;
            // TODO: support javadoc and userSpecifiedImplClass
        } else {
            CClassInfo bt = new CClassInfo(model,selector.getClassScope(),
                    clsName, decl.getLocation(), typeName, elementName, component, bindInfo.toCustomizationList() );

            // set javadoc class comment.
            if(decl.getJavadoc()!=null )
                bt.javadoc = decl.getJavadoc()+"\n\n";
                // add extra blank lines so that the schema fragment
                // and user-specified javadoc would be separated


            // if the implClass is given, set it to ClassItem
            String implClass = decl.getUserSpecifiedImplClass();
            if( implClass!=null )
                bt.setUserSpecifiedImplClass( implClass );

            return bt;
        }
    }

    private BIGlobalBinding getGlobalBinding() {
        return builder.getGlobalBinding();
    }

    /**
     * Derives a name from a schema component.
     * Use the name of the schema component as the default name.
     */
    private String deriveName( XSDeclaration comp ) {
        return builder.deriveName( comp.getName(), comp );
    }

    /**
     * Derives a name from a schema component.
     * For complex types, we take redefinition into account when
     * deriving a default name.
     */
    private String deriveName( XSComplexType comp ) {
        String seed = builder.deriveName( comp.getName(), comp );
        int cnt = comp.getRedefinedCount();
        for( ; cnt>0; cnt-- )
            seed = "Original"+seed;
        return seed;
    }

}