package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.settingsui.InstalledThemes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Appearance page's choices over the library. */
@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.app.testsupport.EdtTestExtension.class)
class SettingsAppearanceTest {
    @TempDir Path home;

    @Test void desktopMetadataIsApplicationOwnedAndFollowsTheAuthoredBuiltIns() throws Exception {
        try (var f = new DesktopAppearanceTest.Fixture(); var system = dev.jasper.app.platform.SystemAppearance.open()) {
            var choices = SettingsAppearance.choices(system, ThemeLibrary::bundledOnly, java.util.Optional.empty(), f.owner, true);
            assertThat(choices.desktop().available()).isTrue();
            assertThat(choices.themes().get().dark()).extracting(InstalledThemes.ThemeOption::id)
                .endsWith("jasper-meadow", "desktop-colors");
            assertThat(dev.jasper.ui.theme.JasperTheme.builtIns()).extracting(dev.jasper.ui.theme.JasperTheme::id)
                .doesNotContain("desktop-colors");
            var unavailable = SettingsAppearance.choices(system, ThemeLibrary::bundledOnly, java.util.Optional.empty(), null, false);
            assertThat(unavailable.themes().get().dark()).extracting(InstalledThemes.ThemeOption::id).doesNotContain("desktop-colors");
        }
    }

    @Test void linuxWithoutHelperDirectoryStillOffersDesktopColorsAndRetainsTheSavedSource() {
        try (var system = dev.jasper.app.platform.SystemAppearance.open()) {
            // Missing helper discovery supplies no DesktopAppearance owner; Linux eligibility remains independent.
            var choices = SettingsAppearance.choices(system, ThemeLibrary::bundledOnly, java.util.Optional.empty(), null, true);
            assertThat(choices.desktop().available()).isTrue();
            assertThat(choices.themes().get().dark()).extracting(InstalledThemes.ThemeOption::id).endsWith("desktop-colors");
            assertThat(choices.themes().get().light()).extracting(InstalledThemes.ThemeOption::id).endsWith("desktop-colors");
            for (String text : java.util.List.of("", "[ui.theme]\ndark='desktop-colors'\ndesktop_source='gtk4'\n")) {
                var draft = draft(text); var page = page(choices, draft);
                try {
                    page.component();
                    var dark = dev.jasper.app.testsupport.KitTestSupport.require(page.component(), "ui.appearance:dark-theme.input", javax.swing.JComboBox.class);
                    for (int i = 0; i < dark.getItemCount(); i++) {
                        var option = (dev.jasper.ui.model.Option)dark.getItemAt(i);
                        if (option.id().equals("desktop-colors")) assertThat(option.label()).isEqualTo("Desktop colors");
                    }
                    assertThat(draft.modified()).isFalse();
                    pick(page, "dark-theme", "desktop-colors");
                    assertThat(dev.jasper.app.settingsui.DesktopPageProbe.status(page)).contains("helpers unavailable", "bundled colors")
                        .doesNotContain("unavailable on this platform");
                    assertThat(dev.jasper.app.settingsui.DesktopPageProbe.preview(page, true).control()).isEqualTo(
                        dev.jasper.ui.theme.JasperTheme.builtIn("slate-cobalt").orElseThrow().palette(true)
                            .color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB());
                    assertThat(draft.value(dev.jasper.app.config.CoreSettings.UI_THEME_DESKTOP_SOURCE)).isEqualTo(text.isEmpty() ? "auto" : "gtk4");
                } finally { page.dispose(); }
            }
        }
    }

    @Test void renderedPageStartsOnlyStagedToolkitDemandAndClosingThenReopeningReleasesIt() throws Exception {
        try (var f = new DesktopAppearanceTest.Fixture(); var system = dev.jasper.app.platform.SystemAppearance.open()) {
            f.owner.update(DesktopAppearanceTest.authoredConfig()); f.drain();
            var choices = SettingsAppearance.choices(system, ThemeLibrary::bundledOnly, java.util.Optional.empty(), f.owner, true);
            var draft = draft("[ui.theme]\ndark='jasper-meadow'\nlight='jasper-meadow'\n");
            var page = page(choices, draft); page.component(); f.drain();
            assertThat(f.nativeInput.launches()).containsExactly("jasper-desktop-portal");
            pick(page, "dark-theme", "desktop-colors"); f.drain();
            f.nativeInput.awaitToolkit(dev.jasper.app.platform.DesktopObservation.Source.GTK4, true);
            pick(page, "desktop-source", "gtk3"); f.drain();
            f.nativeInput.awaitToolkit(dev.jasper.app.platform.DesktopObservation.Source.GTK4, false);
            f.nativeInput.awaitToolkit(dev.jasper.app.platform.DesktopObservation.Source.GTK3, true);
            page.dispose(); f.drain();
            f.nativeInput.awaitToolkit(dev.jasper.app.platform.DesktopObservation.Source.GTK3, false);
            var reopened = page(choices, draft); reopened.component(); f.drain();
            f.nativeInput.awaitToolkit(dev.jasper.app.platform.DesktopObservation.Source.GTK3, true);
            reopened.dispose(); f.drain();
            f.nativeInput.awaitToolkit(dev.jasper.app.platform.DesktopObservation.Source.GTK3, false);
            assertThat(f.themes.current().chrome().name()).isEqualTo("Jasper Meadow");
        }
    }

