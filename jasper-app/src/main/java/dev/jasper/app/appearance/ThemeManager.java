package dev.jasper.app.appearance;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.GeneratedTheme;
import javax.swing.SwingUtilities;

/** EDT installation policy; all generation and installation use the shared module. */
public final class ThemeManager {
    private ThemeManager() {}

    /** Installs the module snapshot under {@code kit} plus app-owned native window defaults; installation failures propagate. */
    static boolean install(GeneratedTheme theme, Kit kit) {
        requireEdt();
        dev.jasper.ui.swing.FlatLafInstaller.install(theme, kit);
        // Native delegate installation only; all palette/style generation belongs to jasper-ui.
        var laf = (com.formdev.flatlaf.FlatLaf) javax.swing.UIManager.getLookAndFeel();
        var extra = new java.util.LinkedHashMap<>(laf.getExtraDefaults());
        int divider = kit.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.NATIVE_SPLIT_DIVIDER);
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

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
