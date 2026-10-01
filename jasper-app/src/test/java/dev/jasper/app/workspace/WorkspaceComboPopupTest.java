package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import java.awt.Color;
import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.WorkspaceStyleFixtures.*;
import static org.assertj.core.api.Assertions.*;

class WorkspaceComboPopupTest {
    private LookAndFeel previousLaf;
    private java.util.Map<String, Object> previousDefaults;
    private static final java.util.Set<String> MANAGED_DEFAULTS = java.util.Set.of("ClassLoader", "defaultFont", "Label.font",
        "List.font", "TextField.font", "PasswordField.font", "FormattedTextField.font", "TextArea.font",
        "ComboBox.font", "Button.font", "Jasper.uiFontFamilyOverride");

    @org.junit.jupiter.api.BeforeEach void captureChrome() throws Exception {
        edt(() -> {
            previousLaf = UIManager.getLookAndFeel();
            previousDefaults = new java.util.HashMap<>();
            for (Object key : UIManager.getDefaults().keySet())
                if (key instanceof String text && (text.startsWith("Jasper.workspace.") || MANAGED_DEFAULTS.contains(text)))
                    previousDefaults.put(text, UIManager.getDefaults().get(key));
        });
    }

    @org.junit.jupiter.api.AfterEach void restoreChrome() throws Exception {
        edt(() -> {
            try { UIManager.setLookAndFeel(previousLaf); }
            catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            var keys = new java.util.HashSet<>(previousDefaults.keySet()); keys.addAll(MANAGED_DEFAULTS);
            for (Object key : UIManager.getDefaults().keySet())
                if (key instanceof String text && text.startsWith("Jasper.workspace.")) keys.add(text);
            for (String key : keys) UIManager.put(key, previousDefaults.get(key));
        });
    }


    private static JList<?> list(JComboBox<?> combo) {
        return ((ComboPopup) combo.getUI().getAccessibleChild(combo, 0)).getList();
    }

