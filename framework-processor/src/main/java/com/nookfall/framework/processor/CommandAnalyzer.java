package com.nookfall.framework.processor;

import com.nookfall.framework.util.time.Durations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

/** Validates {@code @Command} classes and produces their generated specs. */
final class CommandAnalyzer {

    private static final String LABEL = "[a-z0-9_-]+";
    private static final Set<String> NATIVE = Set.of(
            "java.lang.String",
            "int",
            "java.lang.Integer",
            "long",
            "java.lang.Long",
            "double",
            "java.lang.Double",
            "float",
            "java.lang.Float",
            "boolean",
            "java.lang.Boolean",
            Names.PLAYER,
            Names.WORLD,
            "java.util.UUID",
            Names.OFFLINE_PLAYER,
            "java.time.Duration",
            Names.MATERIAL);

    private final Env env;
    private final Set<String> resolverTypes;
    private final Map<String, Element> labels = new HashMap<>();

    CommandAnalyzer(Env env, Set<String> resolverTypes) {
        this.env = env;
        this.resolverTypes = resolverTypes;
    }

    /** Returns the spec expression, or null if the class is invalid. */
    String analyze(TypeElement type) {
        AnnotationMirror command = env.annotation(type, Names.COMMAND);
        int errorsBefore = env.errors();
        env.checkInstantiable(type);
        String name = env.string(command, "value");
        if (!name.matches(LABEL)) {
            env.error(type, command, "Command name '" + name + "' must be lower case letters, digits, '-' or '_'");
        }
        claim(name, type, command, "command");
        List<String> aliases = new ArrayList<>();
        AnnotationMirror aliasMirror = env.annotation(type, Names.ALIASES);
        if (aliasMirror != null) {
            for (String alias : env.strings(aliasMirror, "value")) {
                if (!alias.matches(LABEL)) {
                    env.error(
                            type, aliasMirror, "Alias '" + alias + "' must be lower case letters, digits, '-' or '_'");
                }
                claim(alias, type, aliasMirror, "alias");
                aliases.add(alias);
            }
        }
        String permission = permission(type);
        String qualified = type.getQualifiedName().toString();

        Map<String, ExecutableElement> suggesters = new LinkedHashMap<>();
        for (ExecutableElement method : env.methods(type)) {
            AnnotationMirror suggestions = env.annotation(method, Names.SUGGESTIONS);
            if (suggestions != null) {
                String key = env.string(suggestions, "value");
                checkSuggester(method);
                if (suggesters.put(key, method) != null) {
                    env.error(
                            method, suggestions, "Duplicate @Suggestions(\"" + key + "\") in " + type.getSimpleName());
                }
            }
        }

        List<String> handlers = new ArrayList<>();
        Map<String, ExecutableElement> signatures = new HashMap<>();
        boolean hasExecute = false;
        for (ExecutableElement method : env.methods(type)) {
            AnnotationMirror execute = env.annotation(method, Names.EXECUTE);
            AnnotationMirror sub = env.annotation(method, Names.SUBCOMMAND);
            if (execute == null && sub == null) {
                continue;
            }
            if (execute != null && sub != null) {
                env.error(method, "A method cannot have both @Execute and @Subcommand");
                continue;
            }
            List<String> path = new ArrayList<>();
            String description = "";
            if (execute != null) {
                if (hasExecute) {
                    env.error(method, "Only one @Execute method is allowed per command");
                }
                hasExecute = true;
            } else {
                String value = env.string(sub, "value").strip();
                description = env.string(sub, "description");
                for (String literal : value.split("\\s+")) {
                    if (!literal.matches(LABEL)) {
                        env.error(
                                method, sub, "Subcommand '" + value + "' must be lower case words separated by spaces");
                    }
                    path.add(literal);
                }
            }
            String handler = handler(type, method, path, description, suggesters, signatures);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        if (handlers.isEmpty() && env.errors() == errorsBefore) {
            env.error(type, "Command class " + type.getSimpleName() + " has no @Execute or @Subcommand methods");
        }
        if (env.errors() != errorsBefore) {
            return null;
        }
        StringBuilder suggesterMap = new StringBuilder("java.util.Map.ofEntries(");
        boolean first = true;
        for (Map.Entry<String, ExecutableElement> entry : suggesters.entrySet()) {
            if (!first) suggesterMap.append(", ");
            first = false;
            ExecutableElement method = entry.getValue();
            String call = method.getParameters().size() == 2
                    ? "target." + method.getSimpleName() + "(sender, input)"
                    : "target." + method.getSimpleName() + "(sender)";
            suggesterMap
                    .append("java.util.Map.entry(")
                    .append(Env.literal(entry.getKey()))
                    .append(", (com.nookfall.framework.spi.CommandSpec.Suggester<")
                    .append(qualified)
                    .append(">) (target, sender, input) -> java.util.List.copyOf(")
                    .append(call)
                    .append("))");
        }
        suggesterMap.append(")");
        return "new com.nookfall.framework.spi.CommandSpec<" + qualified + ">(" + qualified + ".class, "
                + Env.literal(name) + ", " + Env.listOf(aliases) + ", " + Env.literal(permission) + ", "
                + Env.literal(env.string(command, "description")) + ",\n                java.util.List.of(\n"
                + String.join(",\n", handlers) + "),\n                " + suggesterMap + ")";
    }

    private void claim(String label, Element element, AnnotationMirror mirror, String what) {
        Element previous = labels.putIfAbsent(label, element);
        if (previous != null) {
            env.error(
                    element,
                    mirror,
                    "Duplicate command name '" + label + "' (" + what + "); already declared by "
                            + previous.getSimpleName());
        }
    }

    private String permission(Element element) {
        AnnotationMirror mirror = env.annotation(element, Names.PERMISSION);
        if (mirror == null) {
            return null;
        }
        String value = env.string(mirror, "value");
        if (value.isBlank() || value.contains(" ")) {
            env.error(element, mirror, "Permission must be a non-empty node without spaces");
        }
        return value;
    }

    private void checkSuggester(ExecutableElement method) {
        List<? extends VariableElement> params = method.getParameters();
        boolean ok = !method.getModifiers().contains(Modifier.PRIVATE)
                && !method.getModifiers().contains(Modifier.STATIC)
                && (params.size() == 1 || params.size() == 2)
                && env.isType(params.get(0).asType(), Names.COMMAND_SENDER)
                && (params.size() == 1 || env.isType(params.get(1).asType(), "java.lang.String"))
                && env.isSubtype(method.getReturnType(), "java.util.Collection");
        if (!ok) {
            env.error(
                    method,
                    "@Suggestions methods must be non-private instance methods with signature "
                            + "List<String> name(CommandSender) or List<String> name(CommandSender, String)");
        }
    }

    private String handler(
            TypeElement type,
            ExecutableElement method,
            List<String> path,
            String description,
            Map<String, ExecutableElement> suggesters,
            Map<String, ExecutableElement> signatures) {
        if (method.getModifiers().contains(Modifier.PRIVATE)
                || method.getModifiers().contains(Modifier.STATIC)) {
            env.error(method, "Command handlers must be non-private instance methods");
            return null;
        }
        List<? extends VariableElement> params = method.getParameters();
        String sender = "ANY";
        int start = 0;
        if (!params.isEmpty() && env.annotation(params.get(0), Names.ARG) == null) {
            TypeMirror first = params.get(0).asType();
            if (env.isType(first, Names.PLAYER)) {
                sender = "PLAYER";
                start = 1;
            } else if (env.isType(first, Names.CONSOLE_SENDER)) {
                sender = "CONSOLE";
                start = 1;
            } else if (env.isType(first, Names.COMMAND_SENDER)) {
                start = 1;
            }
        }
        List<String> paramSpecs = new ArrayList<>();
        List<String> callArgs = new ArrayList<>();
        if (start == 1) {
            callArgs.add(
                    sender.equals("ANY")
                            ? "sender"
                            : "(" + env.erasure(params.get(0).asType()) + ") sender");
        }
        boolean sawOptional = false;
        Set<String> argNames = new HashSet<>();
        StringBuilder signature = new StringBuilder(String.join(" ", path));
        List<String> executes = new ArrayList<>();
        for (int i = start; i < params.size(); i++) {
            VariableElement param = params.get(i);
            AnnotationMirror arg = env.annotation(param, Names.ARG);
            if (arg == null) {
                env.error(
                        param,
                        "Parameter '" + param.getSimpleName() + "' needs @Arg; only the first parameter may be "
                                + "the sender (CommandSender, Player or ConsoleCommandSender)");
                return null;
            }
            String argName = env.string(arg, "value");
            if (!argName.matches("[a-zA-Z0-9_-]+")) {
                env.error(param, arg, "Argument name '" + argName + "' may only use letters, digits, '-' and '_'");
            }
            if (!argNames.add(argName)) {
                env.error(param, arg, "Duplicate argument name '" + argName + "'");
            }
            TypeMirror type0 = param.asType();
            String typeName = env.erasure(type0);
            boolean isEnum = type0.getKind() == TypeKind.DECLARED
                    && ((DeclaredType) type0).asElement().getKind() == ElementKind.ENUM;
            if (!NATIVE.contains(typeName) && !isEnum && !resolverTypes.contains(typeName)) {
                env.error(
                        param,
                        "Unsupported command argument type " + typeName + ". Supported: String, numbers, "
                                + "boolean, Player, OfflinePlayer, World, UUID, Duration, Material, enums, or a type with an "
                                + "ArgumentResolver annotated with @CommandArgument");
                return null;
            }
            AnnotationMirror defaultMirror = env.annotation(param, Names.DEFAULT);
            String defaultValue = defaultMirror == null ? null : env.string(defaultMirror, "value");
            boolean optional = env.bool(arg, "optional") || defaultValue != null;
            if (optional && defaultValue == null && type0.getKind().isPrimitive()) {
                env.error(param, "Optional primitive argument '" + argName + "' needs @Default or a boxed type");
            }
            if (!optional && sawOptional) {
                env.error(param, "Required argument '" + argName + "' cannot follow an optional argument");
            }
            if (optional) {
                executes.add(signature.toString());
                sawOptional = true;
            }
            if (defaultValue != null) {
                checkDefault(
                        param,
                        defaultMirror,
                        typeName,
                        isEnum ? (TypeElement) ((DeclaredType) type0).asElement() : null,
                        defaultValue);
            }
            boolean greedy = env.has(param, Names.GREEDY);
            if (greedy
                    && (i != params.size() - 1
                            || !(typeName.equals("java.lang.String") || resolverTypes.contains(typeName)))) {
                env.error(
                        param,
                        "@Greedy is only allowed on the last argument, of type String or a custom resolver type");
            }
            double min = Double.NEGATIVE_INFINITY;
            double max = Double.POSITIVE_INFINITY;
            AnnotationMirror range = env.annotation(param, Names.RANGE);
            if (range != null) {
                if (!Set.of(
                                "int",
                                "java.lang.Integer",
                                "long",
                                "java.lang.Long",
                                "double",
                                "java.lang.Double",
                                "float",
                                "java.lang.Float")
                        .contains(typeName)) {
                    env.error(param, range, "@Range is only allowed on numeric arguments");
                }
                min = env.number(range, "min");
                max = env.number(range, "max");
                if (min > max) {
                    env.error(param, range, "@Range min must not exceed max");
                }
            }
            String suggestions = env.string(arg, "suggestions");
            if (!suggestions.isEmpty() && !suggesters.containsKey(suggestions)) {
                env.error(param, arg, "No @Suggestions(\"" + suggestions + "\") method in " + type.getSimpleName());
            }
            signature.append(" <").append(typeName).append('>');
            paramSpecs.add("new com.nookfall.framework.spi.CommandSpec.Param(" + Env.literal(argName) + ", " + typeName
                    + ".class, " + optional + ", " + Env.literal(defaultValue) + ", " + greedy + ", "
                    + Env.doubleLiteral(min) + ", " + Env.doubleLiteral(max) + ", "
                    + Env.literal(suggestions.isEmpty() ? null : suggestions) + ")");
            callArgs.add("(" + env.boxed(type0) + ") args[" + (i - start) + "]");
        }
        executes.add(signature.toString());
        for (String execute : executes) {
            ExecutableElement previous = signatures.putIfAbsent(execute, method);
            if (previous != null) {
                env.error(
                        method,
                        "Command input '" + execute.strip() + "' is handled by both " + previous.getSimpleName()
                                + " and " + method.getSimpleName());
            }
        }
        String cooldown = null;
        String bypass = null;
        AnnotationMirror cooldownMirror = env.annotation(method, Names.COOLDOWN);
        if (cooldownMirror != null) {
            cooldown = env.string(cooldownMirror, "value");
            if (!Durations.isValid(cooldown)) {
                env.error(method, cooldownMirror, "Invalid cooldown '" + cooldown + "'. Use forms like 5s or 1m30s");
            }
            bypass = env.string(cooldownMirror, "bypassPermission");
            if (bypass.isEmpty()) {
                bypass = null;
            }
        }
        String qualified = type.getQualifiedName().toString();
        return "                    new com.nookfall.framework.spi.CommandSpec.Handler<" + qualified + ">("
                + Env.literal(method.getSimpleName().toString()) + ", " + Env.listOf(path) + ", "
                + Env.literal(permission(method)) + ", " + Env.literal(description)
                + ", com.nookfall.framework.spi.CommandSpec.SenderKind." + sender
                + ",\n                        java.util.List.of("
                + String.join(", ", paramSpecs) + "),\n                        " + Env.literal(cooldown) + ", "
                + Env.literal(bypass) + ",\n                        (target, sender, args) -> target."
                + method.getSimpleName() + "(" + String.join(", ", callArgs) + "))";
    }

    private void checkDefault(
            VariableElement param, AnnotationMirror mirror, String typeName, TypeElement enumType, String value) {
        try {
            switch (typeName) {
                case "int", "java.lang.Integer" -> Integer.parseInt(value);
                case "long", "java.lang.Long" -> Long.parseLong(value);
                case "double", "java.lang.Double" -> Double.parseDouble(value);
                case "float", "java.lang.Float" -> Float.parseFloat(value);
                case "boolean", "java.lang.Boolean" -> {
                    if (!value.equals("true") && !value.equals("false")) {
                        throw new IllegalArgumentException();
                    }
                }
                case "java.util.UUID" -> java.util.UUID.fromString(value);
                case "java.time.Duration" -> Durations.parse(value);
                case Names.PLAYER -> throw new IllegalArgumentException("players cannot have a default value");
                default -> {
                    if (enumType != null) {
                        boolean found = enumType.getEnclosedElements().stream()
                                .anyMatch(e -> e.getKind() == ElementKind.ENUM_CONSTANT
                                        && e.getSimpleName().toString().equalsIgnoreCase(value.replace('-', '_')));
                        if (!found) {
                            throw new IllegalArgumentException("not a constant of " + enumType.getSimpleName());
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            env.error(
                    param,
                    mirror,
                    "Invalid @Default value '" + value + "' for " + typeName
                            + (e.getMessage() != null ? ": " + e.getMessage() : ""));
        }
    }
}
