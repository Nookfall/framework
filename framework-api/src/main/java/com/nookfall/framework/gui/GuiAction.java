package com.nookfall.framework.gui;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Handles clicks on elements whose {@code action} has this name. Parameters may be {@code GuiClickContext}, {@code GuiEntry<T>}, {@code Player} and {@code ClickType}, in any order. */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface GuiAction {
    String value();
}
