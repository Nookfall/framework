---
title: Localization
description: Per-player translations with MiniMessage, safe placeholders, a fallback chain, atomic reloads and generated typed message interfaces.
---

Translations live in `src/main/resources/lang/<locale>.yml`, for example `en_US.yml`, `pl_PL.yml`,
`de_DE.yml`. Text uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html):

```yaml
prefix: "<aqua>Starter <dark_gray>»</dark_gray> "
starter:
  welcome: "<prefix><gray>Welcome! This is visit number <white><visits></white>."
  visitors: "<prefix><gray>Recent visitors: <white><names>"
  reloaded: "<prefix><green>Reloaded."
  reload-failed: "<prefix><red>Reload failed, previous settings kept: <reason>"
```

`<prefix>` is special: it inserts the top-level `prefix` key of the same language.

## Typed message interfaces

Declare an interface; the processor generates an injectable implementation.

```java
@Messages("starter")
public interface StarterMessages {
    void welcome(Player player, int visits);

    void visitors(CommandSender sender, String names);

    void reloaded(CommandSender sender);

    void reloadFailed(CommandSender sender, String reason);
}
```

- The key is the prefix plus the method name in kebab case: `reloadFailed` uses `starter.reload-failed`.
  `@Key("...")` overrides it.
- Parameter names become placeholders in snake case: `homeName` is `<home_name>`. `@Param("...")` overrides it.
- Return types:
  - `void`: the first parameter is the recipient (`CommandSender`, `Player` or any `Audience`); the message is
    sent in the recipient's locale.
  - `Component`: the first parameter is a recipient or a `Locale`; the message is returned.
  - `List<Component>`: same, for list-valued messages such as lore.
- Placeholder values are inserted by type: numbers are formatted for the locale, `Duration` as `1h 5m`,
  `Component`s as-is, everything else as plain text.

When the processor can see your resources (the Gradle plugin configures this), it checks that every key exists in
the default-locale file and fails the build otherwise.

## Imperative access

Inject `Localization`:

```java
localization.send(player, "homes.created", Arg.text("home", name));
Component title = localization.message(player, "menu.title");
List<Component> lore = localization.lines(Locale.US, "items.home.lore", Arg.number("x", 10));
```

## Placeholder safety

Placeholder values are never parsed as MiniMessage. A player who names a home `<red>Evil` sees the literal text
`<red>Evil`. The `Arg` factory methods make the insertion mode explicit:

| Method | Inserted as |
|---|---|
| `Arg.text(name, value)` | Plain text, tags not interpreted |
| `Arg.number(name, value)` | Number formatted for the recipient's locale |
| `Arg.duration(name, value)` | Short human-readable duration |
| `Arg.component(name, component)` | The component as-is |
| `Arg.trusted(name, miniMessage)` | **Parsed** MiniMessage. Only for markup from trusted sources such as your own config |
| `Arg.of(name, value)` | Picks component, number, duration or text by type |

## Choosing the locale

On first start the plugin writes `plugins/<Plugin>/lang/settings.yml`:

```yaml
# Language used when a player's language has no translation file.
default-locale: en_US
# Use the language selected in each player's Minecraft client.
use-client-locale: true
# Add keys introduced by plugin updates to the files in this folder.
add-missing-keys: true
```

The locale for a recipient is, in order: an override set with `Localization.setLocale(uuid, locale)`, the
client's language (if `use-client-locale`), then `default-locale`. The console uses `default-locale`.

A key is looked up through a fallback chain, stopping at the first file that has it:

1. the exact locale (`pl_PL`)
2. the bare language (`pl`), then any other file of the same language
3. `default-locale` and its bare language
4. `en_US`, then `en`

A key missing everywhere renders as the key itself and logs one warning.

## Server owner edits

Bundled files are copied to `plugins/<Plugin>/lang/` on first start. Server owners edit those copies or add new
languages by creating more files (`es_ES.yml`); incomplete community files are fine because missing keys fall
back. With `add-missing-keys`, keys added in plugin updates are appended to existing files and reported.

`runtime.reload()` (or `Localization.reload()`) re-reads the files. Translations are swapped atomically; if a
file is invalid YAML, the previous translations stay active and the error is returned.

## Framework messages

Command errors and GUI failures use keys under `nookfall.*` (for example `nookfall.command.no-permission`).
English, German and Polish are built in; define the same keys in your own files to override them.
