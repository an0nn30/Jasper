package dev.jasper.editor.recent;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.testsupport.StubDocument;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReopenersTest {
    private final List<Path> local = new ArrayList<>();
    private final Reopeners reopeners = new Reopeners(file -> {
        local.add(file);
        return new StubDocument(LocalDocument.key(file), file.getFileName().toString(), file.toString(), null);
    });
    private int changes;

    ReopenersTest() { reopeners.onChanged(() -> changes++); }

    private static Function<String, Optional<Function<WindowOwner, EditorDocument>>> named(String name) {
        return key -> Optional.of(owner -> new StubDocument(key, name, key, null));
    }

    private String nameFor(String key) { return reopeners.resolve(key).orElseThrow().apply(null).name(); }

    @Test void localKeysReopenAsLocalFiles() {
        Path file = Path.of("notes", "today.txt").toAbsolutePath().normalize();
        EditorDocument document = reopeners.resolve(LocalDocument.key(file)).orElseThrow().apply(null);
        assertThat(local).containsExactly(file);
        assertThat(document.key()).isEqualTo(LocalDocument.key(file));
    }

    @Test void theLongestMatchingPrefixWins() {
        reopeners.register("remote:", named("short"));
        reopeners.register("remote:x", named("long"));
        assertThat(nameFor("remote:x/1")).isEqualTo("long");
        assertThat(nameFor("remote:y")).isEqualTo("short");
        assertThat(reopeners.resolve("other:1")).isEmpty();
    }

    @Test void aLaterRegistrationReplacesTheEarlierAndClosingRemovesOnlyItsOwn() {
        Subscription first = reopeners.register("stub:", named("first"));
        Subscription second = reopeners.register("stub:", named("second"));
        assertThat(nameFor("stub:1")).isEqualTo("second");
        first.close();
        assertThat(nameFor("stub:1")).isEqualTo("second");
        second.close();
        assertThat(reopeners.resolve("stub:1")).isEmpty();
        assertThat(changes).isEqualTo(3);
    }

    @Test void aResolverThatThrowsOrAnswersEmptyCannotReopen() {
        reopeners.register("bad:", key -> { throw new IllegalStateException("boom"); });
        reopeners.register("gone:", key -> Optional.empty());
        reopeners.register("null:", key -> null);
        assertThat(reopeners.resolve("bad:1")).isEmpty();
        assertThat(reopeners.resolve("gone:1")).isEmpty();
        assertThat(reopeners.resolve("null:1")).isEmpty();
    }

    @Test void theLocalPrefixAndBlankPrefixesCannotBeRegistered() {
        assertThatThrownBy(() -> reopeners.register("local:", named("x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reopeners.register(" ", named("x"))).isInstanceOf(IllegalArgumentException.class);
    }
}
