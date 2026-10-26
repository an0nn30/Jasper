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
import dev.jasper.app.testsupport.KitTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 12.2 and 16.1.4: each Settings page's body against its 05 Settings mock (plan 9b Rulings
 * 16): probes on every OS, the ink boxes and the pixel share on macOS; a probe or share that follows the UI face is printed where an
 * installed IBM Plex hides the plugin's files, as plan 9a's chrome comparison does. Renders go to
 * jasper-app/build/enterprise-settings/pages.
 */
class SettingsPagesMockTest {
    static final double FLOOR = 0.93;
    /** A probe whose target lies within PROBE of its surroundings (a row rule, a band, a strip) is held this close. */
    static final double TIGHT_PROBE = 1;
    /**
     * Per page, the probes: a name, a logical point of the content and the colour it should show
     * ({@link SettingsChromeMockTest#expected}); a probe naming none (a composited pixel no role holds) takes the mock's.
     */
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
    /**
     * The probes below rows with a description, by "id/name", and how many pixels higher than the mock's their target may
     * lie: the mocks' description rows are 47.5 tall and plan 9b Task 1 lays them 47, so a rule or box edge drifts half a
     * pixel higher per such row above it (Task 1's residual; Task 7 measured every Tools page).
     */
    static final Map<String, Integer> HALF = new LinkedHashMap<>();
    /**
     * The probes on a residual, by "id/name", with its cause: printed, not asserted (plan 9b Task 7: a Tools page's own
     * composition or the fixture's data, which no plan of this series composes for Enterprise; recorded in the banner).
     */
    static final Map<String, String> RESIDUAL = new LinkedHashMap<>();

    static SettingsChromeMockTest.Probe probe(String name, int x, int y, String colour) { return new SettingsChromeMockTest.Probe(name, x, y, colour); }
    static void tight(String id, String... names) { for (String name : names) TIGHT.add(id + "/" + name); }
    static void face(String id, String... names) { for (String name : names) FACE.add(id + "/" + name); }
    static void half(String id, int pixels, String... names) { for (String name : names) HALF.put(id + "/" + name, pixels); }
    static void residual(String id, String cause, String... names) { for (String name : names) RESIDUAL.put(id + "/" + name, cause); }

    /**
     * An ink box: a text's name, its logical rectangle in the content, wide enough for both the mock's and ours, and the
     * role its ink is.
     *
     * @param name the text
     * @param x logical x
     * @param y logical y
     * @param width logical width
     * @param height logical height
     * @param colour the role key of its ink
     */
    record Ink(String name, int x, int y, int width, int height, String colour) { }

