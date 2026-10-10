package dev.jasper.sdk.ui;

import java.awt.Color;
import java.util.EnumMap;
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

    @Test void hostsMustSupplyEverySpecializedPalette() throws Exception {
        for(var name:java.util.List.of("workspacePalette","editorPalette","syntaxPalette"))
            assertThat(Appearance.class.getMethod(name).isDefault()).isFalse();
    }
}
