package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class WorkspaceValuesTest {
    @Test void privateDefaultsLeaveChromeUntouchedAndSeparateConsoleFromSurface() {
        var chromeLaf = UIManager.getLookAndFeel();
        var chromeBackground = UIManager.getColor("Panel.background");
        var before = colors();
        var base = Theme.DARCULA.source().document();
        var seeds = new java.util.HashMap<>(base.seeds());
        seeds.put(Seed.BACKGROUND, new Color(0x010203));
        var ui = new java.util.LinkedHashMap<>(base.ui());
        ui.put("Panel.background", "#123456");
        var document = new ThemeDocument(base.origin(), base.name(), base.dark(), base.parent(), 0,
            base.uiComplete(), base.metadata(), seeds, base.ansi(), Map.of(), base.syntax(), ui, base.icons());
        var original = ThemeGenerator.generate(document);
        var workspace = WorkspaceValues.resolve(document, original);
        assertThat(UIManager.getLookAndFeel()).isSameAs(chromeLaf);
        assertThat(UIManager.getColor("Panel.background")).isEqualTo(chromeBackground);
        assertThat(colors()).isEqualTo(before);
        assertThat(workspace.tokens().get(Token.SURFACE)).isEqualTo(new Color(0x123456));
        assertThat(workspace.terminal().background()).isEqualTo(new Color(0x010203));
        assertThat(workspace.uiKeys()).isEqualTo(original.uiKeys());
    }

    @Test void aColourOnAKeyFlatLafBuildsLazilyCreatesNothingAgainstTheInstalledLook() throws Exception {
        // Nord sets TableHeader.cellBorder to a colour. FlatLaf's border for that key reads TableHeader.cellMargins from
        // UIManager when built, which before any theme is installed (start-up) is missing.
        var original = UIManager.getLookAndFeel();
        var severe = new java.util.ArrayList<java.util.logging.LogRecord>();
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) {
                if (record.getLevel().intValue() >= java.util.logging.Level.SEVERE.intValue()) severe.add(record);
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        var flatLaf = java.util.logging.Logger.getLogger("com.formdev.flatlaf");
        flatLaf.addHandler(handler);
        try {
            UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel());
            var base = ThemeTestSupport.named("Nord").source().document();
            assertThat(base.ui()).containsEntry("TableHeader.cellBorder", "#434c5e");
            // A copy, so that no earlier test's cached generation hides the lookup.
            var ui = new java.util.LinkedHashMap<>(base.ui());
            var document = new ThemeDocument(base.origin(), base.name(), base.dark(), base.parent(), 0,
                base.uiComplete(), base.metadata(), base.seeds(), base.ansi(), Map.of(), base.syntax(), ui, base.icons());
            var values = WorkspaceValues.resolve(document, ThemeGenerator.generate(document));
            assertThat(severe).extracting(java.util.logging.LogRecord::getMessage).isEmpty();
            assertThat(values.tokens()).isNotEmpty();
        } finally {
            flatLaf.removeHandler(handler);
            UIManager.setLookAndFeel(original);
        }
    }

    @Test void explicitAlphaTokensAndSyntaxSurviveEffectiveDefaults() {
        var base = Theme.DARCULA.source().document();
        var explicit = new Color(1, 2, 3, 100);
        var style = new SyntaxStyle(new Color(0x987654), true, false);
        var document = new ThemeDocument(base.origin(), base.name(), base.dark(), base.parent(), 0,
            base.uiComplete(), base.metadata(), base.seeds(), base.ansi(),
            Map.of(Token.SURFACE, explicit, Token.EDITOR_BACKGROUND, Color.WHITE),
            Map.of(SyntaxRole.COMMENT, style), base.ui(), base.icons());
        var values = WorkspaceValues.resolve(document, ThemeGenerator.generate(document));
        assertThat(values.tokens()).containsEntry(Token.SURFACE, explicit).containsEntry(Token.EDITOR_BACKGROUND, Color.WHITE);
        assertThat(values.syntax()).containsEntry(SyntaxRole.COMMENT, style);
        assertThat(ChromeKeys.contrast(values.syntax().get(SyntaxRole.KEYWORD).color(), Color.WHITE)).isGreaterThanOrEqualTo(4.5);
    }

    @Test void missingEffectiveKeysKeepGeneratedValuesAndPresentColorsKeepAlpha() {
        var alpha = new Color(7, 8, 9, 99);
        var original = ThemeGenerator.generate(Theme.DARK.source().document());
        var values = WorkspaceTokenKeys.resolve(original.tokens(), key -> key.equals("Panel.background") ? alpha : null);
        assertThat(values).containsEntry(Token.SURFACE, alpha).containsEntry(Token.TEXT, original.tokens().get(Token.TEXT));
        assertThat(WorkspaceTokenKeys.KEYS).hasSize(Token.values().length);
    }

    @Test void nativeValuesReuseTheCacheAndCompleteValuesMatchInstalledColors() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            var nativeValues = ThemeManager.generated(Theme.DARK);
            assertThat(ThemeManager.generated(Theme.DARK)).isSameAs(nativeValues);
            var values = ThemeManager.generated(Theme.DARCULA);
            assertThat(ThemeManager.install(Theme.DARCULA)).isTrue();
            var mismatches = new java.util.ArrayList<String>();
            WorkspaceTokenKeys.KEYS.forEach((token, key) -> {
                var color = UIManager.getLookAndFeelDefaults().getColor(key);
                if (color != null && !color.equals(values.tokens().get(token))) mismatches.add(key + ": " + values.tokens().get(token) + " != " + color);
            });
            assertThat(mismatches).isEmpty();
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void obsoleteCatalogValuesAreRemovedButActiveValuesRemainCached() {
        var theme = Theme.of("temporary", new Theme.UserFile(java.nio.file.Path.of("temporary.toml"), Theme.DARK.source().document()));
        var first = ThemeManager.generated(theme);
        ThemeManager.retainGenerated(java.util.List.of(theme));
        assertThat(ThemeManager.generated(theme)).isSameAs(first);
        ThemeManager.retainGenerated(java.util.List.of());
        assertThat(ThemeManager.generated(theme)).isNotSameAs(first);
    }

    @Test void generationAndMaterializationRejectWorkerThreads() throws Exception {
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try {
                assertThatThrownBy(() -> ThemeManager.generated(Theme.DARK)).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> WorkspaceValues.resolve(Theme.DARK.source().document(),
                    ThemeGenerator.generate(Theme.DARK.source().document()))).isInstanceOf(IllegalStateException.class);
            } catch (Throwable problem) { failure.set(problem); }
        });
        thread.start();
        thread.join();
        assertThat(failure.get()).isNull();
    }

    private static Map<String, Color> colors() {
        var colors = new TreeMap<String, Color>();
        var defaults = UIManager.getLookAndFeelDefaults();
        for (var key : defaults.keySet().toArray())
            if (defaults.get(key) instanceof Color color) colors.put(key.toString(), color);
        return colors;
    }
}
