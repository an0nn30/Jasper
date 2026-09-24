package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FreedesktopNamesTest {
    @Test void everyBundledNameHasFreedesktopCandidates() {
        assertThat(FreedesktopNames.NAMES).containsAll(GnomeIcons.NAMES).containsAll(OldGnomeCatalog.NAMES);
        for (String name : FreedesktopNames.NAMES) assertThat(FreedesktopNames.of(name)).as(name).isNotEmpty();
        assertThat(FreedesktopNames.of("SAVE")).containsExactly("document-save");
        assertThat(FreedesktopNames.of("square-plus")).startsWith("tab-new");
        assertThatIllegalArgumentException().isThrownBy(() -> FreedesktopNames.of("nope"));
    }
}
