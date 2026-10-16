package com.nookfall.framework.internal.config;

import com.nookfall.framework.config.Codec;
import com.nookfall.framework.config.Comment;
import com.nookfall.framework.config.ConfigCodec;
import com.nookfall.framework.config.ConfigKey;
import com.nookfall.framework.constraint.NotBlank;
import com.nookfall.framework.constraint.Pattern;
import com.nookfall.framework.constraint.Range;
import com.nookfall.framework.constraint.Size;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * Maps YAML sections onto plain Java objects using field reflection. Private fields are supported; fields that are
 * static, transient or synthetic are ignored.
 */
final class ConfigBinder {

    /** Mutable state of one load. */
    static final class Pass {
        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        boolean changed;
    }

    record FieldInfo(Field field, String key, List<String> comment, ConfigCodec<Object> codec) {}

    private final Codecs codecs;
    private final Map<Class<?>, List<FieldInfo>> fieldCache = new ConcurrentHashMap<>();

    ConfigBinder(Codecs codecs) {
        this.codecs = codecs;
    }

    /**
     * Reads every field of {@code target} from the section. Missing keys keep the target's current (default) value
     * and, when {@code writeDefaults} is true, are added to the section.
     */
    void read(Object target, ConfigurationSection section, String path, boolean writeDefaults, Pass pass) {
        List<FieldInfo> fields = fields(target.getClass());
        Set<String> known = new LinkedHashSet<>();
        for (FieldInfo info : fields) {
            known.add(info.key());
            String fullPath = join(path, info.key());
            Object current = get(info.field(), target);
            if (!section.contains(info.key(), true)) {
                if (current != null && writeDefaults) {
                    writeField(section, info, current, true);
                    pass.changed = true;
                }
                continue;
            }
            Object raw = section.get(info.key());
            try {
                Object value;
                if (info.codec() != null) {
                    value = info.codec().decode(plain(raw));
                } else if (isSection(info.field().getType()) && raw instanceof ConfigurationSection child) {
                    Object nested =
                            current != null ? current : instantiate(info.field().getType());
                    read(nested, child, fullPath, writeDefaults, pass);
                    value = nested;
                } else {
                    value = decode(info.field().getGenericType(), raw, fullPath, pass);
                }
                if (value == null) {
                    continue;
                }
                validate(info.field(), value, fullPath, pass);
                set(info.field(), target, value);
            } catch (IllegalArgumentException e) {
                pass.errors.add(fullPath + ": " + e.getMessage());
            }
        }
        for (String key : section.getKeys(false)) {
            if (!known.contains(key)) {
                pass.warnings.add("unknown key '" + join(path, key) + "' was kept but is not used");
            }
        }
    }

    /** Writes all fields of {@code value} into the section, replacing existing values but keeping other keys. */
    void write(Object value, ConfigurationSection section) {
        for (FieldInfo info : fields(value.getClass())) {
            Object current = get(info.field(), value);
            if (current == null) {
                section.set(info.key(), null);
                continue;
            }
            if (info.codec() == null
                    && isSection(info.field().getType())
                    && section.get(info.key()) instanceof ConfigurationSection child) {
                write(current, child);
            } else {
                writeField(
                        section, info, current, section.getComments(info.key()).isEmpty());
            }
        }
    }

    private void writeField(ConfigurationSection section, FieldInfo info, Object value, boolean comments) {
        if (info.codec() == null && isSection(info.field().getType())) {
            ConfigurationSection child = section.createSection(info.key());
            for (FieldInfo nested : fields(value.getClass())) {
                Object nestedValue = get(nested.field(), value);
                if (nestedValue != null) {
                    writeField(child, nested, nestedValue, true);
                }
            }
        } else if (info.codec() != null) {
            section.set(info.key(), info.codec().encode(value));
        } else {
            section.set(info.key(), encode(info.field().getGenericType(), value));
        }
        if (comments && !info.comment().isEmpty()) {
            section.setComments(info.key(), info.comment());
        }
    }

