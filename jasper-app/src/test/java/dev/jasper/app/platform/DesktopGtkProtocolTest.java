package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class DesktopGtkProtocolTest {
    @Test void incompletePairDoesNotBecomeAValidWindow() {
        String wire = "{\"v\":1,\"kind\":\"palette\",\"generation\":2,\"source\":\"gtk4\",\"appearance\":\"dark\",\"implementation\":\"libadwaita\",\"libraryVersion\":\"1.6\",\"window.bg\":\"0,0,0,1\",\"reason\":\"\"}";
        var message = (DesktopProtocol.PaletteMessage)
            DesktopProtocol.decode(wire.getBytes(StandardCharsets.UTF_8));
        assertThat(message.value().colors()).doesNotContainKey(DesktopObservation.Pair.WINDOW);
    }

    @Test void realSentinelColorsSurviveTheWire() {
        var value = palette(8, "dark", "\"window.bg\":\"1,0,1,1\",\"window.fg\":\"0,1,1,1\",");
        assertThat(value.colors().get(DesktopObservation.Pair.WINDOW).background())
            .isEqualTo(new DesktopObservation.Rgba(1, 0, 1, 1));
        assertThat(value.colors().get(DesktopObservation.Pair.WINDOW).foreground())
            .isEqualTo(new DesktopObservation.Rgba(0, 1, 1, 1));
    }

    @Test void missingModernVariablesRemainAbsent() {
        var value = palette(9, "dark", "\"window.bg\":\"0.1,0.2,0.3,1\",\"window.fg\":\"1,1,1,1\",");
        assertThat(value.colors()).containsOnlyKeys(DesktopObservation.Pair.WINDOW);
    }

    @Test void sequentialPreviewVariantsRetainTheirRequestGeneration() {
        var dark = palette(17, "dark", "");
        var light = palette(18, "light", "");
        assertThat(dark.generation()).isEqualTo(17);
        assertThat(dark.appearance()).isEqualTo(DesktopObservation.Polarity.DARK);
        assertThat(light.generation()).isEqualTo(18);
        assertThat(light.appearance()).isEqualTo(DesktopObservation.Polarity.LIGHT);
    }

    private static DesktopObservation.Palette palette(long generation, String appearance, String colors) {
        String wire = "{\"v\":1,\"kind\":\"palette\",\"generation\":" + generation
            + ",\"source\":\"gtk4\",\"appearance\":\"" + appearance
            + "\",\"implementation\":\"libadwaita\",\"libraryVersion\":\"1.9\"," + colors + "\"reason\":\"\"}";
        return ((DesktopProtocol.PaletteMessage) DesktopProtocol.decode(wire.getBytes(StandardCharsets.UTF_8))).value();
    }
}
