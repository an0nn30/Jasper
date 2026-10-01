package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import java.awt.Color;
import javax.swing.*;
import javax.swing.table.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.WorkspaceStyleFixtures.*;
import static org.assertj.core.api.Assertions.*;

class WorkspaceStylerTest {
    @Test void failedConstructionReleasesRootMarkerAndAllowsRetry() throws Exception {
        edt(() -> {
            var root = new JPanel() {
                boolean fail = true;
                @Override public java.awt.Component[] getComponents() {
                    if (fail) { fail = false; throw new IllegalStateException("fixture traversal failure"); }
                    return super.getComponents();
                }
            };
            assertThatThrownBy(() -> new WorkspaceStyler(root)).isInstanceOf(IllegalStateException.class)
                .hasMessage("fixture traversal failure");
            assertThat(root.getClientProperty("Jasper.workspaceStylerRoot")).isNull();
            try (var styler = new WorkspaceStyler(root)) { styler.apply(independent()); }
            assertThat(root.getClientProperty("Jasper.workspaceStylerRoot")).isNull();
        });
    }
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


    @Test void lateChildReceivesCurrentTheme() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel();
            var child = new JButton();
            try (var styler = new WorkspaceStyler(root)) {
                assertThat(root.getClientProperty("Jasper.workspaceStyle")).isNull();
                styler.apply(independent());
                root.add(child);
                assertThat(child.getBackground()).isEqualTo(independent().workspace().tokens().get(Token.BUTTON_BACKGROUND));
            }
            assertThat(child.getClientProperty("FlatLaf.style")).isNull();
            assertThat(child.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(child.getBackground()).isEqualTo(new JButton().getBackground());
        });
    }

    @Test void removedChildRestoresAndDetaches() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel();
            var child = new JButton("Move");
            Object pluginStyle = "arc: 11";
            child.putClientProperty("FlatLaf.style", pluginStyle);
            root.add(child);
            var listeners = child.getPropertyChangeListeners();
            var containerListeners = child.getContainerListeners();
            try (var styler = new WorkspaceStyler(root)) {
                styler.apply(independent());
                assertThat(child.getClientProperty("Jasper.workspaceStyle")).isNotNull();
                root.remove(child);
                assertThat(child.getClientProperty("FlatLaf.style")).isSameAs(pluginStyle);
                assertThat(child.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(child.getPropertyChangeListeners()).containsExactlyInAnyOrder(listeners);
                assertThat(child.getContainerListeners()).containsExactlyInAnyOrder(containerListeners);
            }
        });
    }

    @SuppressWarnings("try")
    @Test void reparentingHasOneOwner() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var a = new JPanel(); var b = new JPanel(); var text = new JTextArea("unsaved");
            var document = text.getDocument(); var listeners = text.getPropertyChangeListeners();
            a.add(text);
            try (var first = new WorkspaceStyler(a); var second = new WorkspaceStyler(b)) {
                first.apply(independent()); second.apply(recolored(independent(), Token.INPUT_BACKGROUND, Color.MAGENTA));
                a.remove(text); b.add(text);
                assertThat(text.getBackground()).isEqualTo(Color.MAGENTA);
                first.close();
                assertThat(text.getBackground()).isEqualTo(Color.MAGENTA);
                assertThat(text.getDocument()).isSameAs(document);
                assertThat(text.getText()).isEqualTo("unsaved");
            }
            assertThat(text.getPropertyChangeListeners()).containsExactlyInAnyOrder(listeners);
        });
    }

    @Test void exemptionToggleRestoresAndResumes() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel(); var subtree = new JPanel(); var child = new JButton();
            child.putClientProperty("FlatLaf.style", "arc: 11");
            root.add(subtree); subtree.add(child);
            var listeners = child.getPropertyChangeListeners(); var containers = subtree.getContainerListeners();
            try (var styler = new WorkspaceStyler(root)) {
                styler.apply(independent()); subtree.putClientProperty("Jasper.workspace.exempt", true);
                assertThat(child.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(child.getPropertyChangeListeners()).containsExactlyInAnyOrder(listeners);
                assertThat(subtree.getContainerListeners()).containsExactlyInAnyOrder(containers);
                var late = new JLabel("late"); subtree.add(late);
                subtree.putClientProperty("Jasper.workspace.exempt", null);
                assertThat(child.getClientProperty("Jasper.workspaceStyle")).isNotNull();
                assertThat(late.getForeground()).isEqualTo(independent().workspace().tokens().get(Token.TEXT));
            }
        });
    }

    @Test void headerViewportAndModelReplacementRestylesAssociatedComponents() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel(); var table = new JTable(2, 2); root.add(table);
            var old = table.getTableHeader(); var oldListeners = old.getPropertyChangeListeners();
            try (var styler = new WorkspaceStyler(root)) {
                styler.apply(independent());
                assertThat(old.getClientProperty("Jasper.workspaceStyle")).isNotNull();
                var scroll = new JScrollPane(table); scroll.setColumnHeaderView(old); root.add(scroll);
                var header = new JTableHeader(table.getColumnModel()); table.setTableHeader(header);
                // Old header is still reachable through the scroll pane's column viewport.
                assertThat(old.getClientProperty("Jasper.workspaceStyle")).isNotNull();
                scroll.setColumnHeaderView(header);
                assertThat(old.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(old.getPropertyChangeListeners()).containsExactlyInAnyOrder(oldListeners);
                table.setModel(new DefaultTableModel(3, 3));
                assertThat(table.getSelectionBackground()).isEqualTo(independent().workspace().tokens().get(Token.SELECTION));
                assertThat(header.getBackground()).isEqualTo(independent().workspace().tokens().get(Token.SURFACE_RAISED));
                var rowHeader = new JLabel("row"); scroll.setRowHeaderView(rowHeader);
                assertThat(rowHeader.getForeground()).isEqualTo(independent().workspace().tokens().get(Token.TEXT));
                var replacement = new JViewport(); var text = new JTextArea(); replacement.setView(text);
                scroll.setViewport(replacement);
                assertThat(text.getClientProperty("Jasper.workspaceStyle")).isNotNull();
                assertThat(replacement.getBackground()).isEqualTo(independent().workspace().tokens().get(Token.SURFACE_SUNKEN));
            }
        });
    }

    @Test void matchLeavesNoGeneratedLayer() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel(); var b = new JButton(); root.add(b);
            Object zone = new Object(); root.putClientProperty("Jasper.workspaceTheme", zone);
            try (var styler = new WorkspaceStyler(root)) {
                styler.apply(independent()); styler.apply(new ResolvedTheme(independent().chrome(), independent().chrome()));
                assertThat(b.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(b.getClientProperty("FlatLaf.style")).isNull();
                assertThat(root.getClientProperty("Jasper.workspaceTheme")).isSameAs(zone);
                var late = new JButton(); root.add(late);
                assertThat(late.getClientProperty("Jasper.workspaceStyle")).isNull();
                styler.apply(independent()); assertThat(late.getClientProperty("Jasper.workspaceStyle")).isNotNull();
            }
        });
    }

    @Test void closeSuppressesQueuedRefresh() throws Exception {
        var root = new JPanel[1]; var child = new JButton[1];
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            root[0] = new JPanel(); root[0].putClientProperty("FlatLaf.style", "background: $Panel.background"); child[0] = new JButton(); root[0].add(child[0]);
            var listeners = root[0].getPropertyChangeListeners(); var containers = root[0].getContainerListeners();
            var count = new java.util.concurrent.atomic.AtomicInteger();
            addStyleDrivenChild(root[0], count);
            var styler = new WorkspaceStyler(root[0]); styler.apply(independent());
            assertThat(count.get()).isBetween(1, 23); // More reconciliation work is queued, not recursive.
            styler.close(); styler.close();
            assertThat(root[0].getClientProperty("Jasper.workspaceStylerRoot")).isNull();
            assertThat(root[0].getPropertyChangeListeners()).containsExactlyInAnyOrder(listeners);
            assertThat(root[0].getContainerListeners()).containsExactlyInAnyOrder(containers);
            assertThatThrownBy(() -> styler.apply(independent())).isInstanceOf(IllegalStateException.class);
        });
        edt(() -> {
            for (var c : root[0].getComponents()) {
                assertThat(((JComponent) c).getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(((JComponent) c).getClientProperty("Jasper.workspaceStylerOwner")).isNull();
            }
        });
    }

    @Test void newerThemeDoesNotDropPendingReconciliation() throws Exception {
        var root = new JPanel[1];
        var styler = new WorkspaceStyler[1];
        var styled = new java.util.concurrent.atomic.AtomicInteger();
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            root[0] = new JPanel();
            addOnceStyledChild(root[0], styled);
            styler[0] = new WorkspaceStyler(root[0]);
            styler[0].apply(independent());
            styler[0].apply(recolored(independent(), Token.BUTTON_BACKGROUND, Color.MAGENTA));
            assertThat(styled.get()).isLessThan(40);
        });
        // Drain both the superseded callback and any current-generation replacement.
        edt(() -> {});
        edt(() -> {
            try {
                assertThat(styled.get()).isEqualTo(40);
                assertThat(root[0].getComponentCount()).isEqualTo(40);
                for (var child : root[0].getComponents())
                    assertThat(child.getBackground()).isEqualTo(Color.MAGENTA);
            } finally { styler[0].close(); }
        });
    }

    private static void addOnceStyledChild(JPanel root, java.util.concurrent.atomic.AtomicInteger count) {
        var child = new JButton();
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        child.addPropertyChangeListener("Jasper.workspaceStyle", event -> {
            if (event.getNewValue() != null && first.getAndSet(false) && count.incrementAndGet() < 40)
                addOnceStyledChild(root, count);
        });
        root.add(child);
    }

    @Test void associatedHeaderMovedAcrossBoundariesReleasesAndResumes() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel(); var table = new JTable(1, 1); root.add(table);
            var chrome = new JPanel(); chrome.putClientProperty("Jasper.zone", "chrome"); root.add(chrome);
            var exempt = new JPanel(); exempt.putClientProperty("Jasper.workspace.exempt", true); root.add(exempt);
            var ignored = new JLabel(); exempt.add(ignored);
            var ignoredProperties = ignored.getPropertyChangeListeners();
            var ignoredHierarchy = ignored.getHierarchyListeners();
            var header = table.getTableHeader();
            var background = header.getBackground();
            var properties = header.getPropertyChangeListeners();
            var hierarchy = header.getHierarchyListeners();
            var containers = header.getContainerListeners();
            try (var styler = new WorkspaceStyler(root)) {
                styler.apply(independent());
                for (var boundary : new JPanel[]{chrome, exempt}) {
                    assertThat(header.getClientProperty("Jasper.workspaceStyle")).isNotNull();
                    boundary.add(header);
                    assertThat(header.getClientProperty("Jasper.workspaceStyle")).isNull();
                    assertThat(header.getClientProperty("Jasper.workspaceStylerOwner")).isNull();
                    assertThat(header.getBackground()).isSameAs(background);
                    assertThat(header.getPropertyChangeListeners()).containsExactlyInAnyOrder(properties);
                    assertThat(header.getContainerListeners()).containsExactlyInAnyOrder(containers);
                    assertThat(header.getHierarchyListeners()).hasSize(hierarchy.length + 1);
                    boundary.remove(header); // Unparented, but the live table association is eligible again.
                    assertThat(header.getClientProperty("Jasper.workspaceStyle")).isNotNull();
                }
                exempt.add(header);
                assertThat(ignored.getPropertyChangeListeners()).containsExactlyInAnyOrder(ignoredProperties);
                assertThat(ignored.getHierarchyListeners()).containsExactlyInAnyOrder(ignoredHierarchy);
            }
            assertThat(header.getHierarchyListeners()).containsExactlyInAnyOrder(hierarchy);
            assertThat(header.getPropertyChangeListeners()).containsExactlyInAnyOrder(properties);
        });
    }

    @Test void associationWatchesDetachOnReplacementAndSourceRemoval() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel(); var table = new JTable(1, 1); root.add(table);
            var exempt = new JPanel(); exempt.putClientProperty("Jasper.workspace.exempt", true); root.add(exempt);
            var original = table.getTableHeader(); var originalListeners = original.getHierarchyListeners();
            var replacement = new JTableHeader(table.getColumnModel()); var replacementListeners = replacement.getHierarchyListeners();
            try (var styler = new WorkspaceStyler(root)) {
                styler.apply(independent());
                exempt.add(original);
                table.setTableHeader(replacement);
                assertThat(original.getHierarchyListeners()).containsExactlyInAnyOrder(originalListeners);
                exempt.add(replacement);
                assertThat(replacement.getHierarchyListeners()).hasSize(replacementListeners.length + 1);
                root.remove(table);
                assertThat(replacement.getHierarchyListeners()).containsExactlyInAnyOrder(replacementListeners);
            }
        });
    }

    private static void addStyleDrivenChild(JPanel root, java.util.concurrent.atomic.AtomicInteger count) {
        var child = new JButton();
        child.addPropertyChangeListener("Jasper.workspaceStyle", event -> {
            if (event.getNewValue() != null && count.incrementAndGet() < 24) addStyleDrivenChild(root, count);
        });
        root.add(child);
    }

    @Test void associatedHeaderCannotBypassAnExemptAncestor() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel(); var table = new JTable(1, 1); root.add(table);
            var exempt = new JPanel(); exempt.putClientProperty("Jasper.workspace.exempt", true);
            exempt.add(table.getTableHeader()); root.add(exempt);
            try (var styler = new WorkspaceStyler(root)) {
                styler.apply(independent());
                assertThat(table.getTableHeader().getClientProperty("Jasper.workspaceStyle")).isNull();
                exempt.putClientProperty("Jasper.workspace.exempt", null);
                assertThat(table.getTableHeader().getClientProperty("Jasper.workspaceStyle")).isNotNull();
            }
        });
    }

    @Test void nestedChromeAndTransientRendererStayUntouched() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel(); var chrome = new JPanel(); chrome.putClientProperty("Jasper.zone", "chrome");
            var button = new JButton(); chrome.add(button); root.add(chrome);
            var renderer = new CellRendererPane(); var label = new JLabel(); renderer.add(label); root.add(renderer);
            var popup = new JPopupMenu(); popup.add(new JMenuItem("menu")); root.add(popup);
            var tooltip = new JToolTip(); root.add(tooltip); var dialogRoot = new JRootPane(); root.add(dialogRoot);
            var listeners = button.getPropertyChangeListeners();
            try (var styler = new WorkspaceStyler(root)) {
                styler.apply(independent());
                for (var c : new JComponent[]{button, label, popup, tooltip, dialogRoot})
                    assertThat(c.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(button.getPropertyChangeListeners()).containsExactlyInAnyOrder(listeners);
                chrome.putClientProperty("Jasper.zone", null);
                assertThat(button.getClientProperty("Jasper.workspaceStyle")).isNotNull();
            }
        });
    }

    @Test void nestedStylerOwnsItsRootUntilClosed() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel(); var inner = new JPanel(); var child = new JButton(); inner.add(child); root.add(inner);
            try (var outer = new WorkspaceStyler(root)) {
                outer.apply(independent());
                try (var nested = new WorkspaceStyler(inner)) {
                    nested.apply(recolored(independent(), Token.BUTTON_BACKGROUND, Color.MAGENTA));
                    outer.apply(independent()); assertThat(child.getBackground()).isEqualTo(Color.MAGENTA);
                }
                assertThat(child.getBackground()).isEqualTo(independent().workspace().tokens().get(Token.BUTTON_BACKGROUND));
            }
        });
    }

    @Test void nestedUiSuspensionUsesOnlyLatestTheme() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var root = new JPanel(); var b = new JButton(); root.add(b);
            try (var styler = new WorkspaceStyler(root)) {
                styler.apply(independent()); styler.beforeUiUpdate(); styler.beforeUiUpdate();
                styler.apply(recolored(independent(), Token.BUTTON_BACKGROUND, Color.MAGENTA));
                SwingUtilities.updateComponentTreeUI(root);
                var late = new JButton(); root.add(late);
                styler.afterUiUpdate();
                assertThat(b.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(late.getClientProperty("Jasper.workspaceStyle")).isNull();
                styler.afterUiUpdate();
                assertThat(b.getBackground()).isEqualTo(Color.MAGENTA); assertThat(late.getBackground()).isEqualTo(Color.MAGENTA);
            }
        });
    }
}
