package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.vmoptions.AotCacheRecord;
import dev.jasper.app.vmoptions.LauncherImage;
import dev.jasper.app.vmoptions.VmOptionsCheck;
import dev.jasper.app.vmoptions.VmOptionsFile;
import dev.jasper.app.vmoptions.VmOptionsLaunch;
import dev.jasper.app.vmoptions.VmOptionsSyntax;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTestExtension.class)
class VmOptionsFeatureTest {
    @TempDir Path home;
    private NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
    private final List<Path> opened = new ArrayList<>();
    private final List<Path> revealed = new ArrayList<>();
    private Function<List<String>, VmOptionsCheck.Outcome> script = command -> new VmOptionsCheck.Outcome(0, "", false);
    private boolean restartWorks = true;
    private int restarts;
    private boolean trained;

    private Path file() { return home.resolve("jasper.vmoptions"); }
    private Path cacheFile() { return home.resolve("app").resolve("jasper.aot"); }
    private VmOptionsLaunch launch(boolean corrupted, Optional<String> skipped) {
        return new VmOptionsLaunch(Optional.of(file()), Optional.empty(), corrupted, skipped, Optional.empty(), Optional.empty());
    }
    private VmOptionsLaunch managed() { return launch(false, Optional.empty()); }

    private VmOptionsFeature feature(VmOptionsLaunch launch, boolean image) { return feature(launch, image, Runnable::run); }

    private VmOptionsFeature feature(VmOptionsLaunch launch, boolean image, Consumer<Runnable> ui) {
        var options = new ArrayList<>(List.of("-Xmx2048m", "-XX:+IgnoreUnrecognizedVMOptions"));
        if (trained) options.add("-XX:AOTCache=" + cacheFile());
        var launcherImage = new LauncherImage(home.resolve("app"), options, List.of());
        return new VmOptionsFeature(launch, () -> image ? Optional.of(launcherImage) : Optional.empty(),
            found -> new VmOptionsCheck(Path.of("java"), found, (command, limit) -> script.apply(command), Duration.ofSeconds(10)),
            Runnable::run, ui, center, () -> { restarts++; return restartWorks; }, opened::add, revealed::add,
            () -> 2048, () -> OptionalLong.of(16384), Duration.ZERO);
    }

    private List<String> titles() { return center.history().stream().map(entry -> entry.request().title()).toList(); }

    /** What the installed app printed on 2026-10-14 (start-up recovery spec section 1). */
    private static final String SEALED = "[0.142s][error][aot               ] java.lang.SecurityException: sealing violation: "
        + "can't seal package com.jetbrains: already defined\nError occurred during initialization of VM\n"
        + "Unexpected exception when loading aot-linked classes.\n";
    private static final String CACHE_TEXT = "The start-up cache made Java fail at launch, so Jasper starts more slowly until a new "
        + "Jasper build replaces it. Java reported: java.lang.SecurityException: sealing violation: can't seal package com.jetbrains: "
        + "already defined";

    private Path record() { return home.resolve("jasper-aot.failed"); }

    /** A start the launcher made without the cache for {@code reason}, after skipping the options for {@code optionsSkipped}. */
    private VmOptionsLaunch withoutCache(String reason, Optional<String> optionsSkipped, Optional<String> digest) {
        return new VmOptionsLaunch(Optional.of(file()), digest, false, optionsSkipped, Optional.of(reason), Optional.of(record()));
    }

    /** A trained image whose cache is on disk: 5 bytes, modified at 2026-10-14T08:24:05.900Z. */
    private void installCache() throws IOException {
        trained = true;
        Files.createDirectories(cacheFile().getParent());
        Files.writeString(cacheFile(), "cache");
        Files.setLastModifiedTime(cacheFile(), FileTime.from(Instant.parse("2026-10-14T08:24:05.900Z")));
    }

