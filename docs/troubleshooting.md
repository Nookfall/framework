---
title: Troubleshooting
description: Common errors when building or running Nookfall plugins and how to fix them.
---

## Build errors

**`No generated <Main>_NookfallIndex was found`** at startup
: The Nookfall annotation processor did not run. Use the `com.nookfall.framework` Gradle plugin, or add
  `annotationProcessor("com.nookfall:framework-processor:0.1.0")` and Avaje's `avaje-inject-generator` yourself.

**`Several JavaPlugin subclasses found`**
: Tell the processor which one is the plugin with the compiler option `-Anookfall.plugin=com.example.MyPlugin`.

**`Translation key 'x.y' is missing from lang/en_US.yml`**
: A `@Messages` method uses a key the default-locale file does not define. Add it, or set
  `-Anookfall.defaultLocale=` to the locale your bundled files use.

**Avaje reports a missing or ambiguous dependency**
: Wiring is checked at compile time. Make the missing class a bean (`@Singleton`, or one of the Nookfall
  component annotations), or add a qualifier for ambiguous ones. See [Dependency injection](dependency-injection.md).

**`@Config` field has an unsupported type**
: Use a supported type or add a [codec](configuration.md#custom-codecs).

## Startup errors

**`<Plugin> could not start: config.yml: ...`**
: The configuration file is invalid. Every problem is listed with its path. Fix the file and restart; the plugin
  stays disabled until then, with nothing left registered.

**Paper refuses to load a `-shared.jar`, naming `NookfallFramework` as a missing dependency**
: Install `NookfallFramework-0.1.x.jar`, or use the standalone jar instead.

**`<Plugin> was built for Nookfall Framework X but version Y is installed`**
: Update the `NookfallFramework` plugin (or rebuild the plugin against the installed version). See
  [version compatibility](packaging.md#version-compatibility).

**`/<command> is already used by another plugin`**
: Another plugin registered the same label first. Your command is still available as `/<plugin>:<command>`.

**Two handlers "accept the same input"**
: Two methods of one command class map to the same subcommand and argument shape. Rename or merge them.

## Runtime problems

**`Blocking database calls are not allowed on a server thread`**
: Use the `...Async` methods and continue with `thenAcceptAsync(..., tasks.entity(player).executor())`.

**MiniMessage tags show up literally in a message**
: Placeholder values are inserted as plain text on purpose, so players cannot inject formatting. For markup from
  a trusted source, use `Arg.trusted(...)` or pass a `Component`.

**`Missing translation key '...' in every language file`**
: The key is not in any `lang/*.yml`, bundled or edited. The message renders as the key itself.

**A GUI reload "failed" but the menu still works**
: Invalid layouts are rejected and the last working one stays active. The reload result lists every problem with
  its path, for example `elements.next.action: action 'nxt_page' is neither built in ... nor a @GuiAction method`.

**An asynchronous GUI provider's result never appears**
: Results are discarded if the menu was closed, replaced or refreshed before they arrived. Check the log for
  `GUI provider '...' failed` if the future failed.

**A scheduled task stopped following its configured interval**
: After a reload with an invalid value the task keeps its previous interval and logs
  `Could not reschedule ...`. Fix the value and reload again.

**Re-enabling a plugin with a plugin manager fails**
: Paper closes a plugin's class loader when it disables, so enabling the same instance again is not supported by
  Paper. Nookfall detects this and fails cleanly without leaving commands, listeners or tasks behind. Restart the
  server instead.

## Getting help

Open an issue at <https://github.com/nookfall/framework/issues> with the full startup log, the Paper version and
whether you use the standalone or shared jar.
