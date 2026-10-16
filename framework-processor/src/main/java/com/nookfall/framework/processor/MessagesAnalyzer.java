package com.nookfall.framework.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Validates {@code @Messages} interfaces and generates their implementations. */
final class MessagesAnalyzer {

    /** A generated implementation and the keys it uses (for checking bundled translations). */
    record Result(String simpleName, String source, Map<String, ExecutableElement> keys) {}

    private final Env env;

    MessagesAnalyzer(Env env) {
        this.env = env;
    }

    Result analyze(TypeElement type) {
        int errorsBefore = env.errors();
        AnnotationMirror messages = env.annotation(type, Names.MESSAGES);
        if (type.getKind() != ElementKind.INTERFACE) {
            env.error(type, "@Messages must be placed on an interface");
            return null;
        }
        if (!type.getTypeParameters().isEmpty()) {
            env.error(type, "@Messages interfaces cannot be generic");
        }
        String prefix = env.string(messages, "value");
        String qualified = type.getQualifiedName().toString();
        String simpleName = type.getSimpleName() + "_NookfallMessages";
        Map<String, ExecutableElement> keys = new LinkedHashMap<>();
        StringBuilder methods = new StringBuilder();
        for (ExecutableElement method : env.methods(type)) {
            if (method.getModifiers().contains(Modifier.STATIC)
                    || method.isDefault()
                    || method.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            AnnotationMirror keyMirror = env.annotation(method, Names.KEY);
            String relative = keyMirror != null
                    ? env.string(keyMirror, "value")
                    : Env.kebab(method.getSimpleName().toString());
            String key = prefix.isEmpty() ? relative : prefix + "." + relative;
            if (!key.matches("[A-Za-z0-9_.-]+")) {
                env.error(method, "Invalid translation key '" + key + "'");
                continue;
            }
            keys.putIfAbsent(key, method);
            String body = method(method, key);
            if (body != null) {
                methods.append(body);
            }
        }
        if (env.errors() != errorsBefore) {
            return null;
        }
        String pkg = env.packageOf(type);
        String source = (pkg.isEmpty() ? "" : "package " + pkg + ";\n\n")
                + "@com.nookfall.framework.spi.NookfallGenerated\n"
                + "@jakarta.inject.Singleton\n"
                + "public final class " + simpleName + " implements " + qualified + " {\n\n"
                + "    private final com.nookfall.framework.message.Localization localization;\n\n"
                + "    public " + simpleName + "(com.nookfall.framework.message.Localization localization) {\n"
                + "        this.localization = localization;\n"
                + "    }\n" + methods + "}\n";
        return new Result(simpleName, source, keys);
    }

    private String method(ExecutableElement method, String key) {
        List<? extends VariableElement> params = method.getParameters();
        TypeMirror returns = method.getReturnType();
        String kind;
        if (returns.getKind() == TypeKind.VOID) {
            kind = "send";
        } else if (env.isType(returns, Names.COMPONENT)) {
            kind = "message";
        } else if (env.isType(returns, "java.util.List")
                && returns instanceof DeclaredType d
                && d.getTypeArguments().size() == 1
                && env.isType(d.getTypeArguments().get(0), Names.COMPONENT)) {
            kind = "lines";
        } else {
            env.error(method, "@Messages methods must return void (send), Component or List<Component>");
            return null;
        }
        int start = 0;
        String target = null;
        if (!params.isEmpty()) {
            TypeMirror first = params.get(0).asType();
            if (env.isSubtype(first, Names.AUDIENCE) && env.annotation(params.get(0), Names.PARAM) == null) {
                target = params.get(0).getSimpleName().toString();
                start = 1;
            } else if (env.isType(first, "java.util.Locale")) {
                target = params.get(0).getSimpleName().toString();
                start = 1;
                if (kind.equals("send")) {
                    env.error(
                            params.get(0),
                            "A message that is sent needs an audience (for example Player) as its "
                                    + "first parameter, not a Locale");
                    return null;
                }
            }
        }
        if (kind.equals("send") && target == null) {
            env.error(
                    method,
                    "A void @Messages method needs the recipient (CommandSender, Player or Audience) as "
                            + "its first parameter");
            return null;
        }
        if (kind.equals("lines") && (target == null || !env.isType(params.get(0).asType(), "java.util.Locale"))) {
            if (target == null) {
                env.error(method, "Methods returning List<Component> need a Locale or audience first parameter");
                return null;
            }
        }
        List<String> args = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = start; i < params.size(); i++) {
            VariableElement param = params.get(i);
            AnnotationMirror paramMirror = env.annotation(param, Names.PARAM);
            String name = paramMirror != null
                    ? env.string(paramMirror, "value")
                    : Env.snake(param.getSimpleName().toString());
            if (!name.matches("[a-z0-9_-]+")) {
                env.error(param, "Placeholder name '" + name + "' must match [a-z0-9_-]+");
                continue;
            }
            if (!names.add(name)) {
                env.error(param, "Duplicate placeholder name '" + name + "'");
                continue;
            }
            if (param.asType().getKind().isPrimitive()) {
                args.add("com.nookfall.framework.message.Arg.of(" + Env.literal(name) + ", " + env.boxed(param.asType())
                        + ".valueOf(" + param.getSimpleName() + "))");
            } else {
                args.add("com.nookfall.framework.message.Arg.of(" + Env.literal(name) + ", "
                        + "java.util.Objects.requireNonNull(" + param.getSimpleName() + ", " + Env.literal(name)
                        + "))");
            }
        }
        StringBuilder signature = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) signature.append(", ");
            signature
                    .append(params.get(i).asType())
                    .append(' ')
                    .append(params.get(i).getSimpleName());
        }
        String argList = args.isEmpty() ? "" : ", " + String.join(", ", args);
        String audienceOrLocale = target == null ? "localization.defaultLocale()" : target;
        boolean isLocale = target != null && env.isType(params.get(0).asType(), "java.util.Locale");
        String localeExpr = isLocale ? target : "localization.locale(" + audienceOrLocale + ")";
        if (target == null) {
            localeExpr = "localization.defaultLocale()";
        }
        String call = switch (kind) {
            case "send" -> "localization.send(" + target + ", " + Env.literal(key) + argList + ");";
            case "message" -> "return localization.message(" + localeExpr + ", " + Env.literal(key) + argList + ");";
            default -> "return localization.lines(" + localeExpr + ", " + Env.literal(key) + argList + ");";
        };
        return "\n    @Override\n    public " + returns + " " + method.getSimpleName() + "(" + signature
                + ") {\n        " + call + "\n    }\n";
    }
}
