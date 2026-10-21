package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownMode;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.editor.markdown.MarkdownMode.*;

class MarkdownPreferencesTest {
    @Test void defaultsReloadConfigurationAndRestartUseSavedValue() {
        var saved = new AtomicReference<>(SOURCE);
        var preferences = new MarkdownPreferences(saved::get, mode -> { saved.set(mode); return CompletableFuture.completedFuture(null); });
        assertThat(preferences.current()).isEqualTo(SOURCE);
        preferences.select(PREVIEW).join();
        assertThat(new MarkdownPreferences(saved::get, mode -> CompletableFuture.completedFuture(null)).current()).isEqualTo(PREVIEW);
        saved.set(SPLIT);
        assertThat(preferences.current()).isEqualTo(SPLIT);
    }
    @Test void repeatClicksAreSerializedAndFailuresRecoverWithoutLosingLatestChoice() {
        var saved = new AtomicReference<>(SOURCE);
        var writes = new ArrayList<MarkdownMode>();
        var completions = new ArrayList<CompletableFuture<Void>>();
        var preferences = new MarkdownPreferences(saved::get, mode -> {
            writes.add(mode); var future = new CompletableFuture<Void>(); completions.add(future); return future;
        });
        var first = preferences.select(PREVIEW);
        var second = preferences.select(SPLIT);
        var third = preferences.select(SPLIT);
        assertThat(preferences.current()).isEqualTo(SPLIT);
        assertThat(writes).containsExactly(PREVIEW);
        completions.getFirst().completeExceptionally(new IllegalStateException("write failed"));
        assertThat(first).isCompletedExceptionally();
        assertThat(writes).containsExactly(PREVIEW, SPLIT);
        saved.set(SPLIT); completions.get(1).complete(null); completions.get(2).complete(null);
        assertThat(second).isCompleted(); assertThat(third).isCompleted();
        assertThat(writes).containsExactly(PREVIEW, SPLIT, SPLIT);
        assertThat(preferences.current()).isEqualTo(SPLIT);
    }
    @Test void synchronousWriterFailureDoesNotPoisonTheTail() {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var preferences = new MarkdownPreferences(() -> SOURCE, mode -> {
            if (count.getAndIncrement() == 0) throw new IllegalStateException("unavailable");
            return CompletableFuture.completedFuture(null);
        });
        assertThat(preferences.select(PREVIEW)).isCompletedExceptionally();
        preferences.select(SPLIT).join();
        assertThat(preferences.current()).isEqualTo(SPLIT);
    }
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path home;
    @Test void declaredChoiceUsesStableTokensAndSettingsReloadsTheSharedDefault() {
        try (var host = new dev.jasper.sdk.testing.FakePluginHost(home)) {
            var value = new AtomicReference<EditorSettings.Declared>();
            var info = new dev.jasper.sdk.PluginInfo("dev.jasper.editor", "Editor", "0.1.0", java.util.Set.of());
            host.start(info, java.util.Set.of(), java.util.Set.of(), context -> value.set(new EditorSettings.Declared(context.settings(), java.util.Set.of())));
            var declared = value.get();
            assertThat(declared.markdownPreferences().current()).isEqualTo(SOURCE);
            host.setSetting(info.id(), "markdown_view_mode", "preview");
            assertThat(declared.markdownPreferences().current()).isEqualTo(PREVIEW);
            host.setSetting(info.id(), "markdown_view_mode", "split");
            assertThat(declared.markdownPreferences().current()).isEqualTo(SPLIT);
            host.setSetting(info.id(), "markdown_view_mode", "invalid");
            assertThat(declared.markdownPreferences().current()).isEqualTo(SOURCE);
            assertThat(host.settingsReports(info.id())).isNotEmpty();
        }
    }

}
