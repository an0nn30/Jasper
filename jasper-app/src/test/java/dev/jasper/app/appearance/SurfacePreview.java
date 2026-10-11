package dev.jasper.app.appearance;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Opt-in real staged-owner comparison output; originals are never modified. */
public final class SurfacePreview {
    private record Capture(BufferedImage image, String metadata) {}

    /** Optional arguments: output directory, surface substring, theme, state, system font size, constrained logical width. */
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length > 0 ? args[0] : "build/theme-surfaces");
        String filter = args.length > 1 ? args[1] : "";
        ThemeController fonts = args.length > 4 ? SurfaceFixtures.edt(() -> {
            var controller = new ThemeController();
            controller.configure(Theme.DARK, new dev.jasper.app.config.UiFontConfig("system",Float.parseFloat(args[4])));
            return controller;
        }) : null;
        try {
        for (String theme : args.length > 2 ? List.of(args[2]) : List.of("calibration", "dark", "light", "custom")) {
            for (String id : List.of("vault-create", "vault-unlock", "host-add", "fileserver-add")) {
                if (!filter.isEmpty() && !id.contains(filter)) continue;
                var states = id.equals("fileserver-add") ? List.of("empty", "focus", "vault", "entered", "busy", "error")
                    : id.equals("host-add") ? List.of("empty", "focus", "entered", "busy", "error")
                    : id.equals("vault-create")
                    ? List.of("empty", "focus", "entered", "revealed", "mismatch", "busy")
                    : List.of("empty", "entered", "revealed", "busy", "error");
                for (String state : states) {
                    if (args.length > 3 && !state.equals(args[3])) continue;
                    try (var opened = SurfaceFixtures.open(id, state, theme)) {
                        if (args.length > 5) opened.width(Integer.parseInt(args[5]));
                        Path directory = Files.createDirectories(output.resolve(theme).resolve(id));
                        // Busy submission and all metadata share one EDT turn, before any async completion can close the view.
                        Capture capture = SurfaceFixtures.edt(() -> capture(opened, id));
                        ImageIO.write(capture.image(), "png", directory.resolve(state + ".png").toFile());
                        Files.writeString(directory.resolve(state + ".txt"), capture.metadata());
                    }
                }
            }
        }
        } finally { if (fonts != null) SurfaceFixtures.edt(() -> { fonts.configure(Theme.DARK, dev.jasper.app.config.UiFontConfig.defaults()); fonts.close(); return null; }); }
    }

    private static Capture capture(SurfaceFixtures.Opened opened, String id) {
        var image = opened.capture(2);
        var metadata = new StringBuilder("provenance=real staged plugin/headless Java2D\ndeviceScale=2\nlogicalUiScale="
            + com.formdev.flatlaf.util.UIScale.getUserScaleFactor() + "\npreferred=" + opened.preferred() + "\n");
        if (id.equals("fileserver-add") || id.equals("host-add")) {
            var anchors=id.equals("fileserver-add") ? List.of("server.general.card","server.name","server.protocol","server.folder","server.port","server.access.card","server.login","server.security.card","server.preview","server.cancel","server.save")
                : List.of("host.connection.card","host.name","host.hostname","host.port","host.authentication.card","host.choose","host.options.card","host.preview","host.cancel","host.save");
            for(String anchor:anchors) {
                try {metadata.append(anchor).append('=').append(opened.bounds(anchor)).append('\n');}
                catch(AssertionError absent) {if(!anchor.endsWith(".preview"))throw absent;}
            }
            return new Capture(image,metadata.toString());
        }
        for (String anchor : List.of("form.hero.tile", "form.heading", "intro", "password", "password.input", "cancel", "submit"))
            metadata.append(anchor).append('=').append(opened.bounds(anchor)).append('\n');
        if (id.equals("vault-create")) {
            for (String anchor : List.of("strength", "confirm", "bind.card", "form.warning"))
                metadata.append(anchor).append('=').append(opened.bounds(anchor)).append('\n');
        }
        var textIds = id.equals("vault-create")
            ? List.of("form.heading", "intro", "bind.row.title", "bind.row.description", "form.warning.text")
            : List.of("form.heading", "intro");
        for (String textId : textIds) metadata.append(textMetrics(opened, textId));
        return new Capture(image, metadata.toString());
    }

    private static String textMetrics(SurfaceFixtures.Opened opened, String id) {
        var component = (javax.swing.JComponent)opened.component(id);
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.scale(2, 2);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS, java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setFont(component.getFont());
            var metrics = graphics.getFontMetrics();
            int baseline = metrics.getAscent();
            if (component instanceof javax.swing.JLabel label && !id.equals("intro") && !id.equals("form.warning.text")) {
                var insets = label.getInsets();
                var icon = new java.awt.Rectangle();
                var text = new java.awt.Rectangle();
                javax.swing.SwingUtilities.layoutCompoundLabel(label, metrics, label.getText(), label.getIcon(),
                    label.getVerticalAlignment(), label.getHorizontalAlignment(), label.getVerticalTextPosition(), label.getHorizontalTextPosition(),
                    new java.awt.Rectangle(insets.left, insets.top, label.getWidth() - insets.left - insets.right,
                        label.getHeight() - insets.top - insets.bottom), icon, text, label.getIconTextGap());
                baseline += text.y;
            }
            return id + ".typography=" + component.getFont().getFontName(java.util.Locale.ROOT)
                + ",size=" + component.getFont().getSize2D() + ",firstPaintBaselineLogical=" + (opened.bounds(id).y + baseline) + "\n";
        } finally { graphics.dispose(); }
    }
}
