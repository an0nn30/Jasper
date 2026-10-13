package dev.jasper.app.settingsui;

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
        var system = new CorePages.SystemActions(() -> Path.of("config.toml"), () -> {}, () -> {}, () -> {}, () -> {}, () -> false, java.util.Optional.empty());
        var context = new PageContext(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", false).values()), id -> {}, Runnable::run, (title, initial) -> Optional.empty(), () -> {});
        var pages = CorePages.pages(AppearanceFixtures.CHOICES, system,
            ctx -> new GroupPage("keymap", "Keymap", Optional.empty(), false, List::of, ctx),
            ctx -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, ctx)).apply(context);
        var ids = pages.stream().map(SettingsPage::id).toList();
        assertThat(ids).doesNotContain("themes", "themes.intellij", "themes.alacritty").contains("appearance", "terminal.font");
        for (var page : pages) page.parent().ifPresent(parent -> assertThat(ids).contains(parent));
    }
}
