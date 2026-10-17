package dev.jasper.app.platform;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuildInfoTest {
    private static final String COMMIT = "8ba67ed3" + "0".repeat(32);

    /** A clean main checkout with no tags, overridden pair by pair. */
    private static Map<String, String> facts(String... pairs) {
        var facts = new HashMap<>(Map.of("commit", COMMIT, "branch", "main", "commitDate", "2026-10-13T14:02:11-04:00",
            "dirty", "false", "headTags", "", "describe", ""));
        for (int i = 0; i < pairs.length; i += 2) facts.put(pairs[i], pairs[i + 1]);
        return facts;
    }

    @Test void aCleanReleaseTagIsARelease() {
        var build = BuildInfo.of(facts("headTags", "v1.2.0", "describe", "v1.2.0-0-g8ba67ed3"));
        assertThat(build.release()).isTrue();
        assertThat(build.versionLine()).isEqualTo("1.2.0");
        assertThat(build.summary()).isEqualTo("1.2.0");
        assertThat(build.location()).isEqualTo("main @ 8ba67ed3");
    }

    @Test void aReleaseTagOnAModifiedTreeIsADevelopmentBuildOfThatVersion() {
        var build = BuildInfo.of(facts("headTags", "v1.2.0", "dirty", "true"));
        assertThat(build.release()).isFalse();
        assertThat(build.versionLine()).isEqualTo("1.2.0");
        assertThat(build.location()).isEqualTo("main @ 8ba67ed3, modified");
        assertThat(build.summary()).isEqualTo("1.2.0, development build (main @ 8ba67ed3, modified)");
    }

    @Test void commitsAfterATagCountFromIt() {
        var build = BuildInfo.of(facts("describe", "v1.2.0-14-g8ba67ed3"));
        assertThat(build.release()).isFalse();
        assertThat(build.versionLine()).isEqualTo("1.2.0 + 14 commits");
        assertThat(build.summary()).isEqualTo("1.2.0 + 14 commits, development build (main @ 8ba67ed3)");
        assertThat(BuildInfo.of(facts("describe", "v1.2.0-1-g8ba67ed3")).versionLine()).isEqualTo("1.2.0 + 1 commit");
    }

    @Test void withoutAReachableTagThereIsNoVersion() {
        var build = BuildInfo.of(facts());
        assertThat(build.version()).isEmpty();
        assertThat(build.versionLine()).isEmpty();
        assertThat(build.summary()).isEqualTo("development build (main @ 8ba67ed3)");
    }

    @Test void aDetachedHeadShowsTheHashAlone() {
        var build = BuildInfo.of(facts("branch", "HEAD"));
        assertThat(build.branch()).isEmpty();
        assertThat(build.location()).isEqualTo("8ba67ed3");
        assertThat(build.commitLine(ZoneOffset.UTC)).isEqualTo("Commit " + COMMIT + "; committed 2026-10-13");
    }

    @Test void onlyReleaseTagsCountAndTheHighestWins() {
        var build = BuildInfo.of(facts("headTags", "nightly v1.2.0 v1.10.0 v01.2.3 v1.3.0-rc1"));
        assertThat(build.release()).isTrue();
        assertThat(build.version()).contains("1.10.0");
        assertThat(BuildInfo.of(facts("headTags", "pre-author-rewrite-main nightly")).release()).isFalse();
    }

    @Test void anEmptyCommitOrAMissingResourceIsUnknown() {
        assertThat(BuildInfo.of(Map.of())).isEqualTo(BuildInfo.UNKNOWN);
        assertThat(BuildInfo.of(facts("commit", ""))).isEqualTo(BuildInfo.UNKNOWN);
        assertThat(BuildInfo.read(null)).isEqualTo(BuildInfo.UNKNOWN);
        assertThat(BuildInfo.UNKNOWN.known()).isFalse();
        assertThat(BuildInfo.UNKNOWN.release()).isFalse();
        assertThat(BuildInfo.UNKNOWN.versionLine()).isEmpty();
        assertThat(BuildInfo.UNKNOWN.location()).isEqualTo("commit unknown");
        assertThat(BuildInfo.UNKNOWN.summary()).isEqualTo("development build (commit unknown)");
        assertThat(BuildInfo.UNKNOWN.commitLine(ZoneOffset.UTC)).isEqualTo("Commit unknown");
        assertThat(BuildInfo.UNKNOWN.committedLine(ZoneOffset.UTC)).isEmpty();
    }

    @Test void datesAreShownInTheGivenZone() {
        var build = BuildInfo.of(facts("dirty", "true"));
        assertThat(build.committedLine(ZoneOffset.UTC)).isEqualTo("Committed 13 Oct 2026");
        assertThat(build.commitLine(ZoneOffset.UTC)).isEqualTo("Commit " + COMMIT + " on main, modified; committed 2026-10-13");
        var late = BuildInfo.of(facts("commitDate", "2026-10-13T23:30:00-04:00"));
        assertThat(late.committedLine(ZoneOffset.UTC)).isEqualTo("Committed 14 Oct 2026");
    }

    @Test void anUnreadableDateIsLeftOut() {
        var build = BuildInfo.of(facts("commitDate", "yesterday"));
        assertThat(build.committed()).isEmpty();
        assertThat(build.committedLine(ZoneOffset.UTC)).isEmpty();
        assertThat(build.commitLine(ZoneOffset.UTC)).isEqualTo("Commit " + COMMIT + " on main");
    }

    @Test void theResourceIsReadAsUtf8WithItsEscapes() {
        String text = "commit=" + COMMIT + "\nbranch=claude/über\n";
        assertThat(BuildInfo.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).branch()).isEqualTo("claude/über");
        String escaped = "commit=" + COMMIT + "\nbranch=a\\\\b\n";
        assertThat(BuildInfo.read(new ByteArrayInputStream(escaped.getBytes(StandardCharsets.UTF_8))).branch()).isEqualTo("a\\b");
    }

    @Test void theBuildWritesTheResource() {
        assertThat(BuildInfo.class.getResource(BuildInfo.RESOURCE)).isNotNull();
    }

    /** Spec 10.11: the splash's version line is the version and the short commit, as About shows them. */
    @Test void theSplashLineIsTheVersionAndTheShortCommitAsAboutShowsThem() {
        assertThat(BuildInfo.of(facts("headTags", "v1.2.0")).buildLine()).isEqualTo("1.2.0 · Build 8ba67ed3");
        assertThat(BuildInfo.of(facts("describe", "v1.2.0-14-g8ba67ed3")).buildLine()).isEqualTo("1.2.0 + 14 commits · Build 8ba67ed3");
        assertThat(BuildInfo.of(facts()).buildLine()).as("no version").isEqualTo("Build 8ba67ed3");
        assertThat(new BuildInfo(false, java.util.Optional.of("1.2.0"), 0, "", "", false, java.util.Optional.empty()).buildLine())
            .as("no commit").isEqualTo("1.2.0");
        assertThat(BuildInfo.UNKNOWN.buildLine()).isEqualTo("Development build");
        assertThat(BuildInfo.COPYRIGHT).isEqualTo("Copyright 2026 Dustin McKenna-Watts");
    }
}
