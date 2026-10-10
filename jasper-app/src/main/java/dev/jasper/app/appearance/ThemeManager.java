package dev.jasper.app.appearance;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.ThemeGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;

/** App catalog cache and EDT installation policy; all generation and installation use the shared module. */
public final class ThemeManager {
    private static final String BASE = "dev/jasper/ui/themeimport/native/";
    private static final Map<Theme, GeneratedTheme> GENERATED = new HashMap<>();

    private ThemeManager() {}

    /** The five bundled themes: Jasper Light/Dark, Nord and Clear Dark/Light. */
    public static List<Theme> builtIns() { return BuiltInThemes.all(); }

    /** {@code theme}'s generated values; EDT only (the cache is unsynchronized). */
    public static GeneratedTheme generated(Theme theme) {
        requireEdt();
        return GENERATED.computeIfAbsent(theme, key -> ThemeGenerator.generate(key.source().document()));
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

    /** Installs the module snapshot plus app-owned native window defaults; installation failures propagate. */
    static boolean install(GeneratedTheme theme) {
        requireEdt();
        dev.jasper.ui.swing.FlatLafInstaller.install(theme);
        // Native delegate installation only; all palette/style generation belongs to jasper-ui.
        var laf = (com.formdev.flatlaf.FlatLaf) javax.swing.UIManager.getLookAndFeel();
        var extra = new java.util.LinkedHashMap<>(laf.getExtraDefaults());
        int divider = dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.NATIVE_SPLIT_DIVIDER);
        extra.put("SplitPane.dividerSize", Integer.toString(divider));
        extra.put("SplitPaneDivider.border", "dev.jasper.app.workspace.SplitDividerBorder");
        extra.put("TitlePane.centerTitle", "true");
        laf.setExtraDefaults(extra);
        var defaults = javax.swing.UIManager.getLookAndFeelDefaults();
        defaults.put("SplitPane.dividerSize", divider);
        defaults.put("SplitPaneDivider.border", new javax.swing.UIDefaults.ProxyLazyValue("dev.jasper.app.workspace.SplitDividerBorder"));
        defaults.put("TitlePane.centerTitle", true);
        return true;
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
