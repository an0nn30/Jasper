package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import java.awt.Color;
import java.util.EnumMap;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkspacePaletteTest {
    @Test void completeImmutableSyntaxAndValidation() {
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        assertThat(SyntaxRole.values()).hasSize(16);
        assertThatNullPointerException().isThrownBy(() -> new SyntaxPalette(styles));
        for (var role : SyntaxRole.values()) styles.put(role, new SyntaxStyle(Color.RED, true, false));
        var palette = new SyntaxPalette(styles);
        styles.clear();
        assertThat(palette.style(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(Color.RED, true, false));
        assertThatThrownBy(() -> palette.styles().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatNullPointerException().isThrownBy(() -> new SyntaxStyle(null, false, false));
        assertThatNullPointerException().isThrownBy(() -> palette.style(null));
        assertThatNullPointerException().isThrownBy(() -> new WorkspacePalette(null, Color.RED, Color.RED,
            Color.RED, Color.RED, Color.RED, Color.RED, Color.RED, Color.RED, Color.RED, Color.RED, Color.RED, Color.RED));
    }
    @Test void eachWorkspaceColorRejectsNullAndSyntaxCopiesItsInput() throws Exception {
        var components = WorkspacePalette.class.getRecordComponents();
        var types = java.util.Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
        var constructor = WorkspacePalette.class.getConstructor(types);
        for (int absent = 0; absent < components.length; absent++) {
            var colors = new Object[components.length]; java.util.Arrays.fill(colors, Color.BLACK); colors[absent] = null;
            assertThatThrownBy(() -> constructor.newInstance(colors)).hasCauseInstanceOf(NullPointerException.class);
        }
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        for (var role : SyntaxRole.values()) styles.put(role, new SyntaxStyle(Color.BLACK, false, false));
        styles.put(SyntaxRole.ERROR, null);
        assertThatNullPointerException().isThrownBy(() -> new SyntaxPalette(styles));
        assertThatNullPointerException().isThrownBy(() -> new SyntaxPalette(null));
    }

    @Test void componentLookupKeepsChromeOutsideWorkspaceAndFollowsReparenting() {
        Object old = UIManager.get("Jasper.workspace.background");
        try {
            UIManager.put("Jasper.workspace.background", Color.MAGENTA);
            JPanel workspace = new JPanel(), chrome = new JPanel();
            workspace.putClientProperty("Jasper.zone", "workspace");
            JLabel child = new JLabel(); workspace.add(child);
            assertThat(WorkspacePalette.current().background()).isEqualTo(Color.MAGENTA);
            assertThat(WorkspacePalette.current(child).background()).isEqualTo(Color.MAGENTA);
            chrome.add(child);
            assertThat(WorkspacePalette.current(child).background()).isEqualTo(UIManager.getColor("Panel.background"));
        } finally { UIManager.put("Jasper.workspace.background", old); }
    }
    @Test void chromeUsesSemanticColorsAndPopupBoundaries() {
        var oldSuccess = UIManager.get("Jasper.configSuccessForeground");
        var oldWorkspace = UIManager.get("Jasper.workspace.success");
        try {
            UIManager.put("Jasper.configSuccessForeground", Color.GREEN);
            UIManager.put("Jasper.workspace.success", Color.RED);
            JPanel root = new JPanel(); root.putClientProperty("Jasper.zone", "workspace");
            JToolTip tooltip = new JToolTip(); root.add(tooltip);
            assertThat(WorkspacePalette.current(tooltip).success()).isEqualTo(Color.GREEN);
            assertThat(WorkspacePalette.current(root).success()).isEqualTo(Color.RED);
        } finally {
            UIManager.put("Jasper.configSuccessForeground", oldSuccess);
            UIManager.put("Jasper.workspace.success", oldWorkspace);
        }
    }

    @Test void legacyCustomHostInheritsCompleteDefaults() {
        Appearance legacy = new Appearance() {
            public java.awt.Color color(SemanticColor role) { return java.awt.Color.BLACK; }
            public Variant variant() { return Variant.LIGHT; }
            public Subscription onChanged(java.util.function.Consumer<Variant> handler) { return () -> {}; }
            public Icon icon(String path) { return new ImageIcon(); }
        };
        assertThat(legacy.workspaceVariant()).isEqualTo(Variant.LIGHT);
        assertThat(legacy.workspacePalette().background()).isEqualTo(UIManager.getColor("Panel.background"));
        assertThat(legacy.syntaxPalette().styles()).hasSize(16);
        assertThat(legacy.syntaxPalette().style(SyntaxRole.COMMENT).italic()).isTrue();
        assertThatNullPointerException().isThrownBy(() -> legacy.onWorkspaceChanged(null));
    }
}
