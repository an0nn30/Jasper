package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The registry calls: the choice rule with a stand-in registry everywhere, the real registry only on Windows. */
class WindowsRegistryTest {
    private static BiFunction<String, String, Optional<String>> registry(Map<String, String> values) {
        return (key, name) -> Optional.ofNullable(values.get(key + "|" + name));
    }

    @Test void userChoiceLatestComesBeforeUserChoice() {
        String base = "Base";
        assertThat(WindowsRegistry.userChoice(base, registry(Map.of()))).isEmpty();
        assertThat(WindowsRegistry.userChoice(base, registry(Map.of("Base\\UserChoice|ProgId", "A")))).contains("A");
        assertThat(WindowsRegistry.userChoice(base, registry(Map.of("Base\\UserChoice|ProgId", "A",
                "Base\\UserChoiceLatest\\ProgId|ProgId", "B")))).contains("B");
        assertThat(WindowsRegistry.userChoice(base, registry(Map.of("Base\\UserChoice|ProgId", "A",
                "Base\\UserChoiceLatest\\ProgId|ProgId", "B", "Base\\UserChoiceLatest|ProgId", "C")))).contains("C");
        BiFunction<String, String, Optional<String>> failing = (key, name) -> {
            throw new UncheckedIOException(new IOException("RegGetValueW failed with 5"));
        };
        assertThatThrownBy(() -> WindowsRegistry.userChoice(base, failing)).isInstanceOf(UncheckedIOException.class);
    }

    @Test @DisabledOnOs(OS.WINDOWS) void elsewhereEveryCallThrows() {
        assertThatThrownBy(() -> WindowsRegistry.read("Software", "x")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> WindowsRegistry.exists("Software")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> WindowsRegistry.writeString("Software", "x", "y")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> WindowsRegistry.friendlyAppName(".txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(WindowsRegistry::changeNotify).isInstanceOf(IOException.class);
    }

    @Test @EnabledOnOs(OS.WINDOWS) void aScratchKeyRoundTrips() throws IOException {
        String scratch = "Software\\JasperTest-" + UUID.randomUUID();
        try {
            assertThat(WindowsRegistry.exists(scratch)).isFalse();
            assertThat(WindowsRegistry.read(scratch, "Name")).isEmpty();
            WindowsRegistry.writeString(scratch + "\\Sub", "Name", "Value");
            assertThat(WindowsRegistry.exists(scratch)).isTrue();
            assertThat(WindowsRegistry.exists(scratch + "\\Sub")).isTrue();
            assertThat(WindowsRegistry.read(scratch + "\\Sub", "Name")).contains("Value");
            WindowsRegistry.writeString(scratch + "\\Sub", "Empty", "");
            assertThat(WindowsRegistry.read(scratch + "\\Sub", "Empty")).contains("");
        } finally {
            WindowsUserPath.delete(scratch);
        }
        assertThat(WindowsRegistry.exists(scratch)).isFalse();
    }

    @Test @EnabledOnOs(OS.WINDOWS) void theRealRegistryOrdersUserChoiceLatestFirst() throws IOException {
        String scratch = "Software\\JasperTest-" + UUID.randomUUID();
        BiFunction<String, String, Optional<String>> real = (key, name) -> {
            try {
                return WindowsRegistry.read(key, name);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        };
        try {
            assertThat(WindowsRegistry.userChoice(scratch, real)).isEmpty();
            WindowsRegistry.writeString(scratch + "\\UserChoice", "ProgId", "Older");
            assertThat(WindowsRegistry.userChoice(scratch, real)).contains("Older");
            WindowsRegistry.writeString(scratch + "\\UserChoiceLatest", "ProgId", "Newer");
            assertThat(WindowsRegistry.userChoice(scratch, real)).contains("Newer");
        } finally {
            WindowsUserPath.delete(scratch);
        }
    }

    @Test @EnabledOnOs(OS.WINDOWS) void aFriendlyNameOrNothing() throws IOException {
        assertThat(WindowsRegistry.friendlyAppName(".jasper-no-such-extension-" + UUID.randomUUID().toString().substring(0, 8)))
                .isEmpty();
        WindowsRegistry.changeNotify();
    }
}
