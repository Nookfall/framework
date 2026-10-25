package com.nookfall.framework.internal.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.gui.GuiClickContext;
import com.nookfall.framework.gui.GuiEntry;
import com.nookfall.framework.internal.ResourceIndex;
import com.nookfall.framework.internal.i18n.LocalizationImpl;
import com.nookfall.framework.internal.task.TasksImpl;
import com.nookfall.framework.spi.GuiBinding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class GuiRuntimeTest {

    static final String LAYOUT = """
            rows: 3
            title: menu.title
            layout:
              - "EEEEEEEEE"
              - "EEEEEEEEE"
              - "P   C   N"
            elements:
              entries:
                symbol: "E"
                type: collection
                provider: items
                item-template: entry
                variants:
                  locked: locked-entry
                loading-item:
                  material: CLOCK
                  name: "Loading"
              previous:
                symbol: "P"
                type: button
                action: previous_page
                visible-if: has_previous_page
                item: { material: ARROW, name: "Back" }
              next:
                symbol: "N"
                type: button
                action: next_page
                visible-if: has_next_page
                item: { material: ARROW, name: "Next" }
              close:
                symbol: "C"
                type: button
                action: close
                item: { material: BARRIER, name: "<red>Close" }
            templates:
              entry:
                material: PAPER
                translation: items.entry
                action: pick
                click-actions:
                  right: inspect
              locked-entry:
                material: BARRIER
                name: "<red>Locked <number>"
                action: inspect
            """;

    /** The object a generated binding would wrap. */
    static final class Menu {
        final List<String> picked = new ArrayList<>();
        final List<String> inspected = new ArrayList<>();
        int size = 25;
        CompletableFuture<List<GuiEntry<?>>> pending;

        List<GuiEntry<?>> items() {
            List<GuiEntry<?>> out = new ArrayList<>();
            for (int i = 1; i <= size; i++) {
                GuiEntry<?> entry = GuiEntry.of("id-" + i, i).placeholder("number", i);
                // Entry 3 uses a variant the layout maps; entry 4 one it does not.
                out.add(i == 3 ? entry.variant("locked") : i == 4 ? entry.variant("unmapped") : entry);
            }
            return out;
        }
    }

    @TempDir
    Path jar;

    @TempDir
    Path data;

    private ServerMock server;
    private Plugin plugin;
    private GuiManagerImpl guis;
    private TasksImpl tasks;
    private Menu menu;
    private PlayerMock player;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        Files.createDirectories(jar.resolve("guis/menu/lang"));
        Files.writeString(jar.resolve("guis/menu/layout.yml"), LAYOUT);
        Files.createDirectories(jar.resolve("guis/slow/lang"));
        Files.writeString(jar.resolve("guis/slow/layout.yml"), LAYOUT);
        Files.writeString(jar.resolve("guis/menu/lang/en_US.yml"), """
                menu:
                  title: "Menu <page>/<pages>"
                items:
                  entry:
                    name: "<green>Item <number>"
                    lore: ["<gray>Click me"]
                """);
        Files.copy(jar.resolve("guis/menu/lang/en_US.yml"), jar.resolve("guis/slow/lang/en_US.yml"));
        Logger logger = Logger.getLogger("test");
        ResourceIndex resources = ResourceIndex.ofDirectory(jar);
        LocalizationImpl l10n = new LocalizationImpl(data, resources, logger);
        l10n.loadInitial();
        tasks = new TasksImpl(plugin, logger);
        guis = new GuiManagerImpl(plugin, data, resources, l10n, tasks, logger, () -> false);
        menu = new Menu();
        guis.bind(binding("menu", false), menu);
        guis.bind(binding("slow", true), menu);
        guis.loadInitial();
        server.getPluginManager().registerEvents(guis, plugin);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private GuiBinding<Menu> binding(String id, boolean async) {
        return new GuiBinding<>(
                id,
                Menu.class,
                Map.of("items", new GuiBinding.Provider<>(async, (m, context) -> async ? m.pending : m.items())),
                Map.of(
                        "pick", new GuiBinding.Action<>(true, (m, context, entry) -> m.picked.add(entry.id())),
                        "inspect",
                                new GuiBinding.Action<>(true, (m, context, entry) -> {
                                    m.inspected.add(entry.id() + "@" + ((GuiClickContext) context).clickType());
                                })),
                Map.of(),
                Map.of(),
                Map.of());
    }

    private InventoryView open() {
        guis.open(player, "menu");
        return player.getOpenInventory();
    }

    private static String name(ItemStack item) {
        return item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null
                ? null
                : PlainTextComponentSerializer.plainText()
                        .serialize(item.getItemMeta().displayName());
    }

    private InventoryClickEvent click(InventoryView view, int rawSlot, ClickType type, InventoryAction action) {
        InventoryClickEvent event = new InventoryClickEvent(
                view, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, rawSlot, type, action);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void waitDebounce() throws InterruptedException {
        Thread.sleep(110);
    }

    @Test
    void rendersLocalizedTitleItemsAndPagination() {
        InventoryView view = open();
        assertThat(PlainTextComponentSerializer.plainText().serialize(view.title()))
                .isEqualTo("Menu 1/2");
        assertThat(name(view.getTopInventory().getItem(0))).isEqualTo("Item 1");
        assertThat(name(view.getTopInventory().getItem(17))).isEqualTo("Item 18");
        assertThat(view.getTopInventory().getItem(18))
                .as("no previous page on page 1")
                .isNull();
        assertThat(name(view.getTopInventory().getItem(26))).isEqualTo("Next");
        assertThat(guis.openGui(player)).contains("menu");
    }

    @Test
    void clicksResolveToEntriesAndClickTypes() throws Exception {
        InventoryView view = open();
        InventoryClickEvent left = click(view, 4, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(left.isCancelled()).isTrue();
        assertThat(menu.picked).containsExactly("id-5");
        waitDebounce();
        click(view, 4, ClickType.RIGHT, InventoryAction.PICKUP_HALF);
        assertThat(menu.inspected).containsExactly("id-5@RIGHT");
        waitDebounce();
        InventoryClickEvent numberKey = click(view, 4, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP);
        assertThat(numberKey.isCancelled()).isTrue();
        assertThat(menu.picked).as("number keys have no action by default").hasSize(1);
        assertThat(name(view.getTopInventory().getItem(4))).isEqualTo("Item 5");
    }

    @Test
    void entryVariantsUseTheirTemplateAndActions() {
        InventoryView view = open();
        assertThat(view.getTopInventory().getItem(2).getType()).isEqualTo(Material.BARRIER);
        assertThat(name(view.getTopInventory().getItem(2))).isEqualTo("Locked 3");
        assertThat(view.getTopInventory().getItem(3).getType()).isEqualTo(Material.PAPER);
        assertThat(name(view.getTopInventory().getItem(3))).isEqualTo("Item 4");
        click(view, 2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(menu.picked).isEmpty();
        assertThat(menu.inspected).containsExactly("id-3@LEFT");
    }

    @Test
    void paginationChangesEntryIdentityNotSlots() throws Exception {
        InventoryView view = open();
        click(view, 26, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(name(view.getTopInventory().getItem(0))).isEqualTo("Item 19");
        assertThat(name(view.getTopInventory().getItem(18))).isEqualTo("Back");
        assertThat(view.getTopInventory().getItem(26))
                .as("last page has no next button")
                .isNull();
        waitDebounce();
        click(view, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(menu.picked).containsExactly("id-19");
    }

    @Test
    void titleFollowsPlaceholdersWhenTheMenuIsRedrawn() throws Exception {
        InventoryView view = open();
        assertThat(PlainTextComponentSerializer.plainText().serialize(view.title()))
                .isEqualTo("Menu 1/2");
        click(view, 26, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(PlainTextComponentSerializer.plainText()
                        .serialize(player.getOpenInventory().title()))
                .as("the open window shows the new page number")
                .isEqualTo("Menu 2/2");
    }

    @Test
    void protectsGuiFromItemMovement() {
        InventoryView view = open();
        int bottomSlot = view.getTopInventory().getSize() + 5;
        player.getInventory().setItem(5, new ItemStack(Material.DIAMOND));
        InventoryClickEvent shiftIntoGui =
                click(view, bottomSlot, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(shiftIntoGui.isCancelled()).isTrue();
        InventoryClickEvent collect =
                click(view, bottomSlot, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR);
        assertThat(collect.isCancelled()).isTrue();
        InventoryClickEvent ownInventory = click(view, bottomSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(ownInventory.isCancelled())
                .as("ordinary clicks in the player's inventory are allowed")
                .isFalse();

        InventoryDragEvent drag = new InventoryDragEvent(
                view,
                null,
                new ItemStack(Material.DIAMOND),
                false,
                Map.of(3, new ItemStack(Material.DIAMOND), bottomSlot, new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(drag);
        assertThat(drag.isCancelled()).isTrue();
        InventoryDragEvent bottomOnly = new InventoryDragEvent(
                view,
                null,
                new ItemStack(Material.DIAMOND),
                false,
                Map.of(bottomSlot, new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(bottomOnly);
        assertThat(bottomOnly.isCancelled()).isFalse();
        assertThat(DragType.EVEN).isNotNull();
    }

    @Test
    void otherInventoriesAreNotAffected() {
        var chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);
        InventoryClickEvent event = click(view, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void closeButtonAndCloseEventEndTheSession() throws Exception {
        InventoryView view = open();
        click(view, 22, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(guis.openSessions()).isZero();
        assertThat(guis.openGui(player)).isEmpty();
        open();
        player.closeInventory();
        assertThat(guis.openSessions()).isZero();
    }

    @Test
    void quitEndsTheSession() {
        open();
        player.disconnect();
        assertThat(guis.openSessions()).isZero();
    }

    @Test
    void asyncProvidersShowLoadingAndIgnoreStaleResults() {
        menu.pending = new CompletableFuture<>();
        guis.open(player, "slow");
        InventoryView view = player.getOpenInventory();
        assertThat(name(view.getTopInventory().getItem(0))).isEqualTo("Loading");
        CompletableFuture<List<GuiEntry<?>>> first = menu.pending;
        menu.pending.complete(menu.items());
        server.getScheduler().performOneTick();
        assertThat(name(view.getTopInventory().getItem(0))).isEqualTo("Item 1");

        // A refresh supersedes an older pending load; its late result must be ignored.
        CompletableFuture<List<GuiEntry<?>>> stale = new CompletableFuture<>();
        menu.pending = stale;
        guis.refreshSession(currentSession());
        CompletableFuture<List<GuiEntry<?>>> fresh = new CompletableFuture<>();
        menu.pending = fresh;
        guis.refreshSession(currentSession());
        menu.size = 3;
        fresh.complete(menu.items());
        server.getScheduler().performOneTick();
        menu.size = 25;
        stale.complete(menu.items());
        server.getScheduler().performOneTick();
        assertThat(name(view.getTopInventory().getItem(3)))
                .as("stale 25-entry result ignored")
                .isNull();
        assertThat(first).isDone();

        // Results arriving after the GUI closed do not reopen or touch it.
        CompletableFuture<List<GuiEntry<?>>> late = new CompletableFuture<>();
        menu.pending = late;
        guis.refreshSession(currentSession());
        player.closeInventory();
        late.complete(menu.items());
        server.getScheduler().performOneTick();
        assertThat(guis.openSessions()).isZero();
    }

    private GuiSession currentSession() {
        return (GuiSession) player.getOpenInventory().getTopInventory().getHolder();
    }

    @Test
    void reloadAppliesNewLayoutAndKeepsWorkingOneOnErrors() throws Exception {
        InventoryView view = open();
        Path layout = data.resolve("guis/menu/layout.yml");
        Files.writeString(layout, LAYOUT.replace("\"P   C   N\"", "\"P   N   C\""));
        ReloadResult ok = guis.reload();
        assertThat(ok.success()).isTrue();
        server.getScheduler().performOneTick();
        assertThat(name(view.getTopInventory().getItem(26))).isEqualTo("Close");

        Files.writeString(layout, "rows: 9\nlayout: []\n");
        ReloadResult broken = guis.reload();
        assertThat(broken.success()).isFalse();
        assertThat(broken.errors()).anySatisfy(e -> assertThat(e).contains("keeping the previously working layout"));
        guis.open(player, "menu");
        assertThat(name(player.getOpenInventory().getTopInventory().getItem(26)))
                .isEqualTo("Close");
    }

    @Test
    void shutdownClosesOpenGuis() {
        InventoryView view = open();
        var inventory = view.getTopInventory();
        guis.shutdown();
        assertThat(guis.openSessions()).isZero();
        assertThat(player.getOpenInventory().getTopInventory()).isNotEqualTo(inventory);
    }
}
