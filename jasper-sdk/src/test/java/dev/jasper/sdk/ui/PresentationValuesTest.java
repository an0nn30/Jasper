package dev.jasper.sdk.ui;

import dev.jasper.sdk.JasperSdk;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** SDK 0.18.2: the facts about the look a plugin reads through its appearance. */
class PresentationValuesTest {
    private static Presentation classic(Optional<String> uiFace, int bodySize, int toolbarHeight) {
        return new Presentation(true, Presentation.Icons.CLASSIC, Presentation.ToolWindows.STRIPES, Presentation.TitleBar.NATIVE,
            Presentation.Menus.WINDOW, Presentation.ToolbarSearch.FIELD, uiFace, Optional.of("IBM Plex Mono"), bodySize, toolbarHeight);
    }

    @Test void aPresentationKeepsItsFactsAndRefusesMissingOnes() {
        var classic = classic(Optional.of("IBM Plex Sans"), 12, 28);
        assertThat(classic.classic()).isTrue();
        assertThat(classic.icons()).isEqualTo(Presentation.Icons.CLASSIC);
        assertThat(classic.toolWindows()).isEqualTo(Presentation.ToolWindows.STRIPES);
        assertThat(classic.titleBar()).isEqualTo(Presentation.TitleBar.NATIVE);
        assertThat(classic.menus()).isEqualTo(Presentation.Menus.WINDOW);
        assertThat(classic.toolbarSearch()).isEqualTo(Presentation.ToolbarSearch.FIELD);
        assertThat(classic.uiFace()).contains("IBM Plex Sans");
        assertThat(classic.dataFace()).contains("IBM Plex Mono");
        assertThat(classic.bodySize()).isEqualTo(12);
        assertThat(classic.toolbarHeight()).isEqualTo(28);
        assertThatNullPointerException().isThrownBy(() -> new Presentation(false, null, Presentation.ToolWindows.RAILS,
            Presentation.TitleBar.UNIFIED, Presentation.Menus.TITLE_BAR, Presentation.ToolbarSearch.FIELD, Optional.empty(), Optional.empty(), 13, 30));
        assertThatIllegalArgumentException().isThrownBy(() -> classic(Optional.of(" "), 12, 28));
    }

    @Test void theBodySizeAndToolbarHeightHaveTheirRanges() {
        assertThatIllegalArgumentException().isThrownBy(() -> classic(Optional.empty(), 5, 28)).withMessage("bodySize: 5 is outside 6-96");
        assertThatIllegalArgumentException().isThrownBy(() -> classic(Optional.empty(), 97, 28)).withMessage("bodySize: 97 is outside 6-96");
        assertThatIllegalArgumentException().isThrownBy(() -> classic(Optional.empty(), 12, 27)).withMessage("toolbarHeight: 27 is outside 28-64");
        assertThatIllegalArgumentException().isThrownBy(() -> classic(Optional.empty(), 12, 65)).withMessage("toolbarHeight: 65 is outside 28-64");
        assertThat(classic(Optional.empty(), 6, 28).bodySize()).isEqualTo(6);
        assertThat(classic(Optional.empty(), 96, 64).bodySize()).isEqualTo(96);
        assertThat(classic(Optional.empty(), 12, 28).toolbarHeight()).isEqualTo(28);
        assertThat(classic(Optional.empty(), 12, 64).toolbarHeight()).isEqualTo(64);
    }

    @Test void theVersionIsZeroTwentyOne() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.23.0");
    }
}
