package dev.jasper.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.jasper.sdk.terminal.SessionSpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Workspace tabs spec 13 and 14: how a plugin's tab comes back when Jasper restores its windows. */
class RestoreStateTest {
    @Test void aRestoreStateNamesItsRestorerAndHoldsAFewSmallStrings() {
        var values = new HashMap<String, String>(Map.of("path", "/work/notes.md"));
        var state = new RestoreState("document", values);
        values.put("later", "ignored");
        assertThat(state.values()).as("copied").containsExactly(Map.entry("path", "/work/notes.md"));
        assertThat(RestoreState.of("document", "path", "/a")).isEqualTo(new RestoreState("document", Map.of("path", "/a")));
        assertThat(new RestoreState("dev.jasper-x_1", Map.of()).kind()).isEqualTo("dev.jasper-x_1");
        for (String kind : new String[]{"", "has space", "colon:kind", "x".repeat(65)})
            assertThatIllegalArgumentException().as(kind).isThrownBy(() -> new RestoreState(kind, Map.of()));
        var many = new HashMap<String, String>();
        for (int i = 0; i <= RestoreState.MAX_ENTRIES; i++) many.put("k" + i, "");
        assertThatIllegalArgumentException().isThrownBy(() -> new RestoreState("many", many));
        assertThatIllegalArgumentException().isThrownBy(() -> new RestoreState("long", Map.of("v", "x".repeat(RestoreState.MAX_CHARACTERS))));
        assertThat(new RestoreState("long", Map.of("v", "x".repeat(RestoreState.MAX_CHARACTERS - 1))).values()).hasSize(1);
        // Jasper writes a lone surrogate into workspace.toml as U+FFFD, so two such keys could become one after a save.
        assertThatIllegalArgumentException().as("a lone high surrogate").isThrownBy(() -> new RestoreState("half", Map.of("k" + (char) 0xD83D, "")));
        assertThatIllegalArgumentException().as("a lone low surrogate").isThrownBy(() -> new RestoreState("half", Map.of((char) 0xDE00 + "k", "")));
        assertThat(new RestoreState("whole", Map.of("k" + (char) 0xD83D + (char) 0xDE00, "")).values()).as("a whole pair").hasSize(1);
        assertThatNullPointerException().isThrownBy(() -> new RestoreState("nulls", Collections.singletonMap("key", null)));
    }

    @Test void contentAndSessionsComeBackOnlyWithRestoreState() {
        var state = RestoreState.of("document", "path", "/a");
        assertThat(new dev.jasper.sdk.ui.ContentSpec("k", "Title").restore()).isEmpty();
        assertThat(new dev.jasper.sdk.ui.ContentSpec("k", "Title").withRestore(state)).isEqualTo(new dev.jasper.sdk.ui.ContentSpec("k", "Title", Optional.of(state)));
        assertThatNullPointerException().isThrownBy(() -> new dev.jasper.sdk.ui.ContentSpec("k", "Title", null));
        var session = SessionSpec.of("host", pending -> { });
        assertThat(session.restore()).isEmpty();
        assertThat(session.withRestore(state).restore()).contains(state);
        assertThat(session.withRestore(state).connector()).isSameAs(session.connector());
    }
}
