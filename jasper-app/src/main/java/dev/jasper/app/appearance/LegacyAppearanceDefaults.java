package dev.jasper.app.appearance;

import com.formdev.flatlaf.FlatLaf;
import dev.jasper.ui.theme.*;
import java.util.LinkedHashMap;
import java.util.List;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.InsetsUIResource;

/** Temporary raw-control publication for Plan 2 Tasks 6–10. Task 11 removes it; no theme derivation lives here. */
final class LegacyAppearanceDefaults {
    private LegacyAppearanceDefaults() {}
    static void install(GeneratedTheme theme) {
        var defaults = UIManager.getLookAndFeelDefaults();
        var laf = (FlatLaf) UIManager.getLookAndFeel();
        var extra = new LinkedHashMap<>(laf.getExtraDefaults());
        UiTokenKeys.KEYS.forEach((token, key) -> {
            var color = theme.tokens().get(token);
            defaults.put(key, new ColorUIResource(color));
            extra.put(key, ColorMath.hex(color));
        });
        // Native [ui] remains the last-word raw control override, independently of canonical roles.
        dev.jasper.ui.themeimport.FlatLafThemeData.explicitColors(theme.uiKeys()).forEach((key, color) -> {
            defaults.put(key, new ColorUIResource(color));
            extra.put(key, ColorMath.hex(color));
        });
        // Plan 2 Task 6c: notifications read module roles from the chrome KitContext; the five former
        // Jasper.notification.* aliases are no longer published (an authored one is an unsupported override, Task 11).
        // Persist module-installed colors, including semantic values, through reinstallation.
        defaults.forEach((key, value) -> { if (value instanceof java.awt.Color color) extra.put(key.toString(), ColorMath.hex(color)); });
        defaults.put("ListUI", "dev.jasper.app.appearance.AppListUI");
        extra.put("ListUI", "dev.jasper.app.appearance.AppListUI");
        for (String component : List.of("List", "Tree", "Table", "ComboBox", "MenuItem")) {
            defaults.put(component + ".selectionArc", 8);
            defaults.put(component + ".selectionInsets", new InsetsUIResource(0,4,0,4));
            extra.put(component + ".selectionArc", "8");
            extra.put(component + ".selectionInsets", "0,4,0,4");
        }
        for (String component : List.of("Label", "List", "TextField", "PasswordField", "FormattedTextField", "TextArea", "ComboBox", "Button")) {
            var font = UIManager.getFont("defaultFont");
            defaults.put(component + ".font", new javax.swing.plaf.FontUIResource(font.deriveFont(12f * com.formdev.flatlaf.util.UIScale.getUserScaleFactor())));
            extra.put(component + ".font", "12 $defaultFont");
        }
        defaults.put("List.cellMargins", new InsetsUIResource(1,8,1,8)); extra.put("List.cellMargins", "1,8,1,8");
        defaults.put("Table.cellMargins", new InsetsUIResource(2,8,2,8)); extra.put("Table.cellMargins", "2,8,2,8");
        defaults.put("SplitPane.dividerSize", 8); extra.put("SplitPane.dividerSize", "8");
        defaults.put("SplitPaneDivider.border", new javax.swing.UIDefaults.ProxyLazyValue("dev.jasper.app.workspace.SplitDividerBorder"));
        extra.put("SplitPaneDivider.border", "dev.jasper.app.workspace.SplitDividerBorder");
        defaults.put("TitlePane.centerTitle", true); extra.put("TitlePane.centerTitle", "true");
        laf.setExtraDefaults(extra);
    }
}
