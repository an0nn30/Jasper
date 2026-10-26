package dev.jasper.app.platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.jasper.app.platform.DesktopObservation.*;
import static org.assertj.core.api.Assertions.*;

class DesktopQtProtocolTest {
    @Test void readsNativeQtVectorsWithoutLosingSourceGenerationOrMetadata() throws Exception {
        Path vectors = Path.of(System.getProperty("jasper.repoRoot"), "packaging/desktop-colors/test/qt-vectors.jsonl");
        var lines = Files.readAllLines(vectors);
        assertThat(lines).hasSize(4);
        String[] sources = { "QT5", "QT6", "KDE5", "KDE6" };
        for (int i = 0; i < sources.length; i++) {
            Palette palette = decode(lines.get(i));
            assertThat(palette.source().name()).isEqualTo(sources[i]);
            assertThat(palette.generation()).isEqualTo(9007199254740993L);
            assertThat(palette.appearance()).isEqualTo(Polarity.LIGHT);
            assertThat(palette.implementation()).isEqualTo("Qt \"fixture\"");
            assertThat(palette.version()).isEqualTo("runtime");
            assertThat(palette.reason()).isEmpty();
            assertThat(palette.colors()).containsOnlyKeys(Pair.WINDOW);
            assertThat(palette.colors().get(Pair.WINDOW))
                .isEqualTo(new Colors(new Rgba(1, 1, 1, 1), new Rgba(0, 0, 0, 1)));
        }
    }

    @ParameterizedTest @ValueSource(strings = { "qt5", "qt6", "kde5", "kde6" })
    void preservesActualPolarityAlphaAndIndependentMissingPairs(String source) {
        Palette light = decode(wire(source, "light", "native palette", ",\"window.bg\":\"1,1,1,1\",\"window.fg\":\"0,0,0,0.75\",\"view.bg\":\"0,0,0,1\""));
        assertThat(light.source().name()).isEqualTo(source.toUpperCase(Locale.ROOT));
        assertThat(light.appearance()).isEqualTo(Polarity.LIGHT);
        assertThat(light.colors()).containsOnlyKeys(Pair.WINDOW);
        assertThat(light.colors().get(Pair.WINDOW).foreground().alpha()).isEqualTo(0.75);
        Palette unknown = decode(wire(source, "unknown", "Window brush unavailable", ""));
        assertThat(unknown.appearance()).isEqualTo(Polarity.UNKNOWN);
        assertThat(unknown.colors()).isEmpty();
        assertThat(unknown.reason()).isEqualTo("Window brush unavailable");
        assertThat(decode(wire(source, "dark", "", "")).appearance()).isEqualTo(Polarity.DARK);
    }

    @Test void unknownSourcesAndNonIntegerQtGenerationsAreRejected() {
        for (String source : new String[] { "qt", "qt7", "kde", "QT5", "unknown" })
            assertThatIllegalArgumentException().isThrownBy(() -> decode(wire(source, "light", "", "")));
        for (String generation : new String[] { "1.0", "1e0", "-1", "9223372036854775808" })
            assertThatIllegalArgumentException().isThrownBy(() -> decode(wire("qt5", "light", "", "")
                .replace("\"generation\":7", "\"generation\":" + generation)));
    }

    private static String wire(String source, String appearance, String reason, String colors) {
        return "{\"v\":1,\"kind\":\"palette\",\"generation\":7,\"source\":\"" + source
            + "\",\"appearance\":\"" + appearance + "\",\"implementation\":\"Qt style fixture\",\"libraryVersion\":\"6.2.4\",\"reason\":\"" + reason + "\"" + colors + "}";
    }
    private static Palette decode(String wire) {
        return ((DesktopProtocol.PaletteMessage) DesktopProtocol.decode(wire.getBytes(StandardCharsets.UTF_8))).value();
    }
}
