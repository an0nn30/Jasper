package dev.jasper.terminal.view;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static dev.jasper.terminal.view.TerminalLink.Gesture.OPEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TerminalLinkTest {
    @Test
    void aTerminalLinkNeedsAnAddressAGestureAndAPossiblyEmptyTarget() {
        assertThat(new TerminalLink("https://a.example", OPEN, Optional.of("docs")).target()).contains("docs");
        assertThat(new TerminalLink("https://a.example", OPEN, Optional.empty()).target()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> new TerminalLink(null, OPEN, Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TerminalLink("", OPEN, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new TerminalLink("https://a.example", null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new TerminalLink("https://a.example", OPEN, null));
    }
}
