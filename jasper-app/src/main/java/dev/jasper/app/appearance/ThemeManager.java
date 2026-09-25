package dev.jasper.app.appearance;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.IntelliJTheme;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

/**
 * The built-in themes and the one path that installs any theme: resolve its IntelliJ theme file, let
 * FlatLaf build the look and feel, put back the colours FlatLaf skips (IntelliJ-only namespaces such as
 * {@code SearchEverywhere.*}), then derive Jasper's chrome keys. EDT only.
 */
public final class ThemeManager {
    private static final String BASE = "dev/jasper/app/themes/";
    /**
     * {@code ThemeController}'s legacy custom-defaults-source package (registered once, globally, by
     * {@code FlatLaf.registerCustomDefaultsSource}). FlatLaf merges any registered source into every
     * FlatLaf-based look and feel it builds, {@code IntelliJTheme}-derived ones included, so left alone it
     * would overwrite this class's own IntelliJ colours with the pre-engine {@code Jasper.*} properties.
     * Retired with {@code ThemeController} in a later task; until then this brackets every install so it
     * never leaks in, while leaving it registered for {@code ThemeController}'s own installs.
     */
    private static final String LEGACY_DEFAULTS_SOURCE = "dev.jasper.app.themes";
    /** Each theme {@code name} a {@code parentTheme} may name, with its file under {@link #BASE}. */
    private static final Map<String, String> FILES = Map.of(
        "IntelliJ Light", "intellij/Light.theme.json",
        "IntelliJ", "intellij/intellijlaf.theme.json",
        "Darcula", "intellij/darcula.theme.json",
        "Jasper Dark", "jasper-dark.theme.json");
    /** Theme-independent application defaults: form typography and the split divider. Every theme gets them. */
    public static final Map<String, String> APP_DEFAULTS = appDefaults();
    private static final ThemeLoader LOADER = new ThemeLoader(ThemeManager::read, FILES);
    private static final Map<Theme, ThemeLoader.Resolved> RESOLVED = new HashMap<>();

    private ThemeManager() {}

    /** The bundled themes, Light first. */
    public static List<Theme> builtIns() { return List.of(Theme.LIGHT, Theme.DARK); }

    static ThemeLoader.Resolved resolve(Theme theme) { return RESOLVED.computeIfAbsent(theme, key -> LOADER.load(key.file())); }

    /** Installs {@code theme} as the look and feel; false when FlatLaf could not set it up. */
    public static boolean install(Theme theme) {
        requireEdt();
        return install(resolve(theme), theme.palette().background());
    }

    static boolean install(ThemeLoader.Resolved resolved, Color terminalBackground) {
        requireEdt();
        FlatLaf laf;
        try { laf = IntelliJTheme.createLaf(new ByteArrayInputStream(resolved.json().getBytes(StandardCharsets.UTF_8))); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        laf.setExtraDefaults(APP_DEFAULTS);
        boolean installed;
        FlatLaf.unregisterCustomDefaultsSource(LEGACY_DEFAULTS_SOURCE);
        try { installed = FlatLaf.setup(laf); }
        finally { FlatLaf.registerCustomDefaultsSource(LEGACY_DEFAULTS_SOURCE); }
        if (!installed) return false;
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        var extra = new LinkedHashMap<>(APP_DEFAULTS);
        resolved.colors().forEach((key, color) -> {
            if (defaults.get(key) != null) return;
            defaults.put(key, new ColorUIResource(color));
            extra.put(key, hex(color));
        });
        ChromeKeys.derive(defaults, terminalBackground).forEach((key, color) -> extra.put(key, hex(color)));
        // A later reinstall of this instance (a failed switch rolls back to it) rebuilds the defaults.
        laf.setExtraDefaults(extra);
        return true;
    }

    private static Map<String, String> appDefaults() {
        var defaults = new LinkedHashMap<String, String>();
        // Reference form typography in logical points; FlatLaf applies display scaling.
        for (String component : List.of("Label", "List", "TextField", "PasswordField", "FormattedTextField", "TextArea", "ComboBox", "Button"))
            defaults.put(component + ".font", "12 $defaultFont");
        defaults.put("SplitPane.dividerSize", "8");
        defaults.put("SplitPaneDivider.border", "dev.jasper.app.workspace.SplitDividerBorder");
        return Collections.unmodifiableMap(defaults);
    }

    private static String read(String file) {
        try (InputStream in = ThemeManager.class.getClassLoader().getResourceAsStream(BASE + file)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String hex(Color color) {
        String rgb = String.format("#%06x", color.getRGB() & 0xffffff);
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
