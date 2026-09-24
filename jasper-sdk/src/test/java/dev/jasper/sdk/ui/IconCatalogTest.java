package dev.jasper.sdk.ui;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IconCatalogTest {
    @Test void retroChoicesMirrorEverySemanticNameIncludingTheChromeNames() {
        assertThat(Arrays.stream(OldGnomeIcon.values()).map(Enum::name))
            .containsExactlyElementsOf(Arrays.stream(IconName.values()).map(Enum::name).toList());
        assertThat(IconName.values()).hasSize(26)
            .contains(IconName.SPLIT, IconName.ZOOM, IconName.TERMINAL, IconName.SERVER);
    }
}
