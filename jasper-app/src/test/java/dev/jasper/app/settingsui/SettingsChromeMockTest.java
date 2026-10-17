package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.plugins.PluginRuntime;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 12.1 and 16.1.4: the Settings chrome against the eighteen 05 Settings mocks (plan 9a's
 * "Mock measurements"): probes on every OS, pixel shares of the sidebar, header and footer on macOS (Rulings 17); the
 * probes that follow the UI face and the shares are asserted under the shipped kit's IBM Plex and printed where an
 * installed IBM Plex hides the plugin's files (Execution notes E10; plan 5b Execution notes, ruling 3).
 */
class SettingsChromeMockTest {
    /**
     * One mock.
     *
     * @param mock its file under 05 Settings
     * @param id the page it shows
     * @param height its content height in logical pixels
     * @param row the selected navigation row, counted from the first
     */
    record Page(String mock, String id, int height, int row) { }

    static final List<Page> PAGES = List.of(
        new Page("Settings 01 - Appearance & Behavior - Appearance.png", "appearance", 691, 1),
        new Page("Settings 02 - Appearance & Behavior - Window.png", "window", 691, 2),
        new Page("Settings 03 - Appearance & Behavior - Notifications.png", "notifications", 827, 3),
        new Page("Settings 04 - Appearance & Behavior - Command Palette.png", "command-palette", 691, 4),
        new Page("Settings 05 - Appearance & Behavior - Desk Buddy.png", "desk-buddy", 691, 5),
        new Page("Settings 06 - Appearance & Behavior - System Settings.png", "system", 691, 6),
        new Page("Settings 07 - Keymap.png", "keymap", 691, 7),
        new Page("Settings 08 - Plugins.png", "plugins", 691, 8),
        new Page("Settings 09 - Terminal - Font & Colors.png", "terminal.font", 691, 10),
        new Page("Settings 10 - Terminal - Cursor.png", "terminal.cursor", 691, 11),
        new Page("Settings 11 - Terminal - Shell.png", "terminal.shell", 691, 12),
        new Page("Settings 12 - Tools - Credential Vault.png", PluginRuntime.settingsPageId("dev.jasper.vault"), 691, 14),
        new Page("Settings 13 - Tools - Editor.png", PluginRuntime.settingsPageId("dev.jasper.editor"), 731, 15),
        new Page("Settings 14 - Tools - File Server.png", PluginRuntime.settingsPageId("dev.jasper.fileserver"), 691, 16),
        new Page("Settings 15 - Tools - Project Index.png", PluginRuntime.settingsPageId("dev.jasper.indexer"), 949, 17),
        new Page("Settings 16 - Tools - Remote.png", PluginRuntime.settingsPageId("dev.jasper.remote"), 691, 18),
        new Page("Settings 17 - Tools - Sample.png", PluginRuntime.settingsPageId("dev.jasper.sample"), 691, 19),
        new Page("Settings 18 - Tools - Shell History.png", PluginRuntime.settingsPageId("dev.jasper.history"), 691, 20));

    static final double TOLERANCE = 12, PROBE = 3;
    static final boolean MAC = com.formdev.flatlaf.util.SystemInfo.isMacOS;
    /**
     * Whether the render draws the shipped kit's IBM Plex: false only where an installed IBM Plex hides the plugin's files
     * (plan 5b Execution notes, ruling 3), which leaves the probes that follow the face and the shares printed.
     */
    static final boolean PLEX = dev.jasper.app.testsupport.EnterpriseResources.plexResolves();
    /** How a check that follows the face is printed when {@link #PLEX} is false, as {@code MockProfile.fonts} prints it. */
    static final String UNCHECKED = "FONTS unchecked (an installed IBM Plex hides the plugin's files): ";
    /** The probes whose place follows the UI face's line: a titled box's border sits at its title line's middle. */
    static final java.util.Set<String> FONTS = java.util.Set.of("first box top", "Theme box bottom", "Workspace box top");
    /** The probes whose place follows plan 9b's Appearance composition (the Theme box's height): printed here, 9b's to assert. */
    static final java.util.Set<String> COMPOSED = java.util.Set.of("Theme box bottom", "Workspace box top");

