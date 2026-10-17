package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.presentation.KitParser;
import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.ui.gallery.KitGallery;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.JasperTheme;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/**
 * Spec 16.1.5: the real {@code enterprise.kit.toml} through the production parser, the real Plex files registered, and
 * the gallery rendered under it in Enterprise Light and Dark. Images go to {@code jasper-app/build/enterprise-kit/}; the
 * four panels are also rendered at 2x for {@code tools/visual/pairs.json}. Like {@code :jasper-ui:gallery}, it holds
 * FlatLaf's user scale at 1 ({@code flatlaf.uiScale}): the gallery's 24-point fixture would otherwise raise the scale
 * that every later capture's default font is computed from.
 */
class EnterpriseKitRenderTest {
    static final Path OUT = EnterpriseResources.ROOT.resolve("jasper-app/build/enterprise-kit");
    static final List<String> PANELS = List.of("sessions", "tunnels", "sftp", "fileservers");
    /** A body-text label of each panel, checked for its face and size in the 2x view. */
    static final Map<String, String> BODY_LABELS = Map.of("sessions", "session-address-amp", "tunnels", "tunnels-new",
        "sftp", "sftp-choose.main", "fileservers", "server-title");

    @Test void theShippedKitRendersInPlexInBothVariants() throws Exception {
        var parsed = EnterpriseResources.facts().parsed().orElseThrow();
        assertThat(LookFonts.register(parsed.faces())).as("every Plex file registers").isEmpty();
        var theme = EnterpriseResources.theme();
        var fixtures = KitGallery.fixtures();
        String priorScale = System.getProperty("flatlaf.uiScale");
        System.setProperty("flatlaf.uiScale", "1");
        try { render(parsed, theme, fixtures); }
        finally {
            if (priorScale == null) System.clearProperty("flatlaf.uiScale"); else System.setProperty("flatlaf.uiScale", priorScale);
        }
    }

    private static void render(KitParser.Parsed parsed, JasperTheme theme, List<KitGallery.Fixture> fixtures) throws Exception {
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
                        assertThat(UIScale.getUserScaleFactor()).as("user scale after " + fixture.name()).isEqualTo(1f);
                    }
                    for (var fixture : fixtures) {
                        if (!PANELS.contains(fixture.name())) continue;
                        assertThat(UIScale.getUserScaleFactor()).as(fixture.name() + "@2x: user scale").isEqualTo(1f);
                        assertThat(UIManager.getFont("defaultFont").getSize2D()).as(fixture.name() + "@2x: default font").isEqualTo(12f);
                        try (var view = new KitView(context, fixture.spec(), event -> { })) {
                            var image = KitFixtures.render(view, fixture.width(), fixture.height(), 2);
                            ImageIO.write(image, "png", directory.resolve(fixture.name() + "@2x.png").toFile());
                            var title = named(view, fixture.name() + "-header.title").getFont();
                            assertThat(title.getFontName(Locale.ROOT)).as(fixture.name() + " title face").isEqualTo("IBM Plex Sans SmBld");
                            assertThat(title.getSize2D()).as(fixture.name() + " title size")
                                .isEqualTo(typography.font(Typography.Role.PANEL_TITLE).getSize2D());
                            var body = named(view, BODY_LABELS.get(fixture.name())).getFont();
                            assertThat(body.getFontName(Locale.ROOT)).as(fixture.name() + " body face").startsWith("IBM Plex Sans");
                            assertThat(body.getSize2D()).as(fixture.name() + " body size").isEqualTo(12f);
                            plexLabels.add(fixture.name());
                        }
                    }
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            }));
            assertThat(plexLabels).as((dark ? "dark" : "light") + ": every panel paints Plex text").containsAll(PANELS);
            for (String panel : PANELS) assertThat(directory.resolve(panel + "@2x.png")).isRegularFile();
        }
    }

    private static Component named(Container root, String name) {
        return descendants(root).stream().filter(component -> name.equals(component.getName())).findFirst()
            .orElseThrow(() -> new AssertionError("no component named " + name));
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
