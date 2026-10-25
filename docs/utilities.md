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

## Inventories (`util.inventory`)

**`InventorySpace`**: `room(inventory, template)` is how many items fit, `fits(inventory, template, amount)` and `fits(inventory, Map<Material, Integer>)` tell whether items
fit in the 36 storage slots before anything is moved or paid for (stacks are topped up first, empty slots are shared
between all wanted items, never counted twice). `give(inventory, template, amount)` returns how many items did not fit, and
`giveOrDrop(player, template, amount)` drops what does not fit at the player's feet.

## Locations (`util.location`)

**`StoredLocation`**: an immutable location that refers to the world by name and does not keep the world
loaded. `StoredLocation.of(location)`, `serialize()` / `parse(text)` (`world,x,y,z,yaw,pitch`) and `resolve()`,
which returns `Optional.empty()` when the world is gone. It is also a supported configuration type.

**`Locations`**: `serialize`/`deserialize`, distance checks that are false across worlds (`within`,
`withinHorizontal`), `sameWorld`, `sameBlock`, `centered`, `world(nameOrUuid)` and `describe` for messages.

**`Cuboid`**: an axis-aligned block region: `Cuboid.between(a, b)`, `contains(location)`, `expand(n)`,
`intersects(other)` and `volume()`.

**`SafeLocations`**: `check(location)` returns a `Safety` value: `SAFE`, `NO_WORLD`, `OUT_OF_BOUNDS` (below the
minimum height, above the build limit or outside the world border), `OBSTRUCTED` (a block's collision shape would be
inside the player), `NO_GROUND` (nothing to stand on within one block, unless in water) or `HAZARD` (lava, fire,
campfires, magma, cactus, berry bushes, powder snow, wither roses, pointed dripstone, portals). It uses the real
collision shapes, so slabs, carpets, farmland and paths count as ground. `isSafe(location)` is `check(...) == SAFE`;
`findNearby(location, range)` searches up and then down the same column. Call these on the thread that owns the
region with its chunks loaded (otherwise they load synchronously).

## Text (`util.text`)

**`Numbers`**: `compact(value)` abbreviates large numbers for menus and tooltips: `9999` stays, then `10k`, `12.3k`, `200k`,
`1M`, `1.5M`, `2B`, `3T` (one decimal at most). `compact(BigDecimal, threshold)` chooses where abbreviating starts.

## Time (`util.time`)

**`Durations`**:

| Method | Example |
|---|---|
| `parse("1h30m")` | `Duration` of 90 minutes; units `d`, `h`, `m`, `s`, `ms`; plain numbers are rejected |
| `isValid(text)` | `true`/`false` |
| `toCompact(duration)` | `1h30m` |
| `toHuman(duration)` | `1h 30m`; for players, so never milliseconds: below a second is `<1s`, otherwise rounded to whole seconds |
| `toCountdown(duration)` | `05:07`, or `1:30:00` from one hour |
| `toTicks(duration, minimum)` | Server ticks, rounded up |

**`Cooldowns`**: thread-safe in-memory cooldowns keyed by UUID. `start(id, duration)`, `remaining(id)` (rounded up
to whole seconds, `Duration.ZERO` when over), `active(id)`, `reset(id)`. Expired entries are pruned as you go. Pass a
clock (`new Cooldowns(clock::millis)`) to test it.

## Players (`util.player`)

**`Players`**: `online(uuid)`, `onlineByName(name)`, `known(name)` (an offline player the server has seen,
without a web lookup), `isStillOnline(player)` for checks after asynchronous work, `namesStartingWith(prefix)` for
suggestions, `hasAny`/`hasAll` for permissions, and `numericLimit(player, "homes.limit.", 3)` which reads the
highest `homes.limit.<n>` permission.
