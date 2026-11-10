package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Browser plan 7 Task 1c: {@code DownloadMark}, behind {@code Platform.markDownloaded}. Its values are pure; the marks
 * are written only on files in {@code @TempDir} (F15). The macOS cases run on a Mac; the Windows and Linux branches run
 * everywhere through the {@code osName} seam, where the volume keeps user-defined attributes, and for real on their own
 * systems.
 */
class DownloadMarkTest {
    private static final URI SOURCE = URI.create("https://example.com/files/report.zip");
    private static final Optional<URI> PAGE = Optional.of(URI.create("https://example.com/files/"));
    private static final String MAC = "Mac OS X";
    private static final String WINDOWS = "Windows 11";
    private static final String LINUX = "Linux";

    @TempDir Path dir;

    @Test void theQuarantineValueHasChromesFormWithJaspersName() {
        UUID uuid = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
        assertThat(DownloadMark.quarantine(0x6543210fL, uuid)).isEqualTo("0081;6543210f;Jasper;0F8FAD5B-D9CB-469F-A165-70867728950E");
        assertThat(DownloadMark.quarantine(0x10L, uuid)).as("eight hex digits at least").isEqualTo("0081;00000010;Jasper;0F8FAD5B-D9CB-469F-A165-70867728950E");
    }

    @Test void theZoneIdentifierNamesTheReferrerThenTheHost() {
        assertThat(DownloadMark.zoneIdentifier(SOURCE, PAGE)).isEqualTo(
            "[ZoneTransfer]\r\nZoneId=3\r\nReferrerUrl=https://example.com/files/\r\nHostUrl=https://example.com/files/report.zip\r\n");
        assertThat(DownloadMark.zoneIdentifier(SOURCE, Optional.empty())).as("no referrer")
            .isEqualTo("[ZoneTransfer]\r\nZoneId=3\r\nHostUrl=https://example.com/files/report.zip\r\n");
    }

    /** R46: neither address keeps its user name and password; the port, query and fragment stay. */
    @Test void theZoneIdentifierDropsUserInfo() {
        URI withUser = URI.create("https://u:p@example.com/a");
        assertThat(DownloadMark.zoneIdentifier(withUser, Optional.of(withUser)))
            .isEqualTo("[ZoneTransfer]\r\nZoneId=3\r\nReferrerUrl=https://example.com/a\r\nHostUrl=https://example.com/a\r\n");
        assertThat(DownloadMark.zoneIdentifier(URI.create("http://name@example.com:8080/a.zip?b=c&d=e#f"), Optional.empty()))
            .isEqualTo("[ZoneTransfer]\r\nZoneId=3\r\nHostUrl=http://example.com:8080/a.zip?b=c&d=e#f\r\n");
    }

    @Test void anAddressThatIsNotHttpIsWrittenAsAboutInternet() {
        for (String address : List.of("data:text/plain;base64,aGk=", "blob:https://example.com/0f8fad5b", "file:///tmp/a.zip",
                "ftp://example.com/a.zip", "http:opaque", "https:///no-host")) {
            assertThat(DownloadMark.zoneIdentifier(URI.create(address), Optional.of(URI.create(address)))).as(address)
                .isEqualTo("[ZoneTransfer]\r\nZoneId=3\r\nReferrerUrl=about:internet\r\nHostUrl=about:internet\r\n");
        }
    }

    /** F7: a /dev/null download names no address: about:internet and no referrer. */
    @Test void aDownloadWithNoAddressToKeepWritesOnlyAboutInternet() {
        assertThat(DownloadMark.zoneIdentifier(URI.create("about:internet"), Optional.empty()))
            .isEqualTo("[ZoneTransfer]\r\nZoneId=3\r\nHostUrl=about:internet\r\n");
    }

    @Test void theZoneIdentifierIsAscii() {
        assertThat(DownloadMark.zoneIdentifier(URI.create("https://example.com/résumé.pdf"), Optional.empty()))
            .isEqualTo("[ZoneTransfer]\r\nZoneId=3\r\nHostUrl=https://example.com/r%C3%A9sum%C3%A9.pdf\r\n");
    }

    @Test void aMissingFileThrowsNoSuchFileOnEveryPlatform() {
        Path missing = dir.resolve("missing.zip");
        for (String os : List.of(MAC, WINDOWS, LINUX))
            assertThatThrownBy(() -> DownloadMark.apply(missing, SOURCE, PAGE, os)).as(os).isInstanceOf(NoSuchFileException.class);
        assertThat(missing).doesNotExist();
    }

