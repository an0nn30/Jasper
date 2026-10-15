package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.UiColorKeys;
import dev.jasper.ui.theme.UiKeySet;
import java.awt.Insets;
import java.util.List;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.junit.jupiter.api.Test;

class SlateCobaltInstallTest {
    @Test void thePopupShadowComesFromTheMetricsNotTheThemeKeys() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                for (boolean dark : new boolean[] {true, false}) {
                    var theme = JasperTheme.bundled().generated(dark);
                    assertThat(UiColorKeys.flatLaf(theme, UiKeySet.DEFAULT)).doesNotContainKeys("Popup.dropShadowColor", "Popup.dropShadowOpacity");
                    FlatLafInstaller.install(theme);
                    assertThat(UIManager.getColor("Popup.dropShadowColor")).isEqualTo(java.awt.Color.BLACK);
                    assertThat(((Number) UIManager.get("Popup.dropShadowOpacity")).doubleValue()).isCloseTo(dark ? .5 : .18,
                        org.assertj.core.data.Offset.offset(1e-6));
                }
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }

    @Test void flatLafGeometryComesFromTheCompactScale() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                FlatLafInstaller.install(JasperTheme.bundled().generated(true));
                for (var key : List.of("Button.arc", "Component.arc", "TextComponent.arc")) assertThat(UIManager.getInt(key)).as(key).isEqualTo(8);
                assertThat(UIManager.getInsets("Button.margin")).isEqualTo(new Insets(2, 14, 2, 14));
                assertThat(UIManager.getInt("Button.minimumWidth")).isEqualTo(72);
                assertThat(UIManager.getInt("Button.minimumHeight")).isEqualTo(24);
                assertThat(UIManager.getInt("Component.minimumHeight")).isEqualTo(24);
                for (var key : List.of("List", "Tree", "Table", "ComboBox", "MenuItem")) {
                    assertThat(UIManager.getInt(key + ".selectionArc")).as(key).isZero();
                    assertThat(UIManager.getInsets(key + ".selectionInsets")).as(key).isEqualTo(new Insets(0, 0, 0, 0));
                }
                for (var key : List.of("MenuItem", "Menu", "CheckBoxMenuItem", "RadioButtonMenuItem"))
                    assertThat(UIManager.getInsets(key + ".margin")).as(key).isEqualTo(new Insets(3, 8, 3, 8));
                assertThat(UIManager.getInsets("PopupMenu.borderInsets")).isEqualTo(new Insets(4, 1, 4, 1));
                assertThat(UIManager.getInt("Popup.borderCornerRadius")).isEqualTo(6);
                assertThat(UIManager.getInt("PopupMenu.borderCornerRadius")).isEqualTo(6);
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }

    @Test void nativeMenusTakeTheKitMenusRoles() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                for (boolean dark : new boolean[] {true, false}) {
                    var theme = JasperTheme.bundled().generated(dark); var p = theme.semantic();
                    FlatLafInstaller.install(theme);
                    for (var key : List.of("PopupMenu.background", "MenuItem.background", "Menu.background")) assertThat(UIManager.getColor(key)).as(key).isEqualTo(p.color(SemanticColor.SURFACE_MENU));
                    for (var key : List.of("MenuItem.selectionBackground", "Menu.selectionBackground", "CheckBoxMenuItem.selectionBackground", "RadioButtonMenuItem.selectionBackground"))
                        assertThat(UIManager.getColor(key)).as(key).isEqualTo(p.color(SemanticColor.ACCENT));
                    assertThat(UIManager.getColor("PopupMenu.borderColor")).isEqualTo(p.color(SemanticColor.BORDER_MENU));
                    assertThat(UIManager.getColor("MenuItem.acceleratorForeground")).isEqualTo(p.color(SemanticColor.TEXT_TERTIARY));
                    assertThat(UIManager.getColor("MenuItem.acceleratorSelectionForeground")).isEqualTo(p.color(SemanticColor.TEXT_ONACCENT));
                }
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }

    @Test void eachVariantInstallsIntoFlatLafWithItsRoles() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                for (boolean dark : new boolean[] {true, false}) {
                    var theme = JasperTheme.bundled().generated(dark);
                    FlatLafInstaller.install(theme);
                    assertThat(UIManager.get("Jasper.installedTheme")).isSameAs(theme);
                    assertThat(UIManager.getColor("Panel.background")).isEqualTo(theme.semantic().color(SemanticColor.SURFACE_PANEL));
                    assertThat(UIManager.getColor("Jasper.semantic.surface.tabstrip")).isEqualTo(theme.semantic().color(SemanticColor.SURFACE_TABSTRIP));
                    assertThat(UIManager.getColor("Jasper.semantic.surface.scrim").getAlpha()).isEqualTo(dark ? 0x8C : 0x40);
                }
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }
}