    static {
        PROBES.put("appearance", List.of(probe("Light preview ring", 430, 111, "accent"), probe("Light radio dot", 437, 164, "accent"),
            probe("first row rule", 700, 182, "border.row"), probe("second row rule", 700, 221, "border.row"), probe("Dark theme dropdown top", 1000, 191, "button.secondary.border"),
            probe("Theme box bottom", 1000, 313, "window rule"), probe("Workspace box top", 1000, 334, "window rule"),
            // The unchecked box's edge is plan 4b's border.hover, where the mock draws #9AA0A8: the probes take the box's
            // inside and the body just above its edge, which hold its place to the pixel.
            probe("Enable opacity checkbox", 266, 364, "surface.input"), probe("above the Enable opacity checkbox", 266, 357, "surface.app"), probe("Background blur checkbox", 262, 440, "accent")));
        tight("appearance", "first row rule", "second row rule");
        face("appearance", "Theme box bottom", "Workspace box top", "Enable opacity checkbox", "above the Enable opacity checkbox", "Background blur checkbox");
        PROBES.put("window", List.of(probe("Toolbar & Status Bar box top", 700, 53, "window rule"), probe("first row rule", 700, 102, "border.row"),
            probe("third row rule", 700, 180, "border.row"), probe("row rule start", 262, 102, "border.row"), probe("row rule inset", 255, 102, "surface.app"),
            probe("row rule end", 1015, 102, "surface.app"), probe("Toolbar height spinner top", 940, 111, "border.input"), probe("Show status bar checkbox", 262, 160, "accent"),
            probe("Tabs & Panels box top", 700, 285, "window rule")));
        tight("window", "first row rule", "third row rule", "row rule start", "row rule inset", "row rule end");
        face("window", "Tabs & Panels box top");
        PROBES.put("notifications", List.of(probe("System checkbox", 262, 86, "accent"), probe("table box left", 260, 300, "border.control"),
            probe("table box right", 1010, 233, "border.control"), probe("header band", 400, 233, "surface.tableHeader"), probe("Display separator", 755, 233, "border.divider"),
            probe("Source separator", 902, 233, "border.divider"), probe("header rule", 400, 243, "border.divider"), probe("heading band", 400, 255, "surface.tableHeader"),
            // The user's 2026-10-18 direction: the Display arrow is text.placeholder, which the mock's #7A808A matched before the port.
            probe("data row", 400, 277, "surface.card"), probe("Display arrow", 800, 275, "text.placeholder")));
        tight("notifications", "header band", "heading band");
        face("notifications", "table box left", "table box right", "header band", "Display separator", "Source separator",
            "header rule", "heading band", "data row", "Display arrow");
        // The heading count is semibold in text.secondary through the table face's text guard ("heading count", which moves
        // the IntelliJ Light port's text.secondary: the user's 2026-10-18 direction) and the Source cells read text.secondary
        // (2x: "6 groups" 1825 to 1918 and 501 to 522, 630 ink pixels; "Default" 1826 to 1900 and 544 to 561).
        INKS.put("notifications", List.of(new Ink("6 groups", 908, 249, 56, 14, "heading count"), new Ink("Default", 908, 270, 48, 14, "text.secondary")));
        face("notifications", "6 groups", "Default");
        // Find by Shortcut and Collapse All are each 1 px wider than the mock's (the labels' fractional advances round up), so
        // the Find by Shortcut probe takes its face 3 px inside the mock's left edge (737), which ours meets at 735.
        PROBES.put("keymap", List.of(probe("search field top", 300, 54, "border.input"), probe("Find by Shortcut face", 740, 65, "button.secondary.bg"),
            probe("tool button top", 790, 53, "button.secondary.border"), probe("tree box top", 600, 83, "border.control"), probe("header band", 600, 94, "surface.tableHeader"), probe("header rule", 600, 104, "border.divider"),
            probe("Scope separator", 779, 94, "border.divider"), probe("Shortcut separator", 886, 94, "border.divider"), probe("Copy keycap edge", 911, 180, "keycap edge")));
        tight("keymap", "header band");
        face("keymap", "header rule", "Copy keycap edge");
        // Mock 07b: the band's edges and fill, the field's ring, the dot, Set's disabled fill; the selected row's keycaps
        // stand in light boxes on its accent fill (their faces and edges composited, which no role holds: the mock's).
        PROBES.put("keymap-recording", List.of(probe("edited row selected", 600, 183, "selection.bg"), probe("band top edge", 600, 195, "band edge"),
            probe("band fill", 300, 240, "surface.rowHover"), probe("band at the tree's left", 250, 240, "surface.rowHover"), probe("band bottom edge", 600, 254, "band edge"),
            probe("recorder field", 500, 213, "surface.input"), probe("recorder ring", 600, 202, "border.focus"), probe("armed dot", 295, 213, "status.danger"), probe("Set disabled", 1000, 213, "surface.header"),
            probe("Copy keycap face", 915, 183, null)));
        face("keymap-recording", "Copy keycap face", "edited row selected", "band top edge", "band fill", "band at the tree's left", "band bottom edge",
            "recorder field", "recorder ring", "armed dot", "Set disabled");
        // The search strip is surface.sidebar, which the IntelliJ Light port sets apart from surface.panel (the user's 2026-10-18 direction).
        PROBES.put("plugins", List.of(probe("list box", 247, 300, "border.control"), probe("list surface", 400, 400, "surface.card"), probe("search strip", 400, 56, "surface.sidebar"),
            probe("search strip rule", 400, 90, "border.row"), probe("heading band", 450, 99, "surface.tableHeader"), probe("selected row", 450, 120, "selection.bg"),
            probe("checkbox on the selection", 256, 133, "surface.input"), probe("active tab", 558, 113, "surface.card"), probe("inactive tab", 638, 113, "surface.stripe"),
            probe("Disable right edge", 1023, 65, "button.secondary.border"), probe("Disable top edge", 1000, 53, "button.secondary.border"), probe("second heading rule", 450, 151, "border.row"),
            // The split's divider between the list and the details paints no rule (mock 08; a painted one stood at 2x 1078).
            probe("between the list and the details", 539, 400, "surface.app")));
        // The second heading's rule is border.row, held to PROBE; ClassicListHeadingsTest.aLaterHeadingKeepsItsOwnTopRule pins its colour.
        tight("plugins", "search strip", "heading band", "inactive tab", "between the list and the details");
        face("plugins", "heading band", "second heading rule", "selected row", "checkbox on the selection", "active tab", "inactive tab");
        PROBES.put("terminal.shell", List.of(probe("arguments box top", 400, 205, "border.control"), probe("arguments box bottom", 400, 248, "border.control"),
            probe("arguments box interior", 400, 230, "surface.card"), probe("Remove left edge", 919, 217, "border.divider"), probe("Add Variable… right edge", 1010, 317, "button.secondary.border"),
            probe("Add Variable… top edge", 960, 305, "button.secondary.border")));
        face("terminal.shell", "arguments box top", "arguments box bottom", "arguments box interior", "Remove left edge",
            "Add Variable… right edge", "Add Variable… top edge");
        PROBES.put("command-palette", List.of(probe("box top", 600, 53, "window rule"), probe("box bottom", 900, 108, "window rule"), probe("spinner left edge", 929, 83, "border.input")));
        PROBES.put("desk-buddy", List.of(probe("box bottom", 700, 108, "window rule")));
        // Edit Custom VM Options… and Reveal are each 1 px wider than the mock's (Task 3's rounded label advances) and 7 apart
        // (INLINE_GAP) against 6, so the left edge lies 3 px early: the probe takes its face, Reveal's right edge the row's end.
        PROBES.put("system", List.of(probe("Run in background checkbox", 262, 82, "accent"), probe("Edit Custom VM Options… face", 790, 213, "button.secondary.bg"),
            probe("Reveal right edge", 1010, 219, "button.secondary.border")));
        face("system", "Edit Custom VM Options… face", "Reveal right edge");
        PROBES.put("terminal.font", List.of(probe("fallback list top", 400, 172, "border.control"), probe("second fallback row", 400, 196, "surface.zebra"),
            probe("fallback list bottom", 400, 215, "border.control"), probe("Ligatures checkbox", 262, 276, "accent")));
        tight("terminal.font", "second fallback row");
        face("terminal.font", "fallback list bottom", "Ligatures checkbox");
        PROBES.put("terminal.cursor", List.of(probe("row rule", 700, 102, "border.row"), probe("box bottom", 700, 147, "window rule")));
        tight("terminal.cursor", "row rule");
        String vault = dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.vault"), editor = dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.editor");
        String files = dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.fileserver"), index = dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.indexer");
        String remote = dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.remote"), sample = dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.sample");
        String history = dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.history");
        PROBES.put(vault, List.of(probe("first row rule", 700, 110, "border.row"), probe("second row rule", 700, 158, "border.row"), probe("box bottom", 700, 211, "window rule"),
            probe("Generated keys folder field left edge", 791, 135, "border.input")));
        tight(vault, "first row rule", "second row rule");
        face(vault, "second row rule", "box bottom");
        half(vault, 1, "second row rule", "box bottom");
        PROBES.put(editor, List.of(probe("first row rule", 700, 110, "border.row"), probe("Font box bottom", 700, 164, "window rule"), probe("second box top", 700, 185, "window rule")));
        tight(editor, "first row rule");
        face(editor, "Font box bottom", "second box top");
        half(editor, 1, "Font box bottom", "second box top");
        PROBES.put(files, List.of(probe("first row rule", 700, 110, "border.row"), probe("box bottom", 700, 227, "window rule"), probe("port range field left edge", 791, 190, "border.input")));
        tight(files, "first row rule");
        face(files, "box bottom");
        half(files, 2, "box bottom");
        PROBES.put(index, List.of(probe("Projects box top", 600, 53, "window rule"), probe("projects table top", 400, 70, "border.control"), probe("projects header band", 400, 80, "surface.tableHeader"), probe("projects header rule", 400, 91, "border.divider"),
            probe("projects table left", 260, 120, "border.control"), probe("projects table right", 1010, 80, "border.control")));
        tight(index, "projects header band");
        residual(index, "the fixture indexes no project, so the plugin's page shows its empty state for the mock's table",
            "projects table top", "projects header band", "projects header rule", "projects table left", "projects table right");
        // The fixture's empty state in place of the mock's four projects (Rulings 16: fixture text): 0.930, rounded down.
        FLOORS.put(index, 0.92);
        PROBES.put(remote, List.of(probe("first row rule", 700, 110, "border.row"), probe("fifth row rule", 700, 300, "border.row"), probe("box bottom", 700, 353, "window rule")));
        tight(remote, "first row rule", "fifth row rule");
        face(remote, "fifth row rule", "box bottom");
        half(remote, 2, "fifth row rule", "box bottom");
        // The host rows take the Reset menu (spec 12.1), so the checkboxes lead their labels; as on Appearance, the unchecked
        // edge is border.hover, where the mock draws #9AA0A8, so the probes take the box's inside and the body above it.
        PROBES.put(sample, List.of(probe("first checkbox", 266, 86, "surface.input"), probe("above the first checkbox", 266, 79, "surface.app")));
        face(sample, "first checkbox", "above the first checkbox");
        PROBES.put(history, List.of(probe("list top", 700, 126, "border.control"), probe("row rule", 700, 286, "border.row"), probe("box bottom", 700, 339, "window rule")));
        tight(history, "row rule");
        face(history, "row rule", "box bottom");
        residual(history, "the mock's trivial-commands list scrolls after five rows under a horizontal scroll bar; ours shows eight",
            "row rule", "box bottom");
    }

    /** The body: right of the sidebar rule, below the header rule, above the footer rule (2x pixels). */
    static Rectangle body(SettingsChromeMockTest.Page page) { return new Rectangle(2 * 231, 2 * 35, 2080 - 2 * 231, 2 * (page.height() - 35 - 45)); }

    static SettingsChromeMockTest.Page page(String id) {
        return SettingsChromeMockTest.PAGES.stream().filter(page -> page.id().equals(id)).findFirst().orElseThrow();
    }

    /** {@code render}'s probes of {@code key} (a page id) against {@code mock}; the failures, the face's printed when not PLEX. */
    static List<String> probes(String key, BufferedImage render, BufferedImage mock) {
        return probes(key, render, mock, Map.of());
    }

    static List<String> probes(String key, BufferedImage render, BufferedImage mock, Map<String, java.awt.Point> positions) {
        var failures = new ArrayList<String>();
        for (var probe : PROBES.getOrDefault(key, List.of())) {
            int x = 2 * probe.x(), y = 2 * probe.y();
            var actual = positions.getOrDefault(probe.name(), new java.awt.Point(probe.x(), probe.y()));
            int actualX = 2 * actual.x, actualY = 2 * actual.y;
            String name = key + "/" + probe.name();
            double limit = TIGHT.contains(name) ? TIGHT_PROBE : SettingsChromeMockTest.PROBE, distance = Double.MAX_VALUE;
            // A HALF probe's target may lie up to its pixels higher in the render (logical pixels, two 2x rows each).
            int expected = SettingsChromeMockTest.expected(probe, mock, x, y);
            for (int up = 0; up <= 2 * HALF.getOrDefault(name, 0) && distance > limit; up += 2)
                distance = Math.min(distance, SettingsMockMatch.deltaE(render.getRGB(actualX, actualY - up), expected));
            if (distance <= limit) continue;
            String failure = String.format("%s at (%d, %d): render #%06X, expected %s #%06X, ΔE %.1f", name, probe.x(), probe.y(),
                render.getRGB(actualX, actualY) & 0xFFFFFF, probe.colour() == null ? "the mock's" : probe.colour(), expected & 0xFFFFFF, distance);
            if (RESIDUAL.containsKey(name)) System.out.println("Residual (plan 9b Task 7: " + RESIDUAL.get(name) + "): " + failure);
            else if (FACE.contains(name) && !SettingsChromeMockTest.PLEX) System.out.println(SettingsChromeMockTest.UNCHECKED + failure);
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

    /** {@code render}'s ink boxes of {@code key} against {@code mock}; the failures on macOS, the face's printed when not PLEX. */
    static List<String> inks(String key, BufferedImage render, BufferedImage mock) {
        var failures = new ArrayList<String>();
        for (var box : INKS.getOrDefault(key, List.of())) {
            var pixels = new Rectangle(2 * box.x(), 2 * box.y(), 2 * box.width(), 2 * box.height());
            int[] ours = ink(render, pixels), theirs = ink(mock, pixels);
            int colour = SettingsChromeMockTest.expected(box.colour()).getRGB();
            double distance = SettingsMockMatch.deltaE(ours[0], colour), amount = Math.abs(ours[1] - theirs[1]) / (double) Math.max(1, theirs[1]);
            if (distance <= SettingsChromeMockTest.PROBE && amount <= INK_AMOUNT) continue;
            String name = key + "/" + box.name();
            String failure = String.format("%s ink: render #%06X, %s #%06X, ΔE %.1f; amount %d against the mock's %d", name, ours[0] & 0xFFFFFF,
                box.colour(), colour & 0xFFFFFF, distance, ours[1], theirs[1]);
            if (FACE.contains(name) && !SettingsChromeMockTest.PLEX) System.out.println(SettingsChromeMockTest.UNCHECKED + failure);
            // An ink amount is as rasterizer-dependent as the body share, so it is asserted where the shares are (Rulings 16).
            else if (!SettingsChromeMockTest.MAC) System.out.println("Not asserted off macOS (Rulings 16): " + failure);
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
            var positions = new LinkedHashMap<String, java.awt.Point>();
            SwingUtilities.invokeAndWait(() -> {
                render[0] = settings.render(id, 1040, page.height());
                if (id.equals(dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.editor"))) {
                    // The approved Editor font controls expand the first section. Preserve the original
                    // colour probes and share threshold while following the two section edges.
                    var root = settings.content();
                    var font = KitTestSupport.require(root, "editor.settings.font", javax.swing.JComponent.class);
                    var editing = KitTestSupport.require(root, "editor.settings.editing", javax.swing.JComponent.class);
                    var fontBounds = SwingUtilities.convertRectangle(font.getParent(), font.getBounds(), root);
                    var editingBounds = SwingUtilities.convertRectangle(editing.getParent(), editing.getBounds(), root);
                    positions.put("Font box bottom", new java.awt.Point(700, fontBounds.y + fontBounds.height - 1));
                    positions.put("second box top", new java.awt.Point(700, editingBounds.y + editing.getComponent(0).getHeight() / 2));
                }
            });
            var out = Files.createDirectories(EnterpriseSettings.OUT.resolve("pages"));
            String number = page.mock().substring(9, 11);
            ImageIO.write(render[0], "png", out.resolve(number + ".png").toFile());
            var mock = SettingsChromeMockTest.content(page);
            failures.addAll(probes(id, render[0], mock, positions));
            failures.addAll(inks(id, render[0], mock));
            failures.addAll(share(id, number, page, render[0], mock, shares));
        }
        Files.writeString(EnterpriseSettings.OUT.resolve("pages-" + ids.getFirst() + ".txt"), shares);
        return failures;
    }

    @Test void pageFactoryKeepsWorkspaceControlsSpecificToTheHost() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var system = new CorePages.SystemActions(() -> java.nio.file.Path.of("config.toml"),
                () -> { }, () -> { }, () -> { }, () -> { }, () -> false, java.util.Optional.empty());
            var supplied = CorePages.Pages.of(context -> new GroupPage("keymap", "Keymap", java.util.Optional.empty(),
                false, List::of, context), context -> new GroupPage("plugins", "Plugins", java.util.Optional.empty(),
                false, List::of, context));
            var draft = new SettingsDraft(dev.jasper.app.config.ConfigLoader.parse(java.nio.file.Path.of("config.toml"), "", true).values());
            var context = PageKit.context(draft, ignored -> { }, () -> { });
            for (boolean glass : new boolean[] {false, true}) {
                assertWorkspace(CorePages.pages(AppearanceFixtures.CHOICES, system, ShellChoices.NONE,
                    NotificationGroupChoices.NONE, TabNameChoices.NONE, supplied, glass).apply(context), glass);
            }
            assertWorkspace(CorePages.pages(AppearanceFixtures.CHOICES, system, ShellChoices.NONE,
                NotificationGroupChoices.NONE, supplied).apply(context), com.formdev.flatlaf.util.SystemInfo.isMacOS);
            assertThat(draft.value(dev.jasper.app.config.CoreSettings.SYSTEM_RUN_IN_BACKGROUND))
                .isEqualTo(com.formdev.flatlaf.util.SystemInfo.isMacOS || com.formdev.flatlaf.util.SystemInfo.isWindows);
        });
    }

    private static void assertWorkspace(List<SettingsPage> pages, boolean glass) {
        try {
            var appearance = (SchemaPage) pages.stream().filter(page -> page.id().equals("appearance")).findFirst().orElseThrow();
            appearance.component();
            var names = appearance.rows().stream().map(row -> row.setting().name()).toList();
            assertThat(names.contains("ui.workspace.opacity")).isEqualTo(glass);
            assertThat(names.contains("ui.workspace.blur")).isEqualTo(glass);
            assertThat(names).contains("ui.font.family", "ui.font.size");
        } finally { pages.forEach(SettingsPage::dispose); }
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
            var positions = new LinkedHashMap<String, java.awt.Point>();
            SwingUtilities.invokeAndWait(() -> {
                var keymap = keymap(settings);
                dev.jasper.app.shortcuthelp.ShortcutPreviewSupport.expand(keymap, "Clipboard");
                dev.jasper.app.shortcuthelp.ShortcutPreviewSupport.edit(keymap, "Copy");
                render[0] = settings.render("keymap", 1040, recording.height());
                var value = KitTestSupport.require(settings.content(), "editor.value", javax.swing.JComponent.class);
                java.awt.Component detail = value.getParent();
                while (!(detail instanceof dev.jasper.ui.swing.KitView)) detail = detail.getParent();
                var bounds = SwingUtilities.convertRectangle(detail.getParent(), detail.getBounds(), settings.content());
                var field = SwingUtilities.convertRectangle(value.getParent(), value.getBounds(), settings.content());
                var metrics = dev.jasper.app.testsupport.EnterpriseLook.kit().metrics();
                int top = bounds.y - metrics.value(dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_TOP);
                int bottom = bounds.y + bounds.height + metrics.value(dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_BOTTOM) - 1;
                // The native fallback font can grow the rows and editor. Probe the same band surfaces at
                // their laid-out positions, retaining the mock's exact colours and the overall share floor.
                positions.put("band top edge", new java.awt.Point(600, top));
                positions.put("band bottom edge", new java.awt.Point(600, bottom));
                positions.put("band fill", new java.awt.Point(300, bottom - 3));
                positions.put("recorder ring", new java.awt.Point(600, field.y));
            });
            ImageIO.write(render[0], "png", Files.createDirectories(EnterpriseSettings.OUT.resolve("pages")).resolve("07b.png").toFile());
            var mock = SettingsChromeMockTest.content(recording);
            var failures = new ArrayList<>(probes("keymap-recording", render[0], mock, positions));
            var shares = new StringBuilder();
            failures.addAll(share("keymap-recording", "07b", recording, render[0], mock, shares));
            Files.writeString(EnterpriseSettings.OUT.resolve("pages-keymap-recording.txt"), shares);
            if (!failures.isEmpty()) SwingUtilities.invokeAndWait(() -> recordingGeometry(settings.content(), settings.content()));
            assertThat(failures).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }

    private static void recordingGeometry(java.awt.Component component, javax.swing.JComponent root) {
        String name = component.getName();
        if (name != null && (name.contains("editor") || name.contains("Copy") || name.contains("Clipboard"))) {
            var bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
            System.out.println("Recording geometry: " + name + " " + bounds + " font=" + component.getFont());
        }
        if (component instanceof java.awt.Container container)
            for (var child : container.getComponents()) recordingGeometry(child, root);
    }

    @Test void pluginsMatchesItsMock() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            // The mock's selection, THD Sourcing Crypto, is the first row, which the list selects when it opens (fact 10).
            assertThat(compare(settings, List.of("plugins"))).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }

    @Test void shellMatchesItsMock() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            assertThat(compare(settings, List.of("terminal.shell"))).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }

    /**
     * Once the user has variables, the Shell page's environment table is the row's control under its title: its box spans
     * the rows' text inset (260 to 1010, as the Notifications table and the mocks' rules) and the row's padding keeps the
     * next row's rule off its border. No mock draws this state; the render goes to pages/11-variables.png.
     */
    @Test void shellWithVariablesKeepsItsTableInsideTheRows() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            var page = page("terminal.shell");
            SwingUtilities.invokeAndWait(() -> {
                settings.dialog().draft().stage(dev.jasper.app.config.CoreSettings.TERMINAL_ENV, java.util.Optional.of(Map.of("EDITOR", "vim", "LANG", "en_US.UTF-8")));
                var render = settings.render("terminal.shell", 1040, page.height());
                try {
                    ImageIO.write(render, "png", Files.createDirectories(EnterpriseSettings.OUT.resolve("pages")).resolve("11-variables.png").toFile());
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                var content = settings.content();
                String id = dev.jasper.app.config.CoreSettings.TERMINAL_ENV.name();
                var table = SwingUtilities.convertRectangle(KitTestSupport.require(content, id + ":table", javax.swing.JComponent.class).getParent(),
                    KitTestSupport.require(content, id + ":table", javax.swing.JComponent.class).getBounds(), content);
                var row = KitTestSupport.require(content, "row:" + id, javax.swing.JComponent.class);
                var rowBounds = SwingUtilities.convertRectangle(row.getParent(), row.getBounds(), content);
                assertThat(table.x).as("the table's left edge on the rows' text inset").isEqualTo(260);
                assertThat(table.x + table.width - 1).as("its right edge on the rows' text inset (the Notifications table's 1010)").isEqualTo(1010);
                assertThat(rowBounds.y + rowBounds.height - (table.y + table.height)).as("the row's padding below the table, above the next row's rule")
                    .isGreaterThanOrEqualTo(6);
            });
        }
    }

    @Test void theRemainingPagesMatchTheirMocks() throws Exception {
        var ids = List.of("command-palette", "desk-buddy", "system", "terminal.font", "terminal.cursor",
            dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.vault"), dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.editor"),
            dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.fileserver"), dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.indexer"),
            dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.remote"), dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.sample"),
            dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.history"));
        try (var settings = EnterpriseSettings.open(false)) {
            assertThat(compare(settings, ids)).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }

    @Test void composedPagesFollowEnterpriseDark() throws Exception {
        try (var settings = EnterpriseSettings.open(true)) {
            var palette = dev.jasper.app.testsupport.EnterpriseLook.theme().palette(true);
            for (var id : List.of("notifications", "keymap", "plugins", "terminal.shell")) {
                var page = page(id);
                var render = new BufferedImage[1];
                SwingUtilities.invokeAndWait(() -> render[0] = settings.render(id, 1040, page.height()));
                ImageIO.write(render[0], "png", Files.createDirectories(EnterpriseSettings.OUT.resolve("dark")).resolve(page.mock().substring(9, 11) + ".png").toFile());
                assertThat(render[0].getRGB(2 * 600, 2 * 40) & 0xFFFFFF).as(id + ": the body on dark surface.app")
                    .isEqualTo(palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB() & 0xFFFFFF);
                long light = 0;
                for (int y = 0; y < render[0].getHeight(); y += 7) for (int x = 2 * 231; x < render[0].getWidth(); x += 7) {
                    int rgb = render[0].getRGB(x, y);
                    if (((rgb >> 16) & 0xFF) > 0xF0 && ((rgb >> 8) & 0xFF) > 0xF0 && (rgb & 0xFF) > 0xF0) light++;
                }
                assertThat(light).as(id + ": no light surface left in a dark page (text aside)").isLessThan(200);
            }
        }
    }
}
