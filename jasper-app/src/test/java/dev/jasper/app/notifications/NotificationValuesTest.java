package dev.jasper.app.notifications;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotificationValuesTest {
    private static NotificationRequest titled(String title) {
        return NotificationRequest.of("jasper.general", Severity.INFO, title);
    }

    @Test void titlesAreOnePlainTrimmedLine() {
        assertThat(titled("  Could not\r\n start\tthe   shell \u0007 ").title()).isEqualTo("Could not start the shell");
        assertThat(titled("<html><b>bold</b>").title()).as("markup is kept as text").isEqualTo("<html><b>bold</b>");
    }

    @Test void longTitlesAreCutWithAnEllipsisWithoutSplittingAPair() {
        assertThat(titled("a".repeat(250)).title()).hasSize(NotificationRequest.MAX_TITLE).endsWith("…");
        assertThat(titled("a".repeat(198) + "😀" + "bb").title()).isEqualTo("a".repeat(198) + "…");
    }

    @Test void contentKeepsLinesAndIsBounded() {
        assertThat(titled("t").withContent("first\r\nsecond\rthird\tend\u0000").content()).isEqualTo("first\nsecond\nthird end");
        assertThat(titled("t").withContent("x".repeat(5000)).content())
            .hasSize(NotificationRequest.MAX_CONTENT).endsWith("…");
        assertThat(titled("t").withContent("<html><i>literal</i>").content()).isEqualTo("<html><i>literal</i>");
    }

    @Test void requestsRejectWhatCannotBeShown() {
        assertThatThrownBy(() -> titled(" \n\t")).isInstanceOf(IllegalArgumentException.class);
        var eight = titled("t");
        for (int i = 0; i < NotificationRequest.MAX_ACTIONS; i++) eight = eight.withAction(NotificationAction.of("Action " + i, () -> {}));
        var full = eight;
        assertThatThrownBy(() -> full.withAction(NotificationAction.of("One more", () -> {})))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationAction.of(" ", () -> {})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> titled("t").withSource(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aMessageOfUnknownShapeBecomesATitleAndContent() {
        var shortOne = NotificationRequest.message("jasper.general", Severity.ERROR, "Remote: Could not connect");
        assertThat(shortOne.title()).isEqualTo("Remote: Could not connect");
        assertThat(shortOne.content()).isEmpty();
        String wordy = "Vault: " + "word ".repeat(40);
        var cut = NotificationRequest.message("jasper.general", Severity.ERROR, wordy);
        assertThat(cut.title()).hasSize(NotificationRequest.SHORT_TITLE).endsWith("…");
        assertThat(cut.content()).isEqualTo(wordy.strip());
        var lines = NotificationRequest.message("jasper.general", Severity.ERROR, "Could not start terminal in /tmp:\nNo such file");
        assertThat(lines.title()).isEqualTo("Could not start terminal in /tmp:");
        assertThat(lines.content()).isEqualTo("Could not start terminal in /tmp:\nNo such file");
        assertThat(NotificationRequest.message("jasper.general", Severity.ERROR, "  ").title()).isEqualTo("No message");
    }

    @Test void groupIdsArePluginIdShapedAndNamesAreOneBoundedLine() {
        for (String good : List.of("jasper.general", "sample", "dev.example.tool.activities", "dev.example-tool_x"))
            assertThat(new NotificationGroup(good, "Name", "jasper", DisplayMode.CARD).id()).isEqualTo(good);
        for (String bad : List.of("", ".x", "Jasper.general", "x.y z", "a".repeat(161)))
            assertThatThrownBy(() -> new NotificationGroup(bad, "Name", "jasper", DisplayMode.CARD)).as(bad)
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new NotificationGroup("jasper.x", "  Two\nlines  ", "jasper", DisplayMode.CARD).name()).isEqualTo("Two lines");
        assertThat(new NotificationGroup("jasper.x", "n".repeat(100), "jasper", DisplayMode.CARD).name())
            .hasSize(NotificationGroup.MAX_NAME).endsWith("…");
        for (String blank : List.of(" ", "\u00A0", "\u200B\u2060"))
            assertThatThrownBy(() -> new NotificationGroup("jasper.x", blank, "jasper", DisplayMode.CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void invisibleTextIsBlankAndBidiControlsAreDropped() {
        for (String invisible : List.of("\u00A0\u2007", "\u200B\u2060", "\uFEFF", "\u202E"))
            assertThatThrownBy(() -> titled(invisible)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationAction.of("\u00A0", () -> {})).isInstanceOf(IllegalArgumentException.class);
        assertThat(titled("a\u202Eb\u2066c\u200Fd").title()).isEqualTo("abcd");
        assertThat(titled("t").withContent("x\u2067y\u2069\nz\u061C").content()).isEqualTo("xy\nz");
        assertThat(titled("a\u200Db").title()).as("a zero-width joiner joins emoji, so it stays").isEqualTo("a\u200Db");
    }

    @Test void entriesTrackRepeatsReadAndExpiry() {
        var entry = new Notification(UUID.randomUUID(), titled("t"), Instant.EPOCH, 1, false, false);
        var later = Instant.EPOCH.plusSeconds(5);
        var repeated = entry.withRead(true).repeated(later);
        assertThat(repeated.count()).isEqualTo(2);
        assertThat(repeated.read()).as("a repeat is news again").isFalse();
        assertThat(repeated.posted()).isEqualTo(later);
        assertThat(repeated.withExpired().expired()).isTrue();
        assertThatThrownBy(() -> new Notification(UUID.randomUUID(), titled("t"), Instant.EPOCH, 0, false, false))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
