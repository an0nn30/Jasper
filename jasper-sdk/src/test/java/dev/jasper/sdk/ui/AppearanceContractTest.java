package dev.jasper.sdk.ui;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AppearanceContractTest {
    @Test void semanticColorsIncludeTheStatusBarSurface() {
        assertThat(SemanticColor.values()).hasSize(54);
        assertThat(SemanticColor.of("border.identity")).contains(SemanticColor.BORDER_IDENTITY);
        assertThat(SemanticColor.of("surface.status")).contains(SemanticColor.SURFACE_STATUS);
    }
}
