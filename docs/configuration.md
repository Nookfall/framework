---
title: Configuration
description: YAML configuration from plain Java classes, with comments, validation, codecs and atomic reloads.
---

A configuration is an ordinary class annotated with `@Config`. Field initializers are the defaults, private
fields work, and the file is generated with your comments the first time the plugin starts.

```java
@Config(value = "config.yml", header = "NookfallHomes configuration")
public class HomesConfig {

    @Comment({"Maximum number of homes per player.", "Players with homes.limit.<n> may have up to <n> homes."})
    @Range(min = 1, max = 100)
    private int maxHomes = 3;

    @Comment("Delay before teleporting.")
    private Duration teleportDelay = Duration.ofSeconds(3);

    @Comment("Look for a safe spot near the home before teleporting.")
    private boolean safeTeleport = true;

    @Comment("Database connection.")
    private DatabaseSettings database = new DatabaseSettings();

    private Cleanup cleanup = new Cleanup();

    public static class Cleanup {
        @Comment("How often homes in deleted worlds are removed, for example 10m or 1h.")
        private String interval = "10m";
    }

    public int maxHomes() {
        return maxHomes;
    }

    public Duration teleportDelay() {
        return teleportDelay;
    }

    public boolean safeTeleport() {
        return safeTeleport;
    }

    public DatabaseSettings database() {
        return database;
    }
}
```

The generated `config.yml`:

```yaml
# NookfallHomes configuration

# Maximum number of homes per player.
# Players with homes.limit.<n> may have up to <n> homes.
max-homes: 3
# Delay before teleporting.
teleport-delay: 3s
# Look for a safe spot near the home before teleporting.
safe-teleport: true
# Database connection.
database:
  # sqlite, mysql or mariadb
  type: SQLITE
  ...
cleanup:
  # How often homes in deleted worlds are removed, for example 10m or 1h.
  interval: 10m
```

Field names become kebab-case keys (`maxHomes` is `max-homes`); `@ConfigKey("name")` overrides that.
`static` and `transient` fields are ignored. The path in `@Config` is relative to the plugin's data folder and
may contain directories; it cannot point outside it.

## Reading values

Inject `ConfigRef<YourConfig>` and call `get()` each time you need the configuration:

```java
public JoinListener(ConfigRef<StarterConfig> config) {
    this.config = config;
}

// later
if (config.get().greetOnJoin()) { ... }
```

`get()` returns an immutable snapshot that is never modified. A reload builds a new instance, validates it and
publishes it atomically, so a reader never sees a half-loaded configuration. Do not store the result of `get()`
in a long-lived field if you want reloads to take effect. `ConfigRef.onReload(listener)` runs after each
successful reload or save.

## Supported types

| Type | YAML |
|---|---|
| `String`, `boolean`, `int`, `long`, `short`, `byte`, `double`, `float` (and boxed), `BigDecimal` | scalars |
| Enums | constant names, case-insensitive; `-` and spaces are read as `_` |
| `Duration` | `30s`, `5m`, `1h30m`, `250ms`, `1d` |
| `UUID`, `Locale` (`en_US`) | text |
| `Material` | material names |
| `StoredLocation` | `world,x,y,z,yaw,pitch` |
| `ItemStack` | a material name, an item section, or `data: <base64>` (below) |
| `List<T>`, `Set<T>`, `Collection<T>` | lists |
| `Map<K, V>` | sections; keys may be strings, enums or other scalar types |
| Nested classes | sections; the class needs a no-argument constructor |
| Types with a codec | anything the codec produces |

The processor rejects fields of unsupported types at compile time.

### Items

```yaml
reward: diamond
kit:
  - material: diamond_sword
    name: "<red>Blade"
    lore: ["<gray>Sharp"]
    enchantments: { sharpness: 3 }
    flags: [hide_attributes]
    glow: true
    custom-model-data: 7
    amount: 1
```

Items that only use these properties are written back in this readable form. Anything else (books, potions,
other data components) is written as `data: <base64>` using Paper's versioned item format, which the server
upgrades across Minecraft versions.

## Custom codecs

Implement `ConfigCodec<T>` and throw `IllegalArgumentException` with a readable message for bad input. Put
`@Codec` on the **type** to use it wherever the type appears, including inside lists and maps:

```java
@Codec(ColorCodec.class)
public record Color(int rgb) {}

public final class ColorCodec implements ConfigCodec<Color> {
    @Override
    public Color decode(Object raw) {
        String text = String.valueOf(raw);
        if (!text.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("expected #RRGGBB but got " + text);
        }
        return new Color(Integer.parseInt(text.substring(1), 16));
    }

    @Override
    public Object encode(Color value) {
        return String.format("#%06x", value.rgb());
    }
}
```

Or put `@Codec` on a single field to use a codec only there. Codec classes need a no-argument constructor.

## Validation

| Annotation | Applies to |
|---|---|
| `@Range(min, max)` | numbers (inclusive) |
| `@Size(min, max)` | string length, list and map size |
| `@NotBlank` | strings |
| `@Pattern("regex")` | strings (whole value) |

All problems in a file are collected and reported together with their paths, for example
`homes.yml: max-homes: 500 is above the maximum 100` or `palette[1]: expected #RRGGBB but got blue`.

## Loading, reloading and saving

- **First start:** the file is written with defaults and comments.
- **Missing keys** (for example after a plugin update added a field) are filled with defaults and the file is
  rewritten.
- **Unknown keys** are kept in the file and reported as warnings; they are never deleted.
- **Invalid files** at startup fail `Nookfall.start` with every problem listed. On reload, the previous snapshot
  stays active and the problems are returned in the `ReloadResult`.
- `runtime.reload()` reloads configuration, translations and GUIs; `ConfigRef.reload()` reloads one file;
  `ConfigService.reloadAll()` all configuration files.
- `ConfigRef.save(value)` encodes a value, writes it atomically (temporary file and move) and publishes it.

Writes go through Bukkit's YAML library: comments that you or the server owner add above keys are preserved,
but quoting style, blank lines and inline comments may change when the file is rewritten. The file is only
rewritten when it is new, keys were missing, or you call `save`.

## Values in other annotations

`@Scheduled(every = "${cleanup.interval}")` reads `cleanup.interval` from the plugin's configuration. With
several configuration files, name the file: `${homes.yml:cleanup.interval}`. `ConfigRef.value("cleanup.interval")`
gives the same lookup in code.
