package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.gallery.KitGallery;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.Edt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class UbuntuYaruThemeTest {
    @Test void everyGalleryControlRendersInBothAppearances() {
        var theme = JasperTheme.builtIn("ubuntu-yaru").orElseThrow();
        var fixtures = KitGallery.fixtures();
        for (boolean dark : new boolean[] {true, false}) {
            var output = Path.of("build/gallery/ubuntu-yaru-" + (dark ? "dark" : "light"));
            Edt.run(() -> KitGallery.installed(
                theme.generated(dark), Kit.DEFAULT, (context, focus) -> {
                    try {
                        Files.createDirectories(output);
                        for (var fixture : fixtures) {
                            var capture = KitGallery.capture(fixture, context, focus);
                            ImageIO.write(capture.image(), "png", output.resolve(fixture.name() + ".png").toFile());
                        }
                    } catch (IOException failure) { throw new UncheckedIOException(failure); }
                }));
        }
    }

    @Test void bothAppearancesHaveMatchingTerminalAndReadableContent() {
        var theme = JasperTheme.builtIn("ubuntu-yaru").orElseThrow();
        for (boolean dark : new boolean[] {true, false}) {
            assertThat(theme.has(dark)).isTrue();
            var generated = theme.generated(dark);
            var palette = generated.semantic();
            var terminal = theme.matching(dark).colors();
            assertThat(palette.color(SemanticColor.SURFACE_TERMINAL)).isEqualTo(terminal.background());
            assertThat(ColorMath.contrast(terminal.foreground(), terminal.background())).isGreaterThanOrEqualTo(7);
            for (var surface : List.of(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_PANEL,
                    SemanticColor.SURFACE_INPUT, SemanticColor.SURFACE_MENU)) {
                for (var ink : List.of(SemanticColor.TEXT_PRIMARY, SemanticColor.TEXT_SECONDARY, SemanticColor.TEXT_LINK))
                    assertThat(ColorMath.contrast(palette.color(ink), palette.color(surface)))
                        .as("%s %s on %s", dark, ink, surface).isGreaterThanOrEqualTo(4.5);
            }
            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_ONACCENT), palette.color(SemanticColor.ACCENT)))
                .isGreaterThanOrEqualTo(4.5);
            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_ONSELECTION), palette.color(SemanticColor.SELECTION_BG)))
                .isGreaterThanOrEqualTo(4.5);
            // Dark ANSI black remains a conventional background colour; every other foreground stays readable.
            for (int index = dark ? 1 : 0; index < 16; index++)
                assertThat(ColorMath.contrast(terminal.ansi().get(index), terminal.background()))
                    .as("%s ANSI %s", dark, index).isGreaterThanOrEqualTo(4.5);
            for (var syntax : generated.syntax().values())
                assertThat(ColorMath.contrast(syntax.color(), generated.editor().background()))
                    .as("%s syntax %s", dark, syntax).isGreaterThanOrEqualTo(4.5);
        }
    }
}
