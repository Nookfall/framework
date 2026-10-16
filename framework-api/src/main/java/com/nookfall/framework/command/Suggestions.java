package com.nookfall.framework.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a tab-completion provider. Signature: {@code List<String> name(CommandSender sender)} or {@code List<String> name(CommandSender sender, String input)}. */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Suggestions {
    String value();
}
