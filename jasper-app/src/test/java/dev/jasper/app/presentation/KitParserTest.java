package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.ui.kit.ControlVariants;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.MetricTokens;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Spec 5.1 to 5.9: the kit file, its values and every way it can be invalid. */
class KitParserTest {
    static final String PLEX_SANS = "IBM Plex Sans", PLEX_MONO = "IBM Plex Mono";
    /** Spec 5.1's shipped kit without [branding] (plan 11's). */
    static final String FULL = """
        kit = 1
        base = "classic"

        [chrome]
        title_bar = "native"
        menu_bar = "window"
        window_title = "Workstation — {session} — {directory}"
        tool_windows = "stripes"
        editor_tabs = "classic"
        status_bar = "cells"
        palette = "run_window"
        dialogs = "classic"
        motion = "none"

        [controls]
        toggle = "checkbox"
        exclusive = "radios"
        tabs = "folder"
        group = "titled_box"
        form_label = "colon"
        empty = "text"
        table = "classic"
        tree = "classic"
        stepper = "spinner"
        ordered_list = "side_column"
        buttons = "classic"
        tag = "boxed"
        route = "edged"
        prompt = "plain"

        [metrics]
        control_height = 22
        row_height = 22
        control_radius = 3
        field_radius = 2
        group_radius = 3
        focus_ring = 2
        tool_window_header = 24
        group_padding = 12
        dependent_indent = 20
        navigation_width = 230
        status_dot = 7

        [type]
        base = 12

        [type.ui]
        family = "IBM Plex Sans"
        regular = "fonts/ibm-plex-sans-regular.ttf"
        medium = "fonts/ibm-plex-sans-medium.ttf"
        semibold = "fonts/ibm-plex-sans-semibold.ttf"

        [type.data]
        family = "IBM Plex Mono"
        regular = "fonts/ibm-plex-mono-regular.ttf"
        medium = "fonts/ibm-plex-mono-medium.ttf"

        [colors]
        dialog_surface = "surface.app"
        navigation_surface = "surface.card"

        [defaults]
        "ui.theme.light" = "look"
        "ui.theme.dark" = "look"
        "palette.visible_rows" = 8
        "window.toolbar_height" = 34
        """;

    /** The fonts FULL names, under the kit's folder "presentation/". */
    static Map<String, byte[]> fonts() {
        var files = new HashMap<String, byte[]>();
        files.put("presentation/fonts/ibm-plex-sans-regular.ttf", FontFileTest.face(PLEX_SANS, PLEX_SANS));
        files.put("presentation/fonts/ibm-plex-sans-medium.ttf", FontFileTest.face("IBM Plex Sans Medm", PLEX_SANS));
        files.put("presentation/fonts/ibm-plex-sans-semibold.ttf", FontFileTest.face("IBM Plex Sans SmBld", PLEX_SANS));
        files.put("presentation/fonts/ibm-plex-mono-regular.ttf", FontFileTest.face(PLEX_MONO, PLEX_MONO));
        files.put("presentation/fonts/ibm-plex-mono-medium.ttf", FontFileTest.face("IBM Plex Mono Medm", PLEX_MONO));
        return files;
    }

    /** A theme with both variants, declared by the provider as "look". */
    static JasperTheme look() { return JasperTheme.bundled(); }

    static KitParser.Context context(Map<String, byte[]> files, Map<String, Optional<JasperTheme>> themes) {
        return new KitParser.Context("dev.example.look", "presentation/", (path, max) -> {
            byte[] bytes = files.get(path);
            return bytes == null ? Optional.empty() : Optional.of(java.util.Arrays.copyOf(bytes, Math.min(bytes.length, max + 1)));
        }, themes);
    }

    static KitParser.Parsed parse(String text) throws KitParser.Invalid {
        return KitParser.parse(text.getBytes(StandardCharsets.UTF_8), context(fonts(), Map.of("look", Optional.of(look()))));
    }

    static String problem(String text) {
        try { parse(text); throw new AssertionError("valid: " + text); }
        catch (KitParser.Invalid invalid) { return invalid.getMessage(); }
    }

