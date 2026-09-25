package dev.jasper.sdk.ui;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IconCatalogTest {
    @Test void everyRetroChoiceIsASemanticNameAndTheChromeNamesExistInBoth() {
        var names = Arrays.stream(IconName.values()).map(Enum::name).toList();
        assertThat(Arrays.stream(OldGnomeIcon.values()).map(Enum::name)).allMatch(names::contains);
        assertThat(IconName.values()).hasSize(34)
            .contains(IconName.SPLIT, IconName.ZOOM, IconName.TERMINAL, IconName.SERVER);
        assertThat(OldGnomeIcon.values())
            .contains(OldGnomeIcon.SPLIT, OldGnomeIcon.ZOOM, OldGnomeIcon.TERMINAL, OldGnomeIcon.SERVER);
    }
}
