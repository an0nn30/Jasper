package dev.jasper.app.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import dev.jasper.app.config.ToolbarMode;

class ConfigSnapshotBuilderTest {
@Test void snapshotBuilderPreservesEveryUnchangedComponent() {
    var original = ConfigSnapshot.defaults().toBuilder().columns(101).lines(37)
        .buddyEnabled(false).longCommandSeconds(23)
        .maxResults(17).backgroundEnabled(true).build();
    assertThat(original.toBuilder().build()).isEqualTo(original);
    var changed = original.toBuilder().toolbar(ToolbarMode.HIDDEN).build();
    assertThat(changed.toBuilder().toolbar(original.toolbar()).build()).isEqualTo(original);
    assertThatThrownBy(() -> original.toBuilder().maxResults(21).build())
        .isInstanceOf(IllegalArgumentException.class);
}

    @Test void terminalColorsRoundTripThroughTheBuilderAndDefaultToMatch() {
        assertThat(ConfigSnapshot.defaults().terminalColors()).isEqualTo(TerminalColors.MATCH);
        var dark = ConfigSnapshot.builder().terminalColors(TerminalColors.DARK).build();
        assertThat(dark.terminalColors()).isEqualTo(TerminalColors.DARK);
        assertThat(dark.toBuilder().tabHeight(40).build().terminalColors()).isEqualTo(TerminalColors.DARK);
    }
}