    /** The Windows branch, through the seam on any volume that keeps user-defined attributes (streams on NTFS). */
    @Test void theWindowsBranchWritesTheZoneIdentifier() throws IOException {
        Path file = download("report.zip");
        assumeStreams(file);
        DownloadMark.apply(file, URI.create("https://u:p@example.com/report.zip"), PAGE, WINDOWS);
        assertThat(stream(file)).isEqualTo(DownloadMark.zoneIdentifier(URI.create("https://example.com/report.zip"), PAGE))
            .isEqualTo("[ZoneTransfer]\r\nZoneId=3\r\nReferrerUrl=https://example.com/files/\r\nHostUrl=https://example.com/report.zip\r\n");
        assertThat(file).hasContent("zip");
    }

    @Test void theWindowsBranchKeepsAMarkAlreadyThere() throws IOException {
        Path file = download("report.zip");
        assumeStreams(file);
        String chromium = "[ZoneTransfer]\r\nZoneId=3\r\nHostUrl=https://chromium.example/\r\n";
        view(file).write(DownloadMark.ZONE, ByteBuffer.wrap(chromium.getBytes(StandardCharsets.US_ASCII)));
        DownloadMark.apply(file, SOURCE, PAGE, WINDOWS);
        assertThat(stream(file)).isEqualTo(chromium);
    }

    @Test void theWindowsBranchReplacesAnEmptyMark() throws IOException {
        Path file = download("report.zip");
        assumeStreams(file);
        view(file).write(DownloadMark.ZONE, ByteBuffer.allocate(0));
        DownloadMark.apply(file, SOURCE, Optional.empty(), WINDOWS);
        assertThat(stream(file)).isEqualTo("[ZoneTransfer]\r\nZoneId=3\r\nHostUrl=https://example.com/files/report.zip\r\n");
    }

