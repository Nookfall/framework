package com.nookfall.framework.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Validates {@code @Gui} classes and produces their generated bindings. */
final class GuiAnalyzer {

    static final Set<String> BUILTIN_ACTIONS = Set.of("close", "next_page", "previous_page", "refresh", "open");
    static final Set<String> BUILTIN_CONDITIONS = Set.of("has_next_page", "has_previous_page", "permission", "loading");
    private static final String NAME = "[a-z0-9_]+";

    /** Handler names declared by a GUI class, used to validate its layout file. */
    record Declared(Set<String> providers, Set<String> actions, Set<String> conditions, Set<String> renderers) {}

    private final Env env;
    private final Map<String, Element> ids = new HashMap<>();
    final Map<String, Declared> declared = new LinkedHashMap<>();
    final Map<String, TypeElement> types = new LinkedHashMap<>();

    GuiAnalyzer(Env env) {
        this.env = env;
    }

    String analyze(TypeElement type) {
        int errorsBefore = env.errors();
        AnnotationMirror gui = env.annotation(type, Names.GUI);
        env.checkInstantiable(type);
        String id = env.string(gui, "value");
        if (!id.matches("[a-z0-9_-]+")) {
            env.error(type, gui, "GUI id '" + id + "' must be lower case letters, digits, '-' or '_'");
        }
        Element previous = ids.putIfAbsent(id, type);
        if (previous != null) {
            env.error(type, gui, "Duplicate GUI id '" + id + "'; already declared by " + previous.getSimpleName());
        }
        String qualified = type.getQualifiedName().toString();
        Map<String, String> providers = new LinkedHashMap<>();
        Map<String, String> actions = new LinkedHashMap<>();
        Map<String, String> conditions = new LinkedHashMap<>();
        Map<String, String> placeholders = new LinkedHashMap<>();
        Map<String, String> renderers = new LinkedHashMap<>();
        for (ExecutableElement method : env.methods(type)) {
            provider(qualified, method, providers);
            action(qualified, method, actions);
            condition(qualified, method, conditions);
            placeholder(qualified, method, placeholders);
            renderer(qualified, method, renderers);
        }
        declared.put(id, new Declared(providers.keySet(), actions.keySet(), conditions.keySet(), renderers.keySet()));
        types.put(id, type);
        if (env.errors() != errorsBefore) {
            return null;
        }
        return "new com.nookfall.framework.spi.GuiBinding<" + qualified + ">(" + Env.literal(id) + ", " + qualified
                + ".class,\n                " + map(providers) + ",\n                " + map(actions)
                + ",\n                " + map(conditions) + ",\n                " + map(placeholders)
                + ",\n                " + map(renderers) + ")";
    }

    private static String map(Map<String, String> entries) {
        if (entries.isEmpty()) {
            return "java.util.Map.of()";
        }
        List<String> parts = new ArrayList<>();
        entries.forEach((key, value) -> parts.add("java.util.Map.entry(" + Env.literal(key) + ", " + value + ")"));
        return "java.util.Map.ofEntries(" + String.join(", ", parts) + ")";
    }

    private String name(ExecutableElement method, AnnotationMirror mirror, Map<String, String> existing, String kind) {
        String name = env.string(mirror, "value");
        if (!name.matches(NAME)) {
            env.error(method, mirror, kind + " name '" + name + "' must be lower case letters, digits or '_'");
            return null;
        }
        if (existing.containsKey(name)) {
            env.error(method, mirror, "Duplicate " + kind + " identifier '" + name + "'");
            return null;
        }
        if (method.getModifiers().contains(Modifier.PRIVATE)
                || method.getModifiers().contains(Modifier.STATIC)) {
            env.error(method, kind + " methods must be non-private instance methods");
            return null;
        }
        return name;
    }

    private void provider(String qualified, ExecutableElement method, Map<String, String> out) {
        AnnotationMirror mirror = env.annotation(method, Names.GUI_PROVIDER);
        if (mirror == null) return;
        String name = name(method, mirror, out, "GUI provider");
        if (name == null) return;
        List<? extends VariableElement> params = method.getParameters();
        boolean paramsOk = params.size() == 1 && env.isType(params.get(0).asType(), Names.GUI_CONTEXT);
        TypeMirror returns = method.getReturnType();
        boolean async = env.isType(returns, "java.util.concurrent.CompletableFuture");
        TypeMirror list = async ? typeArgument(returns) : returns;
        boolean returnOk = list != null && env.isType(list, "java.util.List") && isEntryType(typeArgument(list));
        if (!paramsOk || !returnOk) {
            env.error(
                    method,
                    "@GuiProvider methods must have signature List<GuiEntry<T>> name(GuiContext) or "
                            + "CompletableFuture<List<GuiEntry<T>>> name(GuiContext)");
            return;
        }
        out.put(
                name,
                "new com.nookfall.framework.spi.GuiBinding.Provider<" + qualified + ">(" + async
                        + ", (gui, context) -> gui." + method.getSimpleName() + "(context))");
    }

    private boolean isEntryType(TypeMirror type) {
        if (type == null) return false;
        if (type.getKind() == TypeKind.WILDCARD) {
            TypeMirror bound = ((javax.lang.model.type.WildcardType) type).getExtendsBound();
            return bound != null && env.isType(bound, Names.GUI_ENTRY);
        }
        return env.isType(type, Names.GUI_ENTRY);
    }

