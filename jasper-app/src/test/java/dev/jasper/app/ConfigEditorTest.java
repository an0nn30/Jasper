package dev.jasper.app;

import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfigEditorTest {
    @Test void fallsBackFromEditToOpenThenRevealWithoutChangingThePath() {
        var calls = new ArrayList<String>();
        Path file = Path.of("/tmp/settings.toml");
        var editor = new ConfigEditor(path -> { calls.add("edit:" + path); throw new IllegalStateException(); },
            path -> { calls.add("open:" + path); throw new IllegalStateException(); }, path -> calls.add("reveal:" + path));
        editor.open(file);
        assertThat(calls).containsExactly("edit:" + file, "open:" + file, "reveal:" + file);
    }
    @Test void successfulEditStopsFallbackAndTotalFailureIsReported() {
        Path file = Path.of("/tmp/settings.toml");
        var opened = new ArrayList<Path>();
        new ConfigEditor(opened::add, path -> { throw new AssertionError("unexpected open"); },
            path -> { throw new AssertionError("unexpected reveal"); }).open(file);
        assertThat(opened).containsExactly(file);
        var broken = new ConfigEditor(path -> { throw new IllegalStateException("edit"); },
            path -> { throw new IllegalStateException("open"); }, path -> { throw new IllegalStateException("reveal"); });
        assertThatThrownBy(() -> broken.open(file)).isInstanceOf(IllegalStateException.class).hasMessageContaining(file.toString());
    }
}