    @Test void synchronousCloseDuringPopupPublicationRestoresDropdown() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            for (String key : java.util.List.of("FlatLaf.style", "selectionBackground", "Jasper.zone")) {
                var combo = new JComboBox<>(new String[]{"one"}); var list = list(combo);
                var baseline = list.getSelectionBackground();
                var popup = new WorkspaceComboPopup(combo);
                var once = new java.util.concurrent.atomic.AtomicBoolean();
                java.beans.PropertyChangeListener close = e -> {
                    if (e.getNewValue() != null && once.compareAndSet(false, true)) popup.close();
                };
                list.addPropertyChangeListener(key, close);
                var listeners = list.getPropertyChangeListeners();
                assertThatCode(() -> popup.apply(independent())).doesNotThrowAnyException();
                assertThat(once).isTrue();
                assertThat(list.getClientProperty("FlatLaf.style")).isNull();
                assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(list.getClientProperty("Jasper.zone")).isNull();
                assertThat(list.getSelectionBackground()).isEqualTo(baseline);
                assertThat(list.getPropertyChangeListeners()).hasSameSizeAs(listeners);
                assertThat(list.getPropertyChangeListeners(key)).containsExactly(close);
            }
        });
        edt(() -> {});
    }

    @Test void hiddenDropdownFollowsSameDarknessThemesAndRestoresZone() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var combo = new JComboBox<>(new String[]{"one", "two"}); var list = list(combo);
            var background = list.getBackground(); var selection = list.getSelectionBackground();
            var listeners = list.getPropertyChangeListeners();
            Object previous = "chrome"; list.putClientProperty("Jasper.zone", previous);
            try (var popup = new WorkspaceComboPopup(combo)) {
                popup.apply(independent());
                assertThat(list.getBackground()).isEqualTo(independent().workspace().tokens().get(Token.SURFACE_SUNKEN));
                assertThat(list.getForeground()).isEqualTo(independent().workspace().tokens().get(Token.TEXT));
                assertThat(list.getSelectionBackground()).isEqualTo(independent().workspace().tokens().get(Token.SELECTION));
                assertThat(list.getSelectionForeground()).isEqualTo(independent().workspace().tokens().get(Token.SELECTION_TEXT));
                assertThat(WorkspaceTheme.workspace(list)).isSameAs(independent().workspace());
                var next = recolored(independent(), Token.SURFACE_SUNKEN, Color.MAGENTA);
                popup.apply(next); assertThat(list.getBackground()).isEqualTo(Color.MAGENTA);
                var scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, list);
                assertThat(scroll.getViewport().getBackground()).isEqualTo(Color.MAGENTA);
                assertThat(scroll.getVerticalScrollBar().getClientProperty("Jasper.workspaceStyle")).isNotNull();
                popup.apply(new ResolvedTheme(next.chrome(), next.chrome()));
                assertThat(list.getClientProperty("Jasper.zone")).isSameAs(previous);
                assertThat(list.getBackground()).isSameAs(background);
                assertThat(list.getSelectionBackground()).isSameAs(selection);
                assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(list.getPropertyChangeListeners()).containsExactlyInAnyOrder(listeners);
            }
        });
    }

    @Test void customRendererRemainsInstalledAndResolvesDropdownZone() throws Exception {
        edt(() -> {
            var controller = new ThemeController(Theme.JASPER_LIGHT);
            controller.configureTerminal(dev.jasper.app.config.TerminalColors.DARK);
            var combo = new JComboBox<>(new String[]{"one"});
            ListCellRenderer<Object> renderer = (list, value, index, selected, focused) -> {
                assertThat(WorkspaceTheme.workspace(list)).isSameAs(independent().workspace());
                assertThat(dev.jasper.app.plugins.WorkspacePaletteTestBridge.text(list))
                    .isEqualTo(independent().workspace().tokens().get(Token.TEXT));
                var label = new JLabel(String.valueOf(value));
                label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
                label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
                return label;
            };
            combo.setRenderer(renderer);
            var sibling = new JPopupMenu(); sibling.setInvoker(combo);
            var siblingLabel = new JLabel("chrome"); sibling.add(siblingLabel);
            var tooltip = new JToolTip(); tooltip.setComponent(combo);
            var dialogRoot = new JRootPane();
            try (var popup = new WorkspaceComboPopup(combo)) {
                popup.apply(independent()); var list = list(combo);
                assertThat(combo.getRenderer()).isSameAs(renderer);
                for (var chrome : new JComponent[]{siblingLabel, tooltip, dialogRoot}) {
                    assertThat(dev.jasper.app.plugins.WorkspacePaletteTestBridge.text(chrome))
                        .isEqualTo(UIManager.getColor("Label.foreground"));
                    assertThat(chrome.getClientProperty("Jasper.workspaceStyle")).isNull();
                }
                assertThat(renderer.getListCellRendererComponent(list, "one", 0, true, false).getBackground())
                    .isEqualTo(independent().workspace().tokens().get(Token.SELECTION));
                assertThat(renderer.getListCellRendererComponent(list, "one", 0, false, false).getBackground())
                    .isEqualTo(independent().workspace().tokens().get(Token.SURFACE_SUNKEN));
            }
        });
    }

    @Test void editableComboAndDelegateReplacementReleaseOldPopup() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel(); var combo = new JComboBox<>(new String[]{"one"}); combo.setEditable(true); root.add(combo);
            var old = list(combo); var oldListeners = old.getPropertyChangeListeners();
            try (var styler = new WorkspaceStyler(root)) {
                styler.apply(independent());
                assertThat(((JComponent) combo.getEditor().getEditorComponent()).getClientProperty("Jasper.workspaceStyle")).isNotNull();
                var editor = new javax.swing.plaf.basic.BasicComboBoxEditor(); combo.setEditor(editor);
                assertThat(((JComponent) editor.getEditorComponent()).getClientProperty("Jasper.workspaceStyle")).isNotNull();
                combo.updateUI();
                assertThat(old.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(old.getClientProperty("Jasper.zone")).isNull();
                assertThat(old.getPropertyChangeListeners()).containsExactlyInAnyOrder(oldListeners);
                assertThat(list(combo).getSelectionBackground()).isEqualTo(independent().workspace().tokens().get(Token.SELECTION));
            }
            assertThat(list(combo).getClientProperty("Jasper.zone")).isNull();
        });
    }

    @Test void popupSuspensionCloseAndNonstandardUiAreSafe() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var combo = new JComboBox<>(new String[]{"one"}); var popup = new WorkspaceComboPopup(combo);
            popup.apply(independent()); popup.beforeUiUpdate(); popup.beforeUiUpdate();
            popup.apply(independent()); combo.updateUI(); popup.afterUiUpdate();
            assertThat(list(combo).getClientProperty("Jasper.zone")).isNull();
            popup.afterUiUpdate(); assertThat(list(combo).getClientProperty("Jasper.zone")).isEqualTo("workspace");
            popup.close(); popup.close(); assertThat(list(combo).getClientProperty("Jasper.zone")).isNull();
            assertThatThrownBy(() -> popup.apply(independent())).isInstanceOf(IllegalStateException.class);
            combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
                @Override public javax.accessibility.Accessible getAccessibleChild(JComponent c, int i) { return null; }
            });
            try (var custom = new WorkspaceComboPopup(combo)) { custom.apply(independent()); }
        });
    }
}
