package dev.jasper.sdk.ui;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AppearanceContractTest {
    @Test void semanticColorsIncludeTheStatusBarSurface() {
        assertThat(SemanticColor.values()).hasSize(48);
        assertThat(SemanticColor.of("surface.status")).contains(SemanticColor.SURFACE_STATUS);
    }
}
