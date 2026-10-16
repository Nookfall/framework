package com.nookfall.framework.gui;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A named condition for {@code visible-if}. Signature: {@code boolean name(GuiContext)} or {@code boolean name(GuiContext, GuiEntry<T>)}. */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface GuiCondition {
    String value();
}
