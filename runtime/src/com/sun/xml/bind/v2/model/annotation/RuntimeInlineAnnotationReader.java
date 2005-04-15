package com.sun.xml.bind.v2.model.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link AnnotationReader} that uses {@code java.lang.reflect} to
 * read annotations from class files.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public final class RuntimeInlineAnnotationReader extends AbstractInlineAnnotationReaderImpl<Type,Class,Field,Method>
    implements RuntimeAnnotationReader {

    public <A extends Annotation> A getFieldAnnotation(Class<A> annotation, String propertyName, Field field, Locatable srcPos) {
        return LocatableAnnotation.create(field.getAnnotation(annotation),srcPos);
    }

    public boolean hasFieldAnnotation(Class<? extends Annotation> annotationType, Field field) {
        return field.isAnnotationPresent(annotationType);
    }

    public <A extends Annotation> A getMethodAnnotation(Class<A> annotation, Method method, Locatable srcPos) {
        return LocatableAnnotation.create(method.getAnnotation(annotation),srcPos);
    }

    public boolean hasMethodAnnotation(Class<? extends Annotation> annotation, Method method) {
        return method.isAnnotationPresent(annotation);
    }

    public <A extends Annotation> A getMethodParameterAnnotation(Class<A> annotation, Method method, int paramIndex, Locatable srcPos) {
        Annotation[] pa = method.getParameterAnnotations()[paramIndex];
        for( Annotation a : pa ) {
            if(a.annotationType()==annotation)
                return LocatableAnnotation.create((A)a,srcPos);
        }
        return null;
    }

    public <A extends Annotation> A getClassAnnotation(Class<A> a, Class clazz, Locatable srcPos) {
        return LocatableAnnotation.create(((Class<?>)clazz).getAnnotation(a),srcPos);
    }


    /**
     * Cache for package-level annotations.
     */
    private final Map<Class<? extends Annotation>,Map<Package,Annotation>> packageCache =
            new HashMap<Class<? extends Annotation>,Map<Package,Annotation>>();

    public <A extends Annotation> A getPackageAnnotation(Class<A> a, Class clazz, Locatable srcPos) {
        Package p = clazz.getPackage();
        if(p==null) return null;

        Map<Package,Annotation> cache = packageCache.get(a);
        if(cache==null) {
            cache = new HashMap<Package,Annotation>();
            packageCache.put(a,cache);
        }

        if(cache.containsKey(p))
            return (A)cache.get(p);
        else {
            A ann = LocatableAnnotation.create(p.getAnnotation(a),srcPos);
            cache.put(p,ann);
            return ann;
        }
    }

    public Class getClassValue(Annotation a, String name) {
        try {
            return (Class)a.getClass().getMethod(name).invoke(a);
        } catch (IllegalAccessException e) {
            // impossible
            throw new IllegalAccessError(e.getMessage());
        } catch (InvocationTargetException e) {
            // impossible
            throw new InternalError(e.getMessage());
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    protected String fullName(Method m) {
        return m.getDeclaringClass().getName()+'#'+m.getName();
    }
}