    @Test void aMinimalKitIsItsBaseUnderTheProvidersId() throws Exception {
        var parsed = parse("kit = 1\nbase = \"default\"\n");
        assertThat(parsed.kit().id()).isEqualTo("dev.example.look");
        assertThat(parsed.kit().metrics()).isEqualTo(Kit.DEFAULT.metrics());
        assertThat(parsed.kit().type()).isEqualTo(Kit.DEFAULT.type());
        assertThat(parsed.kit().faces()).isEqualTo(Kit.DEFAULT.faces());
        assertThat(parsed.kit().controls()).isEqualTo(Kit.DEFAULT.controls());
        assertThat(parsed.kit().colors()).isEqualTo(Kit.DEFAULT.colors());
        assertThat(parsed.kit().uiKeys()).isEqualTo(Kit.DEFAULT.uiKeys());
        assertThat(parsed.chrome()).isEqualTo(ChromeSpec.DEFAULT);
        assertThat(parsed.classicBase()).isFalse();
        assertThat(parsed.defaults()).isEqualTo(dev.jasper.app.config.DefaultSource.CORE);
        assertThat(parsed.faces()).isEqualTo(KitParser.Faces.NONE);
        assertThat(parsed.dropped()).isEmpty();
    }

    @Test void theClassicBaseStartsFromTheClassicCatalogAndChrome() throws Exception {
        var parsed = parse("kit = 1\nbase = \"classic\"\n");
        assertThat(parsed.kit().metrics()).isEqualTo(Kit.CLASSIC.metrics());
        assertThat(parsed.kit().type()).isEqualTo(Kit.CLASSIC.type());
        assertThat(parsed.kit().controls()).isEqualTo(Kit.CLASSIC.controls());
        assertThat(parsed.kit().colors()).isEqualTo(Kit.CLASSIC.colors());
        assertThat(parsed.kit().uiKeys()).isEqualTo(Kit.CLASSIC.uiKeys());
        assertThat(parsed.chrome()).isEqualTo(ChromeSpec.CLASSIC);
        assertThat(parsed.chrome().windowTitle()).isEqualTo("{context}");
        assertThat(parsed.classicBase()).isTrue();
    }

