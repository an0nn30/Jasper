package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.ui.gallery.KitGallery;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Spec 16.1.5: the real {@code enterprise.kit.toml} through the production parser, the real Plex files registered, and
 * the gallery rendered under it in Enterprise Light and Dark. Images go to {@code jasper-app/build/enterprise-kit/}; the
 * four panels are also rendered at 2x for {@code tools/visual/pairs.json}.
 */
class EnterpriseKitRenderTest {
    static final Path OUT = EnterpriseResources.ROOT.resolve("jasper-app/build/enterprise-kit");
    static final List<String> PANELS = List.of("sessions", "tunnels", "sftp", "fileservers");

    @Test void theShippedKitRendersInPlexInBothVariants() throws Exception {
        var parsed = EnterpriseResources.facts().parsed().orElseThrow();
        assertThat(LookFonts.register(parsed.faces())).as("every Plex file registers").isEmpty();
        var theme = EnterpriseResources.theme();
        var fixtures = KitGallery.fixtures();
        for (boolean dark : new boolean[] {false, true}) {
            Path directory = Files.createDirectories(OUT.resolve(dark ? "dark" : "light"));
            var plexLabels = new ArrayList<String>();
            SwingUtilities.invokeAndWait(() -> KitGallery.installed(theme.generated(dark), parsed.kit(), (context, focus) -> {
                var typography = context.typography();
                assertThat(typography.font(Typography.Role.BODY).getFontName(Locale.ROOT)).isEqualTo("IBM Plex Sans");
                assertThat(typography.font(Typography.Role.BODY).getSize2D()).as("[type].base").isEqualTo(12f);
                assertThat(typography.font(Typography.Role.SECTION_LABEL).getFontName(Locale.ROOT)).isEqualTo("IBM Plex Sans SmBld");
                assertThat(typography.font(Typography.Role.MONO).getFontName(Locale.ROOT)).isEqualTo("IBM Plex Mono");
                // Plex has no macOS modifier symbols: a text that needs one draws in the platform face, as the mocks' does.
                assertThat(typography.font(Typography.Role.CAPTION, "Run").getFontName(Locale.ROOT)).isEqualTo("IBM Plex Sans");
                assertThat(typography.font(Typography.Role.CAPTION, "\u21E7\u2318P").getFontName(Locale.ROOT)).doesNotStartWith("IBM Plex");
                assertThat(typography.font(Typography.Role.CAPTION, "\u21E7\u2318P").canDisplayUpTo("\u21E7\u2318P")).isNegative();
                try {
                    for (var fixture : fixtures) {
                        var capture = KitGallery.capture(fixture, context, focus);
                        ImageIO.write(capture.image(), "png", directory.resolve(fixture.name() + ".png").toFile());
                    }
                    for (var fixture : fixtures) {
                        if (!PANELS.contains(fixture.name())) continue;
                        try (var view = new KitView(context, fixture.spec(), event -> { })) {
                            var image = KitFixtures.render(view, fixture.width(), fixture.height(), 2);
                            ImageIO.write(image, "png", directory.resolve(fixture.name() + "@2x.png").toFile());
                            for (Component component : descendants(view))
                                if (component instanceof JComponent text && text.getFont() != null
                                    && text.getFont().getFontName(Locale.ROOT).startsWith("IBM Plex"))
                                    plexLabels.add(fixture.name());
                        }
                    }
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            }));
            assertThat(plexLabels).as((dark ? "dark" : "light") + ": every panel paints Plex text").containsAll(PANELS);
            for (String panel : PANELS) assertThat(directory.resolve(panel + "@2x.png")).isRegularFile();
        }
    }

    private static List<Component> descendants(Container root) {
        var all = new ArrayList<Component>();
        for (Component child : root.getComponents()) {
            all.add(child);
            if (child instanceof Container nested) all.addAll(descendants(nested));
        }
        return all;
    }
}
