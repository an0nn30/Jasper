package dev.jasper.editor.ui;

import dev.jasper.editor.vim.KeyInput;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * IdeaVim's shortcut-conflict notification (Vim IdeaVim keys spec 2): the first time Vim takes a key that is also a
 * shortcut outside Vim, the Editor says so once, ever, and offers to give the key to the Editor with a
 * {@code sethandler} line in the vimrc. The keys it has told of are kept in {@code shortcut-conflicts} in the Editor's
 * data folder, one key in Vim notation per line.
 */
public final class ShortcutConflicts {
    private final Path file;
    private final NotificationGroup group;
    private final Consumer<Path> openVimrc;
    private final Runnable reloadVimrc;
    private final Supplier<Path> vimrc;
    private final System.Logger log;
    private final boolean mac;
    /** The keys told of, read from {@link #file} on the first conflict; null until then. */
    private Set<String> told;

    /**
     * @param file where the keys told of are kept: {@code shortcut-conflicts} in the Editor's data folder
     * @param group where the notification goes
     * @param openVimrc opens the vimrc in an Editor tab, for Configure…
     * @param reloadVimrc reads the vimrc again, as Reload vimrc does
     * @param vimrc the vimrc's path
     * @param log where a file that cannot be read or written is reported
     */
    public ShortcutConflicts(Path file, NotificationGroup group, Consumer<Path> openVimrc, Runnable reloadVimrc, Supplier<Path> vimrc,
                             System.Logger log) {
        this(file, group, openVimrc, reloadVimrc, vimrc, log, KeyNames.MAC);
    }

    ShortcutConflicts(Path file, NotificationGroup group, Consumer<Path> openVimrc, Runnable reloadVimrc, Supplier<Path> vimrc,
                      System.Logger log, boolean mac) {
        this.file = file;
        this.group = group;
        this.openVimrc = openVimrc;
        this.reloadVimrc = reloadVimrc;
        this.vimrc = vimrc;
        this.log = log;
        this.mac = mac;
    }

    /**
     * Vim took {@code key}, which is also a shortcut outside Vim: the first time ever, the notification.
     *
     * @param key the key
     * @param editorBinds whether the Editor binds it; false when only the window does
     */
    public void taken(KeyInput key, boolean editorBinds) {
        String notation = key.notation();
        if (!told().add(notation)) return;
        try {
            Files.writeString(file, notation + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException failed) {
            // The key still counts for this session.
            log.log(System.Logger.Level.WARNING, "Could not record a shortcut conflict in " + file, failed);
        }
        String title = KeyNames.describe(KeyNames.stroke(key), mac) + " is a shortcut for both Vim and " + (editorBinds ? "the Editor" : "Jasper");
        group.post(NotificationSpec.of(Severity.INFO, title).withContent("It is now used by Vim, but you can change this.")
            .withAction(NotificationAction.of("Use as Editor shortcut", () -> useAsEditorShortcut(notation)))
            .withAction(NotificationAction.of("Configure…", this::configure)));
    }

    private Set<String> told() {
        if (told != null) return told;
        told = new HashSet<>();
        try {
            if (Files.exists(file)) for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) if (!line.isBlank()) told.add(line.strip());
        } catch (IOException | RuntimeException unreadable) {
            log.log(System.Logger.Level.WARNING, "Could not read the shortcut conflicts in " + file + "; every key counts as new", unreadable);
        }
        return told;
    }

    /** Appends {@code sethandler <key> a:ide} as the vimrc's last line and reads the vimrc again. */
    private void useAsEditorShortcut(String notation) {
        Path path = vimrc.get();
        try {
            createParent(path);
            String text = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
            String line = (text.isEmpty() || text.endsWith("\n") ? "" : "\n") + "sethandler " + notation + " a:ide\n";
            Files.writeString(path, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException failed) {
            group.error("Could not update the vimrc", path + ": " + reason(failed));
            return;
        }
        reloadVimrc.run();
    }

    /** Configure…: the vimrc, created empty if missing, in an Editor tab. */
    private void configure() {
        Path path = vimrc.get();
        try {
            createParent(path);
            if (!Files.exists(path)) Files.createFile(path);
        } catch (IOException | RuntimeException failed) {
            group.error("Could not open the vimrc", path + ": " + reason(failed));
            return;
        }
        openVimrc.accept(path);
    }

    private static void createParent(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
    }

    private static String reason(Exception failed) {
        String message = failed.getMessage();
        return message == null || message.isBlank() ? failed.getClass().getSimpleName() : failed.getClass().getSimpleName() + ": " + message;
    }
}
