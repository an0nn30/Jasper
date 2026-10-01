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
     * Each theme a {@code parentTheme} may name, with its file under {@link #BASE}. IntelliJ's themes are
     * keyed by the id IntelliJ registers them under, which is what a {@code parentTheme} names; the classic
     * IntelliJ Light and Jasper Dark are also reachable by name.
     */
    private static final Map<String, String> FILES = Map.ofEntries(
        Map.entry("IntelliJ Light", "intellij/Light.theme.json"),
        Map.entry("JetBrainsLightTheme", "intellij/Light.theme.json"),
        Map.entry("IntelliJ", "intellij/intellijlaf.theme.json"),
        Map.entry("Darcula", "intellij/darcula.theme.json"),
        Map.entry("ExperimentalDark", "intellij/expUI/expUI_dark.theme.json"),
        Map.entry("ExperimentalLight", "intellij/expUI/expUI_light.theme.json"),
        Map.entry("ExperimentalLightWithLightHeader", "intellij/expUI/expUI_light_with_light_header.theme.json"),
        Map.entry("Islands Dark", "intellij/islands/ManyIslandsDark.theme.json"),
        Map.entry("Islands Light", "intellij/islands/ManyIslandsLight.theme.json"),
        Map.entry("Islands Darcula", "intellij/islands/ManyIslandsDarcula.theme.json"),
        Map.entry("JetBrainsHighContrastTheme", "intellij/islands/HighContrast.theme.json"),
        Map.entry("Jasper Dark", "jasper-dark.theme.json"));
    /** Theme-independent application defaults: form typography, input geometry and the split divider. Every theme gets them. */
    public static final Map<String, String> APP_DEFAULTS = appDefaults();
    private static final ThemeLoader LOADER = new ThemeLoader(ThemeManager::read, FILES);
    private static final Map<Theme, ThemeLoader.Resolved> RESOLVED = new HashMap<>();

    private ThemeManager() {}

    /** The bundled themes, Light first. */
    public static List<Theme> builtIns() { return List.of(Theme.LIGHT, Theme.DARK); }

    static ThemeLoader.Resolved resolve(Theme theme) { return RESOLVED.computeIfAbsent(theme, key -> load(key.source())); }

    /** Resolves a theme's file; touches no Swing state, so it is safe off the EDT. */
    static ThemeLoader.Resolved load(Theme.Source source) {
        return switch (source) {
            case Theme.Resource resource -> LOADER.load(resource.file());
            // Parents a plugin theme names resolve against the chains Jasper ships.
            case Theme.Archive archive ->
                new ThemeLoader(file -> file.equals(archive.entry()) ? archive.json() : read(file), FILES).load(archive.entry());
        };
    }

    /** Installs {@code theme} as the look and feel; false when FlatLaf could not set it up. */
    public static boolean install(Theme theme) {
        requireEdt();
        return install(resolve(theme), theme.palette().background());
    }

    /** Installs a generated theme through the same path as any IntelliJ theme; false when FlatLaf could not set it up. */
    static boolean install(GeneratedTheme theme) {
        requireEdt();
        var resolved = new ThemeLoader.Resolved(theme.name(), theme.dark(),
            ThemeLoader.json(theme.name(), theme.dark(), "Jasper", theme.uiKeys(), theme.icons()),
            ThemeLoader.explicitColors(theme.uiKeys()), List.of(), theme.uiKeys(), theme.icons());
        return install(resolved, theme.terminal().background());
    }

    static boolean install(ThemeLoader.Resolved resolved, Color terminalBackground) {
        requireEdt();
        FlatLaf laf;
        try { laf = IntelliJTheme.createLaf(new ByteArrayInputStream(resolved.json().getBytes(StandardCharsets.UTF_8))); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        laf.setExtraDefaults(APP_DEFAULTS);
        if (!FlatLaf.setup(laf)) return false;
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        var extra = new LinkedHashMap<>(APP_DEFAULTS);
        resolved.colors().forEach((key, color) -> {
            if (defaults.get(key) != null) return;
            defaults.put(key, new ColorUIResource(color));
            extra.put(key, UiTemplate.hex(color));
        });
        ChromeKeys.derive(defaults, terminalBackground).forEach((key, color) -> extra.put(key, UiTemplate.hex(color)));
        // A later reinstall of this instance (a failed switch rolls back to it) rebuilds the defaults.
        laf.setExtraDefaults(extra);
        return true;
    }

    private static Map<String, String> appDefaults() {
        var defaults = new LinkedHashMap<String, String>();
        // Reference form typography in logical points; FlatLaf applies display scaling.
        for (String component : List.of("Label", "List", "TextField", "PasswordField", "FormattedTextField", "TextArea", "ComboBox", "Button"))
            defaults.put(component + ".font", "12 $defaultFont");
        defaults.put("ListUI", "dev.jasper.app.appearance.AppListUI");
        for (String component : List.of("List", "Tree", "Table", "ComboBox", "MenuItem")) {
            defaults.put(component + ".selectionArc", "8");
            defaults.put(component + ".selectionInsets", "0,4,0,4");
        }
        defaults.put("List.cellMargins", "1,8,1,8");
        defaults.put("Table.cellMargins", "2,8,2,8");
        defaults.put("TextComponent.arc", "8");
        for (String component : List.of("TextField", "PasswordField", "FormattedTextField"))
            defaults.put(component + ".margin", "5,8,5,8");
        defaults.put("SplitPane.dividerSize", "8");
        defaults.put("SplitPaneDivider.border", "dev.jasper.app.workspace.SplitDividerBorder");
        // Windows and Linux dialogs and auxiliary windows: centred titles, like the macOS title rows.
        defaults.put("TitlePane.centerTitle", "true");
        return Collections.unmodifiableMap(defaults);
    }

    private static String read(String file) {
        try (InputStream in = ThemeManager.class.getClassLoader().getResourceAsStream(BASE + file)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