    private String recorded(long size, String options) {
        return AotCacheRecord.HEADER + "\ncache=" + cacheFile() + "\nsize=" + size + "\nmodified=1791966245\noptions=" + options + "\n";
    }

    private void newStart() { center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED); }

    /** Runs {@code action} and returns what VmOptionsFeature logged meanwhile. */
    private static List<java.util.logging.LogRecord> logged(Runnable action) {
        var logger = java.util.logging.Logger.getLogger(VmOptionsFeature.class.getName());
        var previousLevel = logger.getLevel();
        boolean previousParents = logger.getUseParentHandlers();
        var records = new ArrayList<java.util.logging.LogRecord>();
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        handler.setLevel(java.util.logging.Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(java.util.logging.Level.ALL);
        logger.setUseParentHandlers(false);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParents);
        }
        return records;
    }

    @Test void theGroupIsDeclared() {
        feature(managed(), true);
        assertThat(center.groups()).anySatisfy(group -> {
            assertThat(group.id()).isEqualTo("jasper.vmoptions");
            assertThat(group.name()).isEqualTo("Memory & VM options");
            assertThat(group.defaultMode()).isEqualTo(DisplayMode.CARD);
        });
    }

    @Test void theHeapShowsTheFileElseTheBundledDefault() throws IOException {
        var feature = feature(managed(), true);
        feature.reload();
        var heap = feature.hooks().heap();
        assertThat(heap.saved().getAsInt()).isEqualTo(2048);
        assertThat(heap.enabled().getAsBoolean()).isTrue();
        assertThat(heap.comment().apply(2048)).isEqualTo("Current: 2048 MiB.");
        assertThat(heap.comment().apply(32768)).contains("more than this computer's 16384 MiB");
        assertThat(heap.error().apply(256)).isEqualTo("Use 512 MiB or more.");
        Files.writeString(file(), "-Xmx4g\n");
        feature.reload();
        assertThat(heap.saved().getAsInt()).isEqualTo(4096);
    }

    @Test void theSavedHeapIsTheFilesMaximumElseTheBundledDefault() throws IOException {
        var feature = feature(managed(), true);
        feature.reload();
        assertThat(feature.savedHeapMiB()).as("no file: the bundled maximum").isEqualTo(2048);
        Files.writeString(file(), "-Xmx4g\n");
        feature.reload();
        assertThat(feature.savedHeapMiB()).isEqualTo(4096);
        assertThat(feature.savedHeapMiB()).as("the row's saved value is the same number").isEqualTo(feature.hooks().heap().saved().getAsInt());
    }

    @Test void savingTheHeapWritesTheFileAndAsksForARestart() throws IOException {
        var feature = feature(managed(), true);
        feature.startupSettled();
        var hooks = feature.hooks();
        assertThat(hooks.restart().get()).isEmpty();
        hooks.heap().save().apply(4096).join();
        assertThat(Files.readString(file())).isEqualTo(VmOptionsFile.HEADER + "\n\n-Xmx4096m\n");
        assertThat(hooks.restart().get()).hasValueSatisfying(notice -> assertThat(notice.text()).isEqualTo("Restart Jasper to apply your changes."));
        feature.settingsClosed();
        assertThat(titles()).containsExactly("Restart Jasper to apply your VM options");
        hooks.heap().save().apply(2048).join();
        assertThat(hooks.restart().get()).as("back to what this JVM applied").isEmpty();
    }

    @Test void aSavedHeapShowsAtOnce() {
        // The worker runs inline but the EDT turns queue, so a continuation attached to the future runs inside the turn
        // that completes it, as Settings' refresh does.
        var turns = new ArrayList<Runnable>();
        var heap = feature(managed(), true, turns::add).hooks().heap();
        int[] seen = {0};
        var saved = heap.save().apply(4096).thenRun(() -> seen[0] = heap.saved().getAsInt());
        assertThat(saved).isNotDone();
        while (!turns.isEmpty()) turns.removeFirst().run();
        assertThat(saved).isCompleted();
        assertThat(seen[0]).isEqualTo(4096);
    }

    @Test void aFileEditedElsewhereShowsWhileTheSectionIsOpen() throws IOException {
        var feature = feature(managed(), true);
        int[] changes = {0};
        var subscription = feature.hooks().onChanged().apply(() -> changes[0]++);
        int before = changes[0];
        Files.writeString(file(), "-Xmx8g\n");
        feature.pollFile();
        assertThat(changes[0]).isGreaterThan(before);
        assertThat(feature.hooks().heap().saved().getAsInt()).isEqualTo(8192);
        subscription.close();
    }

    @Test void aCorruptedFileDisablesTheHeapRowAndSaysWhy() throws IOException {
        Files.write(file(), new byte[] {'-', 'X', 'm', 'x', '1', 'g', '\n', 0});
        var feature = feature(managed(), true);
        feature.reload();
        var hooks = feature.hooks();
        assertThat(hooks.heap().enabled().getAsBoolean()).isFalse();
        assertThat(hooks.heap().comment().apply(2048)).contains("NUL");
        assertThat(hooks.state().get()).isPresent();
    }

    @Test void aCorruptedFileIsNeverRewritten() throws IOException {
        byte[] corrupted = {'-', 'X', 'm', 'x', '1', 'g', '\n', 0};
        Files.write(file(), corrupted);
        var result = feature(managed(), true).hooks().heap().save().apply(4096);
        assertThat(result).isCompletedExceptionally();
        assertThat(result.exceptionNow()).hasMessage("Could not save jasper.vmoptions: it contains a NUL character or is not valid UTF-8.");
        assertThat(Files.readAllBytes(file())).isEqualTo(corrupted);
        assertThat(titles()).contains("Could not save jasper.vmoptions");
    }

    @Test void withoutJaspersLauncherTheSectionIsReadOnly() {
        var feature = feature(new VmOptionsLaunch(Optional.empty(), Optional.empty(), false, Optional.empty(), Optional.empty(), Optional.empty()), false);
        var hooks = feature.hooks();
        assertThat(feature.editable()).isFalse();
        assertThat(hooks.heap().enabled().getAsBoolean()).isFalse();
        assertThat(hooks.state().get()).hasValueSatisfying(notice -> assertThat(notice.text()).isEqualTo("Custom VM options apply to the installed Jasper app."));
        feature.startupSettled();
        assertThat(center.history()).isEmpty();
    }

    @Test void aFallbackStartSaysWhyWithTheJvmsMessage() throws IOException {
        Files.writeString(file(), "-Xmx3q\n");
        script = command -> command.contains("-Xmx3q") && !command.contains("-XX:-IgnoreUnrecognizedVMOptions")
            ? new VmOptionsCheck.Outcome(1, "Invalid maximum heap size: -Xmx3q\nError: Could not create the Java Virtual Machine.\n", false)
            : new VmOptionsCheck.Outcome(0, "", false);
        var feature = feature(launch(false, Optional.of("launch-failed")), true);
        feature.startupSettled();
        feature.startupSettled();
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().title()).isEqualTo("Jasper started without your custom VM options");
            assertThat(entry.request().content()).contains("Invalid maximum heap size: -Xmx3q");
            assertThat(entry.request().actions()).extracting(NotificationAction::label).containsExactly("Edit Custom VM Options\u2026");
        });
    }

    @Test void aFallbackStartWithoutACheckStillSaysSo() throws IOException {
        Files.writeString(file(), "-Xmx3q\n");
        feature(launch(false, Optional.of("launch-failed")), false).startupSettled();
        assertThat(center.history()).singleElement().satisfies(entry ->
            assertThat(entry.request().content()).isEqualTo("The JVM rejected them, so Jasper started with the bundled options."));
    }

    @Test void aCorruptedLaunchIsAnnounced() {
        feature(launch(true, Optional.empty()), true).startupSettled();
        assertThat(titles()).containsExactly("Custom VM options were ignored");
    }

    @Test void findingsDescribeIgnoredUnrecognizedAndFatalLines() throws IOException {
        Files.writeString(file(), "Xmx300m\n-XX:+NoSuchFlag\n-Xms4g\n");
        script = command -> {
            if (command.contains("-XX:-IgnoreUnrecognizedVMOptions") && command.contains("-XX:+NoSuchFlag"))
                return new VmOptionsCheck.Outcome(1, "Unrecognized VM option 'NoSuchFlag'\n", false);
            if (!command.contains("-XX:-IgnoreUnrecognizedVMOptions"))
                return new VmOptionsCheck.Outcome(1, "Error occurred during initialization of VM\nInitial heap size set to a larger value than the maximum heap size\n", false);
            return new VmOptionsCheck.Outcome(0, "", false);
        };
        var feature = feature(managed(), true);
        feature.reload();
        assertThat(feature.hooks().findings().get()).hasValueSatisfying(notice -> {
            assertThat(notice.title()).isEqualTo("Jasper won't start with these options");
            assertThat(notice.text()).contains("Not an option, ignored: Xmx300m.")
                .contains("Unrecognized option, ignored: -XX:+NoSuchFlag.")
                .contains("Initial heap size set to a larger value than the maximum heap size")
                .contains("JASPER_VM_OPTIONS_SKIP=1");
        });
    }

    @Test void theCacheSentenceAppearsOnlyWhenTheFailingCacheCheckUsedTheOptions() throws IOException {
        trained = true;
        String refused = "Error occurred during initialization of VM\nUnable to use AOT cache.\n";
        Files.writeString(file(), "-XX:+UseZGC\n");
        script = command -> command.contains("-XX:AOTMode=on") && command.contains("-XX:+UseZGC")
            ? new VmOptionsCheck.Outcome(1, refused, false) : new VmOptionsCheck.Outcome(0, "", false);
        var feature = feature(managed(), true);
        feature.reload();
        assertThat(feature.hooks().findings().get()).as("the cache starts with the bundled options alone").hasValueSatisfying(notice ->
            assertThat(notice.text()).isEqualTo("These options turn off Jasper's start-up cache; it will start more slowly."));
        Files.writeString(file(), "-Xmx3q\n");
        script = command -> command.contains("-Xmx3q") ? new VmOptionsCheck.Outcome(1, "Invalid maximum heap size: -Xmx3q\n", false)
            : command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, refused, false) : new VmOptionsCheck.Outcome(0, "", false);
        feature.reload();
        assertThat(feature.hooks().findings().get()).as("the cache failed with the bundled options alone").hasValueSatisfying(notice ->
            assertThat(notice.text()).contains("Invalid maximum heap size: -Xmx3q").doesNotContain("start-up cache"));
    }

    @Test void aCacheThatMadeJavaFailIsRememberedAndAnnouncedOncePerRecord() throws IOException {
        installCache();
        script = command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, SEALED, false) : new VmOptionsCheck.Outcome(0, "", false);
        feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), true).startupSettled();
        assertThat(Files.readString(record())).isEqualTo(recorded(5, ""));
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().group()).isEqualTo("jasper.vmoptions");
            assertThat(entry.request().severity()).isEqualTo(Severity.WARNING);
            assertThat(entry.request().title()).isEqualTo("Jasper started without its start-up cache");
            assertThat(entry.request().content()).isEqualTo(CACHE_TEXT);
            assertThat(entry.request().actions()).isEmpty();
        });
        newStart();
        feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), true).startupSettled();
        assertThat(center.history()).as("the same cache failed again, say because the launcher did not match the record").isEmpty();
        assertThat(Files.readString(record())).isEqualTo(recorded(5, ""));
        newStart();
        Files.writeString(cacheFile(), "cache2");
        Files.setLastModifiedTime(cacheFile(), FileTime.from(Instant.parse("2026-10-14T08:24:05Z")));
        feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), true).startupSettled();
        assertThat(Files.readString(record())).as("a new build's cache fails too: a new record").isEqualTo(recorded(6, ""));
        assertThat(titles()).containsExactly("Jasper started without its start-up cache");
    }

    @Test void aCacheThatFailsWithTheCustomOptionsIsRememberedWithTheirDigest() throws IOException {
        installCache();
        Files.writeString(file(), "-Xmx1g\n");
        String digest = VmOptionsSyntax.digest(List.of("-Xmx1g"));
        script = command -> command.contains("-XX:AOTMode=on") && command.contains("-Xmx1g")
            ? new VmOptionsCheck.Outcome(1, SEALED, false) : new VmOptionsCheck.Outcome(0, "", false);
        feature(withoutCache("launch-failed", Optional.empty(), Optional.of(digest)), true).startupSettled();
        assertThat(Files.readString(record())).isEqualTo(recorded(5, digest));
        assertThat(titles()).containsExactly("Jasper started without its start-up cache");
    }

    @Test void aCacheBrokenOnItsOwnIsNeverBlamedOnTheOptions() throws IOException {
        installCache();
        Files.writeString(file(), "-Xmx1g\n");
        script = command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, SEALED, false) : new VmOptionsCheck.Outcome(0, "", false);
        feature(withoutCache("launch-failed", Optional.empty(), Optional.of(VmOptionsSyntax.digest(List.of("-Xmx1g")))), true).startupSettled();
        assertThat(Files.readString(record())).as("it fails with the bundled options alone too").isEqualTo(recorded(5, ""));
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.request().content()).isEqualTo(CACHE_TEXT));
        var settings = feature(managed(), true);
        settings.reload();
        assertThat(settings.hooks().findings().get()).as("no sentence blames the options").isEmpty();
    }

    @Test void whenBothWereSkippedAndCouldNotBeCheckedTheOptionsNoticeStillAppears() throws IOException {
        installCache();
        var both = withoutCache("launch-failed", Optional.of("launch-failed"), Optional.empty());
        Files.writeString(file(), "-Xms4g\n");
        script = command -> new VmOptionsCheck.Outcome(-1, "", true);
        var timedOut = logged(() -> feature(both, true).startupSettled());
        assertThat(center.history()).as("every run over its limit").singleElement().satisfies(entry -> {
            assertThat(entry.request().title()).isEqualTo("Jasper started without your custom VM options");
            assertThat(entry.request().content()).isEqualTo("The JVM rejected them, so Jasper started with the bundled options.");
        });
        assertThat(timedOut).extracting(java.util.logging.LogRecord::getMessage).containsExactly(
            "Jasper started without the custom VM options after failed launches, and they could not be checked",
            "The start-up cache was skipped after a failed launch and could not be checked; the next start tries it again");
        assertThat(record()).doesNotExist();
        newStart();
        var noImage = logged(() -> feature(both, false).startupSettled());
        assertThat(titles()).as("no Jasper.cfg found").containsExactly("Jasper started without your custom VM options");
        assertThat(noImage).extracting(java.util.logging.LogRecord::getMessage).containsExactly(
            "Jasper started without the custom VM options after failed launches, and they could not be checked",
            "The start-up cache was skipped after a failed launch and could not be checked; the next start tries it again");
        assertThat(record()).doesNotExist();
        newStart();
        String unrecoverable = "Error occurred during initialization of VM\nInitial heap size set to a larger value than the maximum heap size\n";
        script = command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(-1, "", true)
            : command.contains("-Xms4g") && !command.contains("-XX:-IgnoreUnrecognizedVMOptions") ? new VmOptionsCheck.Outcome(1, unrecoverable, false)
            : new VmOptionsCheck.Outcome(0, "", false);
        var cacheTimedOut = logged(() -> feature(both, true).startupSettled());
        assertThat(center.history()).as("only the cache check over its limit").singleElement().satisfies(entry ->
            assertThat(entry.request().content()).isEqualTo("The JVM rejected them: Initial heap size set to a larger value than the maximum heap size"));
        assertThat(cacheTimedOut).extracting(java.util.logging.LogRecord::getMessage)
            .containsExactly("The start-up cache was skipped after a failed launch and could not be checked; the next start tries it again");
        assertThat(record()).doesNotExist();
    }

    @Test void aCacheThatPassesOrCannotBeCheckedIsNeitherRememberedNorAnnounced() throws IOException {
        installCache();
        var passes = logged(() -> feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), true).startupSettled());
        assertThat(passes).singleElement().satisfies(entry -> {
            assertThat(entry.getLevel()).isEqualTo(java.util.logging.Level.INFO);
            assertThat(entry.getMessage()).isEqualTo("The start-up cache was skipped after a failed launch, but its check finds no failure now; the next start tries it again");
        });
        script = command -> new VmOptionsCheck.Outcome(-1, "", true);
        var unchecked = logged(() -> {
            feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), true).startupSettled();
            feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), false).startupSettled();
        });
        assertThat(unchecked).extracting(java.util.logging.LogRecord::getMessage).as("timed out, and no Jasper.cfg found").containsExactly(
            "The start-up cache was skipped after a failed launch and could not be checked; the next start tries it again",
            "The start-up cache was skipped after a failed launch and could not be checked; the next start tries it again");
        assertThat(record()).as("timed out, and no Jasper.cfg found").doesNotExist();
        assertThat(center.history()).isEmpty();
    }

    @Test void aRememberedOrHandSetSkipIsOneLogLine() throws IOException {
        installCache();
        var commands = new ArrayList<List<String>>();
        script = command -> { commands.add(command); return new VmOptionsCheck.Outcome(1, SEALED, false); };
        var lines = logged(() -> {
            for (String reason : List.of("remembered", "environment", "1")) feature(withoutCache(reason, Optional.empty(), Optional.empty()), true).startupSettled();
        });
        assertThat(lines).extracting(java.util.logging.LogRecord::getMessage).containsExactly(
            "Jasper started without its start-up cache, which failed its check after an earlier failed launch",
            "Jasper started without its start-up cache because JASPER_AOT_CACHE_SKIP is set",
            "Jasper started without its start-up cache because JASPER_AOT_CACHE_SKIP is set");
        assertThat(lines).allSatisfy(entry -> assertThat(entry.getLevel()).isEqualTo(java.util.logging.Level.INFO));
        assertThat(commands).as("nothing is checked").isEmpty();
        assertThat(record()).doesNotExist();
        assertThat(center.history()).isEmpty();
    }

    @Test void aRecordThatCannotBeWrittenIsLoggedAndTheNoticeStillAppears() throws IOException {
        installCache();
        Files.writeString(home.resolve("blocker"), "not a folder");
        script = command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, SEALED, false) : new VmOptionsCheck.Outcome(0, "", false);
        var blocked = new VmOptionsLaunch(Optional.of(file()), Optional.empty(), false, Optional.empty(), Optional.of("launch-failed"),
            Optional.of(home.resolve("blocker/jasper-aot.failed")));
        var lines = logged(() -> feature(blocked, true).startupSettled());
        assertThat(titles()).containsExactly("Jasper started without its start-up cache");
        assertThat(lines).anySatisfy(entry -> {
            assertThat(entry.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
            assertThat(entry.getMessage()).isEqualTo("Could not remember the start-up cache that fails its check after a failed launch; the next start tries it again");
            assertThat(entry.getThrown()).isInstanceOf(IOException.class);
        });
        newStart();
        var nowhere = new VmOptionsLaunch(Optional.of(file()), Optional.empty(), false, Optional.empty(), Optional.of("launch-failed"), Optional.empty());
        feature(nowhere, true).startupSettled();
        assertThat(titles()).as("no record file named").containsExactly("Jasper started without its start-up cache");
        assertThat(record()).doesNotExist();
    }

    @Test void whenBothWereSkippedEachNoticeAppearsOnlyWhenItsOwnCheckFails() throws IOException {
        installCache();
        var both = withoutCache("launch-failed", Optional.of("launch-failed"), Optional.empty());
        Files.writeString(file(), "-Xms4g\n");
        String unrecoverable = "Error occurred during initialization of VM\nInitial heap size set to a larger value than the maximum heap size\n";
        script = command -> command.contains("-Xms4g") && !command.contains("-XX:-IgnoreUnrecognizedVMOptions")
            ? new VmOptionsCheck.Outcome(1, unrecoverable, false) : new VmOptionsCheck.Outcome(0, "", false);
        feature(both, true).startupSettled();
        assertThat(titles()).as("the options stop the JVM; the cache passes with the bundled options alone")
            .containsExactly("Jasper started without your custom VM options");
        assertThat(record()).doesNotExist();
        newStart();
        Files.writeString(file(), "-Xmx1g\n");
        script = command -> command.contains("-XX:AOTMode=on") && command.contains("-Xmx1g")
            ? new VmOptionsCheck.Outcome(1, SEALED, false) : new VmOptionsCheck.Outcome(0, "", false);
        feature(both, true).startupSettled();
        assertThat(titles()).as("the options start the JVM; the cache fails with them").containsExactly("Jasper started without its start-up cache");
        assertThat(Files.readString(record())).as("the next start carries their digest").isEqualTo(recorded(5, VmOptionsSyntax.digest(List.of("-Xmx1g"))));
        newStart();
        Files.delete(record());
        script = command -> new VmOptionsCheck.Outcome(0, "", false);
        feature(both, true).startupSettled();
        assertThat(center.history()).as("both pass now").isEmpty();
        assertThat(record()).doesNotExist();
    }

    @Test void restartNowThatCannotRestartSaysSo() {
        restartWorks = false;
        var feature = feature(managed(), true);
        feature.restartNow();
        assertThat(restarts).isEqualTo(1);
        assertThat(titles()).containsExactly("Jasper could not restart");
    }

    @Test void editCreatesTheFileAndOpensIt() throws IOException {
        var feature = feature(managed(), true);
        feature.editCustomOptions();
        assertThat(Files.readString(file())).isEqualTo(VmOptionsFile.HEADER + "\n\n");
        assertThat(opened).containsExactly(file());
        feature.hooks().reveal().run();
        assertThat(revealed).containsExactly(file());
    }

    @Test void aFailedSaveNamesTheFile() throws IOException {
        Files.writeString(home.resolve("blocker"), "not a folder");
        var launch = new VmOptionsLaunch(Optional.of(home.resolve("blocker/jasper.vmoptions")), Optional.empty(), false, Optional.empty(),
            Optional.empty(), Optional.empty());
        var result = feature(launch, true).hooks().heap().save().apply(4096);
        assertThat(result).isCompletedExceptionally();
        assertThat(result.exceptionNow()).hasMessage("Could not save jasper.vmoptions: " + home.resolve("blocker") + " is in the way.");
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().title()).isEqualTo("Could not save jasper.vmoptions");
            assertThat(entry.request().content()).isEqualTo(home.resolve("blocker/jasper.vmoptions") + ": " + home.resolve("blocker") + " is in the way.");
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS) void aFolderThatCannotBeWrittenSaysPermissionDenied() throws IOException {
        Path folder = Files.createDirectory(home.resolve("readonly"));
        assertThat(folder.toFile().setWritable(false)).isTrue();
        try {
            assumeFalse(Files.isWritable(folder), "the user can write anywhere (root)");
            var launch = new VmOptionsLaunch(Optional.of(folder.resolve("jasper.vmoptions")), Optional.empty(), false, Optional.empty(),
                Optional.empty(), Optional.empty());
            var result = feature(launch, true).hooks().heap().save().apply(4096);
            assertThat(result.exceptionNow()).hasMessage("Could not save jasper.vmoptions: permission denied.");
            assertThat(center.history()).singleElement().satisfies(entry ->
                assertThat(entry.request().content()).isEqualTo(folder.resolve("jasper.vmoptions") + ": permission denied."));
        } finally {
            assertThat(folder.toFile().setWritable(true)).isTrue();
        }
    }

    @Test void aCorruptedLaunchWhoseFileIsFixedSaysTheOptionsWereIgnoredAtStartAndAsksForARestart() throws IOException {
        Files.write(file(), new byte[] {'-', 'X', 'm', 'x', '1', 'g', '\n', 0});
        var feature = feature(launch(true, Optional.empty()), true);
        feature.startupSettled();
        var hooks = feature.hooks();
        assertThat(hooks.state().get()).hasValueSatisfying(notice -> {
            assertThat(notice.title()).isEqualTo("Options ignored");
            assertThat(notice.text()).isEqualTo("jasper.vmoptions contains a NUL character or is not valid UTF-8; fix it to apply your options.");
        });
        assertThat(hooks.restart().get()).as("nothing to restart for while the file is unreadable").isEmpty();
        Files.writeString(file(), "-Xmx3g\n");
        feature.reload();
        assertThat(hooks.state().get()).hasValueSatisfying(notice -> {
            assertThat(notice.title()).isEqualTo("Options ignored at start-up");
            assertThat(notice.text()).isEqualTo("jasper.vmoptions could not be read when Jasper started, so it started with the bundled options.");
        });
        assertThat(hooks.restart().get()).hasValueSatisfying(notice -> assertThat(notice.text()).isEqualTo("Restart Jasper to apply your changes."));
    }

    @Test void aFileThatBecomesCorruptedWhileRunningOffersNoRestart() throws IOException {
        Files.writeString(file(), "-Xmx1g\n");
        var launch = new VmOptionsLaunch(Optional.of(file()), Optional.of(VmOptionsSyntax.digest(List.of("-Xmx1g"))), false, Optional.empty(),
            Optional.empty(), Optional.empty());
        var feature = feature(launch, true);
        feature.startupSettled();
        var hooks = feature.hooks();
        assertThat(hooks.state().get()).isEmpty();
        assertThat(hooks.restart().get()).isEmpty();
        Files.write(file(), new byte[] {'-', 'X', 'm', 'x', '3', 'g', '\n', 0});
        feature.reload();
        assertThat(hooks.restart().get()).as("restarting would start Jasper without any of the user's options").isEmpty();
        assertThat(hooks.state().get()).hasValueSatisfying(notice -> {
            assertThat(notice.title()).isEqualTo("Options ignored");
            assertThat(notice.text()).isEqualTo("jasper.vmoptions contains a NUL character or is not valid UTF-8; fix it to apply your options.");
        });
        feature.settingsClosed();
        assertThat(center.history()).isEmpty();
    }

    @Test void aSavedHeapIsReportedSoTheLowMemoryWarningCanShowAgain() throws IOException {
        var feature = feature(managed(), true);
        int[] saved = {0};
        feature.onHeapSaved(() -> saved[0]++);
        feature.hooks().heap().save().apply(4096).join();
        assertThat(saved[0]).isEqualTo(1);
        Files.write(file(), new byte[] {'-', 'X', 'm', 'x', '1', 'g', '\n', 0});
        assertThat(feature.hooks().heap().save().apply(3072)).isCompletedExceptionally();
        assertThat(saved[0]).as("a refused save changes nothing").isEqualTo(1);
    }
}
