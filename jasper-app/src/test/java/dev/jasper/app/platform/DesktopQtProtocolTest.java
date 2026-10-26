package dev.jasper.app.platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.jasper.app.appearance.DesktopPalette;
import dev.jasper.ui.theme.SemanticColor;
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

    @Test void initializationExitFallsBackWithinQtFamilyAndRetryRecoversThroughRealChildPipes() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var recovered = new AtomicBoolean();
            var launched = new CopyOnWriteArrayList<String>();
            try (var service = new DesktopColors(Path.of("test", "helpers").toAbsolutePath(), f.time, f.queue::add, f.time::now, command -> {
                String helper = Path.of(command.getFirst()).getFileName().toString();
                launched.add(helper);
                String mode = helper.equals("jasper-desktop-portal") ? "echo"
                    : helper.equals("jasper-desktop-qt6") && !recovered.get() ? "exit" : "qt";
                var child = new ArrayList<>(DesktopProcessTest.child(mode));
                child.add(helper.substring("jasper-desktop-".length()));
                return f.launch(child);
            })) {
                service.request(1, Set.of(Source.QT6, Source.QT5), true);
                f.await(() -> service.current().palettes().containsKey(Source.QT6)
                    && !service.current().palettes().get(Source.QT6).reason().isBlank()
                    && service.current().palettes().containsKey(Source.QT5));
                var context = DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "LXQt"));
                var fallback = DesktopPalette.resolve(true, DesktopPalette.SourceMode.QT, context, service.current());
                assertThat(fallback.source()).contains(Source.QT5);
                assertThat(SemanticColor.SURFACE_APP.color(fallback.theme())).isEqualTo(new java.awt.Color(26,26,26));
                assertThat(launched).containsExactlyInAnyOrder("jasper-desktop-portal", "jasper-desktop-qt5", "jasper-desktop-qt6");
                recovered.set(true);
                f.time.advance(1, TimeUnit.SECONDS); f.pump();
                f.await(() -> service.current().palettes().get(Source.QT6).colors().containsKey(Pair.WINDOW));
                var current = DesktopPalette.resolve(true, DesktopPalette.SourceMode.QT, context, service.current());
                assertThat(current.source()).contains(Source.QT6);
                assertThat(SemanticColor.SURFACE_APP.color(current.theme())).isEqualTo(new java.awt.Color(51,51,51));
                assertThat(current.status()).doesNotContain("last good");
                assertThat(launched.stream().filter("jasper-desktop-qt5"::equals).count()).isEqualTo(1);
                assertThat(launched.stream().filter("jasper-desktop-qt6"::equals).count()).isEqualTo(2);
            }
            f.pump(); f.await(() -> f.children.stream().noneMatch(Process::isAlive));
        }
    }

    private static String wire(String source, String appearance, String reason, String colors) {
        return "{\"v\":1,\"kind\":\"palette\",\"generation\":7,\"source\":\"" + source
            + "\",\"appearance\":\"" + appearance + "\",\"implementation\":\"Qt style fixture\",\"libraryVersion\":\"6.2.4\",\"reason\":\"" + reason + "\"" + colors + "}";
    }
    private static Palette decode(String wire) {
        return ((DesktopProtocol.PaletteMessage) DesktopProtocol.decode(wire.getBytes(StandardCharsets.UTF_8))).value();
    }
}
