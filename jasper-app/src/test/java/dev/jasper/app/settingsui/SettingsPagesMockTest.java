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
    /**
     * Per page, the ink boxes: a logical rectangle around a text whose darkest pixel (its ink) and amount of ink (its weight)
     * are the mock's there, where a one-pixel residual of the text's place keeps a point probe off its thin strokes.
     */
    static final Map<String, List<Ink>> INKS = new LinkedHashMap<>();
    /** How far a text's ink amount may stray from the mock's, as a share of it: a regular face against a semibold one is 0.4 off. */
    static final double INK_AMOUNT = 0.2;
    /** Per page, a floor lowered under Rulings 16, with its cause in the banner. */
    static final Map<String, Double> FLOORS = new LinkedHashMap<>();

    static SettingsChromeMockTest.Probe probe(String name, int x, int y) { return new SettingsChromeMockTest.Probe(name, x, y); }
    static void tight(String id, String... names) { for (String name : names) TIGHT.add(id + "/" + name); }
    static void face(String id, String... names) { for (String name : names) FACE.add(id + "/" + name); }

    /** An ink box: a text's name and its logical rectangle in the content, wide enough for both the mock's and ours. */
    record Ink(String name, int x, int y, int width, int height) { }

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
        PROBES.put("notifications", List.of(probe("System checkbox", 262, 86), probe("table box left", 260, 300),
            probe("table box right", 1010, 233), probe("header band", 400, 233), probe("Display separator", 755, 233),
            probe("Source separator", 902, 233), probe("header rule", 400, 243), probe("heading band", 400, 255),
            probe("data row", 400, 277), probe("Display arrow", 800, 275)));
        tight("notifications", "header band", "heading band");
        face("notifications", "table box left", "table box right", "header band", "Display separator", "Source separator",
            "header rule", "heading band", "data row", "Display arrow");
        // The heading count is semibold in text.secondary and the Source cells read text.secondary (2x: "6 groups" 1825 to
        // 1918 and 501 to 522, 630 ink pixels; "Default" 1826 to 1900 and 544 to 561, #5C6370).
        INKS.put("notifications", List.of(new Ink("6 groups", 908, 249, 56, 14), new Ink("Default", 908, 270, 48, 14)));
        face("notifications", "6 groups", "Default");
        // Find by Shortcut and Collapse All are each 1 px wider than the mock's (the labels' fractional advances round up), so
        // the Find by Shortcut probe takes its face 3 px inside the mock's left edge (737), which ours meets at 735.
        PROBES.put("keymap", List.of(probe("search field top", 300, 54), probe("Find by Shortcut face", 740, 65),
            probe("tool button top", 790, 53), probe("tree box top", 600, 83), probe("header band", 600, 94), probe("header rule", 600, 104),
            probe("Scope separator", 779, 94), probe("Shortcut separator", 886, 94), probe("Copy keycap edge", 911, 180)));
        tight("keymap", "header band");
        face("keymap", "header rule", "Copy keycap edge");
        // Mock 07b: the band's edges #C9D7F2 and fill #EEF2FA, the field's ring #2F65CA, the dot #D03A3A, Set's disabled fill #E1E3E6.
        PROBES.put("keymap-recording", List.of(probe("edited row selected", 600, 183), probe("band top edge", 600, 195),
            probe("band fill", 300, 240), probe("band at the tree's left", 250, 240), probe("band bottom edge", 600, 254),
            probe("recorder field", 500, 213), probe("recorder ring", 600, 202), probe("armed dot", 295, 213), probe("Set disabled", 1000, 213)));
        face("keymap-recording", "edited row selected", "band top edge", "band fill", "band at the tree's left", "band bottom edge",
            "recorder field", "recorder ring", "armed dot", "Set disabled");
        PROBES.put("plugins", List.of(probe("list box", 247, 300), probe("list surface", 400, 400), probe("search strip", 400, 56),
            probe("search strip rule", 400, 90), probe("heading band", 450, 99), probe("selected row", 450, 120),
            probe("checkbox on the selection", 256, 133), probe("active tab", 558, 113), probe("inactive tab", 638, 113),
            probe("Disable right edge", 1023, 65), probe("Disable top edge", 1000, 53), probe("second heading rule", 450, 151)));
        // The second heading's rule is held to PROBE: border.row #E4E5E8 lies ΔE 2.1 from the mock's #DDE0E4 on every heading
        // rule (the band is ΔE 6 away); ClassicListHeadingsTest.aLaterHeadingKeepsItsOwnTopRule pins its colour.
        tight("plugins", "search strip", "heading band", "inactive tab");
        face("plugins", "heading band", "second heading rule", "selected row", "checkbox on the selection", "active tab", "inactive tab");
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

    /** {@code image}'s ink in {@code box} (2x pixels): its darkest pixel and how many pixels lie nearer it than the paper. */
    static int[] ink(BufferedImage image, Rectangle box) {
        int darkest = 0xFFFFFF, dark = 766, paper = -1;
        for (int y = box.y; y < box.y + box.height; y++) for (int x = box.x; x < box.x + box.width; x++) {
            int rgb = image.getRGB(x, y), sum = (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
            if (sum < dark) { dark = sum; darkest = rgb; }
            paper = Math.max(paper, sum);
        }
        int amount = 0;
        for (int y = box.y; y < box.y + box.height; y++) for (int x = box.x; x < box.x + box.width; x++) {
            int rgb = image.getRGB(x, y);
            if (2 * ((rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF)) <= dark + paper) amount++;
        }
        return new int[] {darkest, amount};
    }

    /** {@code render}'s ink boxes of {@code key} against {@code mock}; the failures, the face's printed when not PLEX. */
    static List<String> inks(String key, BufferedImage render, BufferedImage mock) {
        var failures = new ArrayList<String>();
        for (var box : INKS.getOrDefault(key, List.of())) {
            var pixels = new Rectangle(2 * box.x(), 2 * box.y(), 2 * box.width(), 2 * box.height());
            int[] ours = ink(render, pixels), theirs = ink(mock, pixels);
            double distance = SettingsMockMatch.deltaE(ours[0], theirs[0]), amount = Math.abs(ours[1] - theirs[1]) / (double) Math.max(1, theirs[1]);
            if (distance <= SettingsChromeMockTest.PROBE && amount <= INK_AMOUNT) continue;
            String name = key + "/" + box.name();
            String failure = String.format("%s ink: render #%06X, mock #%06X, ΔE %.1f; amount %d against %d", name, ours[0] & 0xFFFFFF,
                theirs[0] & 0xFFFFFF, distance, ours[1], theirs[1]);
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
            failures.addAll(inks(id, render[0], mock));
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

    @Test void notificationsMatchesItsMock() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            assertThat(compare(settings, List.of("notifications"))).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }

    /** The dialog's Keymap page, selected; EDT. */
    static dev.jasper.app.shortcuthelp.KeymapPage keymap(EnterpriseSettings settings) {
        settings.dialog().select("keymap");
        return (dev.jasper.app.shortcuthelp.KeymapPage) settings.dialog().pages().stream().filter(page -> page.id().equals("keymap")).findFirst().orElseThrow();
    }

    @Test void keymapMatchesItsMock() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            SwingUtilities.invokeAndWait(() -> dev.jasper.app.shortcuthelp.ShortcutPreviewSupport.expand(keymap(settings), "Clipboard"));
            assertThat(compare(settings, List.of("keymap"))).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }

    @Test void keymapRecordingMatchesItsMock() throws Exception {
        var recording = new SettingsChromeMockTest.Page("Settings 07b - Keymap - Recording shortcut.png", "keymap", 713, 7);
        try (var settings = EnterpriseSettings.open(false)) {
            var render = new BufferedImage[1];
            SwingUtilities.invokeAndWait(() -> {
                var keymap = keymap(settings);
                dev.jasper.app.shortcuthelp.ShortcutPreviewSupport.expand(keymap, "Clipboard");
                dev.jasper.app.shortcuthelp.ShortcutPreviewSupport.edit(keymap, "Copy");
                render[0] = settings.render("keymap", 1040, recording.height());
            });
            ImageIO.write(render[0], "png", Files.createDirectories(EnterpriseSettings.OUT.resolve("pages")).resolve("07b.png").toFile());
            var mock = SettingsChromeMockTest.content(recording);
            var failures = new ArrayList<>(probes("keymap-recording", render[0], mock));
            var shares = new StringBuilder();
            failures.addAll(share("keymap-recording", "07b", recording, render[0], mock, shares));
            Files.writeString(EnterpriseSettings.OUT.resolve("pages-keymap-recording.txt"), shares);
            assertThat(failures).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }

    @Test void pluginsMatchesItsMock() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            // The mock's selection, THD Sourcing Crypto, is the first row, which the list selects when it opens (fact 10).
            assertThat(compare(settings, List.of("plugins"))).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }
}
