package dev.jasper.sdk.ui;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShortcutContextTest {
    @Test void aPanelContextNamesAnIdAndTheOthersDoNot() {
        assertThat(ShortcutContext.global().kind()).isEqualTo(ShortcutContext.Kind.GLOBAL);
        assertThat(ShortcutContext.terminal().id()).isEmpty();
        assertThat(ShortcutContext.content().kind()).isEqualTo(ShortcutContext.Kind.CONTENT);
        assertThat(ShortcutContext.panel("dev.x.hosts").id()).hasValue("dev.x.hosts");
        assertThatIllegalArgumentException().isThrownBy(() -> ShortcutContext.panel(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ShortcutContext(ShortcutContext.Kind.PANEL, Optional.of(" ")));
        assertThatIllegalArgumentException().isThrownBy(() -> new ShortcutContext(ShortcutContext.Kind.GLOBAL, Optional.of("dev.x")));
        assertThatNullPointerException().isThrownBy(() -> new ShortcutContext(null, Optional.empty()));
    }
}
