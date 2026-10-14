package dev.jasper.app.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The commit this Jasper was built from, read from {@code dev/jasper/app/build-info.properties} (written by
 * {@code gradle/build-info.gradle}), and what About and error reports call it. A release is a clean tree whose HEAD
 * carries a {@code vX.Y.Z} tag; anything else, including a missing resource or an empty commit, is a development build.
 *
 * @param release whether this is an official release
 * @param version the release's version, or for a development build the latest release tag at or behind HEAD; empty for none
 * @param commitsSince commits after {@code version}; 0 on the tagged commit itself
 * @param commit the full commit hash, empty when unknown
 * @param branch the branch, empty when unknown or detached
 * @param dirty whether the tree had changes or untracked files git does not ignore
 * @param committed when the commit was made
 */
public record BuildInfo(boolean release, Optional<String> version, int commitsSince, String commit, String branch, boolean dirty,
                        Optional<OffsetDateTime> committed) {
    static final String RESOURCE = "/dev/jasper/app/build-info.properties";
    private static final String NUMBER = "(?:0|[1-9]\\d{0,8})";
    private static final Pattern RELEASE_TAG = Pattern.compile("v(" + NUMBER + "\\." + NUMBER + "\\." + NUMBER + ")");
    private static final Pattern DESCRIBE = Pattern.compile("v(" + NUMBER + "\\." + NUMBER + "\\." + NUMBER + ")-(\\d{1,9})-g[0-9a-f]+");
    private static final Comparator<String> NUMERIC = Comparator.<String>comparingInt(version -> part(version, 0))
        .thenComparingInt(version -> part(version, 1)).thenComparingInt(version -> part(version, 2));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** A build nothing is known about. */
    public static final BuildInfo UNKNOWN = new BuildInfo(false, Optional.empty(), 0, "", "", false, Optional.empty());

    public BuildInfo {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(committed, "committed");
        if (commitsSince < 0) throw new IllegalArgumentException("commitsSince must not be negative");
        if (release && version.isEmpty()) throw new IllegalArgumentException("A release has a version");
    }

    /** This process's build, read once from the application jar. */
    public static BuildInfo current() { return Current.BUILD; }

    private static final class Current {
        static final BuildInfo BUILD = read(BuildInfo.class.getResourceAsStream(RESOURCE));
    }

    /** Reads and closes {@code in}; a null or unreadable stream is {@link #UNKNOWN}. */
    static BuildInfo read(InputStream in) {
        if (in == null) return UNKNOWN;
        try (in; var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            var properties = new Properties();
            properties.load(reader);
            return of(properties.stringPropertyNames().stream().collect(Collectors.toMap(key -> key, properties::getProperty)));
        } catch (IOException | IllegalArgumentException unreadable) {
            return UNKNOWN;
        }
    }

    /**
     * The build the raw facts describe.
     *
     * @param facts {@code commit}, {@code branch}, {@code commitDate}, {@code dirty}, {@code headTags} (space-separated)
     *              and {@code describe}; a missing key counts as empty
     * @return the build, {@link #UNKNOWN} when the commit is empty
     */
    public static BuildInfo of(Map<String, String> facts) {
        String commit = fact(facts, "commit");
        if (commit.isEmpty()) return UNKNOWN;
        String branch = fact(facts, "branch");
        if (branch.equals("HEAD")) branch = "";
        boolean dirty = Boolean.parseBoolean(fact(facts, "dirty"));
        Optional<OffsetDateTime> committed;
        try {
            String date = fact(facts, "commitDate");
            committed = date.isEmpty() ? Optional.empty() : Optional.of(OffsetDateTime.parse(date));
        } catch (DateTimeParseException unreadable) {
            committed = Optional.empty();
        }
        Optional<String> tagged = Arrays.stream(fact(facts, "headTags").split("\\s+")).map(RELEASE_TAG::matcher)
            .filter(Matcher::matches).map(match -> match.group(1)).max(NUMERIC);
        if (tagged.isPresent()) return new BuildInfo(!dirty, tagged, 0, commit, branch, dirty, committed);
        Matcher described = DESCRIBE.matcher(fact(facts, "describe"));
        if (described.matches())
            return new BuildInfo(false, Optional.of(described.group(1)), Integer.parseInt(described.group(2)), commit, branch, dirty, committed);
        return new BuildInfo(false, Optional.empty(), 0, commit, branch, dirty, committed);
    }

    /** Whether anything is known about the commit. */
    public boolean known() { return !commit.isEmpty(); }

    /** The first eight characters of the commit, or "unknown". */
    public String shortCommit() { return known() ? commit.substring(0, Math.min(8, commit.length())) : "unknown"; }

    /** "1.2.0", "1.2.0 + 14 commits" or "1.2.0 + 1 commit"; empty when no version is known. */
    public String versionLine() {
        if (version.isEmpty()) return "";
        if (commitsSince == 0) return version.get();
        return version.get() + " + " + commitsSince + (commitsSince == 1 ? " commit" : " commits");
    }

    /** "main @ 8ba67ed3, modified"; the hash alone when detached; "commit unknown" when nothing is known. */
    public String location() {
        if (!known()) return "commit unknown";
        return (branch.isEmpty() ? "" : branch + " @ ") + shortCommit() + (dirty ? ", modified" : "");
    }

    /** What error reports and About's Copy call this build: "1.2.0", or "1.2.0 + 14 commits, development build (main @ 8ba67ed3)". */
    public String summary() {
        if (release) return version.orElseThrow();
        String line = versionLine();
        return (line.isEmpty() ? "" : line + ", ") + "development build (" + location() + ")";
    }

    /** "Committed 13 Oct 2026" in {@code zone}; empty when the date is unknown. */
    public String committedLine(ZoneId zone) {
        return committed.map(at -> "Committed " + DAY.format(at.atZoneSameInstant(zone))).orElse("");
    }

    /** Copy's commit line: "Commit {@code <hash>} on main, modified; committed 2026-10-13", or "Commit unknown". */
    public String commitLine(ZoneId zone) {
        if (!known()) return "Commit unknown";
        return "Commit " + commit + (branch.isEmpty() ? "" : " on " + branch) + (dirty ? ", modified" : "")
            + committed.map(at -> "; committed " + DateTimeFormatter.ISO_LOCAL_DATE.format(at.atZoneSameInstant(zone))).orElse("");
    }

    private static String fact(Map<String, String> facts, String key) {
        String value = facts.get(key);
        return value == null ? "" : value.strip();
    }

    private static int part(String version, int index) { return Integer.parseInt(version.split("\\.")[index]); }
}
