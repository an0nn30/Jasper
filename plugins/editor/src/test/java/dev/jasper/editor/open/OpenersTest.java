package dev.jasper.editor.open;

import dev.jasper.editor.api.OpenRequest;
import dev.jasper.sdk.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OpenersTest {
    private final List<RuntimeException> failures = new ArrayList<>();
    private final Openers openers = new Openers(failures::add);
    private final List<String> asked = new ArrayList<>();
    private static final OpenRequest REQUEST = new OpenRequest(null, Optional.empty(), Optional.of("remote:x"));

    @Test void handlersRunInRegistrationOrderAndTheFirstToAnswerTrueWins() {
        openers.register(request -> { asked.add("first"); return false; });
        openers.register(request -> { asked.add("second"); return true; });
        openers.register(request -> { asked.add("third"); return true; });
        assertThat(openers.handle(REQUEST)).isTrue();
        assertThat(asked).containsExactly("first", "second");
    }

    @Test void aHandlerThatThrowsCountsAsFalseAndIsReported() {
        var boom = new IllegalStateException("boom");
        openers.register(request -> { throw boom; });
        openers.register(request -> { asked.add("next"); return true; });
        assertThat(openers.handle(REQUEST)).isTrue();
        assertThat(asked).containsExactly("next");
        assertThat(failures).containsExactly(boom);
    }

    @Test void handlersThatAnswerNormallyReportNothing() {
        openers.register(request -> false);
        assertThat(openers.handle(REQUEST)).isFalse();
        assertThat(failures).isEmpty();
    }

    @Test void withNoHandlerOrAfterUnsubscribingNothingIsHandled() {
        assertThat(openers.handle(REQUEST)).isFalse();
        Subscription registration = openers.register(request -> true);
        assertThat(openers.handle(REQUEST)).isTrue();
        registration.close();
        assertThat(openers.handle(REQUEST)).isFalse();
    }
}
