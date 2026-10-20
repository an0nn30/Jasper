package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.settings.SettingValues;
import dev.jasper.app.settingsui.SettingsDraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.8: user-saved, then kit default, then core default, from the first snapshot of a fresh home to Settings' Reset. */
class DefaultsPrecedenceTest {
    static final DefaultSource KIT = new DefaultSource(Map.of(CoreSettings.PALETTE_VISIBLE_ROWS, 8, CoreSettings.WINDOW_TOOLBAR_HEIGHT, 34,
        CoreSettings.UI_THEME_DARK, "catppuccin-mocha", CoreSettings.UI_THEME_LIGHT, "catppuccin-latte", CoreSettings.WINDOW_TOOLBAR, ToolbarMode.ICONS));
    @TempDir Path home;

    private ConfigService service(Path file) {
        return new ConfigService(file, false, Executors.newSingleThreadScheduledExecutor(), Runnable::run, KIT);
    }

    @Test void aFreshHomesFirstSnapshotCarriesTheKitsDefaults() {
        try (var service = service(home.resolve("missing/config.toml"))) {
            var snapshot = service.initialState().snapshot();
            assertThat(snapshot.visibleRows()).isEqualTo(8);
            assertThat(snapshot.toolbarHeight()).isEqualTo(34);
            assertThat(snapshot.toolbar()).isEqualTo(ToolbarMode.ICONS);
            assertThat(snapshot.darkTheme()).isEqualTo("catppuccin-mocha");
            assertThat(snapshot.lightTheme()).isEqualTo("catppuccin-latte");
            assertThat(service.initialState().defaults()).isSameAs(KIT);
            assertThat(service.defaults()).isSameAs(KIT);
        }
    }

    @Test void aSavedValueWinsAndASyntaxErrorKeepsTheKitsDefaults() throws Exception {
        Path file = home.resolve("config.toml");
        Files.writeString(file, "[palette]\nvisible_rows = 3\n");
        try (var service = service(file)) {
            assertThat(service.initialState().snapshot().visibleRows()).isEqualTo(3);
            assertThat(service.initialState().snapshot().toolbarHeight()).as("unsaved: the kit's").isEqualTo(34);
        }
        Files.writeString(file, "[palette\n");
        try (var service = service(file)) { assertThat(service.initialState().snapshot().visibleRows()).isEqualTo(8); }
        assertThat(ConfigLoader.parse(file, "[palette\n", false, KIT).snapshot().darkTheme()).isEqualTo("catppuccin-mocha");
        assertThat(ConfigLoader.parse(file, "", false).snapshot().darkTheme()).as("the core look").isEqualTo(CoreSettings.UI_THEME_DARK.defaultValue());
        assertThat(ConfigSnapshot.defaults(DefaultSource.CORE)).isEqualTo(ConfigSnapshot.defaults());
    }

    @Test void aSavedToolbarModeWinsOverTheLooks() {
        Path file = home.resolve("config.toml");
        assertThat(ConfigLoader.parse(file, "[window]\ntoolbar = \"hidden\"\n", false, KIT).snapshot().toolbar()).isEqualTo(ToolbarMode.HIDDEN);
        assertThat(ConfigLoader.parse(file, "", false, KIT).snapshot().toolbar()).as("unsaved: the look's").isEqualTo(ToolbarMode.ICONS);
    }

    @Test void anInvalidSavedValueFallsBackToTheKitsDefault() {
        var read = ConfigLoader.parse(home.resolve("config.toml"), "[palette]\nvisible_rows = 40\n\n[ui.theme]\ndark = \"Not An Id\"\n", false, KIT);
        assertThat(read.snapshot().visibleRows()).isEqualTo(8);
        assertThat(read.snapshot().darkTheme()).isEqualTo("catppuccin-mocha");
        assertThat(read.diagnostics()).isNotEmpty();
        assertThat(ConfigLoader.parse(home.resolve("config.toml"), "[palette]\nvisible_rows = 40\n", false).snapshot().visibleRows())
            .as("the core look").isEqualTo(CoreSettings.PALETTE_VISIBLE_ROWS.defaultValue());
    }

    @Test void theDraftShowsAndResetsToTheEffectiveDefault() {
        var draft = new SettingsDraft(SettingValues.defaults(CoreSettings.SCHEMA), KIT);
        assertThat(draft.value(CoreSettings.PALETTE_VISIBLE_ROWS)).as("an unsaved allowlisted key").isEqualTo(8);
        assertThat(draft.defaultValue(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(8);
        draft.stage(CoreSettings.PALETTE_VISIBLE_ROWS, Optional.of(12));
        assertThat(draft.value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(12);
        draft.stage(CoreSettings.PALETTE_VISIBLE_ROWS, Optional.empty());
        assertThat(draft.value(CoreSettings.PALETTE_VISIBLE_ROWS)).as("Reset gives the effective default").isEqualTo(8);
        assertThat(draft.defaultValue(CoreSettings.UI_FONT_SIZE)).as("outside the allowlist: the core default")
            .isEqualTo(CoreSettings.UI_FONT_SIZE.defaultValue());
        var saved = ConfigLoader.parse(home.resolve("config.toml"), "[palette]\nvisible_rows = 3\n", false, KIT).values();
        assertThat(new SettingsDraft(saved, KIT).value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(3);
        assertThat(new SettingsDraft(saved).defaultValue(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(CoreSettings.PALETTE_VISIBLE_ROWS.defaultValue());
    }

    /** Spec 5.8's audit: the editors that compare with a core default edit no allowlisted key, whose rows go through SchemaPage. */
    @Test void noAllowlistedKeyReachesACoreDefaultComparison() throws Exception {
        Path ui = Path.of(System.getProperty("jasper.repoRoot")).resolve("jasper-app/src/main/java/dev/jasper/app/settingsui");
        for (String editor : List.of("PlatformFontSize.java", "WorkspaceEditors.java", "NotificationGroupsEditor.java")) {
            String text = Files.readString(ui.resolve(editor));
            for (String key : List.of("UI_THEME_LIGHT", "UI_THEME_DARK", "PALETTE_VISIBLE_ROWS", "WINDOW_TOOLBAR_HEIGHT"))
                assertThat(text).as(editor + " " + key).doesNotContain(key);
        }
        String pages = Files.readString(ui.resolve("CorePages.java"));
        assertThat(pages).contains(".row(WINDOW_TOOLBAR, Editors.segments(", ".row(WINDOW_TOOLBAR_HEIGHT, Editors.stepper(\"px\"))", ".row(PALETTE_VISIBLE_ROWS, Editors.stepper(\"\"))",
            ".row(UI_THEME_DARK).shownWhen(false)", ".row(UI_THEME_LIGHT).shownWhen(false)");
        assertThat(Files.readString(ui.resolve("Editors.java"))).as("OptionalNumber is made only for Float settings")
            .contains("static final class OptionalNumber implements ValueEditor<Float>");
        // Spec 5.8's table: these editors compare with the effective default (equal to the core one outside the allowlist).
        assertThat(Files.readString(ui.resolve("Editors.java"))).contains("unset = effectiveDefault(draft, setting);");
        assertThat(Files.readString(ui.resolve("PlatformFontSize.java"))).contains("Editors.effectiveDefault(draft,UI_FONT_SIZE)")
            .doesNotContain("UI_FONT_SIZE.defaultValue()");
        assertThat(Files.readString(ui.resolve("WorkspaceEditors.java"))).doesNotContain("UI_WORKSPACE_OPACITY.defaultValue()");
    }
}
