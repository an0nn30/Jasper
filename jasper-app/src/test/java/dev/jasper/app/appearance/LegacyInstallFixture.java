package dev.jasper.app.appearance;
import com.formdev.flatlaf.*;
import dev.jasper.ui.themeimport.FlatLafThemeData;
import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
/** Pre-migration installation oracle, excluded from production. */
final class LegacyInstallFixture {
    static boolean install(String json, Map<String, Color> colors, Color terminalBackground) {

        FlatLaf laf;
        try { laf = IntelliJTheme.createLaf(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        laf.setExtraDefaults(appDefaults());
        if (!FlatLaf.setup(laf)) return false;
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        var extra = new LinkedHashMap<>(appDefaults());
        colors.forEach((key, color) -> {
            if (defines(defaults, key)) return;
            defaults.put(key, new ColorUIResource(color));
            extra.put(key, dev.jasper.ui.theme.ColorMath.hex(color));
        });
        ChromeKeys.derive(defaults, terminalBackground).forEach((key, color) -> extra.put(key, dev.jasper.ui.theme.ColorMath.hex(color)));
        // A later reinstall of this instance (a failed switch rolls back to it) rebuilds the defaults.
        laf.setExtraDefaults(extra);
        return true;
    }

    private static boolean defines(UIDefaults defaults, String key) {
        return defaults.get(key) instanceof Color;
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

}
