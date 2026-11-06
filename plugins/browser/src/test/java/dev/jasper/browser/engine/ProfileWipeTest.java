package dev.jasper.browser.engine;

import dev.jasper.browser.engine.ProfileWipe.Mark;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 6 Task 8 and the user's V1: Clear Website Data marks Chromium's profile for a wipe at the next start, and Clear
 * Cookies before any start marks its cookies. The marker is {@code chromium-clear} in the data directory; {@code all}
 * wins over {@code cookies}. The wipe moves the profile aside before it deletes it, so a profile that cannot be moved
 * is kept whole, with its marker, and one half deleted is never Chromium's again.
 */
class ProfileWipeTest {
    @TempDir Path data;
    // The wipe's warnings, captured instead of printed. The logger is held, so it keeps the handler.
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final Logger log = Logger.getLogger(ProfileWipe.class.getName());
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) warnings.add(record.getMessage());
        }
        @Override public void flush() { }
        @Override public void close() { }
    };
    private boolean parentHandlers;

    @BeforeEach void listen() {
        parentHandlers = log.getUseParentHandlers();
        log.setUseParentHandlers(false);
        log.addHandler(capture);
    }

    @AfterEach void stopListening() {
        log.removeHandler(capture);
        log.setUseParentHandlers(parentHandlers);
    }

    @Test void allDeletesTheProfileAndTheMarker() throws Exception {
        Path cookies = profile();
        ProfileWipe.mark(data, Mark.ALL);
        assertThat(Files.readString(marker())).isEqualTo("all");
        assertThat(ProfileWipe.runIfMarked(data)).as("nothing left to clear").isEqualTo(Mark.NONE);
        assertThat(cookies).doesNotExist();
        assertThat(data.resolve("chromium")).doesNotExist();
        assertThat(leftovers()).as("the profile moved aside is deleted too").isEmpty();
        assertThat(marker()).doesNotExist();
        assertThat(data.resolve("chromium.log")).as("review round 1's item 10: emptied, as its lines may name hosts").isEmptyFile();
        assertThat(data.resolve("browser.sqlite")).as("the database is not the profile's").isRegularFile();
        assertThat(warnings).isEmpty();
    }

    @Test void cookiesKeepsTheProfileAndTheMarkerForChromiumsInitialization() throws Exception {
        Path cookies = profile();
        ProfileWipe.mark(data, Mark.COOKIES);
        assertThat(Files.readString(marker())).isEqualTo("cookies");
        assertThat(ProfileWipe.runIfMarked(data)).as("the cookies wait for Chromium").isEqualTo(Mark.COOKIES);
        assertThat(cookies).isRegularFile();
        assertThat(marker()).isRegularFile();
        ProfileWipe.cookiesCleared(data);
        assertThat(marker()).as("Chromium cleared them").doesNotExist();
        assertThat(ProfileWipe.marked(data)).isEqualTo(Mark.NONE);
        assertThat(cookies).isRegularFile();
    }

    @Test void noMarkerDoesNothing() throws Exception {
        Path cookies = profile();
        assertThat(ProfileWipe.marked(data)).isEqualTo(Mark.NONE);
        assertThat(ProfileWipe.runIfMarked(data)).isEqualTo(Mark.NONE);
        assertThat(cookies).isRegularFile();
        assertThat(marker()).doesNotExist();
        ProfileWipe.cookiesCleared(data);
        assertThat(marker()).doesNotExist();
        assertThat(warnings).isEmpty();
    }

    @Test void allReplacesCookiesAndCookiesNeverReplacesAll() throws Exception {
        ProfileWipe.mark(data, Mark.COOKIES);
        ProfileWipe.mark(data, Mark.ALL);
        assertThat(Files.readString(marker())).isEqualTo("all");
        ProfileWipe.mark(data, Mark.COOKIES);
        assertThat(Files.readString(marker())).as("all wins").isEqualTo("all");
        ProfileWipe.cookiesCleared(data);
        assertThat(ProfileWipe.marked(data)).as("clearing the cookies leaves the wipe").isEqualTo(Mark.ALL);
        try (var files = Files.list(data)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).as("no temporary file is left")
                .containsExactly("chromium-clear");
        }
    }

    /** A marker this Jasper cannot read the meaning of is taken for a wipe of the profile, never of the database. */
    @Test void aMarkerOfUnknownContentIsAll() throws Exception {
        Files.writeString(marker(), "everything, please");
        assertThat(ProfileWipe.marked(data)).isEqualTo(Mark.ALL);
        assertThat(ProfileWipe.databaseMarked(data)).isFalse();
    }

    /**
     * The dispatch's V1: a profile that cannot be moved aside, as Windows refuses while a file in it is open, keeps its
     * marker and stays whole, and Chromium starts with it as usual; the next start tries again.
     */
    @Test void aProfileThatCannotBeMovedKeepsItsMarkerAndStaysWhole() throws Exception {
        Path cookies = profile();
        ProfileWipe.mark(data, Mark.ALL);
        var refusing = new ProfileWipe.Disk() {
            @Override public void move(Path from, Path to) throws IOException {
                throw new java.nio.file.FileSystemException(from.toString(), to.toString(), "The process cannot access the file");
            }
        };
        assertThat(ProfileWipe.runIfMarked(data, refusing)).isEqualTo(Mark.ALL);
        assertThat(cookies).isRegularFile();
        assertThat(marker()).isRegularFile();
        assertThat(warnings).singleElement().asString().startsWith("Chromium's profile could not be moved aside to be cleared");
        assertThat(ProfileWipe.runIfMarked(data)).as("the next start").isEqualTo(Mark.NONE);
        assertThat(data.resolve("chromium")).doesNotExist();
    }

    /**
     * A profile moved aside that cannot be deleted is no longer Chromium's: its marker goes, so the next start keeps
     * the profile Chromium makes meanwhile, and the next start deletes what was left.
     */
    @Test void aMovedProfileThatCannotBeDeletedIsDeletedAtTheNextStart() throws Exception {
        profile();
        ProfileWipe.mark(data, Mark.ALL);
        var stuck = new ProfileWipe.Disk() {
            @Override public void deleteTree(Path folder) throws IOException {
                throw new java.nio.file.AccessDeniedException(folder.toString());
            }
        };
        assertThat(ProfileWipe.runIfMarked(data, stuck)).isEqualTo(Mark.NONE);
        assertThat(data.resolve("chromium")).as("Chromium makes a new profile").doesNotExist();
        assertThat(leftovers()).singleElement().asString().startsWith("chromium-cleared-");
        assertThat(marker()).doesNotExist();
        assertThat(warnings).singleElement().asString().startsWith("Chromium's cleared profile could not be deleted");
        Path fresh = Files.createDirectories(data.resolve("chromium").resolve("Default"));
        assertThat(ProfileWipe.runIfMarked(data)).as("the next start").isEqualTo(Mark.NONE);
        assertThat(leftovers()).isEmpty();
        assertThat(fresh).as("the profile made since is kept").isDirectory();
    }

    /**
     * Review round 1's item 6: each wipe moves the profile aside under a name of its own, so a profile moved aside that
     * cannot be deleted never blocks a later wipe; every start deletes each one left.
     */
    @Test void aMovedProfileThatStaysStuckBlocksNoLaterWipe() throws Exception {
        var stuck = new ProfileWipe.Disk() {
            @Override public void deleteTree(Path folder) throws IOException {
                throw new java.nio.file.AccessDeniedException(folder.toString());
            }
        };
        profile();
        ProfileWipe.mark(data, Mark.ALL);
        assertThat(ProfileWipe.runIfMarked(data, stuck)).isEqualTo(Mark.NONE);
        Path again = profile();
        ProfileWipe.mark(data, Mark.ALL);
        assertThat(ProfileWipe.runIfMarked(data, stuck)).as("the stuck one is in nobody's way").isEqualTo(Mark.NONE);
        assertThat(again).doesNotExist();
        assertThat(data.resolve("chromium")).doesNotExist();
        assertThat(leftovers()).hasSize(2).allSatisfy(name -> assertThat(name).startsWith("chromium-cleared-"));
        assertThat(ProfileWipe.runIfMarked(data)).as("the next start deletes both").isEqualTo(Mark.NONE);
        assertThat(leftovers()).isEmpty();
    }

    /**
     * Ruling V25: a clearing whose database ran in memory marks the database too; the database's files go before it
     * opens, wherever the data folder is first used, while the profile's wipe, which Chromium's start does, keeps the
     * mark for them.
     */
    @Test void aMarkedDatabaseIsDeletedBeforeItOpensAndOutlivesTheProfilesWipe() throws Exception {
        Path cookies = profile();
        List<Path> files = List.of(data.resolve("browser.sqlite"), data.resolve("browser.sqlite-wal"), data.resolve("browser.sqlite-shm"));
        for (Path file : files.subList(1, 3)) Files.writeString(file, "pages");
        ProfileWipe.wipeMarkedDatabase(data, files);
        assertThat(files).as("no mark: nothing").allSatisfy(file -> assertThat(file).isRegularFile());

        ProfileWipe.mark(data, Mark.ALL, true);
        assertThat(Files.readString(marker())).isEqualTo("all database");
        assertThat(ProfileWipe.marked(data)).isEqualTo(Mark.ALL);
        assertThat(ProfileWipe.databaseMarked(data)).isTrue();
        ProfileWipe.mark(data, Mark.ALL);
        assertThat(Files.readString(marker())).as("a later clearing keeps the database's mark").isEqualTo("all database");
        assertThat(ProfileWipe.runIfMarked(data)).isEqualTo(Mark.NONE);
        assertThat(cookies).doesNotExist();
        assertThat(Files.readString(marker())).as("the database's mark waits for the database").isEqualTo("database");
        assertThat(files).allSatisfy(file -> assertThat(file).isRegularFile());
        ProfileWipe.mark(data, Mark.COOKIES);
        assertThat(Files.readString(marker())).isEqualTo("cookies database");

        ProfileWipe.wipeMarkedDatabase(data, files);
        assertThat(files).allSatisfy(file -> assertThat(file).doesNotExist());
        assertThat(Files.readString(marker())).as("the cookies still wait for Chromium").isEqualTo("cookies");
        assertThat(ProfileWipe.databaseMarked(data)).isFalse();
        ProfileWipe.cookiesCleared(data);
        assertThat(marker()).doesNotExist();
        assertThat(warnings).isEmpty();
    }

    /** Chromium's profile in the data directory, with a cookie file, and its log and the database beside it. */
    private Path profile() throws IOException {
        Path cookies = Files.createDirectories(data.resolve("chromium").resolve("Default")).resolve("Cookies");
        Files.writeString(cookies, "session=1");
        Files.writeString(data.resolve("chromium.log"), "[WARNING] could not load https://bank.example/\n");
        Files.writeString(data.resolve("browser.sqlite"), "rows");
        return cookies;
    }

    /** The profiles moved aside and left in the data directory, by name. */
    private List<String> leftovers() throws IOException {
        try (var files = Files.list(data)) {
            return files.map(path -> path.getFileName().toString()).filter(name -> name.startsWith("chromium-cleared")).sorted().toList();
        }
    }

    private Path marker() {
        return data.resolve("chromium-clear");
    }
}
