package dev.jasper.app.settingsui;

import static dev.jasper.app.settingsui.PageKit.kit;
import static dev.jasper.app.settingsui.PageKit.node;
import static org.assertj.core.api.Assertions.*;
import dev.jasper.app.config.*;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.model.*;
import java.nio.file.Path;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class DesktopSourceSelectionTest {
    @Test void sourceSettingDefaultsToAutomatic() {
        assertThat(CoreSettings.UI_THEME_DESKTOP_SOURCE.defaultValue()).isEqualTo("auto");
        assertThat(ConfigSnapshot.defaults().desktopSource()).isEqualTo("auto");
        assertThat(ConfigSnapshot.defaults().toBuilder().desktopSource("gtk4").build().desktopSource()).isEqualTo("gtk4");
    }
    @Test void eitherStagedPickRevealsTheSourceAndAuthoredPicksRetainItsDraft() {
        try (var f = new Fixture("[ui.theme]\ndark='jasper-meadow'\nlight='jasper-meadow'\n", true)) {
            assertThat(node(f.page,"ui.appearance:desktop-source").state().visible()).isFalse();
            f.pick("dark-theme", "desktop-colors");
            assertThat(node(f.page,"ui.appearance:desktop-source").state().visible()).isTrue();
            assertThat(f.source()).isSameAs(f.originalSource);
            assertThat(options(f.source())).containsExactly("Automatic", "GTK 3", "GTK 4 / libadwaita");
            assertThat(selected(f.source())).isEqualTo("auto");
            f.pick("desktop-source", "gtk4");
            f.pick("dark-theme", "jasper-meadow");
            assertThat(node(f.page,"ui.appearance:desktop-source").state().visible()).isFalse();
            assertThat(f.draft.value(CoreSettings.UI_THEME_DESKTOP_SOURCE)).isEqualTo("gtk4");
            f.pick("light-theme", "desktop-colors");
            assertThat(node(f.page,"ui.appearance:desktop-source").state().visible()).isTrue();
            assertThat(selected(f.source())).isEqualTo("gtk4");
            assertThat(f.draft.edit().changes()).doesNotContainKey(CoreSettings.TERMINAL_SCHEME);
        }
    }
    @Test void sourceOnlyRebaseUpdatesCleanSourceAndPreservesDirtyThemeAndSourceFields() {
        try (var f = new Fixture("[ui.theme]\ndark='desktop-colors'\n", true)) {
            f.pick("light-theme", "slate-cobalt");
            f.rebase("[ui.theme]\ndark='desktop-colors'\ndesktop_source='gtk3'\n");
            assertThat(selected(f.source())).isEqualTo("gtk3");
            assertThat(selected(f.box("light-theme"))).isEqualTo("slate-cobalt");
            f.pick("desktop-source", "gtk4");
            f.rebase("[ui.theme]\ndark='desktop-colors'\ndesktop_source='auto'\n");
            assertThat(selected(f.source())).isEqualTo("gtk4");
            assertThat(f.draft.edit().expected()).containsEntry(CoreSettings.UI_THEME_DESKTOP_SOURCE, Optional.of("auto"));
            assertThat(f.draft.edit().changes()).containsEntry(CoreSettings.UI_THEME_DESKTOP_SOURCE, Optional.of("gtk4"));
        }
    }
    @Test void passiveUpdatesKeepControlsAndDraftAndSeparatePendingFromAppliedStatus() {
        try (var f = new Fixture("[ui.theme]\ndark='desktop-colors'\n", true)) {
            var dark = f.box("dark-theme");
            f.pick("desktop-source", "gtk4");
            f.status = "GTK 3 · partial palette";
            List.copyOf(f.changed).forEach(Runnable::run);
            assertThat(f.box("dark-theme")).isSameAs(dark);
            assertThat(f.source()).isSameAs(f.originalSource);
            assertThat(((Nodes.Text) node(f.page, "ui.appearance:desktop-status")).text())
                .contains("Pending Apply", "GTK 4 / libadwaita", "Applied: GTK 3 · partial palette");
            assertThat(f.draft.edit().changes()).containsOnlyKeys(CoreSettings.UI_THEME_DESKTOP_SOURCE);
        }
    }
    @Test void resetReturnsAllFourFieldsToDefaultsEvenWhenTheSourceIsHidden() {
        try (var f = new Fixture("[ui]\nappearance='light'\n[ui.theme]\ndark='desktop-colors'\nlight='slate-cobalt'\ndesktop_source='gtk4'\n", true)) {
            f.pick("dark-theme", "jasper-meadow");
            kit(f.page, "ui.appearance:reset", JButton.class).doClick(0);
            assertThat(f.draft.value(CoreSettings.UI_APPEARANCE)).isEqualTo(AppearanceMode.SYSTEM);
            assertThat(f.draft.value(CoreSettings.UI_THEME_DARK)).isEqualTo(CoreSettings.UI_THEME_DARK.defaultValue());
            assertThat(f.draft.value(CoreSettings.UI_THEME_LIGHT)).isEqualTo(CoreSettings.UI_THEME_LIGHT.defaultValue());
            assertThat(selected(f.source())).isEqualTo("auto");
            assertThat(f.draft.edit().changes()).containsEntry(CoreSettings.UI_THEME_DESKTOP_SOURCE, Optional.empty());
            if (CoreSettings.UI_THEME_DARK.defaultValue().equals("desktop-colors"))
                assertThat(f.demands).containsExactlyInAnyOrder("auto:true", "auto:false");
            else assertThat(f.demands).isEmpty();
        }
    }
    @Test void eachTileUsesItsMatchingDesktopPolarityAndChangingSourceLeavesTerminalAlone() {
        try (var f = new Fixture("terminal.scheme='slate-cobalt/high-contrast'\n[ui.theme]\ndark='desktop-colors'\nlight='desktop-colors'\n", true)) {
            var tiles = (Nodes.Choice)node(f.page, "ui.appearance:policy");
            assertThat(tiles.options().get(0).preview()).contains(new Option.Preview(AppearanceSelectionTest.OCEAN, Optional.empty()));
            assertThat(tiles.options().get(1).preview()).contains(new Option.Preview(AppearanceSelectionTest.MEADOW, Optional.empty()));
            assertThat(tiles.options().get(2).preview()).contains(new Option.Preview(AppearanceSelectionTest.OCEAN, Optional.of(AppearanceSelectionTest.MEADOW)));
            assertThat(f.demands).containsExactlyInAnyOrder("auto:true", "auto:false");
            f.pick("desktop-source", "gtk3");
            assertThat(f.demands).containsExactlyInAnyOrder("gtk3:true", "gtk3:false");
            assertThat(f.draft.value(CoreSettings.TERMINAL_SCHEME)).isEqualTo("slate-cobalt/high-contrast");
            assertThat(f.draft.edit().changes()).containsOnlyKeys(CoreSettings.UI_THEME_DESKTOP_SOURCE);
        }
    }
    @Test void nonLinuxRetainsOnlySelectedDesktopPicksAndSourceWithoutAcquiringHelpers() {
        try (var f = new Fixture("[ui.theme]\ndark='jasper-meadow'\nlight='desktop-colors'\ndesktop_source='gtk4'\n", false)) {
            assertThat(options(f.box("dark-theme"))).doesNotContain("Desktop colors", "Desktop colors (unavailable on this platform)");
            assertThat(options(f.box("light-theme"))).endsWith("Desktop colors (unavailable on this platform)");
            assertThat(selected(f.box("light-theme"))).isEqualTo("desktop-colors");
            assertThat(selected(f.source())).isEqualTo("gtk4");
            assertThat(((Nodes.Text)node(f.page,"ui.appearance:desktop-status")).text()).contains("unavailable on this platform");
            assertThat(f.demands).isEmpty(); assertThat(f.draft.modified()).isFalse();
        }
    }
    @Test void searchRevealsSourceWithBothDefaultAndEnterpriseCompositionsAndKeepsControls() {
        for (boolean enterprise : List.of(false, true)) {
            try (var f = new Fixture("[ui.theme]\ndark='desktop-colors'\n", true, enterprise)) {
                assertThat(f.page.searchTerms()).contains("Desktop color source", "Automatic", "GTK 3", "GTK 4 / libadwaita");
                var source = f.source();
                f.page.highlight("desktop color source");
                assertThat(kit(f.page,"row:ui.appearance:desktop", JComponent.class).getAccessibleContext().getAccessibleDescription()).isEqualTo("Matches search");
                assertThat(f.source()).isSameAs(source);
                f.pick("desktop-source", "gtk4");
                if (enterprise) f.page.event(new UiEvent.Action("ui.appearance:reset"));
                else kit(f.page,"ui.appearance:reset",JButton.class).doClick(0);
                assertThat(selected(f.source())).isEqualTo("auto");
            }
        }
    }
    @Test void closingTheRenderedPageRetiresItsCallbacksAndEvents() {
        var f = new Fixture("[ui.theme]\ndark='desktop-colors'\n", true);
        var late = List.copyOf(f.changed);
        var source = f.source();
        f.close();
        assertThat(f.demands).isEmpty(); assertThat(f.changed).isEmpty();
        late.forEach(Runnable::run);
        source.setSelectedIndex(2);
        assertThat(f.draft.modified()).isFalse();
    }
    @Test void aDirtySourceEditConflictsWithAnUnseenDiskChangeThenRebasesWithoutLosingTheDraft(@org.junit.jupiter.api.io.TempDir Path home) throws Exception {
        String original = "[ui.theme]\ndark='desktop-colors'\ndesktop_source='auto'\n";
        var file = home.resolve("config.toml"); java.nio.file.Files.writeString(file, original);
        try (var service = java.util.concurrent.CompletableFuture.supplyAsync(() -> new ConfigService(file, false)).join();
             var f = new Fixture(original, true)) {
            f.pick("desktop-source", "gtk4");
            java.nio.file.Files.writeString(file, "[ui.theme]\ndark='desktop-colors'\ndesktop_source='gtk3'\n");
            assertThatThrownBy(() -> service.save(f.draft.edit()).join()).hasRootCauseMessage("ui.theme.desktop_source changed on disk. Review the new value and try again.");
            f.draft.rebase(service.reload().join().values());
            assertThat(selected(f.source())).isEqualTo("gtk4");
            assertThat(f.draft.edit().expected()).containsEntry(CoreSettings.UI_THEME_DESKTOP_SOURCE, Optional.of("gtk3"));
            f.draft.rebase(service.save(f.draft.edit()).join().values());
            assertThat(selected(f.source())).isEqualTo("gtk4"); assertThat(f.draft.modified()).isFalse();
            assertThat(java.nio.file.Files.readString(file)).contains("desktop_source=", "gtk4");
        }
    }
    static List<String> options(JComboBox<?> box) {
        var labels = new ArrayList<String>();
        for (int i=0;i<box.getItemCount();i++) labels.add(((Option) box.getItemAt(i)).label());
        return labels;
    }
    static String selected(JComboBox<?> box) { return ((Option)box.getSelectedItem()).id(); }
    static final class Fixture implements AutoCloseable {
        final SettingsDraft draft;
        final SchemaPage page;
        final AppearanceChoices choices;
        final List<Runnable> changed = new ArrayList<>();
        final List<String> demands = new ArrayList<>();
        String status = "GTK 3";
        final JComboBox<?> originalSource;
        Fixture(String text, boolean available) { this(text, available, false); }
        Fixture(String text, boolean available, boolean enterprise) {
            draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), text, true).values());
            var desktop = available ? new DesktopChoices(true, () -> status,
                (source, dark) -> dark ? AppearanceSelectionTest.OCEAN : AppearanceSelectionTest.MEADOW,
                listener -> { changed.add(listener); return new Subscription(() -> changed.remove(listener)); },
                (source, dark) -> { String demand=source+":"+dark; demands.add(demand); return new Subscription(() -> demands.remove(demand)); }) : DesktopChoices.UNAVAILABLE;
            choices = new AppearanceChoices(100f, false, Optional::empty, ignored -> new Subscription(() -> {}),
                () -> installed(available), Optional.empty(), desktop);
            var system = new CorePages.SystemActions(() -> Path.of("config.toml"), () -> {}, () -> {}, () -> {}, () -> {}, () -> true, Optional.empty());
            var context = PageKit.context(draft, id -> {}, () -> {});
            if (enterprise) {
                var kit = dev.jasper.ui.testsupport.KitFixtures.context(dev.jasper.app.testsupport.EnterpriseLook.theme(false),
                    dev.jasper.app.testsupport.EnterpriseLook.kit());
                context = new PageContext(draft, id -> {}, Runnable::run, (title, initial) -> Optional.empty(), () -> {}, () -> null,
                    () -> kit, SettingsChromeTest.CLASSIC, dev.jasper.app.presentation.Composition.CLASSIC);
            }
            page = CorePages.specs(choices, system).getFirst().build(context);
            page.component(); originalSource=source();
        }
        static InstalledThemes installed(boolean available) {
            var base = AppearanceSelectionTest.THEMES;
            if (!available) return base;
            var dark = new ArrayList<>(base.dark()); var light = new ArrayList<>(base.light());
            dark.add(new InstalledThemes.ThemeOption("desktop-colors", "Desktop colors", AppearanceSelectionTest.OCEAN));
            light.add(new InstalledThemes.ThemeOption("desktop-colors", "Desktop colors", AppearanceSelectionTest.MEADOW));
            var names = new HashMap<>(base.names()); names.put("desktop-colors", "Desktop colors");
            return new InstalledThemes(dark, light, base.schemes(), names);
        }
        JComboBox<?> box(String name) { return kit(page, "ui.appearance:"+name+".input", JComboBox.class); }
        JComboBox<?> source() { return box("desktop-source"); }
        void pick(String name, String id) {
            var box=box(name);
            for(int i=0;i<box.getItemCount();i++) if(((Option)box.getItemAt(i)).id().equals(id)) { box.setSelectedIndex(i); return; }
            throw new AssertionError("No choice "+id);
        }
        void rebase(String text) { draft.rebase(ConfigLoader.parse(Path.of("config.toml"), text, true).values()); }
        @Override public void close() { page.dispose(); }
    }
}