    // ---- decoding ----

    private Object decode(Type type, Object raw, String path, Pass pass) {
        if (raw == null) {
            return null;
        }
        Class<?> rawClass = rawClass(type);
        if (Collection.class.isAssignableFrom(rawClass)) {
            if (!(raw instanceof List<?> list)) {
                throw new IllegalArgumentException("expected a list");
            }
            Type element = typeArgument(type, 0);
            Collection<Object> out = Set.class.isAssignableFrom(rawClass) ? new LinkedHashSet<>() : new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Object decoded = decode(element, list.get(i), path + "[" + i + "]", pass);
                if (decoded != null) {
                    out.add(decoded);
                }
            }
            return Set.class.isAssignableFrom(rawClass) ? Set.copyOf(out) : List.copyOf(out);
        }
        if (Map.class.isAssignableFrom(rawClass)) {
            Map<?, ?> source = asMap(raw);
            Type keyType = typeArgument(type, 0);
            Type valueType = typeArgument(type, 1);
            Map<Object, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String childPath = path + "." + entry.getKey();
                try {
                    Object key = decodeScalar(rawClass(keyType), String.valueOf(entry.getKey()));
                    Object value = decode(valueType, entry.getValue(), childPath, pass);
                    if (value != null) {
                        out.put(key, value);
                    }
                } catch (IllegalArgumentException e) {
                    pass.errors.add(childPath + ": " + e.getMessage());
                }
            }
            return java.util.Collections.unmodifiableMap(out);
        }
        if (isSection(rawClass)) {
            ConfigurationSection section = raw instanceof ConfigurationSection s ? s : toSection(asMap(raw));
            Object instance = instantiate(rawClass);
            read(instance, section, path, false, pass);
            return instance;
        }
        if (rawClass == Object.class) {
            return plain(raw);
        }
        return decodeScalar(rawClass, raw);
    }

    private Object decodeScalar(Class<?> type, Object raw) {
        ConfigCodec<?> codec = codecs.find(type);
        if (codec == null) {
            throw new IllegalArgumentException("no codec for type " + type.getName());
        }
        return codec.decode(plain(raw));
    }

    // ---- encoding ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object encode(Type type, Object value) {
        Class<?> rawClass = rawClass(type);
        if (value instanceof Collection<?> collection) {
            Type element = typeArgument(type, 0);
            List<Object> out = new ArrayList<>();
            for (Object item : collection) {
                out.add(encode(element, item));
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Type valueType = typeArgument(type, 1);
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                out.put(key instanceof Enum<?> e ? e.name() : String.valueOf(key), encode(valueType, entry.getValue()));
            }
            return out;
        }
        if (isSection(rawClass) || (rawClass == Object.class && isSection(value.getClass()))) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (FieldInfo info : fields(value.getClass())) {
                Object fieldValue = get(info.field(), value);
                if (fieldValue != null) {
                    out.put(
                            info.key(),
                            info.codec() != null
                                    ? info.codec().encode(fieldValue)
                                    : encode(info.field().getGenericType(), fieldValue));
                }
            }
            return out;
        }
        ConfigCodec codec = codecs.find(value.getClass());
        if (codec == null) {
            codec = codecs.find(rawClass);
        }
        if (codec == null) {
            throw new IllegalArgumentException(
                    "no codec for type " + value.getClass().getName());
        }
        return codec.encode(value);
    }

    // ---- validation ----

    private static void validate(Field field, Object value, String path, Pass pass) {
        Range range = field.getAnnotation(Range.class);
        if (range != null && value instanceof Number number) {
            double d = number.doubleValue();
            if (d < range.min()) {
                pass.errors.add(path + ": " + format(d) + " is below the minimum " + format(range.min()));
            } else if (d > range.max()) {
                pass.errors.add(path + ": " + format(d) + " is above the maximum " + format(range.max()));
            }
        }
        Size size = field.getAnnotation(Size.class);
        if (size != null) {
            int length = value instanceof CharSequence s
                    ? s.length()
                    : value instanceof Collection<?> c ? c.size() : value instanceof Map<?, ?> m ? m.size() : -1;
            if (length >= 0 && (length < size.min() || length > size.max())) {
                pass.errors.add(path + ": size " + length + " must be between " + size.min() + " and " + size.max());
            }
        }
        if (field.isAnnotationPresent(NotBlank.class)
                && value instanceof CharSequence s
                && s.toString().isBlank()) {
            pass.errors.add(path + ": must not be blank");
        }
        Pattern pattern = field.getAnnotation(Pattern.class);
        if (pattern != null && value instanceof CharSequence s && !s.toString().matches(pattern.value())) {
            pass.errors.add(path + ": '" + s + "' does not match " + pattern.value());
        }
    }

    private static String format(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
    }

    // ---- reflection ----

    List<FieldInfo> fields(Class<?> type) {
        return fieldCache.computeIfAbsent(type, this::scanFields);
    }

    @SuppressWarnings("unchecked")
    private List<FieldInfo> scanFields(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            hierarchy.addFirst(c);
        }
        List<FieldInfo> out = new ArrayList<>();
        for (Class<?> c : hierarchy) {
            for (Field field : c.getDeclaredFields()) {
                int mod = field.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || field.isSynthetic()) {
                    continue;
                }
                if (Modifier.isFinal(mod)) {
                    throw new IllegalStateException(
                            "Configuration field " + c.getName() + "." + field.getName() + " must not be final");
                }
                field.setAccessible(true);
                ConfigKey keyAnnotation = field.getAnnotation(ConfigKey.class);
                String key = keyAnnotation != null ? keyAnnotation.value() : kebab(field.getName());
                Comment comment = field.getAnnotation(Comment.class);
                Codec codecAnnotation = field.getAnnotation(Codec.class);
                ConfigCodec<Object> codec = null;
                if (codecAnnotation != null) {
                    codec = (ConfigCodec<Object>) instantiate(codecAnnotation.value());
                }
                out.add(new FieldInfo(field, key, comment == null ? List.of() : List.of(comment.value()), codec));
            }
        }
        return List.copyOf(out);
    }

    /** {@code maxHomes} becomes {@code max-homes}. */
    static String kebab(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) out.append('-');
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    boolean isSection(Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.isEnum() || type.isInterface() || type.isRecord()) {
            return false;
        }
        if (codecs.find(type) != null || Modifier.isAbstract(type.getModifiers())) {
            return false;
        }
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("org.bukkit.")) {
            return false;
        }
        try {
            type.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    static Object instantiate(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot create " + type.getName() + ": it needs a no-argument constructor", e);
        }
    }

    private static Object get(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "value of type " + value.getClass().getSimpleName() + " does not fit field type "
                            + field.getType().getSimpleName());
        }
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType p) {
            return (Class<?>) p.getRawType();
        }
        if (type instanceof WildcardType w) {
            return rawClass(w.getUpperBounds()[0]);
        }
        return Object.class;
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType p && p.getActualTypeArguments().length > index) {
            return p.getActualTypeArguments()[index];
        }
        return index == 0 && Map.class.isAssignableFrom(rawClass(type)) ? String.class : Object.class;
    }

    private static Map<?, ?> asMap(Object raw) {
        if (raw instanceof ConfigurationSection section) {
            return section.getValues(false);
        }
        if (raw instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("expected a section of key: value pairs");
    }

    private static ConfigurationSection toSection(Map<?, ?> map) {
        MemoryConfiguration memory = new MemoryConfiguration();
        return memory.createSection("value", map);
    }

    /** Unwraps sections into maps so codecs only see plain YAML values. */
    private static Object plain(Object raw) {
        if (raw instanceof ConfigurationSection section) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                out.put(key, plain(section.get(key)));
            }
            return out;
        }
        return raw;
    }

    private static String join(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }
}
