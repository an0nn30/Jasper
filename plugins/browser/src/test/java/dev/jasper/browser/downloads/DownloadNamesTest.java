package dev.jasper.browser.downloads;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Spec section 10 and Review Focus 2, with rulings F4 and F5: a name a server suggests keeps only its last part and no
 * character a file system or a reader misreads, a clash on disk or with a download under way is numbered as iTerm2
 * numbers it, and the file always lands in the folder. Every folder is a {@code @TempDir} (F15).
 */
class DownloadNamesTest {
    @TempDir Path folder;

    @Test void aSuggestedNameKeepsOnlyItsLastPart() {
        assertThat(DownloadNames.safeName("../../.ssh/authorized_keys")).isEqualTo("authorized_keys");
        assertThat(DownloadNames.safeName("a\\b.txt")).isEqualTo("b.txt");
        assertThat(DownloadNames.safeName("C:\\Users\\me\\report.pdf")).isEqualTo("report.pdf");
        assertThat(DownloadNames.safeName("/etc/passwd")).isEqualTo("passwd");
        assertThat(DownloadNames.safeName("folder/")).as("nothing after the last slash").isEqualTo("download");
        assertThat(DownloadNames.safeName("report.pdf")).isEqualTo("report.pdf");
    }

    @Test void nothingOrOnlyDotsAndSpacesIsDownload() {
        for (String name : new String[] { "", ".", "..", "...", " ", " . ", "../..", "..\\.." })
            assertThat(DownloadNames.safeName(name)).as("\"" + name + "\"").isEqualTo("download");
        assertThat(DownloadNames.safeName(null)).as("no name").isEqualTo("download");
    }

    @Test void leadingAndTrailingDotsAndSpacesGo() {
        assertThat(DownloadNames.safeName("name. ")).isEqualTo("name");
        assertThat(DownloadNames.safeName(".bashrc")).as("a dotfile loses its dot, as in Chrome and Firefox").isEqualTo("bashrc");
        assertThat(DownloadNames.safeName("  ..report.pdf.. ")).isEqualTo("report.pdf");
        assertThat(DownloadNames.safeName("my report.pdf")).as("inner spaces stay").isEqualTo("my report.pdf");
        assertThat(DownloadNames.safeName("archive..tar.gz")).as("inner dots stay").isEqualTo("archive..tar.gz");
    }

    /** F4: control and format characters, bidi controls and zero-width ones included, and Windows' reserved ones. */
    @Test void controlFormatAndWindowsReservedCharactersBecomeUnderscores() {
        assertThat(DownloadNames.safeName("evil\u202Efdp.exe")).as("a right-to-left override").isEqualTo("evil_fdp.exe");
        assertThat(DownloadNames.safeName("a:b.txt")).as("an NTFS stream, and Finder's slash").isEqualTo("a_b.txt");
        assertThat(DownloadNames.safeName("a<b>c\"d|e?f*g.txt")).isEqualTo("a_b_c_d_e_f_g.txt");
        assertThat(DownloadNames.safeName("a\u0000b\tc\nd\u007Fe\u0085f.txt")).as("C0, DEL and C1 controls").isEqualTo("a_b_c_d_e_f.txt");
        assertThat(DownloadNames.safeName("zero\u200Bwidth\u200D\uFEFF.txt")).as("zero-width characters").isEqualTo("zero_width__.txt");
        assertThat(DownloadNames.safeName("\u2066isolate\u2069\u200E.txt")).as("isolates and marks").isEqualTo("_isolate__.txt");
        assertThat(DownloadNames.safeName("line\u2028break\u2029.txt")).as("line and paragraph separators").isEqualTo("line_break_.txt");
        assertThat(DownloadNames.safeName("lone\uD800.txt")).as("an unpaired surrogate no file system can store").isEqualTo("lone_.txt");
        assertThat(DownloadNames.safeName("caf\u00E9 \u4E2D\u6587 \uD83D\uDE00.txt")).as("letters and emoji stay")
            .isEqualTo("caf\u00E9 \u4E2D\u6587 \uD83D\uDE00.txt");
    }

