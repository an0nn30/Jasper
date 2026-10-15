package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class WorkspaceRolesTest {
    @Test void nativeSearchKeepsItsDocumentCaretAndSelectionAcrossAnAppearanceRefresh() {
        ThemeTestSupport.install(true);
        var field = new JTextField("pending query");
        dev.jasper.ui.swing.NativeControlStyles.borderlessField(field, () -> dev.jasper.app.platform.ThemeZone.theme(field));
        var document = field.getDocument(); field.select(2, 7);
        ThemeTestSupport.install(false);
        SwingUtilities.updateComponentTreeUI(field);
        assertThat(field.getDocument()).isSameAs(document);
        assertThat(field.getText()).isEqualTo("pending query");
        assertThat(field.getSelectionStart()).isEqualTo(2); assertThat(field.getSelectionEnd()).isEqualTo(7);
        assertThat(field.getBackground()).isEqualTo(ThemeTestSupport.generated(false).semantic().color(SemanticColor.SURFACE_INPUT));
    }

    @Test void liveModuleSnapshotSurvivesReinstallAndTransactionalRollback() throws Exception {
        ThemeTestSupport.install(true);
        var dark = ThemeTestSupport.generated(true); var light = ThemeTestSupport.generated(false);
        var laf = UIManager.getLookAndFeel();
        ThemeTestSupport.install(false);
        UIManager.setLookAndFeel(laf);
        assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(dark);
        try (var binding = new ThemeBinding(new ThemePair(dark, dark), failure -> { throw new AssertionError(failure); });
             var icons = new dev.jasper.ui.icons.IconRegistry();
             var view = new dev.jasper.ui.swing.KitView(new dev.jasper.ui.swing.KitContext(binding, ThemeRoute.CHROME,
                 dev.jasper.ui.metrics.DesignMetrics.DEFAULT, icons, () -> false),
                 new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Text("label", dev.jasper.ui.model.ControlState.READY,
                     "Existing view", dev.jasper.ui.model.Nodes.TextRole.BODY)), event -> {})) {
            var label = (JLabel) view.getComponent(0);
            // Live controls may resolve lazy JButton defaults when rollback republishes fonts.
            // Resolve them before the snapshot so equality checks state, not lazy-cache timing.
            new JButton("warm rollback defaults");
            var beforeLaf = raw(UIManager.getLookAndFeelDefaults());
            var beforeOverrides = raw(UIManager.getDefaults());
            var transaction = new dev.jasper.ui.swing.AppearanceTransaction(binding, next -> {
                dev.jasper.ui.swing.FlatLafInstaller.install(next, dev.jasper.ui.kit.Kit.DEFAULT); throw new IllegalStateException("after install");
            }, previous -> {});
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.apply(new ThemePair(light, light)))
                .hasMessage("after install").satisfies(failure -> assertThat(failure.getSuppressed()).isEmpty());
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(dark);
            assertThat(binding.current().chrome()).isSameAs(dark);
            assertThat(raw(UIManager.getLookAndFeelDefaults())).isEqualTo(beforeLaf);
            assertThat(raw(UIManager.getDefaults())).isEqualTo(beforeOverrides);
            assertThat(label.getForeground()).isEqualTo(dark.semantic().color(SemanticColor.TEXT_PRIMARY));
            try {
                UIManager.put("defaultFont", new java.awt.Font("Dialog", java.awt.Font.PLAIN, 21));
                UIManager.put("Label.font", new java.awt.Font("Dialog", java.awt.Font.PLAIN, 21));
                assertThat(label.getFont().getSize2D()).isEqualTo(21f);
            } finally {
                UIManager.put("Label.font", beforeOverrides.get("Label.font"));
                UIManager.put("defaultFont", beforeOverrides.get("defaultFont"));
            }
            new dev.jasper.ui.swing.AppearanceTransaction(binding, next -> dev.jasper.ui.swing.FlatLafInstaller.install(next, dev.jasper.ui.kit.Kit.DEFAULT), previous -> {})
                .apply(new ThemePair(light, light));
            assertThat(view.getComponent(0)).isSameAs(label);
            assertThat(label.getForeground()).isEqualTo(light.semantic().color(SemanticColor.TEXT_PRIMARY));
        }
    }

    @Test void canonicalChromeRolesWinWithoutChangingSearchContextOnThemeSwitch() {
        var theme = ThemeTestSupport.recolored(ThemeTestSupport.generated(true), Map.of(SemanticColor.SURFACE_APP, new Color(0x123456)));
        var search = new TitleSearchField(() -> {}, () -> "shortcut");
        search.setContext("pending context", "tooltip", null);
        ThemeTestSupport.install(theme);
        var area = new ToolWindowArea(new JPanel(), new dev.jasper.app.dock.DockState());
        assertThat(area.getBackground()).isEqualTo(new Color(0x123456));
        ThemeTestSupport.install(false);
        SwingUtilities.updateComponentTreeUI(search);
        assertThat(search.contextText()).isEqualTo("pending context");
    }
    private static Map<Object, Object> raw(javax.swing.UIDefaults defaults) {
        var values = new java.util.HashMap<Object, Object>(); defaults.forEach(values::put); return values;
    }

}
