package dev.jasper.browser.settings;

import dev.jasper.browser.item.SearchEngine;
import dev.jasper.browser.settings.BrowserSettings.LinkOpening;
import dev.jasper.browser.settings.BrowserSettings.LocalFileOpening;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Settings › Browser (spec section 17): Start page and Page zoom under General, the two link settings under Link
 * Opening, and the search engine and its custom URLs under Search Engine, with the spec's labels and choices, stored as
 * their tokens in the plugin's settings file.
 */
class BrowserSettingsTest {
    private static final String ID = "dev.jasper.browser";

    @Test void theRowsAreStartPageAndPageZoomThenTheLinkAndSearchSettingsWithTheSpecsLabelsChoicesAndDefaults() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var recording = new AtomicReference<Recording>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                recording.set(new Recording(plugin.settings()));
                BrowserSettings.declare(recording.get());
            });
            List<SettingSpec<?>> specs = recording.get().specs;
            assertThat(specs).extracting(SettingSpec::key).containsExactly("start_page", "page_zoom", "open_links_from_terminal",
                "open_local_web_files", "search_engine", "custom_search_url", "custom_suggestions_url");
            assertThat(specs).extracting(SettingSpec::label).containsExactly("Start page", "Page zoom", "Open Links From Terminal",
                "Open Local Web Files", "Default Search Engine", "Custom Search URL", "Custom Search Suggestions URL");
            assertThat(specs).extracting(SettingSpec::section).containsExactly("General", "General", "Link Opening", "Link Opening",
                "Search Engine", "Search Engine", "Search Engine");
            assertThat(specs).allSatisfy(spec -> assertThat(spec.description()).isNotBlank());
            assertThat(specs.get(1).kind()).isEqualTo(SettingKind.integer(25, 500));
            assertThat(specs.get(1).unit()).isEqualTo("%");
            assertThat(specs.get(1).defaultValue()).isEqualTo(100);
            assertThat(specs).filteredOn(spec -> spec != specs.get(1)).allSatisfy(spec -> assertThat(spec.unit()).isEmpty());
            assertThat(choices(specs.get(2))).containsExactly("ask|Always Ask|ASK", "builtin|Built-in Browser|BUILT_IN", "default|Default Browser|DEFAULT_BROWSER");
            assertThat(choices(specs.get(3))).containsExactly("ask|Always Ask|ASK", "builtin|Built-in Browser|BUILT_IN", "editor|Default Editor|DEFAULT_EDITOR");
            assertThat(choices(specs.get(4))).containsExactly("google|Google|GOOGLE", "duckduckgo|DuckDuckGo|DUCKDUCKGO", "bing|Bing|BING",
                "kagi|Kagi|KAGI", "custom|Custom|CUSTOM");
            assertThat(specs.get(2).defaultValue()).isEqualTo(LinkOpening.ASK);
            assertThat(specs.get(3).defaultValue()).isEqualTo(LocalFileOpening.ASK);
            assertThat(specs.get(4).defaultValue()).isEqualTo(SearchEngine.GOOGLE);
            assertThat(specs.get(5).defaultValue()).isEqualTo("");
            assertThat(specs.get(6).defaultValue()).isEqualTo("");
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec section 17 and plan 4b's P28: each custom URL is an http or https address with %s where the terms go, or
     * empty, the default; anything else is refused with one message.
     */
    @Test void aCustomUrlThatIsNoWebAddressWithThePlaceholderIsRefused() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.setSetting(ID, "search_engine", "custom");
            host.setSetting(ID, "custom_search_url", "https://search.example.com/?q=");
            host.setSetting(ID, "custom_suggestions_url", "search.example.com/suggest?q=%s");
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> declared.set(BrowserSettings.declare(plugin.settings())));
            String refused = "The URL must start with https:// or http:// and contain %s";
            assertThat(host.settingsReports(ID)).containsExactly("custom_search_url: " + refused, "custom_suggestions_url: " + refused);
            assertThat(declared.get().read()).isEqualTo(new BrowserSettings(BrowserSettings.WELCOME, 100, LinkOpening.ASK, LocalFileOpening.ASK,
                SearchEngine.CUSTOM, "", ""));
            host.setSetting(ID, "custom_search_url", "https://search.example.com/?q=%s");
            host.setSetting(ID, "custom_suggestions_url", "https://search.example.com/suggest?q=%s");
            assertThat(host.settingsReports(ID)).isEmpty();
            assertThat(declared.get().read().customSearchUrl()).isEqualTo("https://search.example.com/?q=%s");
            assertThat(declared.get().read().customSuggestionsUrl()).isEqualTo("https://search.example.com/suggest?q=%s");
            host.setSetting(ID, "custom_search_url", "ftp://search.example.com/?q=%s");
            host.setSetting(ID, "custom_suggestions_url", "");
            assertThat(host.settingsReports(ID)).containsExactly("custom_search_url: " + refused);
            assertThat(declared.get().read().customSearchUrl()).as("the default").isEmpty();
            assertThat(declared.get().read().customSuggestionsUrl()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void aSnapshotReadsTheFileTheRememberedChoiceIsWrittenAsItsTokenAndChangesAreHeard() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.setSetting(ID, "open_local_web_files", "editor");
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> declared.set(BrowserSettings.declare(plugin.settings())));
            assertThat(declared.get().read()).isEqualTo(new BrowserSettings(BrowserSettings.WELCOME, 100, LinkOpening.ASK,
                LocalFileOpening.DEFAULT_EDITOR, SearchEngine.GOOGLE, "", ""));
            var heard = new CopyOnWriteArrayList<LinkOpening>();
            declared.get().onLinksChanged(heard::add);

            assertThat(declared.get().remember(LinkOpening.BUILT_IN)).isCompleted();
            assertThat(host.savedSettings(ID)).containsEntry("open_links_from_terminal", "builtin");
            assertThat(declared.get().remember(LocalFileOpening.BUILT_IN)).isCompleted();
            assertThat(host.savedSettings(ID)).containsEntry("open_local_web_files", "builtin");
            assertThat(declared.get().remember(LinkOpening.DEFAULT_BROWSER)).isCompleted();
            assertThat(host.savedSettings(ID)).containsEntry("open_links_from_terminal", "default");
            assertThat(declared.get().read().links()).isEqualTo(LinkOpening.DEFAULT_BROWSER);
            host.setSetting(ID, "open_links_from_terminal", "ask");
            assertThat(heard).containsExactly(LinkOpening.BUILT_IN, LinkOpening.DEFAULT_BROWSER, LinkOpening.ASK);

            host.setSetting(ID, "open_links_from_terminal", "sometimes");
            assertThat(host.settingsReports(ID)).singleElement().asString().startsWith("open_links_from_terminal");
            assertThat(declared.get().read().links()).as("an unknown token is the default").isEqualTo(LinkOpening.ASK);
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec sections 10 and 17, with plan 4b's P33: Page zoom is a whole percent from 25 to 500, Chromium's smallest and
     * largest zoom; anything else is refused for the default, 100.
     */
    @Test void pageZoomIsAWholePercentFromTwentyFiveToFiveHundred() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.setSetting(ID, "page_zoom", 150L);
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> declared.set(BrowserSettings.declare(plugin.settings())));
            assertThat(declared.get().read().pageZoom()).isEqualTo(150);
            for (long zoom : new long[] { 25, 500 }) {
                host.setSetting(ID, "page_zoom", zoom);
                assertThat(host.settingsReports(ID)).isEmpty();
                assertThat(declared.get().read().pageZoom()).isEqualTo((int) zoom);
            }
            for (long zoom : new long[] { 24, 5, 501, 0, -100 }) {
                host.setSetting(ID, "page_zoom", zoom);
                assertThat(host.settingsReports(ID)).singleElement().asString().startsWith("page_zoom: ");
                assertThat(declared.get().read().pageZoom()).as("the default").isEqualTo(100);
            }
            assertThatThrownBy(() -> new BrowserSettings(BrowserSettings.WELCOME, 24, LinkOpening.ASK, LocalFileOpening.ASK,
                SearchEngine.GOOGLE, "", "")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BrowserSettings(BrowserSettings.WELCOME, 501, LinkOpening.ASK, LocalFileOpening.ASK,
                SearchEngine.GOOGLE, "", "")).isInstanceOf(IllegalArgumentException.class);
            assertThat(host.failures()).isEmpty();
        } });
    }

    private static List<String> choices(SettingSpec<?> spec) {
        var kind = (SettingKind.OneOf<?>) spec.kind();
        return kind.choices().stream().map(BrowserSettingsTest::choice).toList();
    }

    private static String choice(Choice<?> choice) {
        return choice.token() + "|" + choice.displayName() + "|" + choice.value();
    }

    /** The plugin's settings, recording each spec declared. */
    private static final class Recording implements PluginSettings {
        final List<SettingSpec<?>> specs = new CopyOnWriteArrayList<>();
        private final PluginSettings settings;

        Recording(PluginSettings settings) {
            this.settings = settings;
        }

        @Override public <T> Setting<T> declare(SettingSpec<T> spec) {
            specs.add(spec);
            return settings.declare(spec);
        }

        @Override public Path file() { return settings.file(); }

        @Override public Subscription onChanged(Runnable handler) { return settings.onChanged(handler); }

        @Override public void page(SettingsPanel panel) { settings.page(panel); }

        @Override public void show() { settings.show(); }
    }
}
