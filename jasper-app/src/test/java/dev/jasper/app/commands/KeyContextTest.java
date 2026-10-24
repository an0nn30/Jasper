package dev.jasper.app.commands;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KeyContextTest {
    @Test void ownedContextsNameTheirOwnerAndTheOthersDoNot() {
        assertThat(KeyContext.content("dev.x").owner()).isEqualTo("dev.x");
        assertThat(KeyContext.panel("dev.x.hosts")).isEqualTo(new KeyContext(KeyContext.Kind.PANEL, "dev.x.hosts"));
        assertThatIllegalArgumentException().isThrownBy(() -> KeyContext.panel(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new KeyContext(KeyContext.Kind.GLOBAL, "dev.x"));
        assertThatNullPointerException().isThrownBy(() -> KeyContext.panel(null));
    }

    @Test void specificityAndConflicts() {
        assertThat(KeyContext.GLOBAL.level()).isZero();
        assertThat(KeyContext.TERMINAL.level()).isEqualTo(1);
        assertThat(KeyContext.content("dev.x").level()).isEqualTo(2);
        assertThat(KeyContext.GLOBAL.conflictsWith(KeyContext.TERMINAL)).as("both apply in a terminal pane").isTrue();
        assertThat(KeyContext.panel("dev.x.a").conflictsWith(KeyContext.panel("dev.x.a"))).isTrue();
        assertThat(KeyContext.panel("dev.x.a").conflictsWith(KeyContext.panel("dev.x.b"))).isFalse();
        assertThat(KeyContext.content("dev.x").conflictsWith(KeyContext.GLOBAL)).as("a panel or tab may override").isFalse();
        assertThat(KeyContext.TERMINAL.conflictsWith(KeyContext.panel("dev.x.w"))).isFalse();
    }
}
