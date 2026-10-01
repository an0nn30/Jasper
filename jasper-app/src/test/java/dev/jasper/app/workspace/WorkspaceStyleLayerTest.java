package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import java.awt.Color;
import java.util.*;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.WorkspaceStyleFixtures.*;
import static org.assertj.core.api.Assertions.*;

class WorkspaceStyleLayerTest {
    private LookAndFeel previousLaf;
    private Map<String, Object> previousDefaults;
    private static final Set<String> MANAGED_DEFAULTS = Set.of("ClassLoader", "defaultFont", "Label.font",
        "List.font", "TextField.font", "PasswordField.font", "FormattedTextField.font", "TextArea.font",
        "ComboBox.font", "Button.font", "Jasper.uiFontFamilyOverride");

    @BeforeEach void captureChrome() throws Exception {
        edt(() -> {
            previousLaf = UIManager.getLookAndFeel();
            previousDefaults = new HashMap<>();
            for (Object key : UIManager.getDefaults().keySet()) {
                if (key instanceof String text && (text.startsWith("Jasper.workspace.") || MANAGED_DEFAULTS.contains(text)))
                    previousDefaults.put(text, UIManager.getDefaults().get(key));
            }
        });
    }

    @AfterEach void restoreChrome() throws Exception {
        edt(() -> {
            try { UIManager.setLookAndFeel(previousLaf); }
            catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            var keys = new HashSet<>(previousDefaults.keySet());
            keys.addAll(MANAGED_DEFAULTS);
            for (Object key : UIManager.getDefaults().keySet())
                if (key instanceof String text && text.startsWith("Jasper.workspace.")) keys.add(text);
            for (String key : keys) UIManager.put(key, previousDefaults.get(key));
        });
    }