    /** F33: a volume without streams (FAT32, exFAT, some shares) gives no view, or one whose calls are unsupported. */
    @Test void aVolumeWithoutStreamsFailsWithTheReason() throws IOException {
        Path file = download("report.zip");
        byte[] value = DownloadMark.zoneIdentifier(SOURCE, PAGE).getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> DownloadMark.zone(file, null, value)).isInstanceOf(FileSystemException.class)
            .extracting(failure -> ((FileSystemException) failure).getReason()).isEqualTo("This volume cannot keep a downloaded-file mark");
        assertThatThrownBy(() -> DownloadMark.zone(file, new Unsupported(), value)).isInstanceOf(FileSystemException.class)
            .extracting(failure -> ((FileSystemException) failure).getReason()).isEqualTo("This volume cannot keep a downloaded-file mark");
        assertThat(file).hasContent("zip");
    }

    /** macOS's call gets the value without a trailing NUL, the absolute path, and XATTR_NOFOLLOW | XATTR_CREATE. */
    @Test void theQuarantineCallCreatesTheAttributeOnTheFileItself() throws IOException {
        Path file = download("report.zip");
        String value = "0081;6543210f;Jasper;0F8FAD5B-D9CB-469F-A165-70867728950E";
        var calls = new java.util.ArrayList<String>();
        DownloadMark.quarantine(file, value, (path, bytes, options) -> {
            calls.add(path + "|" + new String(bytes, StandardCharsets.US_ASCII) + "|" + options);
            return 0;
        });
        assertThat(calls).containsExactly(file.toAbsolutePath() + "|" + value + "|"
            + (com.sun.jna.platform.mac.XAttr.XATTR_NOFOLLOW | com.sun.jna.platform.mac.XAttr.XATTR_CREATE));
    }

    /** Review 1c, Minor 1: a native library that cannot load is an IOException with a reason, never an Error. */
    @Test void aQuarantineCallThatCannotLoadIsAnIoException() throws IOException {
        Path file = download("report.zip");
        var unloadable = new UnsatisfiedLinkError("Unable to load library 'c'");
        assertThatThrownBy(() -> DownloadMark.quarantine(file, "0081;6543210f;Jasper;0F8FAD5B-D9CB-469F-A165-70867728950E",
                (path, bytes, options) -> { throw unloadable; }))
            .isInstanceOf(FileSystemException.class).hasCause(unloadable)
            .extracting(failure -> ((FileSystemException) failure).getReason()).asString().contains("Unable to load library 'c'");
        assertThat(file).hasContent("zip");
    }

    /** Linux has no mark: nothing about the file changes. Through the seam everywhere. */
    @Test void theLinuxBranchLeavesTheFileUnchanged() throws IOException {
        assertUnchanged(LINUX);
    }

    @Test @EnabledOnOs(OS.LINUX) void onLinuxTheFileIsUnchanged() throws IOException {
        assertUnchanged(System.getProperty("os.name"));
    }

    @Test @EnabledOnOs(OS.MAC) void onMacOsTheQuarantineAttributeHoldsJaspersValue() throws Exception {
        Path file = download("report.zip");
        long before = Instant.now().getEpochSecond();
        DownloadMark.apply(file, SOURCE, PAGE, System.getProperty("os.name"));
        String value = xattr("-p", DownloadMark.QUARANTINE, file.toString());
        assertThat(value).matches("0081;[0-9a-f]{8};Jasper;[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}\n");
        long written = Long.parseLong(value.substring(5, 13), 16);
        assertThat(written).isBetween(before, Instant.now().getEpochSecond());
        assertThat(file).hasContent("zip");
    }

    @Test @EnabledOnOs(OS.MAC) void onMacOsAMarkAlreadyThereIsKept() throws Exception {
        Path file = download("report.zip");
        String chrome = "0081;6543210f;Chrome;0F8FAD5B-D9CB-469F-A165-70867728950E";
        xattr("-w", DownloadMark.QUARANTINE, chrome, file.toString());
        DownloadMark.apply(file, SOURCE, PAGE, System.getProperty("os.name"));
        assertThat(xattr("-p", DownloadMark.QUARANTINE, file.toString())).isEqualTo(chrome + "\n");
    }

    @Test @EnabledOnOs(OS.MAC) void onMacOsAFileWithoutWritePermissionThrowsTheReason() throws Exception {
        Path file = download("report.zip");
        var permissions = Files.getPosixFilePermissions(file);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
        try {
            assertThatThrownBy(() -> DownloadMark.apply(file, SOURCE, PAGE, System.getProperty("os.name")))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Permission denied")
                .extracting(failure -> ((FileSystemException) failure).getFile()).isEqualTo(file.toString());
        } finally {
            Files.setPosixFilePermissions(file, permissions);
        }
    }

    @Test @EnabledOnOs(OS.WINDOWS) void onWindowsTheStreamHoldsTheExactText() throws IOException {
        Path file = download("report.zip");
        DownloadMark.apply(file, SOURCE, PAGE, System.getProperty("os.name"));
        assertThat(stream(file))
            .isEqualTo("[ZoneTransfer]\r\nZoneId=3\r\nReferrerUrl=https://example.com/files/\r\nHostUrl=https://example.com/files/report.zip\r\n");
    }

    private Path download(String name) throws IOException { return Files.writeString(dir.resolve(name), "zip"); }

    private void assertUnchanged(String os) throws IOException {
        Path file = download("report.zip");
        FileTime modified = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(file, modified);
        List<String> attributes = streams(file);
        DownloadMark.apply(file, SOURCE, PAGE, os);
        assertThat(file).hasContent("zip");
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(modified);
        assertThat(streams(file)).isEqualTo(attributes);
    }

    private static UserDefinedFileAttributeView view(Path file) {
        return Files.getFileAttributeView(file, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static List<String> streams(Path file) throws IOException {
        var view = view(file);
        return view == null || !Files.getFileStore(file).supportsFileAttributeView(UserDefinedFileAttributeView.class)
            ? List.of() : view.list();
    }

    private static void assumeStreams(Path file) throws IOException {
        assumeThat(Files.getFileStore(file).supportsFileAttributeView(UserDefinedFileAttributeView.class))
            .as("the volume keeps user-defined attributes").isTrue();
    }

    private static String stream(Path file) throws IOException {
        var view = view(file);
        ByteBuffer buffer = ByteBuffer.allocate(view.size(DownloadMark.ZONE));
        view.read(DownloadMark.ZONE, buffer);
        return new String(buffer.array(), 0, buffer.position(), StandardCharsets.US_ASCII);
    }

    /** Runs macOS's {@code /usr/bin/xattr} and returns what it printed. */
    private static String xattr(String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("/usr/bin/xattr");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("xattr finished").isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }

    /** A view on a volume that keeps no streams, as some shares answer. */
    private static final class Unsupported implements UserDefinedFileAttributeView {
        @Override public String name() { return "user"; }
        @Override public List<String> list() { throw new UnsupportedOperationException("no streams"); }
        @Override public int size(String name) { throw new UnsupportedOperationException("no streams"); }
        @Override public int read(String name, ByteBuffer dst) { throw new UnsupportedOperationException("no streams"); }
        @Override public int write(String name, ByteBuffer src) { throw new UnsupportedOperationException("no streams"); }
        @Override public void delete(String name) { throw new UnsupportedOperationException("no streams"); }
    }
}
