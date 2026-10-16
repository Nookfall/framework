package com.nookfall.framework.testing;

import java.lang.reflect.Method;
import java.util.List;

/** Reads the registrations generated for a package. */
final class Generated {

    private Generated() {}

    @SuppressWarnings("unchecked")
    static <S> List<S> specs(Class<?> type, String method) {
        String name = type.getPackageName().isEmpty()
                ? "Nookfall_Components"
                : type.getPackageName() + ".Nookfall_Components";
        try {
            Class<?> components = Class.forName(name, true, type.getClassLoader());
            Method accessor = components.getMethod(method);
            return (List<S>) accessor.invoke(null);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "No generated " + name + "; is the Nookfall annotation processor enabled " + "for this source set?",
                    e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + name + "." + method + "()", e);
        }
    }
}
