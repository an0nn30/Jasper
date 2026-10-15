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

    /** The platform UI family the default kit draws in when the user names none (resolved once per process). */
    public static String platformFamily() {
        return dev.jasper.ui.metrics.Typography.of(dev.jasper.ui.metrics.TypeScale.DEFAULT, dev.jasper.ui.metrics.FaceSet.PLATFORM,
            dev.jasper.ui.metrics.Typography.UserFont.NONE).family();
    }

    /** The default kit with {@code metrics}: a kit that differs from the default in geometry alone. */
    public static Kit kit(DesignMetrics metrics) {
        return new Kit("test", metrics, Kit.DEFAULT.type(), Kit.DEFAULT.faces(), Kit.DEFAULT.uiKeys());
    }

    /** A third appearance: Slate Cobalt Dark with every role 20% plum (alpha kept), so a hard-coded variant colour shows. */
    public static GeneratedTheme custom() {
        var plum = new java.awt.Color(0x7a3fa0);
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            var base = dark().semantic().color(role);
            var mixed = ColorMath.mix(base, plum, .8);
            values.put(role, new java.awt.Color(mixed.getRed(), mixed.getGreen(), mixed.getBlue(), base.getAlpha()));
        }
        return withSemantic(dark(), new SemanticPalette(values));
    }

    /** {@code base} with its semantic palette replaced. */
    public static GeneratedTheme withSemantic(GeneratedTheme base, SemanticPalette palette) {
        return new GeneratedTheme(base.name(), base.dark(), base.author(), palette, base.terminal(), base.editor(), base.syntax(),
            base.glass());
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
            base.glass());
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
                        dev.jasper.ui.model.Nodes.LayoutRole.HERO,
                        java.util.List.of(
                                new dev.jasper.ui.model.Nodes.Icon(
                                        "unlock-icon",
                                        ready,
                                        "lock",
                                        dev.jasper.ui.model.Nodes.Hue.BLUE,
                                        dev.jasper.ui.model.Nodes.IconSize.HERO,
                                        "Vault"),
                                new dev.jasper.ui.model.Nodes.Text(
                                        "unlock-title",
                                        ready,
                                        "Unlock Vault",
                                        dev.jasper.ui.model.Nodes.TextRole.HERO_TITLE),
                                new dev.jasper.ui.model.Nodes.Text(
                                        "unlock-description",
                                        ready,
                                        "Enter your password to unlock the vault.",
                                        dev.jasper.ui.model.Nodes.TextRole.DESCRIPTION)));
        return new dev.jasper.ui.model.ViewSpec(
                new dev.jasper.ui.model.Nodes.Form(
                        "unlock",
                        ready,
                        dev.jasper.ui.model.Nodes.FormKind.HERO,
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
    public static GeneratedTheme marked() {
        var base = dark();
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, marker(role));
        return withSemantic(base, new SemanticPalette(values));
    }

    /** {@code role}'s marker colour in {@link #marked()}: opaque, its red channel unique to the role. */
    public static java.awt.Color marker(SemanticColor role) {
        return new java.awt.Color(16 + 3 * role.ordinal(), 64 + role.ordinal(), 200 - role.ordinal());
    }

    /** A context over {@code theme} for both routes; caller owns and closes the binding and icon registry; EDT. */
    public static KitContext context(GeneratedTheme theme) {
        return new KitContext(new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }),
            ThemeRoute.WORKSPACE, DesignMetrics.DEFAULT, new IconRegistry(), () -> false);
    }

    /** Caller owns and closes the binding and icon registry; creation requires EDT. */
    public static KitContext context() {
        return new KitContext(
                new ThemeBinding(
                        new ThemePair(dark(), dark()),
                        failure -> {
                            throw new AssertionError(failure);
                        }),
                ThemeRoute.WORKSPACE,
                DesignMetrics.DEFAULT,
                new IconRegistry(),
                () -> false);
    }
}
