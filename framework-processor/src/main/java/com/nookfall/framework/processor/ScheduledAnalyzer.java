package com.nookfall.framework.processor;

import com.nookfall.framework.util.time.Durations;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/** Validates {@code @Scheduled} methods. */
final class ScheduledAnalyzer {

    private final Env env;

    ScheduledAnalyzer(Env env) {
        this.env = env;
    }

    String analyze(ExecutableElement method) {
        int errorsBefore = env.errors();
        AnnotationMirror scheduled = env.annotation(method, Names.SCHEDULED);
        TypeElement owner = (TypeElement) method.getEnclosingElement();
        if (method.getModifiers().contains(Modifier.PRIVATE)
                || method.getModifiers().contains(Modifier.STATIC)) {
            env.error(method, "@Scheduled methods must be non-private instance methods");
        }
        if (!method.getParameters().isEmpty()) {
            env.error(method, "@Scheduled methods cannot take parameters");
        }
        if (!method.getTypeParameters().isEmpty()) {
            env.error(method, "@Scheduled methods cannot be generic");
        }
        String every = env.string(scheduled, "every").strip();
        String initial = env.string(scheduled, "initialDelay").strip();
        checkDuration(method, scheduled, "every", every, false);
        if (!initial.isEmpty()) {
            checkDuration(method, scheduled, "initialDelay", initial, true);
        }
        env.checkInstantiable(owner);
        if (env.errors() != errorsBefore) {
            return null;
        }
        String qualified = owner.getQualifiedName().toString();
        return "new com.nookfall.framework.spi.ScheduledSpec<" + qualified + ">(" + qualified + ".class, "
                + Env.literal(method.getSimpleName().toString()) + ", " + Env.literal(every) + ", "
                + Env.literal(initial)
                + ", com.nookfall.framework.task.Execution." + env.enumValue(scheduled, "execution")
                + ", target -> target." + method.getSimpleName() + "())";
    }

    private void checkDuration(
            ExecutableElement method, AnnotationMirror mirror, String attribute, String value, boolean zeroAllowed) {
        if (value.startsWith("${")) {
            if (!value.endsWith("}")
                    || value.length() < 4
                    || value.substring(2, value.length() - 1).isBlank()) {
                env.error(
                        method,
                        mirror,
                        attribute + " reference '" + value + "' must look like ${path} or ${file.yml:path}");
            }
            return;
        }
        try {
            java.time.Duration duration = Durations.parse(value);
            if (!zeroAllowed && duration.isZero()) {
                env.error(method, mirror, attribute + " must be greater than zero");
            }
        } catch (IllegalArgumentException e) {
            env.error(method, mirror, "Invalid " + attribute + " '" + value + "': " + e.getMessage());
        }
    }
}
