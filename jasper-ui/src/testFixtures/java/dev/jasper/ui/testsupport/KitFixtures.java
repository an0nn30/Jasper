package dev.jasper.ui.testsupport;

import dev.jasper.ui.theme.JasperTheme;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.theme.*;

/** Deterministic module-only appearances; form/panel/render fixtures arrive with their renderer. */
public final class KitFixtures {
    private KitFixtures() {}

    public static GeneratedTheme dark() { return JasperTheme.bundled().generated(true); }

    public static GeneratedTheme light() { return JasperTheme.bundled().generated(false); }

    /**
     * Spec 7.6's Enterprise Light as a fixture theme (plan 4c Rulings 20): Slate Cobalt Light with the table's named roles
     * and its eight derived overrides; the ruled roles (focus, status, identity and the hue surfaces) follow the named
     * roles as {@code JasperTheme} derives them, and any other role the table does not name keeps Slate Cobalt Light's
     * value. The shipped theme, with its derived neutrals, is plan 5's.
     */
    public static GeneratedTheme enterpriseLight() {
        var base = light();
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, base.semantic().color(role));
        java.util.function.BiConsumer<Integer, SemanticColor[]> name = (rgb, roles) -> { for (var role : roles) values.put(role, new java.awt.Color(rgb)); };
        name.accept(0xECEDEF, new SemanticColor[] {SemanticColor.SURFACE_APP, SemanticColor.SURFACE_DIALOG});
        name.accept(0xE1E3E6, new SemanticColor[] {SemanticColor.SURFACE_TITLEBAR});
        name.accept(0xF7F7F8, new SemanticColor[] {SemanticColor.SURFACE_PANEL, SemanticColor.SURFACE_SIDEBAR});
        name.accept(0xFFFFFF, new SemanticColor[] {SemanticColor.SURFACE_CARD, SemanticColor.SURFACE_INPUT, SemanticColor.SURFACE_TERMINAL,
            SemanticColor.TEXT_ONACCENT, SemanticColor.TEXT_ONSELECTION});
        name.accept(0x2F65CA, new SemanticColor[] {SemanticColor.ACCENT, SemanticColor.SELECTION_BG, SemanticColor.HUE_BLUE});
        name.accept(0xDCE5F7, new SemanticColor[] {SemanticColor.TEXT_ONACCENT_SECONDARY, SemanticColor.TEXT_ONSELECTION_SECONDARY});
        name.accept(0x8A5A00, new SemanticColor[] {SemanticColor.STATUS_WARNING});
        name.accept(0xB5651D, new SemanticColor[] {SemanticColor.HUE_AMBER});
        name.accept(0xC4C7CC, new SemanticColor[] {SemanticColor.BORDER_WINDOW});
        name.accept(0xB4B8BF, new SemanticColor[] {SemanticColor.BORDER_CONTROL, SemanticColor.BORDER_INPUT, SemanticColor.BUTTON_SECONDARY_BORDER});
        name.accept(0x1E1F22, new SemanticColor[] {SemanticColor.TEXT_PRIMARY});
        name.accept(0x3A3D42, new SemanticColor[] {SemanticColor.TEXT_STRONG_SECONDARY});
        name.accept(0x5C6370, new SemanticColor[] {SemanticColor.TEXT_SECONDARY, SemanticColor.TEXT_TERTIARY});
        name.accept(0x7A808A, new SemanticColor[] {SemanticColor.TEXT_PLACEHOLDER});
        name.accept(0x2E9E4F, new SemanticColor[] {SemanticColor.STATUS_SUCCESS, SemanticColor.HUE_GREEN});
        name.accept(0xD03A3A, new SemanticColor[] {SemanticColor.STATUS_DANGER});
        name.accept(0x8A4FBF, new SemanticColor[] {SemanticColor.HUE_PURPLE});
        // The roles a theme file never names follow their rules, as JasperTheme derives them when it loads the file.
        values.put(SemanticColor.BORDER_FOCUS, values.get(SemanticColor.ACCENT));
        values.put(SemanticColor.SURFACE_STATUS, values.get(SemanticColor.SURFACE_APP));
        values.put(SemanticColor.BORDER_IDENTITY, values.get(SemanticColor.BORDER_CONTROL));
        values.put(SemanticColor.HUE_BLUE_SURFACE, values.get(SemanticColor.HUE_BLUE));
        values.put(SemanticColor.HUE_GREEN_SURFACE, values.get(SemanticColor.HUE_GREEN));
        values.put(SemanticColor.HUE_AMBER_SURFACE, values.get(SemanticColor.HUE_AMBER));
        values.put(SemanticColor.HUE_PURPLE_SURFACE, values.get(SemanticColor.HUE_PURPLE));
        values.put(SemanticColor.HUE_NEUTRAL_SURFACE, values.get(SemanticColor.HUE_NEUTRAL));
        var overrides = new java.util.EnumMap<DerivedColor, java.awt.Color>(DerivedColor.class);
        overrides.put(DerivedColor.SURFACE_HEADER, new java.awt.Color(0xE1E3E6));
        overrides.put(DerivedColor.SURFACE_HEADER_ACTIVE, new java.awt.Color(0xD9DFEA));
        overrides.put(DerivedColor.SURFACE_STRIPE, new java.awt.Color(0xE4E6E9));
        overrides.put(DerivedColor.SURFACE_TABLE_HEADER, new java.awt.Color(0xF0F1F3));
        overrides.put(DerivedColor.SURFACE_ZEBRA, new java.awt.Color(0xF7F8FA));
        overrides.put(DerivedColor.SURFACE_ROW_HOVER, new java.awt.Color(0xEEF2FA));
        overrides.put(DerivedColor.WARNING_GROUND, new java.awt.Color(0xFBF1DA));
        overrides.put(DerivedColor.WARNING_BORDER, new java.awt.Color(0xE6CF95));
        return new GeneratedTheme("Enterprise", false, base.author(), new SemanticPalette(values), base.terminal(), base.editor(), base.syntax(),
            base.glass(), overrides);
    }

    /** The platform UI family the default kit draws in when the user names none (resolved once per process). */
    public static String platformFamily() {
        return dev.jasper.ui.metrics.Typography.of(dev.jasper.ui.metrics.TypeScale.DEFAULT, dev.jasper.ui.metrics.FaceSet.PLATFORM,
            dev.jasper.ui.metrics.Typography.UserFont.NONE).family();
    }

    /** The default kit with {@code metrics}: a kit that differs from the default in geometry alone. */
    public static Kit kit(DesignMetrics metrics) {
        return new Kit("test", metrics, Kit.DEFAULT.type(), Kit.DEFAULT.faces(), Kit.DEFAULT.controls(), Kit.DEFAULT.colors(),
            Kit.DEFAULT.uiKeys());
    }

    /** The shipped Enterprise kit file's {@code [metrics]} (spec 5.1), over the classic base's own tokens. */
    public static dev.jasper.ui.metrics.MetricTokens enterpriseTokens() {
        var tokens = dev.jasper.ui.metrics.MetricTokens.of(DesignMetrics.CLASSIC);
        int[] values = {22, 22, 3, 2, 3, 2, 24, 12, 20, 230, 7};
        for (var token : dev.jasper.ui.metrics.MetricTokens.Token.values()) tokens = tokens.with(token, values[token.ordinal()]);
        return tokens;
    }

    /**
     * Spec 16.1.3's Enterprise fixture kit: the classic base with the shipped file's tokens, its 12-point body, its control
     * variants, colour slots and key set, and the platform faces (the Plex faces arrive with plan 5).
     */
    public static Kit enterprise() {
        return new Kit("enterprise", DesignMetrics.CLASSIC.with(enterpriseTokens()), dev.jasper.ui.metrics.TypeScale.CLASSIC.withBody(12),
            dev.jasper.ui.metrics.FaceSet.PLATFORM, dev.jasper.ui.kit.ControlVariants.CLASSIC,
            new dev.jasper.ui.kit.ColorSlots(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_CARD), UiKeySet.CLASSIC);
    }

    /** Spec 16.1.3's complement kit: every control point at the value Enterprise does not use, with Enterprise's tokens and type. */
    public static Kit complement() {
        var enterprise = enterprise();
        return new Kit("complement", enterprise.metrics(), enterprise.type(), enterprise.faces(), enterprise.controls().complement(),
            enterprise.colors(), enterprise.uiKeys());
    }

    /** {@code base} with {@code controls}: a covering-array row (spec 16.1.3). */
    public static Kit withControls(Kit base, dev.jasper.ui.kit.ControlVariants controls) {
        return new Kit(base.id() + "+controls", base.metrics(), base.type(), base.faces(), controls, base.colors(), base.uiKeys());
    }

    /** A third appearance: Slate Cobalt Dark with every role 20% plum (alpha kept), so a hard-coded variant colour shows. */
    public static GeneratedTheme custom() { return custom(dark()); }

    /** {@code base} with every role and every authored derived override 20% plum (alpha kept). */
    public static GeneratedTheme custom(GeneratedTheme base) {
        var plum = new java.awt.Color(0x7a3fa0);
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            var own = base.semantic().color(role);
            var mixed = ColorMath.mix(own, plum, .8);
            values.put(role, new java.awt.Color(mixed.getRed(), mixed.getGreen(), mixed.getBlue(), own.getAlpha()));
        }
        var overrides = new java.util.EnumMap<DerivedColor, java.awt.Color>(DerivedColor.class);
        base.derivedOverrides().forEach((role, colour) -> overrides.put(role, ColorMath.mix(colour, plum, .8)));
        return new GeneratedTheme(base.name(), base.dark(), base.author(), new SemanticPalette(values), base.terminal(), base.editor(),
            base.syntax(), base.glass(), overrides);
    }

    /**
     * The theme set every look is checked over (spec 16.1.2): Slate Cobalt Dark and Light, the four Catppuccin flavours,
     * the converter goldens' UI variants (ocean, ocean-dawn, stray) and Enterprise Dark and Light (the Enterprise plugin's
     * theme file), in that order.
     */
    public static java.util.List<GeneratedTheme> themes() {
        var themes = new java.util.ArrayList<GeneratedTheme>();
        for (var theme : JasperTheme.builtIns())
            for (boolean dark : new boolean[] {true, false}) if (theme.has(dark)) themes.add(theme.generated(dark));
        var expected = java.nio.file.Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize()
            .resolve("tools/themes/testdata/expected");
        for (String id : java.util.List.of("ocean", "ocean-dawn", "stray")) {
            try {
                var theme = JasperTheme.parse(java.nio.file.Files.readString(expected.resolve(id + ".json")), id);
                for (boolean dark : new boolean[] {true, false}) if (theme.has(dark)) themes.add(theme.generated(dark));
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
        var enterprise = expected.getParent().getParent().getParent().getParent().resolve("plugins/enterprise/src/main/resources/themes/enterprise.json");
        try {
            var theme = JasperTheme.parse(java.nio.file.Files.readString(enterprise), "enterprise");
            for (boolean dark : new boolean[] {true, false}) themes.add(theme.generated(dark));
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        return java.util.List.copyOf(themes);
    }

    /**
     * Eight palettes no theme should have (spec 16.1.2), each Slate Cobalt's with its terminal, editor and syntax kept, and
     * each role's alpha kept: every role one grey; all black; all white; the accent equal to the surfaces; maximum chroma,
     * seeded; each text equal to its surface; each border equal to its surface; Light's colours under the dark flag. A
     * name says which.
     */
    public static java.util.List<GeneratedTheme> degenerate() {
        var grey = new java.awt.Color(0x808080);
        var card = light().semantic().color(SemanticColor.SURFACE_CARD);
        var chroma = java.util.List.of(new java.awt.Color(0xFF0000), new java.awt.Color(0x00FF00), new java.awt.Color(0x0000FF),
            new java.awt.Color(0xFFFF00), new java.awt.Color(0xFF00FF), new java.awt.Color(0x00FFFF));
        var seeded = new java.util.Random(0x5EEDL);
        var maximum = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) maximum.put(role, chroma.get(seeded.nextInt(chroma.size())));
        return java.util.List.of(
            degenerate("one grey", dark(), role -> grey),
            degenerate("all black", dark(), role -> java.awt.Color.BLACK),
            degenerate("all white", dark(), role -> java.awt.Color.WHITE),
            degenerate("accent on the surfaces", light(), role -> role.key().startsWith("surface.") || role == SemanticColor.ACCENT
                || role == SemanticColor.ACCENT_HOVER || role == SemanticColor.SELECTION_BG || role == SemanticColor.BORDER_FOCUS
                || role == SemanticColor.TAB_INDICATOR ? card : null),
            degenerate("maximum chroma", dark(), maximum::get),
            degenerate("text on the surfaces", light(), role -> role.key().startsWith("surface.") || role.key().startsWith("text.")
                || role.key().startsWith("status.") ? card : null),
            degenerate("borders on the surfaces", light(), role -> role.key().startsWith("surface.") || role.key().startsWith("border.")
                || role == SemanticColor.BUTTON_SECONDARY_BG || role == SemanticColor.BUTTON_SECONDARY_BORDER ? card : null),
            withName(withDark(light(), true), "Degenerate: light under the dark flag"));
    }

    /** {@code base} named for a degenerate palette, each role {@code colour}'s value with its own alpha, or kept when null. */
    private static GeneratedTheme degenerate(String name, GeneratedTheme base,
                                             java.util.function.Function<SemanticColor, java.awt.Color> colour) {
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            var own = base.semantic().color(role);
            var replaced = colour.apply(role);
            values.put(role, replaced == null ? own
                : new java.awt.Color(replaced.getRed(), replaced.getGreen(), replaced.getBlue(), own.getAlpha()));
        }
        return withName(withSemantic(base, new SemanticPalette(values)), "Degenerate: " + name);
    }

    /** {@code base} named {@code name}. */
    private static GeneratedTheme withName(GeneratedTheme base, String name) {
        return new GeneratedTheme(name, base.dark(), base.author(), base.semantic(), base.terminal(), base.editor(), base.syntax(),
            base.glass(), base.derivedOverrides());
    }

    /** {@code base} with its semantic palette replaced. */
    public static GeneratedTheme withSemantic(GeneratedTheme base, SemanticPalette palette) {
        return new GeneratedTheme(base.name(), base.dark(), base.author(), palette, base.terminal(), base.editor(), base.syntax(),
            base.glass(), base.derivedOverrides());
    }

    /** {@code top} painted over opaque {@code bottom} as Java2D composites it: what an alpha-carrying role shows on a surface. */
    public static int over(java.awt.Color top, java.awt.Color bottom) {
        var image = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(bottom); g.fillRect(0, 0, 1, 1); g.setColor(top); g.fillRect(0, 0, 1, 1); } finally { g.dispose(); }
        return image.getRGB(0, 0);
    }

    /** {@code base} reporting {@code dark}. */
    public static GeneratedTheme withDark(GeneratedTheme base, boolean dark) {
        return new GeneratedTheme(base.name(), dark, base.author(), base.semantic(), base.terminal(), base.editor(), base.syntax(),
            base.glass(), base.derivedOverrides());
    }

    /**
     * Canonical real unlock form: centered hero, mutable password and equal-width footer actions.
     */
    public static dev.jasper.ui.model.ViewSpec form(String name) {
        if (!"unlock".equals(name))
            throw new IllegalArgumentException("Unknown form fixture: " + name);
        var ready = dev.jasper.ui.model.ControlState.READY;
        var hero =
                new dev.jasper.ui.model.Nodes.Stack(
                        "unlock-hero",
                        ready,
                        dev.jasper.ui.model.Nodes.LayoutRole.PROMPT_HEADER,
                        java.util.List.of(
                                new dev.jasper.ui.model.Nodes.Icon(
                                        "unlock-icon",
                                        ready,
                                        "lock",
                                        dev.jasper.ui.model.Nodes.Hue.BLUE,
                                        dev.jasper.ui.model.Nodes.IconSize.PROMPT,
                                        "Vault"),
                                new dev.jasper.ui.model.Nodes.Text(
                                        "unlock-title",
                                        ready,
                                        "Unlock Vault",
                                        dev.jasper.ui.model.Nodes.TextRole.PROMPT_TITLE),
                                new dev.jasper.ui.model.Nodes.Text(
                                        "unlock-description",
                                        ready,
                                        "Enter your password to unlock the vault.",
                                        dev.jasper.ui.model.Nodes.TextRole.DESCRIPTION)));
        return new dev.jasper.ui.model.ViewSpec(
                new dev.jasper.ui.model.Nodes.Form(
                        "unlock",
                        ready,
                        dev.jasper.ui.model.Nodes.FormKind.PROMPT,
                        "Unlock Vault",
                        java.util.Optional.of(hero),
                        java.util.List.of(
                                new dev.jasper.ui.model.Nodes.Secret(
                                        "password", ready, "Password", "Password", "")),
                        java.util.Optional.empty(),
                        java.util.List.of(
                                new dev.jasper.ui.model.Nodes.Button(
                                        "cancel-action",
                                        ready,
                                        "Cancel",
                                        "",
                                        dev.jasper.ui.model.Nodes.ButtonKind.SECONDARY,
                                        "cancel",
                                        ""),
                                new dev.jasper.ui.model.Nodes.Button(
                                        "unlock-action",
                                        ready,
                                        "Unlock",
                                        "",
                                        dev.jasper.ui.model.Nodes.ButtonKind.PRIMARY,
                                        "unlock",
                                        "")),
                        "cancel"));
    }

    /** One of the four canonical module-only panel compositions. */
    public static dev.jasper.ui.model.ViewSpec panel(String name) {
        return dev.jasper.ui.gallery.PanelFixtures.create(name);
    }

    /**
     * {@code spec} rendered at 1x in {@code context} and written to {@code build/classic-faces/<name>.png} (relative to the
     * test's working directory) for inspection; the view is closed, the context stays the caller's. EDT.
     */
    public static java.awt.image.BufferedImage capture(KitContext context, dev.jasper.ui.model.ViewSpec spec, int width, int height, String name) {
        try (var view = new dev.jasper.ui.swing.KitView(context, spec, event -> {})) {
            var image = render(view, width, height, 1);
            var out = java.nio.file.Path.of("build", "classic-faces", name + ".png");
            java.nio.file.Files.createDirectories(out.getParent());
            javax.imageio.ImageIO.write(image, "png", out.toFile());
            return image;
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** Headless paint with layout settling; the view and context remain owned by the caller. */
    public static java.awt.image.BufferedImage render(dev.jasper.ui.swing.KitView view, int width, int height, double deviceScale) {
        if (width <= 0 || height <= 0 || !Double.isFinite(deviceScale) || deviceScale <= 0) throw new IllegalArgumentException("Invalid render size/scale");
        view.setSize(width, height);
        for (int i = 0; i < 3; i++) layout(view);
        var image = new java.awt.image.BufferedImage((int)Math.ceil(width*deviceScale), (int)Math.ceil(height*deviceScale), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.scale(deviceScale, deviceScale); view.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }
    private static void layout(java.awt.Container container) {
        container.doLayout();
        for (var child : container.getComponents()) if (child instanceof java.awt.Container nested) layout(nested);
    }

    /** The dark fixture with every semantic role in its own colour, so a painted pixel names the role it came from. */
    public static GeneratedTheme marked() { return marked(dark()); }

    /** {@code base} with every semantic role in its marker colour and no authored overrides, so every derived role follows the markers. */
    public static GeneratedTheme marked(GeneratedTheme base) {
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, marker(role));
        return new GeneratedTheme(base.name(), base.dark(), base.author(), new SemanticPalette(values), base.terminal(), base.editor(),
            base.syntax(), base.glass(), java.util.Map.of());
    }

    /** {@code role}'s marker colour in {@link #marked()}: opaque, its red channel unique to the role. */
    public static java.awt.Color marker(SemanticColor role) {
        return new java.awt.Color(16 + 3 * role.ordinal(), 64 + role.ordinal(), 200 - role.ordinal());
    }

    /** A context over {@code theme} and {@code kit} for both routes; caller owns and closes the binding and icon registry; EDT. */
    public static KitContext context(GeneratedTheme theme, Kit kit) {
        return KitContext.of(kit, new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }),
            ThemeRoute.WORKSPACE, new IconRegistry(), () -> false, action -> "");
    }

    /** A context over {@code theme} for both routes; caller owns and closes the binding and icon registry; EDT. */
    public static KitContext context(GeneratedTheme theme) { return context(theme, Kit.DEFAULT); }

    /** Caller owns and closes the binding and icon registry; creation requires EDT. */
    public static KitContext context() { return context(dark()); }
}
