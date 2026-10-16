package com.nookfall.framework.gui;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Binds Java behaviour to the GUI defined in {@code guis/<id>/layout.yml}. */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Gui {
    /** GUI id, matching the directory name under {@code guis/}. */
    String value();
}