    private static TypeMirror typeArgument(TypeMirror type) {
        if (type instanceof DeclaredType declared && declared.getTypeArguments().size() == 1) {
            return declared.getTypeArguments().get(0);
        }
        return null;
    }

    private void action(String qualified, ExecutableElement method, Map<String, String> out) {
        AnnotationMirror mirror = env.annotation(method, Names.GUI_ACTION);
        if (mirror == null) return;
        String name = name(method, mirror, out, "GUI action");
        if (name == null) return;
        if (BUILTIN_ACTIONS.contains(name)) {
            env.error(method, mirror, "GUI action '" + name + "' conflicts with a built-in action");
            return;
        }
        List<String> args = new ArrayList<>();
        boolean needsEntry = false;
        Set<String> seen = new java.util.HashSet<>();
        for (VariableElement param : method.getParameters()) {
            TypeMirror type = param.asType();
            String arg;
            String kind;
            if (env.isType(type, Names.GUI_CLICK_CONTEXT) || env.isType(type, Names.GUI_CONTEXT)) {
                arg = "context";
                kind = "context";
            } else if (env.isType(type, Names.GUI_ENTRY)) {
                arg = "(" + Names.GUI_ENTRY + ") entry";
                kind = "entry";
                needsEntry = true;
            } else if (env.isType(type, Names.PLAYER)) {
                arg = "context.player()";
                kind = "player";
            } else if (env.isType(type, Names.CLICK_TYPE)) {
                arg = "context.clickType()";
                kind = "click";
            } else {
                env.error(
                        param,
                        "Unsupported @GuiAction parameter type " + env.erasure(type)
                                + ". Use GuiClickContext, GuiEntry<T>, Player or ClickType");
                return;
            }
            if (!seen.add(kind)) {
                env.error(param, "Duplicate " + kind + " parameter in @GuiAction method");
                return;
            }
            args.add(arg);
        }
        out.put(
                name,
                "new com.nookfall.framework.spi.GuiBinding.Action<" + qualified + ">(" + needsEntry
                        + ", (gui, context, entry) -> gui." + method.getSimpleName() + "(" + String.join(", ", args)
                        + "))");
    }

    private void condition(String qualified, ExecutableElement method, Map<String, String> out) {
        AnnotationMirror mirror = env.annotation(method, Names.GUI_CONDITION);
        if (mirror == null) return;
        String name = name(method, mirror, out, "GUI condition");
        if (name == null) return;
        if (BUILTIN_CONDITIONS.contains(name)) {
            env.error(method, mirror, "GUI condition '" + name + "' conflicts with a built-in condition");
            return;
        }
        List<? extends VariableElement> params = method.getParameters();
        boolean ok = method.getReturnType().getKind() == TypeKind.BOOLEAN
                && (params.size() == 1 || params.size() == 2)
                && env.isType(params.get(0).asType(), Names.GUI_CONTEXT)
                && (params.size() == 1 || env.isType(params.get(1).asType(), Names.GUI_ENTRY));
        if (!ok) {
            env.error(
                    method,
                    "@GuiCondition methods must have signature boolean name(GuiContext) or "
                            + "boolean name(GuiContext, GuiEntry<T>)");
            return;
        }
        String call = params.size() == 2
                ? "gui." + method.getSimpleName() + "(context, (" + Names.GUI_ENTRY + ") entry)"
                : "gui." + method.getSimpleName() + "(context)";
        out.put(
                name,
                "(com.nookfall.framework.spi.GuiBinding.Condition<" + qualified + ">) (gui, context, entry) -> "
                        + call);
    }

    private void placeholder(String qualified, ExecutableElement method, Map<String, String> out) {
        AnnotationMirror mirror = env.annotation(method, Names.GUI_PLACEHOLDER);
        if (mirror == null) return;
        String name = name(method, mirror, out, "GUI placeholder");
        if (name == null) return;
        List<? extends VariableElement> params = method.getParameters();
        boolean ok = method.getReturnType().getKind() != TypeKind.VOID
                && params.size() == 1
                && (env.isType(params.get(0).asType(), Names.GUI_RENDER_CONTEXT)
                        || env.isType(params.get(0).asType(), Names.GUI_CONTEXT));
        if (!ok) {
            env.error(method, "@GuiPlaceholder methods must have signature Object name(GuiRenderContext)");
            return;
        }
        out.put(
                name,
                "(com.nookfall.framework.spi.GuiBinding.Placeholder<" + qualified + ">) (gui, context) -> gui."
                        + method.getSimpleName() + "(context)");
    }

    private void renderer(String qualified, ExecutableElement method, Map<String, String> out) {
        AnnotationMirror mirror = env.annotation(method, Names.GUI_RENDERER);
        if (mirror == null) return;
        String name = name(method, mirror, out, "GUI renderer");
        if (name == null) return;
        List<? extends VariableElement> params = method.getParameters();
        boolean ok = env.isType(method.getReturnType(), Names.ITEM_STACK)
                && params.size() == 2
                && (env.isType(params.get(0).asType(), Names.GUI_RENDER_CONTEXT)
                        || env.isType(params.get(0).asType(), Names.GUI_CONTEXT))
                && env.isType(params.get(1).asType(), Names.ITEM_STACK);
        if (!ok) {
            env.error(method, "@GuiRenderer methods must have signature ItemStack name(GuiRenderContext, ItemStack)");
            return;
        }
        out.put(
                name,
                "(com.nookfall.framework.spi.GuiBinding.Renderer<" + qualified + ">) (gui, context, item) -> gui."
                        + method.getSimpleName() + "(context, item)");
    }
}
