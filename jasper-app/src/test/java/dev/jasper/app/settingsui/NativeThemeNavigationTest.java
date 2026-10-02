package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.ThemeManager;
import dev.jasper.app.config.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class NativeThemeNavigationTest {
    @ParameterizedTest @ValueSource(strings={"true", "false", "'ignored'", "{nested=true}"})
    void onlyExactRetiredMarketplaceKeyIsIgnored(String value) {
        assertThat(CoreSettings.SCHEMA.find(List.of("themes", "marketplace", "enabled"))).isEmpty();
        var parsed = ConfigLoader.parse(Path.of("config.toml"), "themes.marketplace.enabled=" + value + "\nthemes.marketplace.unknown=1\n", false);
        assertThat(parsed.rejected()).isFalse();
        assertThat(parsed.diagnostics()).singleElement().satisfies(p -> assertThat(p.key()).isEqualTo("themes.marketplace.unknown"));
    }
    @Test void pagesAndPickersOfferOnlyNativeNamesAndLiveNavigationTargets() {
        var system = new CorePages.SystemActions(() -> Path.of("config.toml"), () -> {}, () -> {}, () -> {}, () -> {}, () -> false);
        var context = new PageContext(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", false).values()), id -> {}, Runnable::run, (title, initial) -> Optional.empty(), () -> {});
        var names = ThemeManager.builtIns().stream().map(t -> t.name()).toList();
        var pages = CorePages.pages(() -> names, system,
            ctx -> new GroupPage("keymap", "Keymap", Optional.empty(), false, List::of, ctx),
            ctx -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, ctx)).apply(context);
        var ids = pages.stream().map(SettingsPage::id).toList();
        assertThat(ids).doesNotContain("themes", "themes.intellij", "themes.alacritty").contains("appearance", "terminal.font");
        for (var page : pages) page.parent().ifPresent(parent -> assertThat(ids).contains(parent));
        var chrome = Editors.theme(() -> names).build().apply(CoreSettings.UI_THEME_NAME, context);
        var box = (javax.swing.JComboBox<?>) chrome.component();
        var items = new ArrayList<String>(); for (int i=0; i<box.getItemCount(); i++) items.add(box.getItemAt(i).toString());
        assertThat(items).contains("Studio Dark", "Catppuccin Mocha").doesNotContain("Get More Themes\u2026", "Manage Themes\u2026");
        var workspace = (Editors.TerminalColorsEditor) Editors.terminalColors(() -> names).build().apply(CoreSettings.UI_THEME_WORKSPACE, context);
        workspace.show("match"); assertThat(workspace.value()).isEqualTo("match");
        workspace.show("Studio Dark"); assertThat(workspace.value()).isEqualTo("Studio Dark");
    }
}
