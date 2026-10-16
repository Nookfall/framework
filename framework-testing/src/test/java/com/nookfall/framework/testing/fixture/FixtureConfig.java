package com.nookfall.framework.testing.fixture;

import com.nookfall.framework.config.Comment;
import com.nookfall.framework.config.Config;
import com.nookfall.framework.constraint.Range;

@Config("fixture.yml")
public class FixtureConfig {
    @Comment("Greeting shown by /fixture.")
    private String greeting = "hi";

    @Range(min = 1, max = 10)
    private int times = 2;

    public String greeting() {
        return greeting;
    }

    public int times() {
        return times;
    }
}
