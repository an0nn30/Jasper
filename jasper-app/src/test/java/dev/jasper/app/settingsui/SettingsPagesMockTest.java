package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 12.2 and 16.1.4: each Settings page's body against its 05 Settings mock (plan 9b Rulings
 * 16): probes on every OS, the pixel share on macOS; a probe or share that follows the UI face is printed where an
 * installed IBM Plex hides the plugin's files, as plan 9a's chrome comparison does. Renders go to
 * jasper-app/build/enterprise-settings/pages.
 */
class SettingsPagesMockTest {
    static final double FLOOR = 0.93;
    /** A probe whose target lies within PROBE of its surroundings (a row rule, a band, a strip) is held this close. */
    static final double TIGHT_PROBE = 1;
    /** Per page, the probes: a name, a logical point of the content and the mock's pixel there is the target. */
    static final Map<String, List<SettingsChromeMockTest.Probe>> PROBES = new LinkedHashMap<>();
    /** The probes held within {@link #TIGHT_PROBE}, by page id and name ("id/name"). */
    static final Set<String> TIGHT = new java.util.HashSet<>();
    /** The probes whose place follows the UI face's lines (below a titled box's title), by "id/name"; asserted when PLEX. */
    static final Set<String> FACE = new java.util.HashSet<>();
    /** Per page, a floor lowered under Rulings 16, with its cause in the banner. */
    static final Map<String, Double> FLOORS = new LinkedHashMap<>();

    static SettingsChromeMockTest.Probe probe(String name, int x, int y) { return new SettingsChromeMockTest.Probe(name, x, y); }
    static void tight(String id, String... names) { for (String name : names) TIGHT.add(id + "/" + name); }
    static void face(String id, String... names) { for (String name : names) FACE.add(id + "/" + name); }

    static {
        PROBES.put("appearance", List.of(probe("Light preview ring", 430, 111), probe("Light radio dot", 437, 164),
            probe("first row rule", 700, 182), probe("second row rule", 700, 221), probe("Dark theme dropdown top", 1000, 191),
            probe("Theme box bottom", 1000, 313), probe("Workspace box top", 1000, 334),
            // The unchecked box's edge is plan 4b's border.hover (#909399), ΔE 5 from the mock's #9AA0A8: the probes take the
            // box's inside and the body just above its edge, which hold its place to the pixel.
            probe("Enable opacity checkbox", 266, 364), probe("above the Enable opacity checkbox", 266, 357), probe("Background blur checkbox", 262, 440)));
        tight("appearance", "first row rule", "second row rule");
        face("appearance", "Theme box bottom", "Workspace box top", "Enable opacity checkbox", "above the Enable opacity checkbox", "Background blur checkbox");
        PROBES.put("window", List.of(probe("Toolbar & Status Bar box top", 700, 53), probe("first row rule", 700, 102),
            probe("third row rule", 700, 180), probe("row rule start", 262, 102), probe("row rule inset", 255, 102),
            probe("row rule end", 1015, 102), probe("Toolbar height spinner top", 940, 111), probe("Show status bar checkbox", 262, 160),
            probe("Tabs & Panels box top", 700, 285)));
        tight("window", "first row rule", "third row rule", "row rule start", "row rule inset", "row rule end");
        face("window", "Tabs & Panels box top");
    }

    /** The body: right of the sidebar rule, below the header rule, above the footer rule (2x pixels). */
    static Rectangle body(SettingsChromeMockTest.Page page) { return new Rectangle(2 * 231, 2 * 35, 2080 - 2 * 231, 2 * (page.height() - 35 - 45)); }

    static SettingsChromeMockTest.Page page(String id) {
        return SettingsChromeMockTest.PAGES.stream().filter(page -> page.id().equals(id)).findFirst().orElseThrow();
    }

    /** {@code render}'s probes of {@code key} (a page id) against {@code mock}; the failures, the face's printed when not PLEX. */
    static List<String> probes(String key, BufferedImage render, BufferedImage mock) {
        var failures = new ArrayList<String>();
        for (var probe : PROBES.getOrDefault(key, List.of())) {
            int x = 2 * probe.x(), y = 2 * probe.y();
            double distance = SettingsMockMatch.deltaE(render.getRGB(x, y), mock.getRGB(x, y));
            String name = key + "/" + probe.name();
            if (distance <= (TIGHT.contains(name) ? TIGHT_PROBE : SettingsChromeMockTest.PROBE)) continue;
            String failure = String.format("%s at (%d, %d): render #%06X, mock #%06X, ΔE %.1f", name, probe.x(), probe.y(),
                render.getRGB(x, y) & 0xFFFFFF, mock.getRGB(x, y) & 0xFFFFFF, distance);
            if (FACE.contains(name) && !SettingsChromeMockTest.PLEX) System.out.println(SettingsChromeMockTest.UNCHECKED + failure);
            else failures.add(failure);
        }
        return failures;
    }

    /** The body share of {@code render} against {@code mock} under {@code key}'s floor; a failure on macOS with PLEX. */
    static List<String> share(String key, String number, SettingsChromeMockTest.Page page, BufferedImage render, BufferedImage mock, StringBuilder shares) {
        var ours = SettingsMockMatch.crop(render, body(page)); var theirs = SettingsMockMatch.crop(mock, body(page));
        var result = SettingsMockMatch.compare(ours, theirs, 2, SettingsChromeMockTest.TOLERANCE);
        double floor = FLOORS.getOrDefault(key, FLOOR);
        shares.append(String.format("%s %.3f (%d, %d) floor %.2f%n", key, result.matching(), result.dx(), result.dy(), floor));
        if (result.matching() >= floor) return List.of();
        SettingsMockMatch.inspect(EnterpriseSettings.OUT.resolve("compare"), number + "-body", ours, theirs, SettingsChromeMockTest.TOLERANCE);
        String below = String.format("%s body: %.3f below %.2f", key, result.matching(), floor);
        if (SettingsChromeMockTest.MAC && SettingsChromeMockTest.PLEX) return List.of(below);
        System.out.println((SettingsChromeMockTest.MAC ? SettingsChromeMockTest.UNCHECKED : "Not asserted off macOS (Rulings 16): ") + below);
        return List.of();
    }

    /** Renders {@code ids}, checks each page's probes and its body share; the failures. */
    static List<String> compare(EnterpriseSettings settings, List<String> ids) throws Exception {
        var failures = new ArrayList<String>();
        var shares = new StringBuilder();
        for (String id : ids) {
            var page = page(id);
            var render = new BufferedImage[1];
            SwingUtilities.invokeAndWait(() -> render[0] = settings.render(id, 1040, page.height()));
            var out = Files.createDirectories(EnterpriseSettings.OUT.resolve("pages"));
            String number = page.mock().substring(9, 11);
            ImageIO.write(render[0], "png", out.resolve(number + ".png").toFile());
            var mock = SettingsChromeMockTest.content(page);
            failures.addAll(probes(id, render[0], mock));
            failures.addAll(share(id, number, page, render[0], mock, shares));
        }
        Files.writeString(EnterpriseSettings.OUT.resolve("pages-" + ids.getFirst() + ".txt"), shares);
        return failures;
    }

    @Test void appearanceAndWindowMatchTheirMocks() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            assertThat(compare(settings, List.of("appearance", "window"))).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }
}
