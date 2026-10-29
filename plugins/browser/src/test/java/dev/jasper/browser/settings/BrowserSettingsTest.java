package dev.jasper.browser.settings;

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

/**
 * Settings › Browser (spec section 17): Start page under General, and the two link settings under Link Opening with
 * the spec's labels and choices, stored as their tokens in the plugin's settings file.
 */
class BrowserSettingsTest {
    private static final String ID = "dev.jasper.browser";

    @Test void theRowsAreStartPageThenTheTwoLinkSettingsWithTheSpecsLabelsChoicesAndDefaults() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var recording = new AtomicReference<Recording>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                recording.set(new Recording(plugin.settings()));
                BrowserSettings.declare(recording.get());
            });
            List<SettingSpec<?>> specs = recording.get().specs;
            assertThat(specs).extracting(SettingSpec::key).containsExactly("start_page", "open_links_from_terminal", "open_local_web_files");
            assertThat(specs).extracting(SettingSpec::label).containsExactly("Start page", "Open Links From Terminal", "Open Local Web Files");
            assertThat(specs).extracting(SettingSpec::section).containsExactly("General", "Link Opening", "Link Opening");
            assertThat(specs).allSatisfy(spec -> assertThat(spec.description()).isNotBlank());
            assertThat(choices(specs.get(1))).containsExactly("ask|Always Ask|ASK", "builtin|Built-in Browser|BUILT_IN", "default|Default Browser|DEFAULT_BROWSER");
            assertThat(choices(specs.get(2))).containsExactly("ask|Always Ask|ASK", "builtin|Built-in Browser|BUILT_IN", "editor|Default Editor|DEFAULT_EDITOR");
            assertThat(specs.get(1).defaultValue()).isEqualTo(LinkOpening.ASK);
            assertThat(specs.get(2).defaultValue()).isEqualTo(LocalFileOpening.ASK);
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void aSnapshotReadsTheFileTheRememberedChoiceIsWrittenAsItsTokenAndChangesAreHeard() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.setSetting(ID, "open_local_web_files", "editor");
            var declared = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
                plugin -> declared.set(BrowserSettings.declare(plugin.settings())));
            assertThat(declared.get().read()).isEqualTo(new BrowserSettings(BrowserSettings.WELCOME, LinkOpening.ASK, LocalFileOpening.DEFAULT_EDITOR));
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