    /** F4: Windows' device names, in any case, before the first dot. */
    @Test void aWindowsDeviceNameGetsALeadingUnderscore() {
        for (String device : List.of("CON", "PRN", "AUX", "NUL", "COM1", "COM9", "LPT1", "LPT9", "CONIN$", "CONOUT$", "COM\u00B9", "COM\u00B2",
                "COM\u00B3", "LPT\u00B9", "LPT\u00B2", "LPT\u00B3")) {
            assertThat(DownloadNames.safeName(device)).as(device).isEqualTo("_" + device);
            assertThat(DownloadNames.safeName(device + ".txt")).as(device + ".txt").isEqualTo("_" + device + ".txt");
            assertThat(DownloadNames.safeName(device.toLowerCase(java.util.Locale.ROOT) + ".tar.gz")).as("lower case")
                .isEqualTo("_" + device.toLowerCase(java.util.Locale.ROOT) + ".tar.gz");
        }
        assertThat(DownloadNames.safeName("CON.txt")).isEqualTo("_CON.txt");
        assertThat(DownloadNames.safeName("Nul")).isEqualTo("_Nul");
        assertThat(DownloadNames.safeName("NUL .txt")).as("Windows ignores the spaces before the dot").isEqualTo("_NUL .txt");
        for (String name : List.of("CONSOLE.txt", "COM10.txt", "LPT.txt", "a.CON", "_CON.txt", "CON_.txt", "COMA.txt"))
            assertThat(DownloadNames.safeName(name)).as(name).isEqualTo(name);
    }

    /** F4: over 200 UTF-8 bytes a name is cut on a code point, keeping an extension of at most 16 characters. */
    @Test void aLongNameIsCutToTwoHundredBytesKeepingItsExtension() {
        String ascii = DownloadNames.safeName("a".repeat(296) + ".pdf");
        assertThat(ascii).isEqualTo("a".repeat(196) + ".pdf");
        assertThat(DownloadNames.safeName("a".repeat(300))).isEqualTo("a".repeat(200));
        assertThat(DownloadNames.safeName("a".repeat(196) + ".pdf")).as("200 bytes is kept").isEqualTo("a".repeat(196) + ".pdf");

        String cjk = DownloadNames.safeName("\u4E2D".repeat(300) + ".txt");
        assertThat(cjk).as("three bytes each").isEqualTo("\u4E2D".repeat(65) + ".txt");
        assertThat(bytes(cjk)).isEqualTo(199);
        assertThat(DownloadNames.safeName("\u4E2D".repeat(300))).isEqualTo("\u4E2D".repeat(66));

        String sixteen = "." + "e".repeat(16), seventeen = "." + "e".repeat(17);
        assertThat(DownloadNames.safeName("a".repeat(300) + sixteen)).isEqualTo("a".repeat(183) + sixteen);
        assertThat(DownloadNames.safeName("a".repeat(300) + seventeen)).as("no extension to keep").isEqualTo("a".repeat(200));
        assertThat(bytes(DownloadNames.safeName("\u00E9".repeat(300) + "." + "\u00E9".repeat(16)))).isLessThanOrEqualTo(200);
    }

    /** F4: an emoji right at the cut goes whole; its two surrogates never part. */
    @Test void anEmojiAtTheCutGoesWhole() {
        String emoji = "\uD83D\uDE00";
        String noExtension = DownloadNames.safeName("a".repeat(198) + emoji + "b".repeat(10));
        assertThat(noExtension).isEqualTo("a".repeat(198));
        String withExtension = DownloadNames.safeName("a".repeat(194) + emoji + "zz.txt");
        assertThat(withExtension).isEqualTo("a".repeat(194) + ".txt");
        String fits = DownloadNames.safeName("a".repeat(192) + emoji + "zz.txt");
        assertThat(fits).as("an emoji that fits stays").isEqualTo("a".repeat(192) + emoji + ".txt");
        String emojis = DownloadNames.safeName(emoji.repeat(80) + ".txt");
        assertThat(emojis).isEqualTo(emoji.repeat(49) + ".txt");
        for (String name : List.of(noExtension, withExtension, fits, emojis))
            assertThat(unpaired(name)).as(name).isFalse();
    }

