package dev.jasper.sdk.ui;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AppearanceContractTest {
    @Test void semanticColorsIncludeTheStatusBarSurface() {
        assertThat(SemanticColor.values()).hasSize(70);
        assertThat(SemanticColor.of("text.onSelection")).contains(SemanticColor.TEXT_ONSELECTION);
        assertThat(SemanticColor.of("tab.indicator.inactive")).contains(SemanticColor.TAB_INDICATOR_INACTIVE);
        assertThat(SemanticColor.of("border.identity")).contains(SemanticColor.BORDER_IDENTITY);
        assertThat(SemanticColor.of("surface.status")).contains(SemanticColor.SURFACE_STATUS);
    }
}
