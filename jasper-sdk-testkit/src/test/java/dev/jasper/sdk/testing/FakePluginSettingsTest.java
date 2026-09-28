package dev.jasper.sdk.testing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FakePluginSettingsTest {
    private final FakePluginHost host = new FakePluginHost();
    private static final PluginInfo INFO = new PluginInfo("test.fake", "Fake", "1.0.0", Set.of());

    @AfterEach void close() { host.close(); }

    @Test void declaredValuesComeFromTheFileWithReportsForBadAndUnknownKeys() {
        host.setSetting("test.fake", "font_size", 99L);
        host.setSetting("test.fake", "ratio", 2L);
        host.setSetting("test.fake", "mode", "safe");
        host.setSetting("test.fake", "words", List.of("ls", "cd"));
        host.setSetting("test.fake", "mystery", 1L);
        var size = new AtomicReference<Setting<Integer>>();
        var ratio = new AtomicReference<Setting<Double>>();
        var mode = new AtomicReference<Setting<Integer>>();
        var words = new AtomicReference<Setting<List<String>>>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            ratio.set(context.settings().declare(SettingSpec.of("ratio", SettingKind.number(0.5, 3), 1.0).label("Ratio")));
            mode.set(context.settings().declare(SettingSpec.of("mode",
                SettingKind.choice(List.of(Choice.of("fast", "Fast", 1), Choice.of("safe", "Safe", 2))), 1).label("Mode")));
            words.set(context.settings().declare(SettingSpec.of("words", SettingKind.stringList(), List.of()).label("Words")));
        });
        assertThat(size.get().get()).isEqualTo(13);
        assertThat(ratio.get().get()).isEqualTo(2.0);
        assertThat(mode.get().get()).isEqualTo(2);
        assertThat(words.get().get()).containsExactly("ls", "cd");
        assertThat(host.settingsReports("test.fake")).containsExactlyInAnyOrder(
            "font_size: Use a whole number from 8–48.", "mystery: Unknown setting or table; ignored.");
        assertThat(host.declared("test.fake")).containsExactly("font_size", "ratio", "mode", "words");
    }

    @Test void setSavesTheRawValueAndListenersHearIt() throws Exception {
        var vim = new AtomicReference<Setting<Boolean>>();
        var seen = new ArrayList<Boolean>();
        int[] any = {0};
        host.start(INFO, Set.of(), Set.of(), context -> {
            vim.set(context.settings().declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode")));
            vim.get().onChanged(seen::add);
            context.settings().onChanged(() -> any[0]++);
        });
        vim.get().set(true).get();
        assertThat(vim.get().get()).isTrue();
        assertThat(seen).containsExactly(true);
        assertThat(any[0]).isEqualTo(1);
        assertThat(host.savedSettings("test.fake")).containsEntry("vim", true);
        host.setSetting("test.fake", "vim", "yes");
        assertThat(vim.get().get()).as("a wrong type keeps the last good value").isTrue();
        assertThat(host.settingsReports("test.fake")).singleElement().asString().startsWith("vim: Expected a boolean");
    }

    @Test void invalidSavesAndLateDeclarationsAreRefused() {
        var settings = new AtomicReference<PluginSettings>();
        var size = new AtomicReference<Setting<Integer>>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            settings.set(context.settings());
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            assertThatIllegalArgumentException().isThrownBy(() ->
                context.settings().declare(SettingSpec.of("font_size", SettingKind.bool(), true).label("Again")));
            assertThatIllegalArgumentException().isThrownBy(() ->
                context.settings().declare(SettingSpec.of("font_size.inner", SettingKind.bool(), true).label("Inside")));
            assertThatIllegalArgumentException().isThrownBy(() ->
                context.settings().declare(SettingSpec.of("unlabelled", SettingKind.bool(), true)));
        });
        assertThat(host.active("test.fake")).isTrue();
        assertThatThrownBy(() -> size.get().set(99).get()).isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThatIllegalStateException().isThrownBy(() ->
            settings.get().declare(SettingSpec.of("late", SettingKind.bool(), false).label("Late")))
            .withMessage("Declare settings while the plugin starts: late");
        assertThat(settings.get().file()).isEqualTo(host.dataRoot().resolve("test.fake").resolve("test.fake.toml"));
    }
}
