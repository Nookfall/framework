package com.nookfall.examples.homes;

import com.nookfall.framework.util.location.StoredLocation;
import java.util.UUID;

/** A saved home. */
public record Home(UUID owner, String name, StoredLocation location, long createdAt) {}
