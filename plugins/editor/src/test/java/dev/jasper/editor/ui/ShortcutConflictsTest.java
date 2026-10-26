package dev.jasper.editor.ui;

import dev.jasper.editor.vim.KeyInput;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.testing.FakeNotifications;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ShortcutConflictsTest {
    private static final KeyInput CTRL_F = KeyInput.ctrl('f');
    private static final String TITLE = "Ctrl+F is a shortcut for both Vim and the Editor";

    @TempDir Path dir;
    private final FakeNotifications notifications = new FakeNotifications();
    private final List<Path> opened = new ArrayList<>();
    private final AtomicInteger reloads = new AtomicInteger();

    private ShortcutConflicts conflicts(Path file, Path vimrc) {
        return new ShortcutConflicts(file, notifications.group("dev.jasper.editor"), opened::add, reloads::incrementAndGet, () -> vimrc,
            System.getLogger(ShortcutConflictsTest.class.getName()), false);
    }

    private ShortcutConflicts conflicts() { return conflicts(dir.resolve("shortcut-conflicts"), dir.resolve("vimrc")); }

    @Test void firstTakeNotifiesOnce() throws Exception {
        var conflicts = conflicts();
        conflicts.taken(CTRL_F, true);
        conflicts.taken(CTRL_F, true);
        assertThat(notifications.lines()).containsExactly("dev.jasper.editor|INFO|" + TITLE + "|It is now used by Vim, but you can change this.");
        assertThat(notifications.posts().getFirst().spec().actions()).extracting(NotificationAction::label)
            .containsExactly("Use as Editor shortcut", "Configure…");
        assertThat(Files.readAllLines(dir.resolve("shortcut-conflicts"))).containsExactly("<C-f>");
    }

    @Test void windowOnlyNamesJasper() {
        conflicts().taken(KeyInput.ctrl('t'), false);
        assertThat(notifications.posts().getFirst().spec().title()).isEqualTo("Ctrl+T is a shortcut for both Vim and Jasper");
    }

    @Test void rememberedAcrossInstances() {
        conflicts().taken(CTRL_F, true);
        conflicts().taken(CTRL_F, true);
        assertThat(notifications.posts()).hasSize(1);
    }

    @Test void useAsEditorShortcutAppendsAndReloads() throws Exception {
        var conflicts = conflicts(dir.resolve("shortcut-conflicts"), dir.resolve("data/vimrc"));
        conflicts.taken(CTRL_F, true);
        assertThat(notifications.runAction(TITLE, "Use as Editor shortcut")).isTrue();
        assertThat(Files.readString(dir.resolve("data/vimrc"))).isEqualTo("sethandler <C-f> a:ide\n");
        assertThat(reloads).hasValue(1);
        assertThat(notifications.posts().getFirst().expired()).isTrue();

        Path other = dir.resolve("other-vimrc");
        Files.writeString(other, "set hlsearch", StandardCharsets.UTF_8);
        conflicts(dir.resolve("other-conflicts"), other).taken(CTRL_F, true);
        assertThat(notifications.runAction(TITLE, "Use as Editor shortcut")).isTrue();
        assertThat(Files.readString(other)).isEqualTo("set hlsearch\nsethandler <C-f> a:ide\n");
        assertThat(reloads).hasValue(2);
    }

    @Test void unwritableVimrcPostsAnError() throws Exception {
        Path file = Files.writeString(dir.resolve("a-file"), "x");
        conflicts(dir.resolve("shortcut-conflicts"), file.resolve("vimrc")).taken(CTRL_F, true);
        assertThat(notifications.runAction(TITLE, "Use as Editor shortcut")).isTrue();
        assertThat(notifications.posts()).hasSize(2);
        assertThat(notifications.posts().get(1).spec().severity()).hasToString("ERROR");
        assertThat(notifications.posts().get(1).spec().title()).isEqualTo("Could not update the vimrc");
        assertThat(notifications.posts().get(1).spec().content()).isNotEmpty();
        assertThat(reloads).hasValue(0);
    }

    @Test void unreadableConflictFileIsEmpty() throws Exception {
        Path folder = Files.createDirectory(dir.resolve("shortcut-conflicts"));
        var conflicts = conflicts(folder, dir.resolve("vimrc"));
        conflicts.taken(CTRL_F, true);
        conflicts.taken(CTRL_F, true);
        assertThat(notifications.posts()).as("the key still counts for this session").hasSize(1);
    }

    @Test void configureCreatesAndOpens() throws Exception {
        Path vimrc = dir.resolve("data/vimrc");
        conflicts(dir.resolve("shortcut-conflicts"), vimrc).taken(CTRL_F, true);
        assertThat(notifications.runAction(TITLE, "Configure…")).isTrue();
        assertThat(vimrc).exists();
        assertThat(Files.readString(vimrc)).isEmpty();
        assertThat(opened).containsExactly(vimrc);
        assertThat(reloads).hasValue(0);
    }

    @Test void macNamesTheKeyWithSymbols() {
        new ShortcutConflicts(dir.resolve("shortcut-conflicts"), notifications.group("g"), opened::add, reloads::incrementAndGet,
            () -> dir.resolve("vimrc"), System.getLogger("test"), true).taken(KeyInput.parse("<D-k>").getFirst(), false);
        assertThat(notifications.posts().getFirst().spec().title()).isEqualTo("⌘K is a shortcut for both Vim and Jasper");
    }
}
