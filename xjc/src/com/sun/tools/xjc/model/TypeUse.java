package com.sun.tools.xjc.model;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JExpression;
import com.sun.tools.xjc.model.nav.NType;
import com.sun.xml.bind.v2.model.core.ID;

import org.relaxng.datatype.ValidationContext;

/**
 * Information about how another type is referenced.
 *
 * <p>
 * In practice it is often easier to use {@link CTypeInfo}
 * instead of {@link NType}, so this interface defines {@link #getInfo()}.
 *
 * @author Kohsuke Kawaguchi
 * @see TypeUseImpl
 */
public interface TypeUse {
    /**
     * If the use can hold multiple values of the specified type.
     */
    boolean isCollection();

    /**
     * If this type use is adapting the type, returns the adapter.
     * Otherwise return null.
     */
    CAdapter getAdapterUse();

    /**
     * Individual item type.
     */
    CTypeInfo getInfo();

    /**
     * Whether the referenced type (individual item type in case of collection)
     * is ID/IDREF.
     *
     * <p>
     * ID is a property of a relationship. When a bean Foo has an ID property
     * called 'bar' whose type is String, Foo isn't an ID, String isn't an ID,
     * but this relationship is an ID (in the sense that Foo uses this String
     * as an ID.)
     *
     * <p>
     * The same thing can be said with IDREF. When Foo refers to Bar by means of
     * IDREF, neither Foo nor Bar is IDREF.
     *
     * <p>
     * That's why we have this method in {@link TypeUse}.
     */
    ID idUse();

    /**
     * Creates a constant for the given lexical value.
     *
     * <p>
     * Creating a constant for a {@link TypeUse} can be
     * done by combining adapting, listifying, and
     * {@link CBuiltinLeafInfo#createConstant(JCodeModel, String, ValidationContext)}.
     *
     * @throws IllegalStateException
     *      if the type isn't bound to a text in XML.
     *
     * @return null
     *      if the constant cannot be created for this {@link TypeUse}
     *      (such as when it's a collection)
     */
    JExpression createConstant(JCodeModel codeModel, String lexical, ValidationContext context);
}
