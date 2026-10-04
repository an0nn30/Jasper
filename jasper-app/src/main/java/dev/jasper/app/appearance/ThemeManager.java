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
 * The built-in themes and the one path that installs any theme. Every theme, a Jasper theme file or a converted
 * IntelliJ one, is first generated ({@link ThemeGenerator}); its UI keys are then written as an IntelliJ theme file,
 * FlatLaf builds the look and feel from that file, the colours FlatLaf skips (IntelliJ-only namespaces such as
 * {@code SearchEverywhere.*}) are put back, and Jasper's chrome keys are derived. EDT only.
 */
public final class ThemeManager {
    private static final String BASE = "dev/jasper/app/themes/";
    /** Theme-independent application defaults: form typography, input geometry and the split divider. Every theme gets them. */
    public static final Map<String, String> APP_DEFAULTS = appDefaults();
    private static final Map<Theme, GeneratedTheme> GENERATED = new HashMap<>();

    private ThemeManager() {}

    /** The five bundled themes: Jasper Light/Dark, Nord and Clear Dark/Light. */
    public static List<Theme> builtIns() { return BuiltInThemes.all(); }

    /** {@code theme}'s generated values; EDT only (the cache is unsynchronized). */
    public static GeneratedTheme generated(Theme theme) {
        requireEdt();
        return GENERATED.computeIfAbsent(theme, key -> WorkspaceValues.resolve(key.source().document(),
            ThemeGenerator.generate(key.source().document())));
    }

    /** Drops obsolete catalog values after a refresh; include active choices that should remain cached. EDT only. */
    public static void retainGenerated(java.util.Collection<Theme> themes) {
        requireEdt();
        GENERATED.keySet().removeIf(theme -> !(theme.source() instanceof Theme.Bundled) && !themes.contains(theme));
    }

    /** Installs {@code theme} as the look and feel; false when FlatLaf could not set it up. */
    public static boolean install(Theme theme) {
        requireEdt();
        return install(generated(theme));
    }

    /** Installs a generated theme through the same path as any IntelliJ theme; false when FlatLaf could not set it up. */
    static boolean install(GeneratedTheme theme) {
        requireEdt();
        return install(FlatLafThemeData.json(theme.name(), theme.dark(), theme.author(), theme.uiKeys(), theme.icons()),
            FlatLafThemeData.explicitColors(theme.uiKeys()), theme.terminal().background());
    }

    static boolean install(String json, Map<String, Color> colors, Color terminalBackground) {
        requireEdt();
        FlatLaf laf;
        try { laf = IntelliJTheme.createLaf(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        laf.setExtraDefaults(APP_DEFAULTS);
        if (!FlatLaf.setup(laf)) return false;
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        var extra = new LinkedHashMap<>(APP_DEFAULTS);
        colors.forEach((key, color) -> {
            if (FlatLafThemeData.defines(defaults, key)) return;
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

    static String read(String file) {
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