    @Test void aCutNameEndsWithNoDotOrSpace() {
        assertThat(DownloadNames.safeName("a".repeat(199) + " . " + "b".repeat(100))).as("no extension of 16 characters or fewer")
            .isEqualTo("a".repeat(199));
    }

    /** iTerm2's numbering, before the last extension. */
    @Test void aNumberGoesBeforeTheLastExtension() {
        assertThat(DownloadNames.numbered("report.pdf", 1)).isEqualTo("report (1).pdf");
        assertThat(DownloadNames.numbered("archive.tar.gz", 2)).isEqualTo("archive.tar (2).gz");
        assertThat(DownloadNames.numbered("README", 1)).isEqualTo("README (1)");
        assertThat(DownloadNames.numbered(".bashrc", 1)).isEqualTo(".bashrc (1)");
        assertThat(DownloadNames.numbered("report.pdf", 1000)).isEqualTo("report (1000).pdf");
        assertThatThrownBy(() -> DownloadNames.numbered("report.pdf", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aFreeNameIsTakenAsItIsAndInTheFolder() {
        var names = new DownloadNames();
        assertThat(reserve(names, folder, "report.pdf")).contains(folder.resolve("report.pdf"));
        assertThat(reserve(names, folder, "../../.ssh/authorized_keys")).as("the safe name").contains(folder.resolve("authorized_keys"));
        assertThat(reserve(names, folder, "")).contains(folder.resolve("download"));
        assertThat(reserve(names, folder, "C:\\evil\\x.exe")).contains(folder.resolve("x.exe"));
        assertThat(reserve(names, folder, "..")).contains(folder.resolve("download (1)"));
        for (String name : List.of("a/../../b.txt", "CON", "evil\u202Efdp.exe", "x".repeat(400)))
            assertThat(reserve(names, folder, name)).as(name).hasValueSatisfying(file -> assertThat(file.getParent()).isEqualTo(folder));
    }

    /** Review Focus 2: two downloads of one name at once, and a file already there. */
    @Test void aClashOnDiskAndTwoReservationsAtOnceAreNumbered() throws IOException {
        Files.writeString(folder.resolve("report.pdf"), "already here");
        var names = new DownloadNames();
        assertThat(reserve(names, folder, "report.pdf")).contains(folder.resolve("report (1).pdf"));
        assertThat(reserve(names, folder, "report.pdf")).contains(folder.resolve("report (2).pdf"));
        assertThat(reserve(names, folder, "notes.txt")).contains(folder.resolve("notes.txt"));
        assertThat(reserve(names, folder, "notes.txt")).contains(folder.resolve("notes (1).txt"));
        Files.createDirectory(folder.resolve("data"));
        assertThat(reserve(names, folder, "data")).as("a folder of that name").contains(folder.resolve("data (1)"));
        assertThat(Files.readString(folder.resolve("report.pdf"))).as("nothing written").isEqualTo("already here");
        try (var listed = Files.list(folder)) {
            assertThat(listed.map(path -> path.getFileName().toString()).sorted().toList()).as("reserving creates nothing")
                .containsExactly("data", "report.pdf");
        }
    }

    /** The disk is probed without following links, so a dangling link counts as taken. */
    @Test void aLinkOnDiskIsTakenEvenWhenItLeadsNowhere() throws IOException {
        Path link = folder.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, folder.resolve("missing"));
        } catch (UnsupportedOperationException | IOException noLinks) {
            assumeThat(false).as("this file system makes no symbolic links").isTrue();
        }
        assertThat(reserve(new DownloadNames(), folder, "link.txt")).contains(folder.resolve("link (1).txt"));
    }

    /** F5: case and normalization are folded on every platform, as APFS and NTFS fold them. */
    @Test void namesThatDifferOnlyInCaseOrNormalizationClash() {
        var names = new DownloadNames();
        assertThat(reserve(names, folder, "Report.pdf")).contains(folder.resolve("Report.pdf"));
        assertThat(reserve(names, folder, "report.pdf")).contains(folder.resolve("report (1).pdf"));
        assertThat(reserve(names, folder, "REPORT (1).PDF")).as("the numbered one too").contains(folder.resolve("REPORT (1) (1).PDF"));
        String composed = "caf\u00E9.txt", decomposed = "cafe\u0301.txt";
        assertThat(reserve(names, folder, composed)).contains(folder.resolve(composed));
        assertThat(reserve(names, folder, decomposed)).contains(folder.resolve("cafe\u0301 (1).txt"));
        assertThat(reserve(names, folder, "CAF\u00C9.TXT")).as("both").contains(folder.resolve("CAF\u00C9 (2).TXT"));
    }

    /**
     * Task 2's review: a reservation is a token that frees only itself, so a second release, after another download
     * reserved the same name, frees nothing of that one's, and another list's token frees nothing here.
     */
    @Test void releaseFreesOnlyItsOwnReservation() {
        var names = new DownloadNames();
        DownloadNames.Reservation first = names.reserve(folder, "a.txt").orElseThrow();
        assertThat(first.file()).isEqualTo(folder.resolve("a.txt"));
        DownloadNames.Reservation second = names.reserve(folder, "a.txt").orElseThrow();
        assertThat(second.file()).isEqualTo(folder.resolve("a (1).txt"));
        names.release(first);
        DownloadNames.Reservation again = names.reserve(folder, "A.TXT").orElseThrow();
        assertThat(again.file()).as("free again, its case folded").isEqualTo(folder.resolve("A.TXT"));
        names.release(first);
        assertThat(reserve(names, folder, "a.txt")).as("a second release frees nothing of the download that took the name since")
            .contains(folder.resolve("a (2).txt"));
        names.release(second);
        DownloadNames.Reservation later = names.reserve(folder, "a.txt").orElseThrow();
        assertThat(later.file()).isEqualTo(second.file());
        assertThat(later).as("a token is equal only to itself").isNotEqualTo(second);
        names.release(second);
        assertThat(reserve(names, folder, "a.txt")).as("the earlier token frees nothing of the later").contains(folder.resolve("a (3).txt"));
        DownloadNames.Reservation elsewhere = new DownloadNames().reserve(folder, "a.txt").orElseThrow();
        assertThat(elsewhere.file()).isEqualTo(folder.resolve("a.txt"));
        names.release(elsewhere);
        assertThat(reserve(names, folder, "a.txt")).as("another list's token frees nothing here").contains(folder.resolve("a (4).txt"));
    }

    @Test void eachFolderHasItsOwnNames() {
        var names = new DownloadNames();
        Path other = folder.resolve("other");
        assertThat(reserve(names, folder, "a.txt")).contains(folder.resolve("a.txt"));
        assertThat(reserve(names, other, "a.txt")).contains(other.resolve("a.txt"));
    }

    /** F3: at most 1,000 numbered names are tried, and past them there is no free name. */
    @Test void pastAThousandNumberedNamesThereIsNoFreeName() {
        var names = new DownloadNames();
        var taken = new ArrayList<DownloadNames.Reservation>();
        for (int n = 0; n <= 1000; n++) {
            DownloadNames.Reservation reservation = names.reserve(folder, "x.bin").orElseThrow();
            assertThat(reservation.file()).as("n = " + n).isEqualTo(folder.resolve(n == 0 ? "x.bin" : "x (" + n + ").bin"));
            taken.add(reservation);
        }
        assertThat(reserve(names, folder, "x.bin")).isEmpty();
        assertThat(reserve(names, folder, "y.bin")).as("another name is free").contains(folder.resolve("y.bin"));
        names.release(taken.get(500));
        assertThat(reserve(names, folder, "x.bin")).contains(folder.resolve("x (500).bin"));
    }

    @Test void reservationsAtOnceFromManyThreadsAreAllDifferent() throws Exception {
        var names = new DownloadNames();
        var taken = ConcurrentHashMap.<Path>newKeySet();
        var start = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(8);
        try {
            var done = new ArrayList<Future<?>>();
            for (int t = 0; t < 8; t++)
                done.add(threads.submit(() -> {
                    start.await();
                    for (int i = 0; i < 50; i++) assertThat(taken.add(names.reserve(folder, "same.txt").orElseThrow().file())).isTrue();
                    return null;
                }));
            start.countDown();
            for (Future<?> each : done) each.get(30, TimeUnit.SECONDS);
        } finally {
            threads.shutdownNow();
        }
        assertThat(taken).hasSize(400).contains(folder.resolve("same.txt"), folder.resolve("same (399).txt"));
    }

    /**
     * F4: on Windows the full path, with " (1000)" and Chromium's {@code .crdownload} added, stays under {@code MAX_PATH}'s
     * 260 characters, so the stem is shortened in a deep folder, keeping the extension.
     */
    @Test void onWindowsADeepFolderShortensTheStemBelowMaxPath() {
        Path deep = deep(200);
        var names = new DownloadNames();
        String name = "a".repeat(100) + ".pdf";
        Path first = names.reserve(deep, name, "Windows 11").orElseThrow().file();
        assertThat(first).isEqualTo(deep.resolve("a".repeat(36) + ".pdf"));
        assertThat(first.toString().length() + " (1000)".length() + ".crdownload".length()).isEqualTo(259);
        Path second = names.reserve(deep, name, "Windows 11").orElseThrow().file();
        assertThat(second).as("numbered on the shortened stem").isEqualTo(deep.resolve("a".repeat(36) + " (1).pdf"));
        assertThat(second.toString().length() + ".crdownload".length()).isLessThan(260);
        assertThat(reserve(new DownloadNames(), deep, name, "Mac OS X")).as("elsewhere the name stays").contains(deep.resolve(name));
        assertThat(reserve(new DownloadNames(), deep, name, "Linux")).contains(deep.resolve(name));
        assertThat(reserve(new DownloadNames(), deep, "short.pdf", "Windows 10")).as("a short name stays").contains(deep.resolve("short.pdf"));
        assertThat(reserve(new DownloadNames(), deep(233), "CONSOLE.txt", "Windows 11")).as("a stem cut to a device name")
            .contains(deep(233).resolve("_CO.txt"));
        assertThat(reserve(new DownloadNames(), deep(236), "ab   cd", "Windows 11")).as("no trailing space").contains(deep(236).resolve("ab"));
        assertThat(reserve(new DownloadNames(), deep(250), "a.pdf", "Windows 11")).as("no name fits").isEmpty();
    }

    /** The file {@code names} reserves for {@code name} in {@code folder}, on this system. */
    private static Optional<Path> reserve(DownloadNames names, Path folder, String name) {
        return names.reserve(folder, name).map(DownloadNames.Reservation::file);
    }

    /** The file {@code names} reserves for {@code name} in {@code folder}, on the system {@code osName} names. */
    private static Optional<Path> reserve(DownloadNames names, Path folder, String name, String osName) {
        return names.reserve(folder, name, osName).map(DownloadNames.Reservation::file);
    }

    /** A folder under the {@code @TempDir}, never created, whose path is {@code length} characters long. */
    private Path deep(int length) {
        Path deep = folder.resolve("d".repeat(length - folder.toString().length() - 1));
        assertThat(deep.toString()).hasSize(length);
        return deep;
    }

    private static int bytes(String name) {
        return name.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean unpaired(String text) {
        return text.codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF);
    }
}
