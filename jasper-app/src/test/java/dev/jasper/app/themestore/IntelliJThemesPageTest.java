package dev.jasper.app.themestore;

import static dev.jasper.app.appearance.ThemeFixtures.plugin;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.settingsui.CorePages;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsDraft;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTestExtension.class)
class IntelliJThemesPageTest {
    @TempDir Path home;
    private FakeMarketplace market;
    private ThemeCatalog catalog;
    private ThemeStore store;
    private SettingsDraft draft;
    private PageContext context;
    private Optional<Path> chosen = Optional.empty();
    private final List<String> confirmations = new ArrayList<>();
    private final List<URI> browsed = new ArrayList<>();

    @BeforeEach void start() throws Exception {
        market = new FakeMarketplace();
        catalog = ThemeCatalog.beside(home.resolve("config.toml"));
        catalog.scan();
        store = new ThemeStore(catalog, market.client(), Runnable::run, Runnable::run, new ThemeStore.Hooks(
            () -> { catalog.scan(); return CompletableFuture.completedFuture(null); }, browsed::add, folder -> { },
            (parent, question) -> { confirmations.add(question); return true; }), () -> { });
        draft = new SettingsDraft(ConfigLoader.parse(home.resolve("config.toml"), "", true).values());
        context = new PageContext(draft, id -> { }, Runnable::run, (title, initial) -> chosen, () -> { });
    }

    @AfterEach void stop() { store.close(); market.close(); }

    private IntelliJThemesPage page() {
        var system = new CorePages.SystemActions(() -> home.resolve("config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> false);
        var header = CorePages.specs(List::of, system).stream().filter(spec -> spec.id().equals(CorePages.INTELLIJ_THEMES))
            .findFirst().orElseThrow().build(context);
        var page = (IntelliJThemesPage) store.page(context, header);
        page.component();
        return page;
    }

    @Test void theMarketplaceTabFollowsTheSettingAndRevealPicksATab() {
        var page = page();
        assertThat(page.id()).isEqualTo("themes.intellij");
        assertThat(page.tabTitles()).containsExactly("Marketplace", "Installed");
        draft.stage(CoreSettings.THEMES_MARKETPLACE_ENABLED, Optional.of(false));
        assertThat(page.tabTitles()).containsExactly("Installed");
        page.reveal("marketplace");
        assertThat(page.selectedTab()).isEqualTo("Installed");
        assertThat(market.requests).isEmpty();
        draft.stage(CoreSettings.THEMES_MARKETPLACE_ENABLED, Optional.of(true));
        assertThat(page.tabTitles()).containsExactly("Marketplace", "Installed");
        page.reveal("marketplace");
        assertThat(page.selectedTab()).isEqualTo("Marketplace");
        page.reveal("installed");
        assertThat(page.selectedTab()).isEqualTo("Installed");
    }

    @Test void searchingAndInstallingAThemeOffersToUseIt() throws Exception {
        market.theme(home.resolve("served"), 12275, "com.vermouthx.idea", "Dracula Theme", "Dracula", "2.3.1");
        var page = page();
        page.selected();
        var tab = page.marketplace();
        assertThat(market.requests).contains("/api/searchPlugins?tags=Theme&max=20&offset=0&orderBy=downloads");
        assertThat(tab.listed()).containsExactly("Dracula Theme");
        tab.select("Dracula Theme");
        assertThat(tab.details()).contains("Dracula Theme", "Vendor 12275", "Version 2.3.1", "A dark theme");
        assertThat(market.requests).noneMatch(request -> request.contains("example.invalid"));
        assertThat(tab.installButton().getText()).isEqualTo("Install");
        tab.installButton().doClick();
        assertThat(home.resolve("themes/com.vermouthx.idea-2.3.1.jar")).isRegularFile();
        assertThat(catalog.latest().find("Dracula")).isPresent();
        assertThat(page.banner().text()).isEqualTo("Installed Dracula Theme.");
        assertThat(page.banner().actionText()).isEqualTo("Use \u201cDracula\u201d");
        assertThat(tab.installButton().getText()).isEqualTo("Installed");
        page.banner().click();
        assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Dracula");
        page.reveal("installed");
        assertThat(page.installed().listed()).containsExactly("Dracula Theme");
        assertThat(page.installed().details()).contains("2.3.1", "Marketplace");
        tab.viewButton().doClick();
        assertThat(browsed).containsExactly(URI.create("https://plugins.jetbrains.com/plugin/12275"));
    }

    @Test void anInstalledMarketplaceThemeOffersItsUpdate() throws Exception {
        market.theme(home.resolve("served"), 12275, "com.vermouthx.idea", "Dracula Theme", "Dracula", "2.4.0");
        var installer = new ThemeInstaller(home.resolve("themes"));
        Path part = installer.downloadTarget(new MarketplaceClient.Release(1, "2.3.1", "x.jar"));
        plugin(part, "Dracula", true, "#282a36", "282a36");
        installer.installDownload(part, ".jar", new Provenance.Entry(12275, "com.vermouthx.idea", "Dracula Theme", "2.3.1"));
        catalog.scan();
        var page = page();
        page.reveal("installed");
        var tab = page.installed();
        tab.select("Dracula Theme");
        assertThat(tab.updateButton().isVisible()).isTrue();
        assertThat(tab.updateButton().getText()).isEqualTo("Update to 2.4.0");
        tab.updateButton().doClick();
        assertThat(home.resolve("themes/com.vermouthx.idea-2.4.0.jar")).isRegularFile();
        assertThat(home.resolve("themes/com.vermouthx.idea-2.3.1.jar")).doesNotExist();
        assertThat(tab.updateButton().isVisible()).isFalse();
    }

    @Test void installingFromDiskAsksBeforeReplacingAndUninstallAsksFirst() throws Exception {
        chosen = Optional.of(plugin(home.resolve("Downloads/nord.jar"), "Nord", true, "#2e3440", "2e3440"));
        var page = page();
        page.reveal("installed");
        var tab = page.installed();
        tab.installFromDiskButton().doClick();
        assertThat(home.resolve("themes/nord.jar")).isRegularFile();
        assertThat(tab.listed()).containsExactly("nord");
        assertThat(page.banner().actionText()).isEqualTo("Use \u201cNord\u201d");
        tab.installFromDiskButton().doClick();
        assertThat(confirmations).containsExactly("nord.jar is already installed. Replace it?");
        tab.select("nord");
        assertThat(tab.useButtons()).containsExactly("Use \u201cNord\u201d");
        tab.uninstallButton().doClick();
        assertThat(confirmations.getLast()).isEqualTo("Uninstall nord? Its themes: Nord.");
        assertThat(home.resolve("themes/nord.jar")).doesNotExist();
        assertThat(tab.listed()).isEmpty();
        assertThat(tab.details()).contains("No theme plugins installed");
        assertThat(market.requests).isEmpty();
    }

    @Test void aMarketplaceErrorOffersRetry() {
        market.bytes("/api/searchPlugins", 500, new byte[0]);
        var page = page();
        page.selected();
        assertThat(page.marketplace().status()).isEqualTo("The Marketplace answered HTTP 500.");
        assertThat(page.marketplace().retryButton().isVisible()).isTrue();
        market.json("/api/searchPlugins", FakeMarketplace.search(0));
        page.marketplace().retryButton().doClick();
        assertThat(page.marketplace().status()).isEqualTo("The Marketplace lists no themes.");
        assertThat(page.marketplace().retryButton().isVisible()).isFalse();
    }
}
