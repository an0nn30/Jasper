package dev.jasper.editor.open;

import dev.jasper.editor.api.OpenRequest;
import dev.jasper.sdk.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OpenersTest {
    private final Openers openers = new Openers();
    private final List<String> asked = new ArrayList<>();
    private static final OpenRequest REQUEST = new OpenRequest(null, Optional.empty(), Optional.of("remote:x"));

    @Test void handlersRunInRegistrationOrderAndTheFirstToAnswerTrueWins() {
        openers.register(request -> { asked.add("first"); return false; });
        openers.register(request -> { asked.add("second"); return true; });
        openers.register(request -> { asked.add("third"); return true; });
        assertThat(openers.handle(REQUEST)).isTrue();
        assertThat(asked).containsExactly("first", "second");
    }

    @Test void aHandlerThatThrowsCountsAsFalse() {
        openers.register(request -> { throw new IllegalStateException("boom"); });
        openers.register(request -> { asked.add("next"); return true; });
        assertThat(openers.handle(REQUEST)).isTrue();
        assertThat(asked).containsExactly("next");
    }

    @Test void withNoHandlerOrAfterUnsubscribingNothingIsHandled() {
        assertThat(openers.handle(REQUEST)).isFalse();
        Subscription registration = openers.register(request -> true);
        assertThat(openers.handle(REQUEST)).isTrue();
        registration.close();
        assertThat(openers.handle(REQUEST)).isFalse();
    }
}