    /** The mock's content, as the render covers it: from 2x (2, 60). */
    static BufferedImage content(Page page) {
        var mock = SettingsMockMatch.mock("05 Settings/" + page.mock());
        return SettingsMockMatch.crop(mock, new Rectangle(2, 60, 2080, 2 * page.height()));
    }

    /** A probe: a logical point of the content and what it should show. */
    record Probe(String name, int x, int y) { }

    /** The frame's probes, and for two pages the page geometry of Rulings 16 (box edges and the gap between boxes). */
    static List<Probe> probesOf(Page page) {
        int h = page.height(), selected = 43 + 22 * page.row() + 11;
        var probes = new ArrayList<>(List.of(new Probe("strip", 115, 4), new Probe("strip rule", 115, 38), new Probe("navigation", 115, 41),
            new Probe("sidebar rule", 230, 300), new Probe("body edge", 231, 300), new Probe("header", 640, 2), new Probe("header rule", 640, 34),
            new Probe("body", 640, 36), new Probe("footer rule", 300, h - 45), new Probe("footer band", 300, h - 3), new Probe("selected row", 215, selected),
            new Probe("selection at the left edge", 1, selected), new Probe("selection at the right edge", 229, selected)));
        if (page.id().equals("command-palette"))
            probes.addAll(List.of(new Probe("first box top", 600, 53), new Probe("box left", 247, 70), new Probe("box right", 1023, 70)));
        if (page.id().equals("appearance"))
            probes.addAll(List.of(new Probe("Theme box bottom", 1000, 313), new Probe("Workspace box top", 1000, 334)));
        return probes;
    }

    /** Each probe's rendered pixel within {@link #PROBE} of the mock's at the same point; the failures. */
    static List<String> probes(Page page, BufferedImage render, BufferedImage mock) {
        var failures = new ArrayList<String>();
        for (var probe : probesOf(page)) {
            int x = 2 * probe.x(), y = 2 * probe.y();
            double distance = SettingsMockMatch.deltaE(render.getRGB(x, y), mock.getRGB(x, y));
            if (distance <= PROBE) continue;
            String failure = String.format("%s %s at (%d, %d): render #%06X, mock #%06X, ΔE %.1f", page.id(), probe.name(),
                probe.x(), probe.y(), render.getRGB(x, y) & 0xFFFFFF, mock.getRGB(x, y) & 0xFFFFFF, distance);
            if (COMPOSED.contains(probe.name())) System.out.println("Plan 9b's composition: " + failure);
            else if (FONTS.contains(probe.name()) && !PLEX) System.out.println(UNCHECKED + failure);
            else failures.add(failure);
        }
        return failures;
    }

    /** A glyph's or fill's ink: the darkest (or lightest) pixel of a logical box of the content. */
    record Ink(String name, Rectangle box, boolean darkest) { }

    /** The history pair's glyphs and the footer's "?" (its glyph and its fill), which the frame probes do not reach. */
    static List<Ink> inksOf(Page page) {
        int h = page.height();
        return List.of(new Ink("Back glyph", new Rectangle(250, 10, 15, 15), true), new Ink("Forward glyph", new Rectangle(275, 11, 15, 13), true),
            new Ink("? glyph", new Rectangle(17, h - 37, 15, 15), true), new Ink("? fill", new Rectangle(17, h - 37, 15, 15), false));
    }

