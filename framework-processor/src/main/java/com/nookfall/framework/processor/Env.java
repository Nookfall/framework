package com.nookfall.framework.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/** Processing helpers with error tracking. */
final class Env {

    final ProcessingEnvironment processing;
    final Types types;
    final Elements elements;
    private final Messager messager;
    private boolean failed;
    private int errors;

    Env(ProcessingEnvironment processing) {
        this.processing = processing;
        this.types = processing.getTypeUtils();
        this.elements = processing.getElementUtils();
        this.messager = processing.getMessager();
    }

    boolean failed() {
        return failed;
    }

    int errors() {
        return errors;
    }

    void error(Element element, String message) {
        failed = true;
        errors++;
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    void error(Element element, AnnotationMirror annotation, String message) {
        failed = true;
        errors++;
        messager.printMessage(Diagnostic.Kind.ERROR, message, element, annotation);
    }

    void warn(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.WARNING, message, element);
    }

    void note(String message) {
        messager.printMessage(Diagnostic.Kind.NOTE, message);
    }

    TypeElement type(String name) {
        return elements.getTypeElement(name);
    }

    /** Erasure-based subtype check; false if the named type is not on the classpath. */
    boolean isSubtype(TypeMirror type, String name) {
        TypeElement target = type(name);
        if (target == null || type == null) {
            return false;
        }
        return types.isSubtype(types.erasure(type), types.erasure(target.asType()));
    }

    boolean isType(TypeMirror type, String name) {
        TypeElement target = type(name);
        return target != null && types.isSameType(types.erasure(type), types.erasure(target.asType()));
    }

    AnnotationMirror annotation(Element element, String name) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement type = (TypeElement) mirror.getAnnotationType().asElement();
            if (type.getQualifiedName().contentEquals(name)) {
                return mirror;
            }
        }
        return null;
    }

    boolean has(Element element, String annotation) {
        return annotation(element, annotation) != null;
    }

    Object value(AnnotationMirror mirror, String attribute) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                elements.getElementValuesWithDefaults(mirror).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(attribute)) {
                return entry.getValue().getValue();
            }
        }
        return null;
    }

    String string(AnnotationMirror mirror, String attribute) {
        Object value = value(mirror, attribute);
        return value == null ? "" : value.toString();
    }

    boolean bool(AnnotationMirror mirror, String attribute) {
        return Boolean.TRUE.equals(value(mirror, attribute));
    }

    double number(AnnotationMirror mirror, String attribute) {
        Object value = value(mirror, attribute);
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    List<String> strings(AnnotationMirror mirror, String attribute) {
        List<String> out = new ArrayList<>();
        Object value = value(mirror, attribute);
        if (value instanceof List<?> list) {
            for (Object item : list) {
                out.add(((AnnotationValue) item).getValue().toString());
            }
        }
        return out;
    }

    TypeMirror classValue(AnnotationMirror mirror, String attribute) {
        Object value = value(mirror, attribute);
        return value instanceof TypeMirror t ? t : null;
    }

    String enumValue(AnnotationMirror mirror, String attribute) {
        Object value = value(mirror, attribute);
        return value instanceof javax.lang.model.element.VariableElement v
                ? v.getSimpleName().toString()
                : "";
    }

    String packageOf(Element element) {
        PackageElement pkg = elements.getPackageOf(element);
        return pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
    }

    /** Source form of a type usable in a class literal or cast. */
    String erasure(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return type.toString();
        }
        TypeMirror erased = types.erasure(type);
        if (erased.getKind() == TypeKind.DECLARED) {
            return ((TypeElement) ((DeclaredType) erased).asElement())
                    .getQualifiedName()
                    .toString();
        }
        return erased.toString();
    }

    /** Boxed source type for casting values out of an {@code Object[]}. */
    String boxed(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return types.boxedClass((javax.lang.model.type.PrimitiveType) type)
                    .getQualifiedName()
                    .toString();
        }
        return erasure(type);
    }

    boolean isAccessibleFromPackage(Element element) {
        return !element.getModifiers().contains(Modifier.PRIVATE);
    }

    /** Whether a class can be constructed by Avaje (concrete, not private, static if nested). */
    boolean checkInstantiable(TypeElement type) {
        boolean ok = true;
        if (type.getKind() != ElementKind.CLASS || type.getModifiers().contains(Modifier.ABSTRACT)) {
            error(type, type.getSimpleName() + " must be a concrete class");
            ok = false;
        }
        if (type.getModifiers().contains(Modifier.PRIVATE)) {
            error(type, type.getSimpleName() + " must not be private");
            ok = false;
        }
        if (type.getNestingKind().isNested() && !type.getModifiers().contains(Modifier.STATIC)) {
            error(type, type.getSimpleName() + " must be a top-level or static nested class");
            ok = false;
        }
        return ok;
    }

    List<ExecutableElement> methods(TypeElement type) {
        return ElementFilter.methodsIn(type.getEnclosedElements());
    }

    static String literal(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    static String listOf(List<String> values) {
        if (values.isEmpty()) {
            return "java.util.List.of()";
        }
        List<String> literals = new ArrayList<>();
        for (String value : values) {
            literals.add(literal(value));
        }
        return "java.util.List.of(" + String.join(", ", literals) + ")";
    }

    static String doubleLiteral(double value) {
        if (value == Double.NEGATIVE_INFINITY) return "java.lang.Double.NEGATIVE_INFINITY";
        if (value == Double.POSITIVE_INFINITY) return "java.lang.Double.POSITIVE_INFINITY";
        return value + "d";
    }

    /** {@code homeName} becomes {@code home_name}. */
    static String snake(String name) {
        StringBuilder out = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (!out.isEmpty()) out.append('_');
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** {@code createdHome} becomes {@code created-home}. */
    static String kebab(String name) {
        return snake(name).replace('_', '-');
    }
}
