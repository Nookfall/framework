---
title: GUIs
description: Inventory menus whose layout, items, text and conditions are configured in YAML while Java supplies data and behaviour.
---

A Nookfall menu has two halves:

- **YAML** (`guis/<id>/layout.yml` and `guis/<id>/lang/<locale>.yml`) decides what goes where, which items
  are used, what they say and when they are visible. Server owners can rearrange everything.
- **Java** (a `@Gui("<id>")` class) supplies dynamic content and handles actions. It never refers to slot
  numbers.

```text
src/main/resources/guis/
└── homes/
    ├── layout.yml
    └── lang/
        ├── en_US.yml
        └── pl_PL.yml
```

A YAML-only menu (no Java class) is fine when it uses only built-in actions and static items.

## Layout

```yaml
rows: 6
title: homes.title

layout:
  - "#########"
  - "#HHHHHHH#"
  - "#HHHHHHH#"
  - "#HHHHHHH#"
  - "#P#####N#"
  - "####C####"

elements:
  border:
    symbol: "#"
    item:
      material: GRAY_STAINED_GLASS_PANE
      name: "<empty>"

  homes:
    symbol: "H"
    type: collection
    provider: homes
    item-template: home
    page-size: auto
    empty-item:
      material: BARRIER
      translation: items.no-homes
    loading-item:
      material: CLOCK
      translation: items.loading

  previous:
    symbol: "P"
    type: button
    action: previous_page
    visible-if: has_previous_page
    item:
      material: ARROW
      translation: items.previous

  next:
    symbol: "N"
    type: button
    action: next_page
    visible-if: has_next_page
    item:
      material: ARROW
      translation: items.next

  close:
    symbol: "C"
    type: button
    action: close
    item:
      material: BARRIER
      translation: items.close

templates:
  home:
    material: RED_BED
    translation: items.home
    action: home_click
    click-actions:
      shift-right: delete_home
```

Rules, all checked when the layout loads (and at compile time for bundled layouts):

- `rows` is 1–6 and `layout` has exactly that many rows of exactly 9 characters.
- Every character other than a space is the `symbol` of exactly one element. Spaces are empty slots.
- Element ids and symbols are unique; unknown keys, actions, conditions, providers, renderers and materials are
  errors. Unused elements and templates are warnings.

### Element types

| `type` | Purpose | Keys |
|---|---|---|
| `item` (default) | Static decoration | `item`, optional `action`, `click-actions`, `visible-if` |
| `button` | Static item that does something | `item`, and `action` or `click-actions` (required), `visible-if` |
| `collection` | Dynamic entries, paginated across all slots with its symbol | `provider`, `item-template`, `variants`, `page-size` (`auto` = all its slots, or a number), `empty-item`, `loading-item`, `visible-if` |

### Items

| Key | Meaning |
|---|---|
| `material` | Required. Item material. A collection entry may override it. |
| `amount` | 1–99 |
| `translation` | Uses `<key>.name` and `<key>.lore` from the GUI's translations |
| `name`, `lore` | Literal MiniMessage, used when there is no `translation` |
| `flags` | Item flags, for example `[hide_attributes]` |
| `enchantments` | `{ sharpness: 2 }` (namespaced keys allowed) |
| `glow` | Force or remove the enchantment glint |
| `custom-model-data` | Integer custom model data |
| `renderer` | Name of a `@GuiRenderer` method that post-processes the item |
| `action`, `click-actions` | What a click does (below) |

`name: "<empty>"` gives an item with a blank name.

### Titles and text

`title` is a translation key if the GUI's translations contain it, otherwise literal MiniMessage. Names, lore and
titles can use placeholders: `<player>`, `<page>` and `<pages>` (of the first collection), every
`@GuiPlaceholder`, and, inside collection items, the placeholders of the entry being drawn.

GUI translations live next to the layout and fall back like plugin translations (see
[Localization](localization.md)):

```yaml
# guis/homes/lang/en_US.yml
homes:
  title: "<dark_green>Your Homes"
items:
  home:
    name: "<green><home_name>"
    lore:
      - "<gray>World: <white><world>"
      - "<gray>Location: <white><x>, <y>, <z>"
      - ""
      - "<yellow>Click to teleport!"
```

### Actions

`action` handles left, right, shift-left and shift-right clicks. `click-actions` maps specific click types
(`left`, `right`, `shift-left`, `shift-right`, `middle`, `number-key`, `drop`, `control-drop`, `double-click`,
`swap-offhand`) to actions and takes precedence.

Built-in actions:

| Action | Effect |
|---|---|
| `close` | Close the menu |
| `next_page`, `previous_page` | Page the first collection; `next_page:<element>` names another |
| `refresh` | Re-run providers and redraw |
| `open:<gui id>` | Open another menu of the same plugin |

