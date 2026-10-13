package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.vmoptions.LauncherImage;
import dev.jasper.app.vmoptions.VmOptionsCheck;
import dev.jasper.app.vmoptions.VmOptionsFile;
import dev.jasper.app.vmoptions.VmOptionsLaunch;
import dev.jasper.app.vmoptions.VmOptionsSyntax;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
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
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
    private final List<Path> opened = new ArrayList<>();
    private final List<Path> revealed = new ArrayList<>();
    private Function<List<String>, VmOptionsCheck.Outcome> script = command -> new VmOptionsCheck.Outcome(0, "", false);
    private boolean restartWorks = true;
    private int restarts;

    private Path file() { return home.resolve("jasper.vmoptions"); }
    private VmOptionsLaunch launch(boolean corrupted, Optional<String> skipped) { return new VmOptionsLaunch(Optional.of(file()), Optional.empty(), corrupted, skipped); }
    private VmOptionsLaunch managed() { return launch(false, Optional.empty()); }

    private VmOptionsFeature feature(VmOptionsLaunch launch, boolean image) { return feature(launch, image, Runnable::run); }

    private VmOptionsFeature feature(VmOptionsLaunch launch, boolean image, Consumer<Runnable> ui) {
        var launcherImage = new LauncherImage(home.resolve("app"), List.of("-Xmx2048m", "-XX:+IgnoreUnrecognizedVMOptions"), List.of());
        return new VmOptionsFeature(launch, () -> image ? Optional.of(launcherImage) : Optional.empty(),
            found -> new VmOptionsCheck(Path.of("java"), found, (command, limit) -> script.apply(command), Duration.ofSeconds(10)),
            Runnable::run, ui, center, () -> { restarts++; return restartWorks; }, opened::add, revealed::add,
            () -> 2048, () -> OptionalLong.of(16384), Duration.ZERO);
    }

    private List<String> titles() { return center.history().stream().map(entry -> entry.request().title()).toList(); }

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
        var feature = feature(new VmOptionsLaunch(Optional.empty(), Optional.empty(), false, Optional.empty()), false);
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
        var launch = new VmOptionsLaunch(Optional.of(home.resolve("blocker/jasper.vmoptions")), Optional.empty(), false, Optional.empty());
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
            var launch = new VmOptionsLaunch(Optional.of(folder.resolve("jasper.vmoptions")), Optional.empty(), false, Optional.empty());
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
        var launch = new VmOptionsLaunch(Optional.of(file()), Optional.of(VmOptionsSyntax.digest(List.of("-Xmx1g"))), false, Optional.empty());
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
}
