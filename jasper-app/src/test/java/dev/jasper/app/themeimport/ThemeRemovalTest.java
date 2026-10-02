package dev.jasper.app.themeimport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class ThemeRemovalTest {
    @TempDir Path home;
    @Test void removalRejectsBundledOutsideAndSymlinkTargets() throws Exception {
        var files = new ThemeFiles(home); Path target = files.create(ThemeFilesTest.named("User"));
        Path outside = Files.writeString(home.resolve("outside.toml"), "keep");
        Path link = target.resolveSibling("link.toml"); Files.createSymbolicLink(link, outside);
        for (Path path : java.util.List.of(outside, link, Path.of("jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.toml")))
            assertThatThrownBy(() -> files.prepareRemoval(path)).isInstanceOf(IOException.class);
        assertThat(Files.readString(outside)).isEqualTo("keep");
    }
    @Test void removalRechecksHashAndFileIdentity() throws Exception {
        var files = new ThemeFiles(home); Path target = files.create(ThemeFilesTest.named("User"));
        var intent = files.prepareRemoval(target);
        Files.writeString(target, "edited");
        assertThatThrownBy(() -> files.delete(intent)).isInstanceOf(IOException.class);
        assertThat(Files.readString(target)).isEqualTo("edited");
        var edited = files.prepareRemoval(target);
        Path replacement = Files.writeString(target.resolveSibling("replacement.tmp"), "edited");
        Files.move(replacement, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertThatThrownBy(() -> files.delete(edited)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> files.trash(edited, path -> { throw new AssertionError("must not call trash"); })).isInstanceOf(IOException.class);
        assertThat(Files.readString(target)).isEqualTo("edited");
        var fresh = files.prepareRemoval(target); Files.delete(target);
        Path foreign = Files.writeString(home.resolve("foreign.toml"), "foreign"); Files.createSymbolicLink(target, foreign);
        assertThatThrownBy(() -> files.delete(fresh)).isInstanceOf(IOException.class);
        assertThat(Files.readString(foreign)).isEqualTo("foreign");
    }
    @Test void failedTrashDoesNotDelete() throws Exception {
        var files = new ThemeFiles(home); Path target = files.create(ThemeFilesTest.named("User")); var intent = files.prepareRemoval(target);
        assertThat(files.trash(intent, path -> false)).isFalse(); assertThat(target).exists();
        assertThat(files.trash(intent, path -> true)).isFalse(); assertThat(target).exists();
        assertThatThrownBy(() -> files.trash(intent, path -> { throw new IllegalStateException("unavailable"); })).isInstanceOf(IOException.class);
        assertThat(target).exists();
    }
    @Test void successfulTrashMovesOnlyThePlannedFile() throws Exception {
        var files = new ThemeFiles(home); Path target = files.create(ThemeFilesTest.named("User"));
        Path trash = Files.createDirectory(home.resolve("trash")).resolve("saved.toml");
        assertThat(files.trash(files.prepareRemoval(target), path -> {
            assertThat(path).isEqualTo(target);
            try { Files.move(path, trash); return true; } catch (IOException failure) { throw new UncheckedIOException(failure); }
        })).isTrue();
        assertThat(target).doesNotExist(); assertThat(trash).exists();
    }
    @Test void explicitDeleteUsesSameFrozenIntent() throws Exception {
        var files = new ThemeFiles(home); Path target = files.create(ThemeFilesTest.named("User")); var intent = files.prepareRemoval(target);
        assertThat(files.trash(intent, path -> false)).isFalse(); files.delete(intent); assertThat(target).doesNotExist();
    }
    @Test void removalRequiresWorkerThread() throws Exception {
        var files = new ThemeFiles(home); Path target = files.create(ThemeFilesTest.named("User")); var intent = files.prepareRemoval(target);
        edt(() -> {
            assertThatThrownBy(() -> files.prepareRemoval(target)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> files.trash(intent, path -> true)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> files.delete(intent)).isInstanceOf(IllegalStateException.class);
        });
    }
}
