---
title: Utilities
description: Focused helpers for items, persistent data, locations, durations and players.
---

`framework-utils` contains small, separate helper classes in `com.nookfall.framework.util.*`. They are plain
static helpers or values and do not need the runtime.

## Items (`util.item`)

**`ItemBuilder`**: Adventure names and lore, not italic unless you say so.

```java
ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
        .name(Component.text("Blade", NamedTextColor.RED))
        .lore(Component.text("Sharp", NamedTextColor.GRAY))
        .enchant(Enchantment.SHARPNESS, 3)
        .flags(ItemFlag.HIDE_ATTRIBUTES)
        .glow(true)
        .customModelData(7)
        .data(PersistentData.key(plugin, "kit"), PersistentDataType.STRING, "starter")
        .build();
```

`ItemBuilder.from(stack)` starts from a copy; `meta(consumer)` applies any other `ItemMeta` change.
`customModelData` sets the legacy integer form, which newer Paper versions still accept.

**`Items`**: `toBase64(item)` / `fromBase64(text)` use Paper's versioned binary format, so stored items are
upgraded when Minecraft changes. `fromBase64` throws `IllegalArgumentException` with a readable message for bad
data. `isEmpty(item)` treats `null`, air and zero amounts as empty.

**`Materials`**: `find(name)` and `findItem(name)` look materials up case-insensitively, accepting
`minecraft:` keys, and return `Optional`. `requireItem(name)` throws a readable error.

**`PersistentData`**: `key(plugin, name)`, `get`, `set`, `has` and `remove` on items and other
persistent-data holders, returning `Optional` for reads.

## Locations (`util.location`)

**`StoredLocation`**: an immutable location that refers to the world by name and does not keep the world
loaded. `StoredLocation.of(location)`, `serialize()` / `parse(text)` (`world,x,y,z,yaw,pitch`) and `resolve()`,
which returns `Optional.empty()` when the world is gone. It is also a supported configuration type.

**`Locations`**: `serialize`/`deserialize`, distance checks that are false across worlds (`within`,
`withinHorizontal`), `sameWorld`, `sameBlock`, `centered`, `world(nameOrUuid)` and `describe` for messages.

**`Cuboid`**: an axis-aligned block region: `Cuboid.between(a, b)`, `contains(location)`, `expand(n)`,
`intersects(other)` and `volume()`.

**`SafeLocations`**: `isSafe(location)` (solid ground, two free blocks, no hazards) and
`findNearby(location, range)`, which searches up and then down the same block column for the nearest safe
spot. Call these on the thread that owns the region.

## Time (`util.time`)

**`Durations`**:

| Method | Example |
|---|---|
| `parse("1h30m")` | `Duration` of 90 minutes; units `d`, `h`, `m`, `s`, `ms`; plain numbers are rejected |
| `isValid(text)` | `true`/`false` |
| `toCompact(duration)` | `1h30m` |
| `toHuman(duration)` | `1h 30m` |
| `toCountdown(duration)` | `05:07`, or `1:30:00` from one hour |
| `toTicks(duration, minimum)` | Server ticks, rounded up |

## Players (`util.player`)

**`Players`**: `online(uuid)`, `onlineByName(name)`, `known(name)` (an offline player the server has seen,
without a web lookup), `isStillOnline(player)` for checks after asynchronous work, `namesStartingWith(prefix)` for
suggestions, `hasAny`/`hasAll` for permissions, and `numericLimit(player, "homes.limit.", 3)` which reads the
highest `homes.limit.<n>` permission.
