package dev.jasper.sdk.notifications;

import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotificationsValuesTest {
    /** A group that keeps what was posted. */
    private static final class Kept implements NotificationGroup {
        final List<NotificationSpec> posted = new ArrayList<>();
        @Override public String id() { return "dev.example.tool"; }
        @Override public NotificationHandle post(NotificationSpec spec) {
            posted.add(spec);
            return new NotificationHandle() {
                @Override public void expire() { }
                @Override public boolean expired() { return false; }
            };
        }
    }

    @Test void aSpecNeedsATitleThatShowsSomething() {
        assertThat(NotificationSpec.of(Severity.INFO, "Saved").title()).isEqualTo("Saved");
        for (String blank : List.of("", "  ", "\n\t", "\u00A0\u2007", "\u200B\u2060", "\u202E\u2066", "\uFEFF"))
            assertThatIllegalArgumentException().as("%s", blank.codePoints().boxed().toList())
                .isThrownBy(() -> NotificationSpec.of(Severity.INFO, blank));
        assertThatNullPointerException().isThrownBy(() -> NotificationSpec.of(Severity.INFO, null));
        assertThat(NotificationSpec.of(Severity.INFO, "t".repeat(200)).title()).hasSize(200);
        assertThatIllegalArgumentException().isThrownBy(() -> NotificationSpec.of(Severity.INFO, "t".repeat(201)));
    }

    @Test void contentActionsAndTheOptionalPartsAreBounded() {
        var spec = NotificationSpec.of(Severity.WARNING, "Sync").withContent("c".repeat(4_000));
        assertThat(spec.content()).hasSize(4_000);
        assertThatIllegalArgumentException().isThrownBy(() -> spec.withContent("c".repeat(4_001)));
        var eight = NotificationSpec.of(Severity.INFO, "t");
        for (int i = 0; i < 8; i++) eight = eight.withAction(NotificationAction.of("Action " + i, () -> { }));
        var full = eight;
        assertThatIllegalArgumentException().isThrownBy(() -> full.withAction(NotificationAction.of("Ninth", () -> { })));
        assertThatIllegalArgumentException().as("Show more… takes the eighth place when there is a cause")
            .isThrownBy(() -> full.withCause(new IllegalStateException("x")));
        assertThatIllegalArgumentException().isThrownBy(() -> NotificationAction.of("\u00A0", () -> { }));
        assertThatIllegalArgumentException().isThrownBy(() -> NotificationAction.of("l".repeat(41), () -> { }));
        assertThat(new NotificationAction("Keep", () -> { }, false).expires()).isFalse();
        assertThat(NotificationAction.of("Go", () -> { }).expires()).isTrue();
        Runnable body = () -> { };
        var cause = new IllegalStateException("x");
        var icon = "file";
        var rich = NotificationSpec.of(Severity.ERROR, "t").withActivate(body).withCause(cause).withIcon(icon);
        assertThat(rich.activate()).containsSame(body);
        assertThat(rich.cause()).containsSame(cause);
        assertThat(rich.icon()).containsSame(icon);
        assertThat(rich.window()).isEmpty();
    }

    @Test void groupSpecsAreNamespacedAndNamedOnOneLine() {
        assertThat(new GroupSpec("dev.example.tool.sync", "Tool sync", DisplayMode.HISTORY).id()).isEqualTo("dev.example.tool.sync");
        for (String bad : List.of("tool", "Dev.example.x", "dev..x", "dev.x.", ".dev.x", "dev.x y", "d." + "x".repeat(159)))
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> new GroupSpec(bad, "Name", DisplayMode.CARD));
        for (String name : List.of(" ", "\u200B", "Two\nlines", "n".repeat(81)))
            assertThatIllegalArgumentException().as(name).isThrownBy(() -> new GroupSpec("dev.example.x", name, DisplayMode.CARD));
        assertThatNullPointerException().isThrownBy(() -> new GroupSpec("dev.example.x", "X", null));
    }

    @Test void theShortcutsPostTheirSeverityAndAnErrorsCauseIsSummarized() {
        var group = new Kept();
        group.info("Saved", "Three hosts");
        group.warning("Slow", "");
        group.error("Broke", "detail");
        group.error("Could not copy the address", new IllegalStateException("clipboard busy\nsecond line"));
        group.error("Could not save", "hosts.toml is read-only", new java.io.UncheckedIOException(new java.io.IOException("read-only")));
        var wrapped = new java.util.concurrent.CompletionException(new java.io.IOException("connection reset"));
        group.error("Could not connect", wrapped);
        assertThat(group.posted).extracting(NotificationSpec::severity, NotificationSpec::title, NotificationSpec::content)
            .containsExactly(tuple(Severity.INFO, "Saved", "Three hosts"), tuple(Severity.WARNING, "Slow", ""),
                tuple(Severity.ERROR, "Broke", "detail"),
                tuple(Severity.ERROR, "Could not copy the address", "IllegalStateException: clipboard busy"),
                tuple(Severity.ERROR, "Could not save", "hosts.toml is read-only"),
                tuple(Severity.ERROR, "Could not connect", "IOException: connection reset"));
        assertThat(group.posted.get(2).cause()).isEmpty();
        assertThat(group.posted.get(3).cause()).isPresent();
        assertThat(group.posted.get(4).cause()).isPresent();
        assertThat(group.posted.get(5).cause()).as("the cause is kept as given").containsSame(wrapped);
    }

    @Test void aCauseThatCannotDescribeItselfIsSummarizedByItsNameAndLongTextIsCut() {
        var group = new Kept();
        group.error("Broke", new RuntimeException() { @Override public String getMessage() { throw new IllegalStateException("no"); } });
        group.error("Broke", new IllegalStateException("m".repeat(500)));
        group.error("Broke", "c".repeat(5_000), new IllegalStateException("x"));
        group.info("Long", "i".repeat(5_000));
        assertThat(group.posted.get(0).content()).isEqualTo(group.posted.get(0).cause().orElseThrow().getClass().getName());
        assertThat(group.posted.get(1).content()).hasSize(300).startsWith("IllegalStateException: mmm").endsWith("…");
        assertThat(group.posted.get(2).content()).as("a shortcut cuts what it is given").hasSize(4_000).endsWith("…");
        assertThat(group.posted.get(3).content()).hasSize(4_000);
    }
}