    @Test void pluginEditsWinAndSurviveClearAndClose() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var button = new JButton("Run");
            button.putClientProperty("FlatLaf.style", "arc: 13; background: #123456");
            var latest = Map.<String, Object>of("arc", 17, "background", new Color(0x654321));
            try (var layer = new WorkspaceStyleLayer(button)) {
                layer.apply(WorkspaceStyles.recipe(button, independent().workspace()));
                assertThat(button.getBackground()).isEqualTo(new Color(0x123456));
                button.putClientProperty("FlatLaf.style", latest);
                assertThat(button.getBackground()).isEqualTo(new Color(0x654321));
                layer.clear();
                assertThat(button.getClientProperty("FlatLaf.style")).isSameAs(latest);
                layer.apply(WorkspaceStyles.recipe(button, independent().workspace()));
            }
            assertThat(button.getClientProperty("FlatLaf.style")).isSameAs(latest);
            assertThat(button.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(button.getBackground()).isEqualTo(new Color(0x654321));
        });
    }

    @Test void styleClassesOverrideGeneratedButInlineOverridesClasses() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            UIManager.put("[style]Button.plugin", "background: #345678");
            try {
                for (Object classes : List.of("plugin", new String[]{"plugin"}, List.of("plugin"))) {
                    var b = new JButton();
                    b.putClientProperty("FlatLaf.styleClass", classes);
                    try (var layer = new WorkspaceStyleLayer(b)) {
                        layer.apply(WorkspaceStyles.recipe(b, independent().workspace()));
                        assertThat(b.getBackground()).isEqualTo(new Color(0x345678));
                        b.putClientProperty("FlatLaf.style", "background: #123456");
                        assertThat(b.getBackground()).isEqualTo(new Color(0x123456));
                        b.putClientProperty("FlatLaf.style", null);
                        assertThat(b.getBackground()).isEqualTo(new Color(0x345678));
                        b.putClientProperty("FlatLaf.styleClass", null);
                        assertThat(b.getBackground()).isEqualTo(independent().workspace().tokens().get(Token.BUTTON_BACKGROUND));
                    }
                    assertThat(b.getClientProperty("FlatLaf.style")).isNull();
                }
            } finally { UIManager.put("[style]Button.plugin", null); }
        });
    }

    @Test void pluginRemovesItsStyleWhileMounted() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var b = new JButton();
            Color chrome = b.getBackground();
            b.putClientProperty("FlatLaf.style", "background: #123456");
            try (var layer = new WorkspaceStyleLayer(b)) {
                layer.apply(WorkspaceStyles.recipe(b, independent().workspace()));
                b.putClientProperty("FlatLaf.style", null);
                layer.clear();
                assertThat(b.getBackground()).isSameAs(chrome);
            }
        });
    }

    @Test void classChangesAreNeverRewrittenAndRemovalRestoresChrome() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            UIManager.put("[style]Button.plugin", "background: #345678");
            try {
                var b = new JButton();
                Color chrome = b.getBackground();
                b.putClientProperty("FlatLaf.styleClass", "plugin");
                var events = new ArrayList<Object>();
                b.addPropertyChangeListener("FlatLaf.styleClass", e -> events.add(e.getNewValue()));
                try (var layer = new WorkspaceStyleLayer(b)) {
                    layer.apply(WorkspaceStyles.recipe(b, independent().workspace()));
                    assertThat(events).isEmpty();
                    b.putClientProperty("FlatLaf.styleClass", null);
                    assertThat(events).containsExactly((Object) null);
                    layer.clear();
                    assertThat(b.getBackground()).isSameAs(chrome);
                }
            } finally { UIManager.put("[style]Button.plugin", null); }
        });
    }

    @Test void directFallbackHonorsInlineAndClassStyles() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            UIManager.put("[style]Viewport.plugin", "background: #345678");
            try {
                var v = new JViewport();
                v.putClientProperty("FlatLaf.styleClass", "plugin");
                try (var layer = new WorkspaceStyleLayer(v)) {
                    layer.apply(WorkspaceStyles.recipe(v, independent().workspace()));
                    assertThat(v.getBackground()).isEqualTo(new Color(0x345678));
                    v.putClientProperty("FlatLaf.style", "background: #123456");
                    assertThat(v.getBackground()).isEqualTo(new Color(0x123456));
                }
            } finally { UIManager.put("[style]Viewport.plugin", null); }
        });
    }

    @Test void directPluginColorEditSurvivesNextApplyAndClose() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var c = new JViewport();
            var plugin = new Color(0x123456);
            try (var layer = new WorkspaceStyleLayer(c)) {
                layer.apply(WorkspaceStyles.recipe(c, independent().workspace()));
                c.setBackground(plugin);
                layer.apply(WorkspaceStyles.recipe(c, independent().workspace()));
                assertThat(c.getBackground()).isSameAs(plugin);
            }
            assertThat(c.getBackground()).isSameAs(plugin);
        });
    }

    @Test void restoresExactColorProvenanceAndDetaches() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            for (Color original : List.of(new Color(0x123456), new ColorUIResource(0x123456))) {
                var c = new JViewport();
                c.setBackground(original);
                var listeners = c.getPropertyChangeListeners();
                var layer = new WorkspaceStyleLayer(c);
                layer.apply(WorkspaceStyles.recipe(c, independent().workspace()));
                layer.clear();
                assertThat(c.getBackground()).isSameAs(original);
                layer.apply(WorkspaceStyles.recipe(c, independent().workspace()));
                // Equal RGB must not imply ownership; Swing suppresses the property event.
                var replacement = new Color(c.getBackground().getRGB());
                c.setBackground(replacement);
                layer.close();
                layer.close();
                assertThat(c.getBackground()).isSameAs(replacement);
                assertThat(c.getPropertyChangeListeners()).containsExactly(listeners);
                c.putClientProperty("FlatLaf.style", "background: #123456");
                assertThat(c.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThatThrownBy(() -> layer.apply(WorkspaceStyles.recipe(c, independent().workspace())))
                    .isInstanceOf(IllegalStateException.class);
            }
        });
    }

    @Test void chromeDelegateRebuildThenClearUsesNewChromeDefaults() throws Exception {
        edt(() -> {
            for (boolean isolated : List.of(false, true)) {
                new ThemeController(Theme.JASPER_LIGHT);
                var b = new JButton();
                var v = new JViewport();
                try (var bl = new WorkspaceStyleLayer(b); var vl = new WorkspaceStyleLayer(v)) {
                    var br = WorkspaceStyles.recipe(b, independent().workspace());
                    var vr = WorkspaceStyles.recipe(v, independent().workspace());
                    bl.apply(br); vl.apply(vr);
                    if (!isolated) { bl.beforeUiUpdate(); vl.beforeUiUpdate(); }
                    new ThemeController(Theme.DARK);
                    if (!isolated) {
                        bl.apply(br); vl.apply(vr);
                        assertThat(b.getClientProperty("Jasper.workspaceStyle")).isNull();
                    }
                    b.updateUI(); v.updateUI();
                    if (!isolated) { bl.afterUiUpdate(); vl.afterUiUpdate(); }
                    assertThat(b.getBackground()).isEqualTo(br.style().get("background"));
                    assertThat(v.getBackground()).isEqualTo(vr.direct().get("background"));
                    bl.clear(); vl.clear();
                    assertThat(b.getBackground()).isEqualTo(new JButton().getBackground());
                    assertThat(b.getBackground()).isInstanceOf(javax.swing.plaf.UIResource.class);
                    assertThat(v.getBackground()).isEqualTo(new JViewport().getBackground());
                }
            }
        });
    }

    @Test void equalRgbPluginSetterWhileClearedRemainsAnOverride() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var v = new JViewport();
            var recipe = new WorkspaceStyles.Recipe("Viewport", Map.of(), Map.of("background", Color.MAGENTA));
            try (var layer = new WorkspaceStyleLayer(v)) {
                layer.apply(recipe);
                layer.clear();
                var plugin = new Color(v.getBackground().getRGB());
                v.setBackground(plugin);
                layer.apply(recipe);
                assertThat(v.getBackground()).isSameAs(plugin);
            }
        });
    }

    @Test void isolatedRefreshWhileClearedDoesNotBecomeAPluginOverride() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            Object previous = UIManager.get("Viewport.background");
            try {
                var v = new JViewport();
                var recipe = new WorkspaceStyles.Recipe("Viewport", Map.of(), Map.of("background", Color.MAGENTA));
                try (var layer = new WorkspaceStyleLayer(v)) {
                    layer.apply(recipe);
                    layer.clear();
                    var chrome = new ColorUIResource(Color.CYAN);
                    UIManager.put("Viewport.background", chrome);
                    var sharedUi = v.getUI();
                    v.updateUI();
                    assertThat(v.getUI()).isSameAs(sharedUi);
                    assertThat(v.getBackground()).isSameAs(chrome);
                    layer.apply(recipe);
                    assertThat(v.getBackground()).isSameAs(Color.MAGENTA);
                    layer.clear();
                    assertThat(v.getBackground()).isSameAs(chrome);
                    var plugin = new ColorUIResource(Color.ORANGE);
                    v.setBackground(plugin);
                    v.updateUI();
                    layer.apply(recipe);
                    assertThat(v.getBackground()).isSameAs(plugin);
                    layer.clear();
                    // Even the exact chrome UIResource is a genuine override when set by the plugin.
                    v.setBackground(chrome);
                    layer.apply(recipe);
                    assertThat(v.getBackground()).isSameAs(chrome);
                    layer.clear();
                    var latestPlugin = new Color(0x123456);
                    v.setBackground(latestPlugin);
                    layer.apply(recipe);
                    assertThat(v.getBackground()).isSameAs(latestPlugin);
                }
                assertThat(v.getBackground()).isEqualTo(new Color(0x123456));
            } finally { UIManager.put("Viewport.background", previous); }
        });
    }

    @Test void referencesResolveAgainOnApplyAndUiUpdate() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var b = new JButton();
            String plugin = "foreground: $Label.foreground";
            b.putClientProperty("FlatLaf.style", plugin);
            try (var layer = new WorkspaceStyleLayer(b)) {
                var recipe = WorkspaceStyles.recipe(b, independent().workspace());
                layer.apply(recipe);
                assertThat(b.getForeground()).isEqualTo(UIManager.getColor("Label.foreground"));
                new ThemeController(Theme.DARK);
                b.updateUI();
                assertThat(b.getForeground()).isEqualTo(UIManager.getColor("Label.foreground"));
                layer.clear();
                assertThat(b.getClientProperty("FlatLaf.style")).isSameAs(plugin);
            }
        });
    }

    @Test void initiallyInvalidStyleIsPreservedAndGeneratedDefectsAreNotSuppressed() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            var b = new JButton();
            Object invalid = Map.of("background", "not a color");
            b.putClientProperty("FlatLaf.style", invalid);
            try (var layer = new WorkspaceStyleLayer(b)) {
                layer.apply(WorkspaceStyles.recipe(b, independent().workspace()));
                assertThat(b.getClientProperty("FlatLaf.style")).isSameAs(invalid);
                assertThat(b.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThatThrownBy(() -> layer.apply(new WorkspaceStyles.Recipe("Button",
                    Map.of("misspelledHostKey", Color.RED), Map.of())))
                    .isInstanceOf(IllegalArgumentException.class);
            }
        });
    }

    @Test void stringReferenceIsReevaluatedOnEveryApplyAndClassesOnRefresh() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            UIManager.put("[style]Button.plugin", "background: $Label.foreground");
            var b = new JButton();
            b.putClientProperty("FlatLaf.styleClass", "plugin");
            try (var layer = new WorkspaceStyleLayer(b)) {
                var recipe = WorkspaceStyles.recipe(b, independent().workspace());
                layer.apply(recipe);
                assertThat(b.getBackground()).isEqualTo(UIManager.getColor("Label.foreground"));
                layer.beforeUiUpdate();
                new ThemeController(Theme.DARK);
                b.updateUI();
                layer.afterUiUpdate();
                assertThat(b.getBackground()).isEqualTo(UIManager.getColor("Label.foreground"));
                b.putClientProperty("FlatLaf.style", "foreground: $Label.foreground");
                Object old = UIManager.get("Label.foreground");
                try {
                    UIManager.put("Label.foreground", Color.MAGENTA);
                    layer.apply(recipe);
                    assertThat(b.getForeground()).isEqualTo(Color.MAGENTA);
                } finally { UIManager.put("Label.foreground", old); }
            } finally { UIManager.put("[style]Button.plugin", null); }
        });
    }

    @Test void flatBaselineRestoresExactOrdinaryAndUiResourceValues() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            for (Color original : List.of(new Color(0x123456), new ColorUIResource(0x123456))) {
                var b = new JButton();
                b.setBackground(original);
                try (var layer = new WorkspaceStyleLayer(b)) {
                    layer.apply(WorkspaceStyles.recipe(b, independent().workspace()));
                    layer.clear();
                    assertThat(b.getBackground()).isSameAs(original);
                }
            }
        });
    }

    @Test void malformedAndUnsupportedPluginStylesReleaseHostAndCanRecover() throws Exception {
        edt(() -> {
            new ThemeController(Theme.JASPER_LIGHT);
            for (Object invalid : List.of("invalid style", Map.of("notAStyleKey", Color.RED), 42)) {
                var b = new JButton();
                Color baseline = b.getBackground();
                try (var layer = new WorkspaceStyleLayer(b)) {
                    var recipe = WorkspaceStyles.recipe(b, independent().workspace());
                    layer.apply(recipe);
                    b.putClientProperty("FlatLaf.style", invalid);
                    assertThat(b.getClientProperty("FlatLaf.style")).isSameAs(invalid);
                    assertThat(b.getClientProperty("Jasper.workspaceStyle")).isNull();
                    assertThat(b.getBackground()).isEqualTo(baseline);
                    b.putClientProperty("FlatLaf.style", null);
                    assertThat(b.getBackground()).isEqualTo(recipe.style().get("background"));
                }
            }
        });
    }
}
