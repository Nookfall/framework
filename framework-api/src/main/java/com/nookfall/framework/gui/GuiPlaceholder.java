package com.nookfall.framework.gui;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A named placeholder available as {@code <name>} in titles, names and lore. Signature: {@code Object name(GuiRenderContext)}; components are inserted as-is, anything else as plain text. */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface GuiPlaceholder {
    String value();
}