Anything else must be a `@GuiAction` in the GUI's class.

### Conditions

`visible-if` hides an element when the condition is false. Prefix with `!` to negate.

| Condition | True when |
|---|---|
| `has_next_page`, `has_previous_page` | The first collection (or `has_next_page:<element>`) has another page |
| `loading` | The collection is still loading (`loading:<element>`) |
| `permission:<node>` | The player has the permission |
| any other name | A `@GuiCondition` method returns true |

## Java

```java
@Gui("homes")
public final class HomesGui {

    private final HomeService homes;

    public HomesGui(HomeService homes) {
        this.homes = homes;
    }

    @GuiProvider("homes")
    public CompletableFuture<List<GuiEntry<Home>>> entries(GuiContext context) {
        return homes.entries(context.player().getUniqueId());
    }

    @GuiAction("home_click")
    public void click(GuiClickContext context, GuiEntry<Home> entry) {
        homes.teleport(context.player(), entry.value());
    }

    @GuiAction("delete_home")
    public void delete(GuiClickContext context, GuiEntry<Home> entry) {
        homes.delete(context.player(), entry.value().name());
    }

    @GuiPlaceholder("home_count")
    public Object count(GuiRenderContext context) {
        return homes.names(context.player().getUniqueId()).size();
    }
}
```

(From `examples/homes-demo`.)

| Annotation | Signature |
|---|---|
| `@GuiProvider("name")` | `List<GuiEntry<T>> m(GuiContext)` or `CompletableFuture<List<GuiEntry<T>>> m(GuiContext)` |
| `@GuiAction("name")` | Any of `GuiClickContext`, `GuiEntry<T>`, `Player`, `ClickType`, in any order |
| `@GuiCondition("name")` | `boolean m(GuiContext)` or `boolean m(GuiContext, GuiEntry<T>)` |
| `@GuiPlaceholder("name")` | `Object m(GuiRenderContext)`; components are inserted as-is, other values as plain text |
| `@GuiRenderer("name")` | `ItemStack m(GuiRenderContext, ItemStack)` |

Names use lower case letters, digits and `_`, and are scoped to the GUI class, so two menus can both have a
`delete` action. The processor rejects wrong signatures, duplicate names, names that clash with built-ins, and checks that the bundled layout only references names that exist.

### Entries

```java
GuiEntry.of(home.id().toString(), home)
        .placeholder("home_name", home.name())
        .placeholder("world", home.world())
        .material(Material.CYAN_BED);
```

An entry can ask to be drawn with another template by naming a **variant**. The layout maps variant names to
templates, so server owners decide what each state looks like and which clicks it accepts:

```java
GuiEntry.of(home.id().toString(), home).variant("unavailable");
```

```yaml
  homes:
    type: collection
    item-template: home
    variants:
      unavailable: unavailable-home   # a template under templates:
```

A variant the layout does not map uses `item-template`, so layouts may ignore variants. Variants must reference
existing templates (checked when the layout loads and, for bundled layouts, at compile time).

The **id must be stable**: it identifies the entry across refreshes, so a click always reaches the entry that
was displayed, even if the list changed or the layout was rearranged. Duplicate ids are logged as a warning
because clicks on them are ambiguous. Placeholder values are plain text unless they are components.

## Opening and state

Inject `GuiManager`:

```java
guis.open(player, "homes");
guis.open(player, "home-edit", Map.of("home", homeId));
```

`GuiContext` gives you the player, their locale, `arguments()`, a mutable per-session `state()` map that
survives refreshes and reloads while the menu is open, page control, `refresh()`, `close()` and `open(...)`.
Call these on the player's thread (where actions are delivered).

## Asynchronous loading

When a provider returns a `CompletableFuture`, the collection shows `loading-item` (and the `loading` condition
is true) until it completes. The result is applied on the player's thread. A result is **discarded** if, in the
meantime, the menu was closed or replaced, the player refreshed it again, the player left, or the plugin was
disabled. A failed future is logged and the collection is shown empty.

## Protection

While a Nookfall menu is open, every click in its slots is cancelled and only triggers actions, including
shift-clicks, number-key swaps, double-click collecting and drags that touch the menu. Clicks in the player's
own inventory still work, except those that would move items into the menu. Clicks are debounced (100 ms).
Inventories of other plugins are not affected.

## Hot reload

Bundled layouts and GUI translations are copied to `plugins/<Plugin>/guis/` on first start. After editing them,
`runtime.reload()` (or `GuiManager.reload()`) re-reads them. A layout with errors is reported and the
previously working definition stays in use; valid changes are applied and open menus are redrawn in place.

## Cleanup

Sessions end when the inventory closes or the player quits. When the plugin disables, its open menus are
closed and pending asynchronous results are ignored.
