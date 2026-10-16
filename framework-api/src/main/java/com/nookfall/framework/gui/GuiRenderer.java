package com.nookfall.framework.gui;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Post-processes an item before display. Signature: {@code ItemStack name(GuiRenderContext, ItemStack)}. */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface GuiRenderer {
    String value();
}