    @Test void retiredPreviewCompletionsCannotRefreshClosedPageOrReplaceNewSourceCache() throws Exception {
        try (var f = new DesktopAppearanceTest.Fixture(); var system = dev.jasper.app.platform.SystemAppearance.open()) {
            f.owner.update(DesktopAppearanceTest.authoredConfig()); f.drain();
            var choices = SettingsAppearance.choices(system, ThemeLibrary::bundledOnly, java.util.Optional.empty(), f.owner, true);
            var page = page(choices, draft("[ui.theme]\ndark='desktop-colors'\nlight='jasper-meadow'\ndesktop_source='gtk3'\n"));
            page.component(); f.drain();
            var before = f.themes.current();
            f.nativeInput.palette(dev.jasper.app.platform.DesktopObservation.Source.GTK3, true, "0.1,0.1,0.1,1", "");
            f.edt(); f.work(); // Queue the GTK 3 callback before retiring that demand.
            pick(page, "desktop-source", "gtk4"); f.drain();
            assertThat(dev.jasper.app.settingsui.DesktopPageProbe.preview(page, true).control()).isNotEqualTo(0xff1a1a1a);
            var source = dev.jasper.app.testsupport.KitTestSupport.require(page.component(), "ui.appearance:desktop-source.input", javax.swing.JComboBox.class);
            assertThat(((dev.jasper.ui.model.Option) source.getSelectedItem()).id()).isEqualTo("gtk4");
            f.nativeInput.palette(dev.jasper.app.platform.DesktopObservation.Source.GTK4, true, "0.2,0.2,0.2,1", "");
            f.edt(); f.work(); page.dispose(); f.drain();
            assertThat(f.themes.current()).isEqualTo(before);
            f.nativeInput.awaitToolkit(dev.jasper.app.platform.DesktopObservation.Source.GTK4, false);
        }
    }

    @Test void renderedTilesReceiveNativePreviewColorsForTheirOwnPolarityWithoutInstallingThem() throws Exception {
        try (var f = new DesktopAppearanceTest.Fixture(); var system = dev.jasper.app.platform.SystemAppearance.open()) {
            f.owner.update(DesktopAppearanceTest.authoredConfig()); f.drain();
            var choices = SettingsAppearance.choices(system, ThemeLibrary::bundledOnly, java.util.Optional.empty(), f.owner, true);
            var page = page(choices, draft("[ui.theme]\ndark='desktop-colors'\nlight='desktop-colors'\ndesktop_source='gtk4'\n"));
            try {
                page.component(); f.drain();
                var before = f.themes.current();
                f.nativeInput.awaitRequested(dev.jasper.app.platform.DesktopObservation.Source.GTK4, true);
                f.nativeInput.palette(dev.jasper.app.platform.DesktopObservation.Source.GTK4, true, "0.1,0.1,0.1,1", ""); f.drain();
                f.nativeInput.awaitRequested(dev.jasper.app.platform.DesktopObservation.Source.GTK4, false);
                f.nativeInput.palette(dev.jasper.app.platform.DesktopObservation.Source.GTK4, false, "0.95,0.95,0.95,1", ""); f.drain();
                assertThat(dev.jasper.app.settingsui.DesktopPageProbe.preview(page, true).control()).isEqualTo(0xff1a1a1a);
                assertThat(dev.jasper.app.settingsui.DesktopPageProbe.preview(page, false).control()).isEqualTo(0xfff2f2f2);
                assertThat(f.themes.current()).isEqualTo(before);
            } finally { page.dispose(); }
        }
    }

