package com.nookfall.framework.internal.config;

import com.nookfall.framework.config.Describe;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The comment lines that tell a server owner which values an enum field accepts, and what each one does. Applies to enum
 * fields and to lists and sets of enums. Large enums (materials, entity types) are not listed.
 */
final class EnumDocs {

    /** Enums with more constants than this are not listed. */
    static final int MAX_LISTED = 24;

    private EnumDocs() {}

    static List<String> lines(Type type) {
        Class<?> enumType = enumType(type);
        if (enumType == null) {
            return List.of();
        }
        Object[] constants = enumType.getEnumConstants();
        if (constants.length == 0 || constants.length > MAX_LISTED) {
            return List.of();
        }
        boolean described = false;
        List<String> names = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            names.add(name);
            String text = description(enumType, name);
            descriptions.add(text);
            described |= text != null;
        }
        if (!described) {
            return List.of("Options: " + String.join(", ", names));
        }
        List<String> out = new ArrayList<>();
        out.add("Options:");
        for (int i = 0; i < names.size(); i++) {
            out.add("  " + names.get(i) + (descriptions.get(i) == null ? "" : ": " + descriptions.get(i)));
        }
        return out;
    }

    private static String description(Class<?> enumType, String name) {
        try {
            Field field = enumType.getField(name);
            Describe describe = field.getAnnotation(Describe.class);
            return describe == null ? null : describe.value();
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static Class<?> enumType(Type type) {
        if (type instanceof Class<?> c) {
            return c.isEnum() ? c : null;
        }
        if (type instanceof ParameterizedType p
                && p.getRawType() instanceof Class<?> raw
                && Collection.class.isAssignableFrom(raw)
                && p.getActualTypeArguments().length == 1
                && p.getActualTypeArguments()[0] instanceof Class<?> element
                && element.isEnum()) {
            return element;
        }
        return null;
    }
}
