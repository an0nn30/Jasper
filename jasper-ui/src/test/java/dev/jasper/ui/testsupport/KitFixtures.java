package dev.jasper.ui.testsupport;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.themeio.ThemeToml;

/** Deterministic module-only appearances; form/panel/render fixtures arrive with their renderer. */
public final class KitFixtures {
    private KitFixtures() {}

    public static GeneratedTheme dark() {
        return generate("Dark", "#18202b", "#e6edf5", "#79afff");
    }

    public static GeneratedTheme light() {
        return generate("Light", "#f5f6f8", "#202734", "#245daa");
    }

    public static GeneratedTheme custom() {
        return generate("Custom", "#261b32", "#f0e8f7", "#b99cfa");
    }

    private static GeneratedTheme generate(String name, String bg, String fg, String accent) {
        return ThemeGenerator.generate(
                ThemeResolver.resolve(
                        ThemeToml.parse(
                                "name = \""
                                        + name
                                        + "\"\n[colors]\nbackground = \""
                                        + bg
                                        + "\"\nforeground = \""
                                        + fg
                                        + "\"\naccent = \""
                                        + accent
                                        + "\"\n"
                                        + """
                                        [colors.normal]
                                        black = "#313b49"
                                        red = "#d75461"
                                        green = "#4b9a6c"
                                        yellow = "#c89d42"
                                        blue = "#4b82cf"
                                        magenta = "#9e6bc4"
                                        cyan = "#448fa4"
                                        white = "#c7ced7"
                                        """,
                                "fixture.toml")));
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
