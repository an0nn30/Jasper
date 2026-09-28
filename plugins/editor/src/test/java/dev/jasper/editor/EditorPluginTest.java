package dev.jasper.editor;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.ui.EditorEnvironment;
import dev.jasper.editor.ui.EditorWindow;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class EditorPluginTest {
    @TempDir Path home;
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of());

    private static EditorPlugin plugin() {
        return new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
            owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false));
    }

    @Test void registersItsActionsAndPublishesTheEditorService() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.actions()).contains("dev.jasper.editor.open|Open in Editor…|true",
                "dev.jasper.editor.new|New Editor File|true", "dev.jasper.editor.show|Show Editor|true");
            assertThat(host.menu("FILE")).contains("item:dev.jasper.editor.open", "item:dev.jasper.editor.new");
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(Files.writeString(home.resolve("a.json"), "{}\n"));
            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
            while (host.runBackground() > 0) { }
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void theOpenActionUsesATerminalWindowsChooserAndClosingTheWindowLetsAFreshOneOpen() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            host.queuePathSelection(List.of(Files.writeString(home.resolve("b.yaml"), "a: 1\n")));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
            while (host.runBackground() > 0) { }
            assertThat(host.contentOwner(host.contentTabs().getFirst())).isInstanceOf(dev.jasper.sdk.terminal.WindowHandle.class);
            assertThat(host.contentTabs().getFirst().requestClose().join()).isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(host.invoke("dev.jasper.editor.new", terminal, null)).isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
        }
    }

    @Test void changingTheAppearanceRecoloursOpenTabs() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(Files.writeString(home.resolve("a.json"), "{\"a\": true}\n"));
            while (host.runBackground() > 0) { }
            RSyntaxTextArea area = find(host.contentComponent(host.contentTabs().getFirst())).orElseThrow();
            Color before = area.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground;
            host.setVariant(consumer.get().appearance().variant() == Variant.DARK ? Variant.LIGHT : Variant.DARK);
            host.flush();
            assertThat(area.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground).isNotEqualTo(before);
        }
    }

    private static Optional<RSyntaxTextArea> find(Component component) {
        if (component instanceof RSyntaxTextArea area) return Optional.of(area);
        if (component instanceof Container container)
            for (Component child : container.getComponents()) {
                Optional<RSyntaxTextArea> found = find(child);
                if (found.isPresent()) return found;
            }
        return Optional.empty();
    }

    @Test void badSettingsAreReportedAtStartAndOnEveryChange() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.setSetting("dev.jasper.editor", "font_size", 99L);
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.settingsReports("dev.jasper.editor")).containsExactly("font_size: Use a whole number from 8\u201348.");
            host.setSetting("dev.jasper.editor", "tab_size", 0L);
            assertThat(host.settingsReports("dev.jasper.editor")).contains("tab_size: Use a whole number from 1\u201316.");
        }
    }
}
