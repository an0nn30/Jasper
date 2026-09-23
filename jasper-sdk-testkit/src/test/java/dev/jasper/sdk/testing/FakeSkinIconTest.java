package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.OldGnomeIcon;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class FakeSkinIconTest {
    private static final PluginInfo INFO = new PluginInfo("dev.x.icons", "Icons", "1.0", Set.of());
    private static final String RESOURCE = "dev/jasper/sdk/testing/FakeSkinIconTest.class";

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void validatesAndRecordsBothChoices(boolean retro) {
        try (var host = new FakePluginHost()) {
            host.setRetroIcons(retro);
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            assertThat(context.appearance().variant()).isEqualTo(retro ? Variant.LIGHT : Variant.DARK);
            for (var choice : OldGnomeIcon.values()) {
                var icon = (FakeSkinIcon) context.appearance().icon(RESOURCE, choice);
                assertThat(icon.modernSvgResourcePath()).isEqualTo(RESOURCE);
                assertThat(icon.retroIcon()).isEqualTo(choice);
                assertThat(icon.retro()).isEqualTo(retro);
                assertThat(icon.getIconWidth()).isEqualTo(16);
                assertThat(icon.getIconHeight()).isEqualTo(16);
            }
            assertThatIllegalArgumentException().isThrownBy(() -> context.appearance().icon("missing.svg", OldGnomeIcon.LOCK));
            assertThatIllegalArgumentException().isThrownBy(() -> context.appearance().icon(null, OldGnomeIcon.LOCK));
            assertThatNullPointerException().isThrownBy(() -> context.appearance().icon(RESOURCE, null));
            assertThat(context.appearance().icon(RESOURCE)).isNotInstanceOf(FakeSkinIcon.class);
            assertThatIllegalStateException().isThrownBy(() -> host.setRetroIcons(!retro));
            if (retro) {
                assertThatIllegalArgumentException().isThrownBy(() -> host.setVariant(Variant.DARK));
                assertThat(context.appearance().variant()).isEqualTo(Variant.LIGHT);
            } else {
                host.setVariant(Variant.LIGHT);
                assertThat(context.appearance().variant()).isEqualTo(Variant.LIGHT);
            }
        }
    }

    @Test void failedStartAlsoFreezesStyle() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin -> { throw new IllegalStateException("fail"); });
            assertThat(host.failures()).isNotEmpty();
            assertThatIllegalStateException().isThrownBy(() -> host.setRetroIcons(true));
        }
    }
}
