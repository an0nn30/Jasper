package dev.jasper.app.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import dev.jasper.app.config.ToolbarMode;

class ConfigSnapshotBuilderTest {
@Test void snapshotBuilderPreservesEveryUnchangedComponent() {
    var original = ConfigSnapshot.defaults().toBuilder().columns(101).lines(37)
        .buddyEnabled(false).longCommandSeconds(23)
        .visibleRows(17).backgroundEnabled(true).build();
    assertThat(original.toBuilder().build()).isEqualTo(original);
    var changed = original.toBuilder().toolbar(ToolbarMode.HIDDEN).build();
    assertThat(changed.toBuilder().toolbar(original.toolbar()).build()).isEqualTo(original);
    assertThatThrownBy(() -> original.toBuilder().visibleRows(21).build())
        .isInstanceOf(IllegalArgumentException.class);
}

    @Test void appearanceAndSchemeRoundTripThroughTheBuilderAndDefaultToSystemAndMatch() {
        assertThat(ConfigSnapshot.defaults().appearance()).isEqualTo(AppearanceMode.SYSTEM);
        assertThat(ConfigSnapshot.defaults().terminalScheme()).isEqualTo(TerminalSchemeChoice.MATCH);
        var chosen = ConfigSnapshot.builder().appearance(AppearanceMode.LIGHT).terminalScheme(TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow()).build();
        var copied = chosen.toBuilder().tabHeight(40).build();
        assertThat(copied.appearance()).isEqualTo(AppearanceMode.LIGHT);
        assertThat(copied.terminalScheme()).isEqualTo(TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow());
    }
}
