package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
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
 * installed IBM Plex hides the plugin's files (Execution notes E10; plan 5b Execution notes, ruling 3). A probe's colour is
 * the shipped theme's role or painted pair there ({@link #expected}); the mocks give the places.
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
    static final java.util.Set<String> FONTS = java.util.Set.of("first box top");

    /** The mock's content, as the render covers it: from 2x (2, 60). */
    static BufferedImage content(Page page) {
        var mock = SettingsMockMatch.mock("05 Settings/" + page.mock());
        return SettingsMockMatch.crop(mock, new Rectangle(2, 60, 2080, 2 * page.height()));
    }

    /**
     * A probe: a logical point of the content and the colour it should show, a role key of the shipped Light theme or one
     * of {@link #PAINTED}'s names; null for a composited pixel no role holds, which takes the mock's pixel there.
     *
     * @param name what it probes
     * @param x logical x
     * @param y logical y
     * @param colour the colour it should show, or null for the mock's
     */
    record Probe(String name, int x, int y, String colour) { }

    /** The frame's titled boxes: border.window held to the structural guard on the kit's dialog surface (the group face's box). */
    static PaintPair box() { return new PaintPair(SemanticColor.BORDER_WINDOW, EnterpriseLook.kit().colors().dialogSurface(), SurfaceContrast.Guard.STRUCTURAL); }

    /**
     * The guarded and composited colours a probe may name: the frame's header rule and the titled boxes, the sidebar rule
     * on the navigation slot, the footer band's rule, and the recording band's edges (border.focus at the kit's edge alpha
     * over surface.rowHover).
     */
    static final java.util.Map<String, java.util.function.Supplier<Color>> PAINTED = java.util.Map.of(
        "window rule", () -> MockProfile.painted(false, box()),
        "sidebar rule", () -> MockProfile.painted(false, new PaintPair(SemanticColor.BORDER_WINDOW, EnterpriseLook.kit().colors().navigationSurface(),
            SurfaceContrast.Guard.STRUCTURAL)),
        "footer rule", () -> MockProfile.painted(false, dev.jasper.ui.swing.ClassicDialog.BAND_RULE),
        "band edge", () -> {
            Color focus = MockProfile.role(false, "border.focus");
            double alpha = EnterpriseLook.kit().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_EDGE_ALPHA) / 255.0;
            return ColorMath.mix(focus, MockProfile.role(false, "surface.rowHover"), alpha);
        });

    /** {@code colour}'s value in the shipped Light theme: a {@link #PAINTED} name or a role key. */
    static Color expected(String colour) {
        var painted = PAINTED.get(colour);
        return painted != null ? painted.get() : MockProfile.role(false, colour);
    }

    /** What {@code probe} should show at device {@code (x, y)}: its colour, or the mock's pixel when it names none. */
    static int expected(Probe probe, BufferedImage mock, int x, int y) {
        return probe.colour() == null ? mock.getRGB(x, y) : expected(probe.colour()).getRGB();
    }

    /** The frame's probes, and for Command Palette the page geometry of Rulings 16 (its box edges); plan 9b's test holds the rest. */
    static List<Probe> probesOf(Page page) {
        int h = page.height(), selected = 43 + 22 * page.row() + 11;
        var probes = new ArrayList<>(List.of(new Probe("strip", 115, 4, "surface.sidebar"), new Probe("strip rule", 115, 38, "border.row"),
            new Probe("navigation", 115, 41, "surface.card"), new Probe("sidebar rule", 230, 300, "sidebar rule"),
            new Probe("body edge", 231, 300, "surface.app"), new Probe("header", 640, 2, "surface.app"), new Probe("header rule", 640, 34, "window rule"),
            new Probe("body", 640, 36, "surface.app"), new Probe("footer rule", 300, h - 45, "footer rule"), new Probe("footer band", 300, h - 3, "surface.footer"),
            new Probe("selected row", 215, selected, "selection.bg"), new Probe("selection at the left edge", 1, selected, "selection.bg"),
            new Probe("selection at the right edge", 229, selected, "selection.bg")));
        if (page.id().equals("command-palette"))
            probes.addAll(List.of(new Probe("first box top", 600, 53, "window rule"), new Probe("box left", 247, 70, "window rule"),
                new Probe("box right", 1023, 70, "window rule")));
        return probes;
    }

    /** Each probe's rendered pixel within {@link #PROBE} of the colour it should show; the failures. */
    static List<String> probes(Page page, BufferedImage render, BufferedImage mock) {
        var failures = new ArrayList<String>();
        for (var probe : probesOf(page)) {
            int x = 2 * probe.x(), y = 2 * probe.y(), expected = expected(probe, mock, x, y);
            double distance = SettingsMockMatch.deltaE(render.getRGB(x, y), expected);
            if (distance <= PROBE) continue;
            String failure = String.format("%s %s at (%d, %d): render #%06X, expected %s #%06X, ΔE %.1f", page.id(), probe.name(),
                probe.x(), probe.y(), render.getRGB(x, y) & 0xFFFFFF, probe.colour() == null ? "the mock's" : probe.colour(), expected & 0xFFFFFF, distance);
            if (FONTS.contains(probe.name()) && !PLEX) System.out.println(UNCHECKED + failure);
            else failures.add(failure);
        }
        return failures;
    }

    /**
     * A glyph's or fill's ink: the darkest (or lightest) pixel of a logical box of the content, and the colour it should
     * be, as a {@link Probe}'s.
     *
     * @param name what it is
     * @param box the logical box
     * @param darkest whether the darkest pixel (else the lightest)
     * @param colour the colour it should be, or null for the mock's
     */
    record Ink(String name, Rectangle box, boolean darkest, String colour) { }

    /**
     * The history pair's glyphs and the footer's "?" (its glyph and its fill, the classic gradient's top), which the frame
     * probes do not reach. The disabled Forward glyph is faded as one layer, a composite no role holds: it takes the mock's.
     */
    static List<Ink> inksOf(Page page) {
        int h = page.height();
        return List.of(new Ink("Back glyph", new Rectangle(250, 10, 15, 15), true, "text.primary"),
            new Ink("Forward glyph", new Rectangle(275, 11, 15, 13), true, null),
            new Ink("? glyph", new Rectangle(17, h - 37, 15, 15), true, "text.strong.secondary"),
            new Ink("? fill", new Rectangle(17, h - 37, 15, 15), false, "button.secondary.bg"));
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
            int ours = extreme(render, ink.box(), ink.darkest()), theirs = ink.colour() == null ? extreme(mock, ink.box(), ink.darkest())
                : expected(ink.colour()).getRGB() & 0xFFFFFF;
            double distance = SettingsMockMatch.deltaE(ours, theirs);
            if (distance > PROBE) failures.add(String.format("%s %s: render #%06X, expected #%06X, ΔE %.1f", page.id(), ink.name(), ours, theirs, distance));
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
            assertThat(render[0].getRGB(230, 8) & 0xFFFFFF).as("the strip is dark surface.sidebar")
                .isEqualTo(palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR).getRGB() & 0xFFFFFF);
            // Logical (640, 40): the body between the header rule and the first box (lower, the Theme box's tiles begin).
            assertThat(render[0].getRGB(1280, 80) & 0xFFFFFF).as("the body is dark surface.app")
                .isEqualTo(palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB() & 0xFFFFFF);
            assertThat(render[0].getRGB(1280, 68) & 0xFFFFFF).as("the header rule stands apart from the body")
                .isNotEqualTo(render[0].getRGB(1280, 72) & 0xFFFFFF);
            assertThat(SettingsMockMatch.deltaE(render[0].getRGB(1280, 68), MockProfile.painted(true, box()).getRGB())).as("in Dark's own colour")
                .isLessThanOrEqualTo(PROBE);
        }
    }
}
