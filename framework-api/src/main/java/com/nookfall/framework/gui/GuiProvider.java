package com.nookfall.framework.gui;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Supplies entries for a {@code collection} element. Signature: {@code List<GuiEntry<T>> name(GuiContext)} or {@code CompletableFuture<List<GuiEntry<T>>> name(GuiContext)} for asynchronous loading. */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface GuiProvider {
    String value();
}
