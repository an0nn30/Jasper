package dev.jasper.ui.swing;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.IntelliJTheme;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.themeimport.FlatLafThemeData;
import dev.jasper.ui.metrics.DesignMetrics;
import java.awt.EventQueue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

/** Real FlatLaf installation. Authored colors are retained; geometry is owned by the kit. EDT only. */
public final class FlatLafInstaller {
    private FlatLafInstaller() {}
    /** Installed immutable snapshot; stored with the LAF so appearance rollback restores it atomically. */
    public static GeneratedTheme installedTheme() {
        Object value = UIManager.getLookAndFeelDefaults().get("Jasper.installedTheme");
        if (value instanceof GeneratedTheme theme) return theme;
        throw new IllegalStateException("No Jasper theme installed");
    }
    private static final class SnapshotLaf extends IntelliJTheme.ThemeLaf {
        private final GeneratedTheme snapshot;
        SnapshotLaf(IntelliJTheme source, GeneratedTheme snapshot) { super(source); this.snapshot = snapshot; }
        @Override public javax.swing.UIDefaults getDefaults() {
            var defaults = super.getDefaults();
            publish(defaults, snapshot);
            return defaults;
        }
    }
    public static void install(GeneratedTheme theme) {
        if (!EventQueue.isDispatchThread()) throw new IllegalStateException("Appearance requires EDT");
        String json = FlatLafThemeData.json(theme.name(), theme.dark(), theme.author(), theme.uiKeys(), theme.icons());
        FlatLaf laf;
        try { laf = new SnapshotLaf(new IntelliJTheme(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))), theme); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        var fixed = new LinkedHashMap<String, String>();
        var metrics = DesignMetrics.DEFAULT;
        fixed.put("TextComponent.arc", Integer.toString(metrics.value(DesignMetrics.Role.CONTROL_RADIUS) * 2));
        fixed.put("Button.arc", Integer.toString(metrics.value(DesignMetrics.Role.CONTROL_RADIUS) * 2));
        fixed.put("Component.focusWidth", Integer.toString(metrics.value(DesignMetrics.Role.FOCUS_RING)));
        fixed.put("Button.minimumHeight", Integer.toString(metrics.value(DesignMetrics.Role.BUTTON_HEIGHT)));
        fixed.put("Component.minimumHeight", Integer.toString(metrics.value(DesignMetrics.Role.FIELD_HEIGHT)));
        laf.setExtraDefaults(fixed);
        try { UIManager.setLookAndFeel(laf); }
        catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new IllegalStateException("Cannot install generated appearance", failure); }
    }

    // A LAF reinstall (including rollback) obtains fresh defaults; publish the same immutable snapshot every time.
    private static void publish(javax.swing.UIDefaults defaults, GeneratedTheme theme) {
        defaults.put("Jasper.installedTheme", theme);
        FlatLafThemeData.explicitColors(theme.uiKeys()).forEach((key, color) -> defaults.put(key, new ColorUIResource(color)));
        for (var role : SemanticColor.values()) defaults.put("Jasper.semantic." + role.key(), new ColorUIResource(theme.semantic().color(role)));
        var palette = theme.semantic();
        // FlatLaf primitives also inherit the canonical roles; kit paints use the palette directly.
        Map<String, SemanticColor> roles = Map.ofEntries(
            Map.entry("Panel.background", SemanticColor.SURFACE_PANEL), Map.entry("Label.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("TextField.background", SemanticColor.SURFACE_INPUT), Map.entry("TextField.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("PasswordField.background", SemanticColor.SURFACE_INPUT), Map.entry("PasswordField.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("Component.borderColor", SemanticColor.BORDER_INPUT), Map.entry("Component.focusColor", SemanticColor.BORDER_FOCUS),
            Map.entry("Button.background", SemanticColor.BUTTON_SECONDARY_BG), Map.entry("Button.foreground", SemanticColor.TEXT_STRONG_SECONDARY),
            Map.entry("List.selectionBackground", SemanticColor.SELECTION_BG), Map.entry("List.selectionForeground", SemanticColor.TEXT_ONACCENT));
        roles.forEach((key, role) -> defaults.put(key, new ColorUIResource(palette.color(role))));
    }
}