    @Test void theShippedShapeParsesIntoEveryValueItNames() throws Exception {
        var parsed = parse(FULL);
        assertThat(parsed.chrome()).isEqualTo(new ChromeSpec(ChromeSpec.TitleBar.NATIVE, ChromeSpec.MenuBar.WINDOW,
            "Workstation — {session} — {directory}", ChromeSpec.ToolWindows.STRIPES, ChromeSpec.EditorTabs.CLASSIC,
            ChromeSpec.StatusBar.CELLS, ChromeSpec.PaletteKind.RUN_WINDOW, ChromeSpec.DialogKind.CLASSIC, ChromeSpec.MotionKind.NONE));
        assertThat(parsed.kit().controls()).isEqualTo(new ControlVariants(ControlVariants.Toggle.CHECKBOX, ControlVariants.Exclusive.RADIOS,
            ControlVariants.Tabs.FOLDER, ControlVariants.Group.TITLED_BOX, ControlVariants.FormLabel.COLON, ControlVariants.Empty.TEXT,
            ControlVariants.Table.CLASSIC, ControlVariants.Tree.CLASSIC, ControlVariants.Stepper.SPINNER, ControlVariants.OrderedList.SIDE_COLUMN,
            ControlVariants.Buttons.CLASSIC, ControlVariants.Tag.BOXED, ControlVariants.Route.EDGED, ControlVariants.Prompt.PLAIN));
        assertThat(parsed.kit().metrics()).isEqualTo(Kit.CLASSIC.metrics().with(KitFixtures.enterpriseTokens()));
        assertThat(parsed.kit().type()).isEqualTo(Kit.CLASSIC.type().withBody(12));
        assertThat(parsed.kit().colors().dialogSurface()).isEqualTo(SemanticColor.SURFACE_APP);
        assertThat(parsed.kit().colors().navigationSurface()).isEqualTo(SemanticColor.SURFACE_CARD);
        assertThat(parsed.defaults().value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("look");
        assertThat(parsed.defaults().value(CoreSettings.UI_THEME_DARK)).isEqualTo("look");
        assertThat(parsed.defaults().value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(8);
        assertThat(parsed.defaults().value(CoreSettings.WINDOW_TOOLBAR_HEIGHT)).isEqualTo(34);
        var ui = parsed.faces().ui().orElseThrow();
        assertThat(ui.family()).isEqualTo(PLEX_SANS);
        assertThat(List.of(ui.regular().fullName(), ui.medium().fullName(), ui.semibold().fullName()))
            .containsExactly(PLEX_SANS, "IBM Plex Sans Medm", "IBM Plex Sans SmBld");
        assertThat(parsed.faces().data().orElseThrow().medium().family()).isEqualTo(PLEX_MONO);
        assertThat(parsed.faces().files()).extracting(FontFile::path).containsExactly("fonts/ibm-plex-sans-regular.ttf",
            "fonts/ibm-plex-sans-medium.ttf", "fonts/ibm-plex-sans-semibold.ttf", "fonts/ibm-plex-mono-regular.ttf", "fonts/ibm-plex-mono-medium.ttf");
    }

    /** Plan 4a's MetricTokens.of reads each token's first role, so a token left out keeps its base's value exactly. */
    @Test void aTokenLeftOutKeepsItsBasesValue() throws Exception {
        var metrics = parse("kit = 1\nbase = \"classic\"\n[metrics]\nrow_height = 22\n").kit().metrics();
        assertThat(metrics.value(DesignMetrics.Role.FIELD_HEIGHT)).as("control_height left out").isEqualTo(22);
        assertThat(metrics.value(DesignMetrics.Role.BUTTON_HEIGHT)).isEqualTo(24);
        assertThat(metrics).isEqualTo(Kit.CLASSIC.metrics());
        var tall = parse("kit = 1\nbase = \"default\"\n[metrics]\nrow_height = 26\n").kit().metrics();
        assertThat(MetricTokens.of(tall).value(MetricTokens.Token.ROW_HEIGHT)).isEqualTo(26);
        assertThat(MetricTokens.of(tall).value(MetricTokens.Token.CONTROL_HEIGHT))
            .isEqualTo(MetricTokens.of(Kit.DEFAULT.metrics()).value(MetricTokens.Token.CONTROL_HEIGHT));
    }

    @Test void everyChromeAndControlPointTakesBothItsValues() throws Exception {
        assertThat(parse("kit = 1\nbase = \"default\"\n[chrome]\ntitle_bar = \"native\"\n").chrome().titleBar()).isEqualTo(ChromeSpec.TitleBar.NATIVE);
        assertThat(parse("kit = 1\nbase = \"classic\"\n[chrome]\ntitle_bar = \"unified\"\nmenu_bar = \"system\"\n").chrome().menuBar())
            .isEqualTo(ChromeSpec.MenuBar.SYSTEM);
        String[][] controls = {{"toggle", "switch", "checkbox"}, {"exclusive", "segments", "radios"}, {"tabs", "segments", "folder"},
            {"group", "card", "titled_box"}, {"form_label", "plain", "colon"}, {"empty", "tile", "text"}, {"table", "plain", "classic"},
            {"tree", "plain", "classic"}, {"stepper", "stepper", "spinner"}, {"ordered_list", "inline", "side_column"},
            {"buttons", "raised", "classic"}, {"tag", "tint", "boxed"}, {"route", "tiles", "edged"}, {"prompt", "tile", "plain"}};
        // Each point's own component, in the order of controls above: a substring of toString() could match another point.
        List<java.util.function.Function<ControlVariants, ControlVariants.Variant>> components = List.of(
            ControlVariants::toggle, ControlVariants::exclusive, ControlVariants::tabs,
            ControlVariants::group, ControlVariants::formLabel, ControlVariants::empty,
            ControlVariants::table, ControlVariants::tree, ControlVariants::stepper,
            ControlVariants::orderedList, ControlVariants::buttons, ControlVariants::tag,
            ControlVariants::route, ControlVariants::prompt);
        assertThat(components).hasSameSizeAs(controls);
        for (int index = 0; index < controls.length; index++) {
            String[] point = controls[index];
            for (int value = 1; value <= 2; value++) {
                var kit = parse("kit = 1\nbase = \"" + (value == 1 ? "classic" : "default") + "\"\n[controls]\n" + point[0] + " = \"" + point[value] + "\"\n").kit();
                assertThat(components.get(index).apply(kit.controls()).key()).as(point[0]).isEqualTo(point[value]);
            }
        }
    }

    @Test void aDefaultsThemeMayBeBuiltInOrDeclaredAndADeclaredThemeThatDidNotLoadIsDropped() throws Exception {
        var builtIn = parse("kit = 1\nbase = \"default\"\n[defaults]\n\"ui.theme.light\" = \"catppuccin-latte\"\n");
        assertThat(builtIn.defaults().value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("catppuccin-latte");
        var dropped = KitParser.parse("kit = 1\nbase = \"default\"\n[defaults]\n\"ui.theme.dark\" = \"look\"\n\"palette.visible_rows\" = 3\n"
            .getBytes(StandardCharsets.UTF_8), context(fonts(), Map.of("look", Optional.empty())));
        assertThat(dropped.dropped()).containsExactly("ui.theme.dark");
        assertThat(dropped.defaults().value(CoreSettings.UI_THEME_DARK)).as("the core default applies").isEqualTo(CoreSettings.UI_THEME_DARK.defaultValue());
        assertThat(dropped.defaults().value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(3);
    }

    @Test void everyInvalidKitGivesOneMessageNamingTheKey() {
        String head = "kit = 1\nbase = \"default\"\n";
        String ui = "[type.ui]\nfamily = \"F\"\nmedium = \"fonts/ibm-plex-sans-medium.ttf\"\nsemibold = \"fonts/ibm-plex-sans-semibold.ttf\"\n";
        var cases = new java.util.LinkedHashMap<String, String>();
        cases.put(head + "[branding]\nproduct = \"JasperOps\"\n", "[branding]: unknown table");
        cases.put(head + "colour = 1\n", "colour: unknown key");
        cases.put(head + "[chrome]\ntitle = \"x\"\n", "[chrome].title: unknown key");
        cases.put(head + "chrome = 1\n", "chrome: expected a table");
        cases.put(head + "[metrics]\nrow_height = \"22\"\n", "[metrics].row_height: expected an integer");
        cases.put(head + "[chrome]\ntitle_bar = \"glass\"\n", "[chrome].title_bar: \"glass\" is not one of unified, native");
        cases.put(head + "[controls]\ntoggle = 1\n", "[controls].toggle: expected a string");
        cases.put("kit = 2\nbase = \"default\"\n", "kit: 2 is not a format this Jasper reads; expected 1");
        cases.put("base = \"default\"\n", "kit: missing; a kit file begins with kit = 1");
        cases.put("kit = \"1\"\nbase = \"default\"\n", "kit: expected an integer");
        cases.put("kit = 1\n", "base: missing; use \"default\" or \"classic\"");
        cases.put("kit = 1\nbase = \"modern\"\n", "base: \"modern\" is not one of default, classic");
        cases.put(head + "[metrics]\nrow_height = 40\n", "[metrics].row_height: 40 is outside 18-28");
        cases.put(head + "[metrics]\ncontrol_height = 19\n", "[metrics].control_height: 19 is outside 20-28");
        cases.put(head + "[type]\nbase = 9\n", "[type].base: 9 is not a body size from 10 to 16");
        cases.put(head + "[type]\nbase = 12.5\n", "[type].base: expected an integer");
        cases.put(head + "[chrome]\nmenu_bar = \"window\"\n", "[chrome].menu_bar: \"window\" needs [chrome].title_bar = \"native\"");
        cases.put(head + "[defaults]\n\"ui.font.size\" = 12\n", "[defaults].\"ui.font.size\": not a setting a look may default; use "
            + "\"ui.theme.light\", \"ui.theme.dark\", \"palette.visible_rows\" or \"window.toolbar_height\"");
        cases.put(head + "[defaults]\n\"palette.visible_rows\" = 30\n", "[defaults].\"palette.visible_rows\": Use an integer from 1–20.");
        cases.put(head + "[defaults]\n\"window.toolbar_height\" = 70\n", "[defaults].\"window.toolbar_height\": Use an integer from 28–64.");
        cases.put(head + "[defaults]\n\"ui.theme.dark\" = \"nope\"\n",
            "[defaults].\"ui.theme.dark\": \"nope\" is neither a built-in theme nor one of this plugin's themes");
        cases.put(head + "[defaults]\n\"ui.theme.dark\" = \"catppuccin-latte\"\n", "[defaults].\"ui.theme.dark\": \"catppuccin-latte\" has no Dark variant");
        cases.put(head + ui + "regular = \"../ibm-plex-sans-regular.ttf\"\n",
            "[type.ui].regular: ../ibm-plex-sans-regular.ttf must be a lowercase path inside the kit's folder, without . or .. segments");
        cases.put(head + ui + "regular = \"fonts/missing.ttf\"\n", "[type.ui].regular: fonts/missing.ttf is not in the plugin's jars");
        cases.put(head + ui + "regular = \"fonts/face.woff2\"\n", "[type.ui].regular: fonts/face.woff2 is not a .ttf or .otf file");
        cases.put(head + ui, "[type.ui].regular: missing");
        cases.put(head + "[type.data]\nfamily = \" \"\nregular = \"fonts/ibm-plex-mono-regular.ttf\"\nmedium = \"fonts/ibm-plex-mono-medium.ttf\"\n",
            "[type.data].family: expected a family name of 1 to 64 characters");
        cases.put(head + "[chrome]\nwindow_title = \"{host} — {directory}\"\n",
            "[chrome].window_title: unknown token {host}; use {context}, {session} and {directory}");
        cases.put(head + "[chrome]\nwindow_title = \"Jasper {session\"\n",
            "[chrome].window_title: a brace that is not part of {context}, {session} or {directory}");
        cases.put(head + "[colors]\ndialog_surface = \"surface.card\"\n", "[colors].dialog_surface: \"surface.card\" is not one of surface.dialog, surface.app");
        cases.put(head + "[chrome]\nwindow_title = \"#1E1F22\"\n", "[chrome].window_title: \"#1E1F22\" looks like a colour; colours come from the theme");
        cases.put(head + "# the accent is #2F65CA\n", "kit file: line 3: \"#2F65CA\" looks like a colour; colours come from the theme");
        cases.put(head + "[chrome\n", null);
        for (var item : cases.entrySet()) {
            String message = problem(item.getKey());
            if (item.getValue() == null) assertThat(message).as(item.getKey()).startsWith("kit file: not valid TOML");
            else assertThat(message).as(item.getKey()).isEqualTo(item.getValue());
        }
    }

    @Test void filesOverTheirLimitsAreRefused() throws Exception {
        byte[] large = ("kit = 1\nbase = \"default\"\n#" + "x".repeat(KitParser.MAX_KIT_BYTES)).getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> KitParser.parse(large, context(fonts(), Map.of()))).hasMessage("kit file: larger than 64 KiB");
        var files = fonts();
        files.put("presentation/fonts/ibm-plex-sans-regular.ttf", new byte[KitParser.MAX_FONT_BYTES + 1]);
        assertThatThrownBy(() -> KitParser.parse(FULL.getBytes(StandardCharsets.UTF_8), context(files, Map.of("look", Optional.of(look())))))
            .hasMessage("[type.ui].regular: fonts/ibm-plex-sans-regular.ttf is larger than 2 MiB");
        assertThatThrownBy(() -> KitParser.parse(new byte[] {(byte) 0xC3, 0x28}, context(fonts(), Map.of()))).hasMessage("kit file: not UTF-8 text");
    }

    /** Rulings 13: tomlj accepts very deep dotted tables; the parser reports one problem and never recurses through them. */
    @Test void deepNestingIsOneProblem() {
        String head = "kit = 1\nbase = \"default\"\n";
        assertThat(problem(head + "[" + "a.".repeat(20_000) + "b]\n")).isEqualTo("[a]: unknown table");
        assertThat(problem(head + "[chrome." + "a.".repeat(20_000) + "b]\n")).isEqualTo("[chrome].a: unknown key");
        assertThat(problem(head + "a = " + "[".repeat(30_000))).startsWith("kit file: not valid TOML");
    }

    @Test void fuzzedInputsOnlyEverProduceProblems() {
        var inputs = new ArrayList<byte[]>();
        byte[] full = FULL.getBytes(StandardCharsets.UTF_8);
        for (int end = 0; end < full.length; end += 7) inputs.add(java.util.Arrays.copyOf(full, end));
        var random = new Random(5);
        for (int i = 0; i < 200; i++) { byte[] noise = new byte[random.nextInt(2048)]; random.nextBytes(noise); inputs.add(noise); }
        inputs.add(("kit = 1\nbase = \"default\"\na = " + "[".repeat(30_000)).getBytes(StandardCharsets.UTF_8));
        inputs.add(("kit = 1\nbase = \"default\"\n[" + "a.".repeat(20_000) + "b]\n").getBytes(StandardCharsets.UTF_8));
        inputs.add(FULL.replace("fonts/ibm-plex-sans-regular.ttf", "../../plugin.toml").getBytes(StandardCharsets.UTF_8));
        inputs.add(FULL.replace("fonts/ibm-plex-sans-regular.ttf", "/etc/passwd").getBytes(StandardCharsets.UTF_8));
        inputs.add(new byte[KitParser.MAX_KIT_BYTES * 2]);
        for (byte[] input : inputs) {
            try { KitParser.parse(input, context(fonts(), Map.of("look", Optional.of(look())))); }
            catch (KitParser.Invalid expected) { assertThat(expected.getMessage()).isNotBlank(); }
        }
    }
}
