package com.sun.xml.bind.v2.model.core;

import com.sun.xml.bind.v2.model.annotation.Locatable;


/**
 * Either {@link ClassInfo}, {@link ElementInfo}, or {@link LeafInfo}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface TypeInfo<TypeT,ClassDeclT> extends Locatable {

    /**
     * Gets the underlying Java type that object represents.
     *
     * @return
     *      always non-null.
     */
    TypeT getType();
}
