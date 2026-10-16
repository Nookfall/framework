package com.nookfall.examples.hello;

import com.nookfall.framework.config.Comment;
import com.nookfall.framework.config.Config;
import com.nookfall.framework.constraint.Range;

@Config("config.yml")
public class HelloConfig {

    @Comment("How many times /hello repeats the greeting.")
    @Range(min = 1, max = 5)
    private int repeat = 1;

    public int repeat() {
        return repeat;
    }
}