    /** The extreme pixel of {@code box} (logical) in a 2x image. */
    static int extreme(BufferedImage image, Rectangle box, boolean darkest) {
        int found = darkest ? 0xFFFFFF : 0, sum = darkest ? Integer.MAX_VALUE : -1;
        for (int y = 2 * box.y; y < 2 * (box.y + box.height); y++)
            for (int x = 2 * box.x; x < 2 * (box.x + box.width); x++) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF, value = (rgb >> 16) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
                if (darkest ? value < sum : value > sum) { sum = value; found = rgb; }
            }
        return found;
    }

    /** Each ink within {@link #PROBE} of the mock's; the failures. */
    static List<String> inks(Page page, BufferedImage render, BufferedImage mock) {
        var failures = new ArrayList<String>();
        for (var ink : inksOf(page)) {
            int ours = extreme(render, ink.box(), ink.darkest()), theirs = extreme(mock, ink.box(), ink.darkest());
            double distance = SettingsMockMatch.deltaE(ours, theirs);
            if (distance > PROBE) failures.add(String.format("%s %s: render #%06X, mock #%06X, ΔE %.1f", page.id(), ink.name(), ours, theirs, distance));
        }
        return failures;
    }

    @Test void theChromeMatchesTheEighteenMocks() throws Exception {
        var failures = new ArrayList<String>();
        var shares = new StringBuilder();
        try (var settings = EnterpriseSettings.open(false)) {
            for (var page : PAGES) {
                var render = new BufferedImage[1];
                SwingUtilities.invokeAndWait(() -> render[0] = settings.render(page.id(), 1040, page.height()));
                var mock = content(page);
                var out = EnterpriseSettings.OUT.resolve("light");
                Files.createDirectories(out);
                ImageIO.write(render[0], "png", out.resolve(page.mock().substring(9, 11) + ".png").toFile());
                failures.addAll(probes(page, render[0], mock));
                failures.addAll(inks(page, render[0], mock));
                int h = 2 * page.height();
                var areas = List.of(new Rectangle(0, 0, 460, h - 90), new Rectangle(462, 0, 1618, 70), new Rectangle(0, h - 90, 2080, 90));
                var names = List.of("sidebar", "header", "footer");
                var floors = List.of(0.97, 0.96, 0.98);
                for (int i = 0; i < areas.size(); i++) {
                    var area = areas.get(i);
                    var ours = SettingsMockMatch.crop(render[0], area); var theirs = SettingsMockMatch.crop(mock, area);
                    var result = SettingsMockMatch.compare(ours, theirs, 2, TOLERANCE);
                    shares.append(String.format("%s %s %.3f (%d, %d)%n", page.id(), names.get(i), result.matching(), result.dx(), result.dy()));
                    if (result.matching() < floors.get(i)) {
                        SettingsMockMatch.inspect(EnterpriseSettings.OUT.resolve("compare"), page.mock().substring(9, 11) + "-" + names.get(i), ours, theirs, TOLERANCE);
                        String below = String.format("%s %s: %.3f below %.2f", page.id(), names.get(i), result.matching(), floors.get(i));
                        if (MAC && PLEX) failures.add(below);
                        else System.out.println((MAC ? UNCHECKED : "Not asserted off macOS (Rulings 17): ") + below);
                    }
                }
            }
        }
        Files.writeString(EnterpriseSettings.OUT.resolve("chrome-shares.txt"), shares);
        assertThat(failures).as("see jasper-app/build/enterprise-settings/compare and chrome-shares.txt").isEmpty();
    }

    @Test void theDarkVariantPaintsTheSameFrameInItsOwnRoles() throws Exception {
        try (var settings = EnterpriseSettings.open(true)) {
            var render = new BufferedImage[1];
            SwingUtilities.invokeAndWait(() -> render[0] = settings.render("appearance", 1040, 691));
            ImageIO.write(render[0], "png", Files.createDirectories(EnterpriseSettings.OUT.resolve("dark")).resolve("01.png").toFile());
            var palette = dev.jasper.app.testsupport.EnterpriseLook.theme().palette(true);
            var light = content(PAGES.getFirst());
            assertThat(render[0].getRGB(230, 8) & 0xFFFFFF).as("the strip is dark surface.sidebar")
                .isEqualTo(palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR).getRGB() & 0xFFFFFF);
            // Logical (640, 40): the body between the header rule and the first box (lower, the Theme box's tiles begin).
            assertThat(render[0].getRGB(1280, 80) & 0xFFFFFF).as("the body is dark surface.app")
                .isEqualTo(palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB() & 0xFFFFFF);
            assertThat(render[0].getRGB(1280, 68) & 0xFFFFFF).as("the header rule stands apart from the body")
                .isNotEqualTo(render[0].getRGB(1280, 72) & 0xFFFFFF);
            assertThat(SettingsMockMatch.deltaE(render[0].getRGB(1280, 68), light.getRGB(1280, 68))).as("in Dark's own colour").isGreaterThan(PROBE);
        }
    }
}
