package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.config.ViewMode;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class ZenModeTest {
    @TempDir Path directory;
    @AfterEach void cleanup() throws Exception { closeOwners(); }
    private static WindowContent window() { return content(launcher(new ArrayDeque<>())); }
    private static ActionId zenAction() {
        return ActionId.forId("toggle_zen_mode").orElseThrow(() -> new AssertionError("Missing Zen action"));
    }
    private static void toggle(WindowContent owner) { owner.invoke(zenAction()); }
    private static void show(Contributions model, WindowContent owner, String id) {
        model.requestPanel(new Contributions.PanelRequest(owner.id(), id, Contributions.PanelRequest.Op.SHOW));
    }

    @Test void zenHidesAllSixDockSlotsAndRestoresTheirInstancesGeometryAndVisibility() throws Exception {
        edt(() -> {
            var model = new Contributions(); var state = UiState.inMemory(); var owner = window();
            owner.connectContributions(model, state); owner.setSize(1400, 900);
            var sites = new ArrayList<PanelSite>(); var notifications = new ArrayList<List<Boolean>>();
            for (Slot slot : Slot.values()) {
                var events = new ArrayList<Boolean>(); notifications.add(events);
                model.addPanel(slot.name(), slot.name(), new ImageIcon(), slot, site -> {
                    sites.add(site); site.onVisibility(events::add); return new JLabel(slot.name());
                });
                show(model, owner, slot.name());
            }
            owner.dock().resize(Side.LEFT, 240); owner.dock().resize(Side.RIGHT, 310);
            owner.dock().setRatio(Side.LEFT, .37); owner.dock().setRatio(Side.BOTTOM, .62);
            LayoutTestSupport.layoutTree(owner);
            Rectangle normal = new Rectangle(owner.tabStrip().getBounds());
            var panels = state.panels(); var sizes = state.dock();
            List<JComponent> contents = Arrays.stream(Slot.values()).map(owner.area()::content).toList();
            var tab = owner.currentTab();
            toggle(owner); LayoutTestSupport.layoutTree(owner);
            assertThat(owner.toolbar().isVisible()).isTrue(); assertThat(owner.status().isVisible()).isFalse();
            assertThat(owner.chrome().search().isVisible()).isTrue();
            assertThat(owner.leftStripe().isVisible()).isFalse(); assertThat(owner.rightStripe().isVisible()).isFalse();
            for (Slot slot : Slot.values()) assertThat(owner.area().content(slot)).isNull();
            assertThat(sites).allSatisfy(site -> assertThat(site.visible()).isFalse());
            assertThat(owner.tabStrip().getWidth()).isGreaterThan(normal.width);
            assertThat(owner.tabStrip().getHeight()).isGreaterThan(normal.height);
            assertThat(state.panels()).isEqualTo(panels); assertThat(state.dock()).isEqualTo(sizes);
            assertThat(owner.currentTab()).isSameAs(tab);
            toggle(owner); LayoutTestSupport.layoutTree(owner);
            assertThat(owner.tabStrip().getBounds()).isEqualTo(normal);
            assertThat(Arrays.stream(Slot.values()).map(owner.area()::content).toList()).containsExactlyElementsOf(contents);
            assertThat(sites).hasSize(6).allSatisfy(site -> assertThat(site.visible()).isTrue());
            assertThat(notifications).allSatisfy(events -> assertThat(events).containsExactly(true, false, true));
        });
    }

    @Test void closingInZenPersistsNormalLayoutAndOtherWindowsStayNormal() throws Exception {
        edt(() -> {
            var model = new Contributions(); var state = UiState.load(directory.resolve("ui-state.toml"));
            var first = window(); first.connectContributions(model, state);
            model.addPanel("test.panel", "Panel", new ImageIcon(), Slot.RIGHT_BOTTOM, site -> new JLabel("panel"));
            show(model, first, "test.panel"); first.setRailVisible(true);
            var second = window(); second.connectContributions(model, state);
            toggle(first);
            assertThat(second.toolbar().isVisible()).isTrue();
            assertThat(second.area().content(Slot.RIGHT_BOTTOM)).isNotNull();
            first.close();
            var saved = UiState.load(directory.resolve("ui-state.toml"));
            assertThat(saved.panel("test.panel")).hasValue(new UiState.Panel(Slot.RIGHT_BOTTOM, 0, true));
            assertThat(saved.railVisible()).isTrue();
            assertThat(second.area().content(Slot.RIGHT_BOTTOM)).isNotNull();
        });
    }

    @Test void panelsRequestedOrReplacedInZenStayHiddenAndAreBuiltOnlyOnExit() throws Exception {
        edt(() -> {
            var model = new Contributions(); var owner = window(); owner.connectContributions(model);
            List<PanelSite> sites = new ArrayList<>();
            var old = model.addPanel("test.panel", "Old", new ImageIcon(), Slot.LEFT_TOP, site -> {
                sites.add(site); return new JLabel("old");
            });
            show(model, owner, "test.panel"); toggle(owner); old.close();
            model.addPanel("test.panel", "New", new ImageIcon(), Slot.LEFT_TOP, site -> {
                sites.add(site); return new JLabel("new");
            });
            show(model, owner, "test.panel");
            assertThat(sites).hasSize(1); assertThat(sites.getFirst().visible()).isFalse();
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNull(); assertThat(owner.leftStripe().isVisible()).isFalse();
            toggle(owner);
            assertThat(sites).hasSize(2); assertThat(sites.getLast().visible()).isTrue();
            assertThat((JLabel) owner.area().content(Slot.LEFT_TOP)).extracting(JLabel::getText).isEqualTo("new");
        });
    }

    @Test void normalPreferencesAndLiveSettingsAreSeparateFromEffectiveZenVisibility() throws Exception {
        edt(() -> {
            var owner = window(); owner.setToolbarMode(ToolbarMode.ICONS); owner.setStatusVisible(false);
            owner.setRailVisible(false); toggle(owner); toggle(owner);
            assertThat(owner.toolbarMode()).isEqualTo(ToolbarMode.ICONS);
            assertThat(owner.status().isVisible()).isFalse(); assertThat(owner.leftStripe().isVisible()).isFalse();
            toggle(owner);
            owner.applyConfiguration(ConfigSnapshot.defaults(), true);
            assertThat(owner.toolbar().isVisible()).isTrue(); assertThat(owner.status().isVisible()).isFalse();
            var next = ConfigLoader.parse(directory.resolve("config.toml"), "window.status_bar=false\nwindow.toolbar='icons'", true);
            assertThat(next.diagnostics()).isEmpty(); owner.applyConfiguration(next.snapshot(), true);
            toggle(owner);
            assertThat(owner.toolbarMode()).isEqualTo(ToolbarMode.ICONS);
            assertThat(owner.toolbar().isVisible()).isTrue(); assertThat(owner.status().isVisible()).isFalse();
        });
    }

    @Test void zenIsUnassignedButMenuPaletteAndLiveShortcutShareItsStateOnBothPlatforms() throws Exception {
        edt(() -> {
            for (boolean mac : new boolean[]{true, false}) {
                var owner = content(launcher(new ArrayDeque<>()), mac);
                var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
                var zen = item(child(view(owner), "View Modes"), "Zen");
                var action = zenAction(); var key = KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0);
                assertThat(owner.bindings().strokeFor(action)).isEmpty(); assertThat(zen.getAccelerator()).isNull();
                owner.setBindings(KeyBindings.withOverrides(mac, Map.of("toggle_zen_mode", "f10")));
                assertThat(zen.getAccelerator()).isEqualTo(key); assertThat(root.activate(key)).isTrue();
                assertThat(zen.isSelected()).isTrue(); assertThat(owner.toolbar().isVisible()).isTrue();
                owner.dispatchCommand(owner.commands().find("toggle_zen_mode").orElseThrow());
                assertThat(zen.isSelected()).isFalse(); assertThat(owner.toolbar().isVisible()).isTrue();
                zen.doClick(); owner.setBindings(KeyBindings.defaults(mac));
                assertThat(root.activate(key)).isFalse(); assertThat(zen.isSelected()).isTrue();
                assertThat(zen.getAccelerator()).isNull(); zen.doClick();
                assertThat(owner.toolbar().isVisible()).isTrue();
                owner.close(); assertThat(owner.action(action).isEnabled()).isFalse();
            }
        });
    }

    @Test void viewMenuGroupsVisibilityFontAndAppearanceWhileKeepingContributedTools() throws Exception {
        edt(() -> {
            var owner = window(); var model = new Contributions(); owner.connectContributions(model);
            model.addPanel("test.panel", "Panel", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("panel"));
            var view = view(owner);
            assertThat(item(view, "Zoom Pane").getAction()).isSameAs(owner.action(ActionId.ZOOM_PANE));
            assertThat(item(child(view, "Font Size"), "Increase Font Size").getAction()).isSameAs(owner.action(ActionId.FONT_BIGGER));
            var appearance = child(view, "Appearance");
            assertThat(child(appearance, "UI Theme")).isNotNull(); assertThat(child(appearance, "Workspace Scheme")).isNotNull();
            assertThat(item(appearance, "Window Settings\u2026")).isNotNull();
            item(child(view, "Tools"), "Toggle Panel").doClick();
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNotNull();
            toggle(owner);
            var visibility = child(view, "Show/Hide");
            assertThat(item(visibility, "Status Bar").isEnabled()).isFalse();
            assertThat(item(visibility, "Rail").isEnabled()).isFalse();
            assertThat(item(child(visibility, "Toolbar"), "Icons Only").isEnabled()).isTrue();
            toggle(owner); assertThat(item(visibility, "Status Bar").isEnabled()).isTrue();
        });
    }

    @Test void zenSavesImmediatelyRestoresBeforePanelCreationAndPersistsExit() throws Exception {
        edt(() -> {
            var file = directory.resolve("ui-state.toml");
            var model = new Contributions(); var builds = new ArrayList<PanelSite>();
            model.addPanel("test.panel", "Panel", new ImageIcon(), Slot.LEFT_TOP, site -> {
                builds.add(site); return new JLabel("panel");
            });
            var first = window(); first.connectContributions(model, UiState.load(file));
            show(model, first, "test.panel"); toggle(first);
            // Read from disk before close: the toggle itself must save.
            var restored = window(); restored.connectContributions(model, UiState.load(file));
            assertThat(restored.zenMode()).isTrue();
            assertThat(restored.action(zenAction()).getValue(Action.SELECTED_KEY)).isEqualTo(true);
            assertThat(restored.status().isVisible()).isFalse();
            assertThat(restored.leftStripe().isVisible()).isFalse();
            assertThat(restored.area().content(Slot.LEFT_TOP)).isNull();
            assertThat(builds).as("no panel is constructed during Zen startup").hasSize(1);
            assertThat(restored.toolbar().isVisible()).isTrue();
            assertThat(restored.chrome().search().isVisible()).isTrue();
            toggle(restored);
            assertThat(restored.area().content(Slot.LEFT_TOP)).isNotNull();
            var normal = window(); normal.connectContributions(model, UiState.load(file));
            assertThat(normal.zenMode()).isFalse();
            assertThat(normal.area().content(Slot.LEFT_TOP)).isNotNull();
            // Last-closed layout ownership includes its mode, even if another window toggled most recently.
            first.close();
            var last = window(); last.connectContributions(model, UiState.load(file));
            assertThat(last.zenMode()).isTrue();
            assertThat(last.area().content(Slot.LEFT_TOP)).isNull();
        });
    }

    @Test void absentOrMalformedSavedZenFallsBackWithoutLosingOtherLayout() throws Exception {
        for (String value : new String[]{"", "zen_mode = 'yes'\n", "zen_mode = 42\n"}) {
            var file = directory.resolve("legacy.toml");
            java.nio.file.Files.writeString(file, "version = 2\nrail_visible = false\n" + value);
            edt(() -> {
                var owner = window(); owner.connectContributions(new Contributions(), UiState.load(file));
                assertThat(owner.zenMode()).isFalse();
                assertThat(owner.railVisible()).isFalse();
                assertThat(owner.toolbar().isVisible()).isTrue();
                assertThat(owner.status().isVisible()).isTrue();
            });
        }
    }

    @Test void toolbarStyleAndPaletteRemainUsableWhileZenIsActive() throws Exception {
        edt(() -> {
            var owner = window(); var root = new JRootPane(); root.setContentPane(owner);
            owner.installRootBindings(root); root.setSize(1200, 800);
            toggle(owner);
            assertThat(owner.chrome().search().isVisible()).isTrue();
            owner.windowCommands().view("view.toolbar.icons").actionPerformed(null);
            assertThat(owner.toolbarMode()).isEqualTo(ToolbarMode.ICONS);
            assertThat(owner.toolbar().isVisible()).isTrue();
            owner.invoke(ActionId.COMMAND_PALETTE);
            assertThat(owner.commandPalette().isOpen()).isTrue();
            owner.commandPalette().dismiss();
            owner.windowCommands().view("view.toolbar.hidden").actionPerformed(null);
            assertThat(owner.toolbar().isVisible()).isFalse();
            toggle(owner);
            assertThat(owner.toolbar().isVisible()).as("Zen never overrides the chosen toolbar style").isFalse();
        });
    }

    @Test void savedZenAndTogglesPreserveTheStartupChromeMode() throws Exception {
        edt(() -> {
            for (ViewMode mode : ViewMode.values()) {
                var state = UiState.inMemory(); state.setZenMode(true);
                var owner = window();
                owner.applyConfiguration(ConfigSnapshot.defaults().toBuilder().viewMode(mode).build(), true);
                owner.connectContributions(new Contributions(), state);
                assertThat(owner.zenMode()).isTrue();
                assertThat(owner.viewMode()).isEqualTo(mode);
                assertThat(owner.toolbar().isVisible()).isEqualTo(mode == ViewMode.NORMAL);
                assertThat(owner.status().isVisible()).isFalse();
                toggle(owner);
                assertThat(owner.viewMode()).isEqualTo(mode);
                assertThat(owner.toolbar().isVisible()).isEqualTo(mode == ViewMode.NORMAL);
                assertThat(owner.status().isVisible()).isTrue();
                toggle(owner);
                owner.applyConfiguration(ConfigSnapshot.defaults().toBuilder().viewMode(mode).build(), true);
                assertThat(owner.zenMode()).isTrue();
                assertThat(owner.viewMode()).isEqualTo(mode);
                assertThat(owner.toolbar().isVisible()).isEqualTo(mode == ViewMode.NORMAL);
                assertThat(owner.status().isVisible()).isFalse();
            }
        });
    }

    static JMenu view(WindowContent owner) { return owner.menuBar().getMenu(2); }
    static JMenu child(JMenu parent, String label) { return (JMenu) item(parent, label); }
    static JMenuItem item(JMenu parent, String label) {
        return Arrays.stream(parent.getMenuComponents()).filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast)
            .filter(item -> item.getText().equals(label)).findFirst().orElseThrow(() -> new AssertionError("Missing " + label));
    }
    private static final class BindingRoot extends JRootPane {
        boolean activate(KeyStroke stroke) {
            return processKeyBinding(stroke, new KeyEvent(this, KeyEvent.KEY_PRESSED, 0, stroke.getModifiers(),
                stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED), WHEN_IN_FOCUSED_WINDOW, true);
        }
    }
}
