package com.nookfall.framework.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/** Validates {@code @Config} classes. */
final class ConfigAnalyzer {

    private static final Set<String> SCALARS = Set.of(
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Boolean",
            "java.math.BigDecimal",
            "java.time.Duration",
            "java.util.UUID",
            "java.util.Locale",
            Names.MATERIAL,
            "com.nookfall.framework.util.location.StoredLocation",
            "org.bukkit.inventory.ItemStack",
            "java.lang.Object");
    private static final Set<String> NUMBERS = Set.of(
            "int",
            "long",
            "short",
            "byte",
            "double",
            "float",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Double",
            "java.lang.Float",
            "java.math.BigDecimal");

    private final Env env;
    private final Map<String, Element> files = new HashMap<>();

    ConfigAnalyzer(Env env) {
        this.env = env;
    }

    String analyze(TypeElement type) {
        int errorsBefore = env.errors();
        AnnotationMirror config = env.annotation(type, Names.CONFIG);
        String file = env.string(config, "value");
        if (!file.endsWith(".yml") || file.startsWith("/") || file.contains("..") || file.contains("\\")) {
            env.error(type, config, "@Config file must be a relative path ending in .yml without '..'");
        }
        Element previous = files.putIfAbsent(file, type);
        if (previous != null) {
            env.error(type, config, "Configuration file '" + file + "' is already used by " + previous.getSimpleName());
        }
        if (type.getKind() != ElementKind.CLASS || type.getModifiers().contains(Modifier.ABSTRACT)) {
            env.error(type, "@Config must be on a concrete class");
        }
        if (type.getNestingKind().isNested() && !type.getModifiers().contains(Modifier.STATIC)) {
            env.error(type, "@Config classes must be top-level or static nested classes");
        }
        checkSection(type, type, 0);
        if (env.errors() != errorsBefore) {
            return null;
        }
        String qualified = type.getQualifiedName().toString();
        return "new com.nookfall.framework.spi.ConfigSpec<" + qualified + ">(" + qualified + ".class, "
                + Env.literal(file) + ", " + Env.listOf(env.strings(config, "header")) + ", " + qualified + "::new)";
    }

    private void checkSection(TypeElement owner, TypeElement type, int depth) {
        if (depth > 8) {
            env.error(owner, "Configuration nesting is too deep (recursive section types are not supported)");
            return;
        }
        boolean hasNoArg = false;
        for (ExecutableElement constructor : ElementFilter.constructorsIn(type.getEnclosedElements())) {
            if (constructor.getParameters().isEmpty()
                    && !constructor.getModifiers().contains(Modifier.PRIVATE)) {
                hasNoArg = true;
            }
        }
        if (!hasNoArg) {
            env.error(type, type.getSimpleName() + " needs a non-private no-argument constructor");
        }
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            Set<Modifier> modifiers = field.getModifiers();
            if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.TRANSIENT)) {
                continue;
            }
            if (modifiers.contains(Modifier.FINAL)) {
                env.error(
                        field,
                        "Configuration field '" + field.getSimpleName()
                                + "' must not be final; make it static or transient to exclude it");
                continue;
            }
            if (env.annotation(field, Names.CODEC) == null) {
                checkType(owner, field, field.asType(), depth);
            }
            checkConstraints(field);
        }
        TypeMirror superclass = type.getSuperclass();
        if (superclass.getKind() == TypeKind.DECLARED && !env.isType(superclass, "java.lang.Object")) {
            checkSection(owner, (TypeElement) ((DeclaredType) superclass).asElement(), depth + 1);
        }
    }

    private void checkType(TypeElement owner, VariableElement field, TypeMirror type, int depth) {
        if (type.getKind().isPrimitive()) {
            if (type.getKind() == TypeKind.CHAR) {
                unsupported(field, type);
            }
            return;
        }
        if (type.getKind() != TypeKind.DECLARED) {
            unsupported(field, type);
            return;
        }
        String name = env.erasure(type);
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        if (SCALARS.contains(name) || element.getKind() == ElementKind.ENUM || env.has(element, Names.CODEC)) {
            return;
        }
        List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
        if (name.equals("java.util.List") || name.equals("java.util.Set") || name.equals("java.util.Collection")) {
            if (args.size() == 1) {
                checkType(owner, field, args.get(0), depth);
            }
            return;
        }
        if (name.equals("java.util.Map")) {
            if (args.size() == 2) {
                TypeMirror key = args.get(0);
                String keyName = env.erasure(key);
                boolean keyOk = keyName.equals("java.lang.String")
                        || SCALARS.contains(keyName)
                        || (key.getKind() == TypeKind.DECLARED
                                && ((DeclaredType) key).asElement().getKind() == ElementKind.ENUM);
                if (!keyOk) {
                    env.error(field, "Map keys must be strings, enums or simple values");
                }
                checkType(owner, field, args.get(1), depth);
            }
            return;
        }
        if (name.startsWith("java.")
                || name.startsWith("org.bukkit.")
                || element.getKind() != ElementKind.CLASS
                || element.getModifiers().contains(Modifier.ABSTRACT)) {
            unsupported(field, type);
            return;
        }
        // A nested section.
        if (element.getNestingKind().isNested() && !element.getModifiers().contains(Modifier.STATIC)) {
            env.error(field, "Section type " + element.getSimpleName() + " must be a static nested or top-level class");
            return;
        }
        checkSection(owner, element, depth + 1);
    }

    private void unsupported(VariableElement field, TypeMirror type) {
        env.error(
                field,
                "Unsupported configuration type " + type + " for field '" + field.getSimpleName()
                        + "'. Use simple values, enums, Duration, lists, maps, nested classes, or add @Codec");
    }

    private void checkConstraints(VariableElement field) {
        String type = env.erasure(field.asType());
        AnnotationMirror range = env.annotation(field, Names.RANGE);
        if (range != null) {
            if (!NUMBERS.contains(type)) {
                env.error(field, range, "@Range is only allowed on numeric fields");
            } else if (env.number(range, "min") > env.number(range, "max")) {
                env.error(field, range, "@Range min must not exceed max");
            }
        }
        AnnotationMirror size = env.annotation(field, Names.SIZE);
        if (size != null
                && !(type.equals("java.lang.String")
                        || type.equals("java.util.List")
                        || type.equals("java.util.Set")
                        || type.equals("java.util.Map")
                        || type.equals("java.util.Collection"))) {
            env.error(field, size, "@Size is only allowed on strings, lists, sets and maps");
        }
        for (String annotation : List.of(Names.NOT_BLANK, Names.PATTERN)) {
            AnnotationMirror mirror = env.annotation(field, annotation);
            if (mirror != null && !type.equals("java.lang.String")) {
                env.error(
                        field,
                        mirror,
                        "@" + annotation.substring(annotation.lastIndexOf('.') + 1)
                                + " is only allowed on String fields");
            }
        }
        AnnotationMirror pattern = env.annotation(field, Names.PATTERN);
        if (pattern != null) {
            try {
                java.util.regex.Pattern.compile(env.string(pattern, "value"));
            } catch (java.util.regex.PatternSyntaxException e) {
                env.error(field, pattern, "Invalid regular expression: " + e.getDescription());
            }
        }
    }
}