    @Test void missingToolkitStatusRemainsAppliedWhileDraftSourceIsPending() throws Exception {
        try (var f = new DesktopAppearanceTest.Fixture(); var system = dev.jasper.app.platform.SystemAppearance.open()) {
            f.owner.update(DesktopAppearanceTest.config("gtk4", true)); f.drain();
            var choices = SettingsAppearance.choices(system, ThemeLibrary::bundledOnly, java.util.Optional.empty(), f.owner, true);
            var draft = draft("[ui.theme]\ndark='desktop-colors'\ndesktop_source='gtk4'\n");
            var page = page(choices, draft);
            try {
                page.component(); f.drain(); f.nativeInput.fail(dev.jasper.app.platform.DesktopObservation.Source.GTK4); f.drain();
                String applied = f.owner.current().status();
                assertThat(dev.jasper.app.settingsui.DesktopPageProbe.status(page)).isEqualTo(applied).contains("unavailable");
                assertThat(draft.modified()).isFalse();
                pick(page,"desktop-source","gtk3"); f.drain();
                assertThat(dev.jasper.app.settingsui.DesktopPageProbe.status(page)).contains("Pending Apply: GTK 3", "Applied: " + applied);
                assertThat(draft.edit().changes()).containsOnlyKeys(dev.jasper.app.config.CoreSettings.UI_THEME_DESKTOP_SOURCE);
            } finally { page.dispose(); }
        }
    }

    private static dev.jasper.app.settingsui.SettingsDraft draft(String text) {
        return new dev.jasper.app.settingsui.SettingsDraft(dev.jasper.app.config.ConfigLoader.parse(Path.of("config.toml"), text, true).values());
    }
    private static dev.jasper.app.settingsui.SchemaPage page(dev.jasper.app.settingsui.AppearanceChoices choices,
                                                             dev.jasper.app.settingsui.SettingsDraft draft) {
        var context = new dev.jasper.app.settingsui.PageContext(draft, id -> {}, Runnable::run, (title, initial) -> java.util.Optional.empty(),
            () -> {}, () -> null, dev.jasper.app.testsupport.KitTestSupport::context);
        var system = new dev.jasper.app.settingsui.CorePages.SystemActions(() -> Path.of("config.toml"), () -> {}, () -> {}, () -> {}, () -> {}, () -> true, java.util.Optional.empty());
        return dev.jasper.app.settingsui.CorePages.specs(choices, system).getFirst().build(context);
    }
    private static void pick(dev.jasper.app.settingsui.SchemaPage page, String name, String value) {
        var box = dev.jasper.app.testsupport.KitTestSupport.require(page.component(), "ui.appearance:"+name+".input", javax.swing.JComboBox.class);
        for (int i=0;i<box.getItemCount();i++) if (((dev.jasper.ui.model.Option)box.getItemAt(i)).id().equals(value)) { box.setSelectedIndex(i); return; }
        throw new AssertionError("Missing choice " + value);
    }

    @Test void theChoicesNameEveryInstalledThemeEvenOneWithoutAnyUiVariant() throws Exception {
        var themes = Files.createDirectories(home.resolve("themes"));
        Files.writeString(themes.resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
        Files.writeString(themes.resolve("zenburn.json"), ThemeTestSupport.terminalThemeJson("Zenburn"));
        Files.writeString(themes.resolve("cobalt-ii.json"), ThemeTestSupport.terminalThemeJson("Slate Cobalt"));
        var choices = SettingsAppearance.themeChoices(ThemeLibrary.read(themes, ThemeLibrary.bundledOnly()));
        assertThat(choices.dark()).extracting(InstalledThemes.ThemeOption::id)
            .containsExactly("slate-cobalt", "catppuccin-frappe", "catppuccin-macchiato", "catppuccin-mocha", "ubuntu-yaru", "jasper-meadow", "ocean");
        assertThat(choices.light()).extracting(InstalledThemes.ThemeOption::id).containsExactly("slate-cobalt", "catppuccin-latte", "ubuntu-yaru", "jasper-meadow", "ocean");
        assertThat(choices.names()).containsOnly(Map.entry("slate-cobalt", "Slate Cobalt (slate-cobalt)"), Map.entry("cobalt-ii", "Slate Cobalt (cobalt-ii)"),
            Map.entry("catppuccin-latte", "Catppuccin Latte"), Map.entry("catppuccin-frappe", "Catppuccin Frapp\u00e9"),
            Map.entry("catppuccin-macchiato", "Catppuccin Macchiato"), Map.entry("catppuccin-mocha", "Catppuccin Mocha"),
            Map.entry("ubuntu-yaru", "Ubuntu Yaru"), Map.entry("jasper-meadow", "Jasper Meadow"), Map.entry("ocean", "Ocean"), Map.entry("zenburn", "Zenburn"));
    }
}
