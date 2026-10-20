import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** Build-time tool only. Keeps installed application files separate from Jasper's user data. */
public final class UserInstall {
    static final String MARKER = ".jasper-user-install";
    static final String IDENTITY = "Jasper standard-launcher user installation v1";
    interface Desktop {
        void assertStopped(Path directory) throws IOException;
        String target(Path shortcut) throws IOException;
        void save(Path shortcut, Path executable) throws IOException;
        void removeLogin(Path executable) throws IOException;
    }
    private final Desktop desktop;
    UserInstall(Desktop desktop) { this.desktop = desktop; }

    public static void main(String[] args) throws Exception {
        if (!windows()) throw new IOException("Windows user installation requires Windows.");
        if (args.length == 0 || !(args[0].equals("install") && args.length == 2
                || args[0].equals("uninstall") && args.length == 1))
            throw new IllegalArgumentException("Usage: UserInstall install <image> | uninstall");
        try (var desktop = new WindowsDesktop()) {
            var installer = new UserInstall(desktop);
            Path directory = WindowsDesktop.appData().resolve("JasperApp");
            Path shortcut = WindowsDesktop.programs().resolve("Jasper.lnk");
            if (args[0].equals("install")) {
                installer.install(Path.of(args[1]).toAbsolutePath(), directory, shortcut);
                System.out.println("Installed Jasper at " + directory + ". Open Jasper from the Start Menu.");
            } else {
                installer.uninstall(directory, shortcut);
                System.out.println("Uninstalled Jasper. User settings, plugins and data are preserved.");
            }
        }
    }

    void install(Path source, Path directory, Path shortcut) throws IOException {
        assertPlainTree(source);
        for (String file : new String[]{"Jasper.exe", "app/Jasper.cfg", "runtime/bin/java.exe"})
            if (!Files.isRegularFile(source.resolve(file))) throw new IOException("Incomplete app image: " + file);
        assertOwned(directory);
        desktop.assertStopped(directory);
        Path executable = directory.resolve("Jasper.exe");
        assertPlainTree(shortcut);
        if (exists(shortcut) && !executable.toString().equalsIgnoreCase(desktop.target(shortcut)))
            throw new IOException("Another installation owns " + shortcut);
        byte[] oldShortcut = exists(shortcut) ? Files.readAllBytes(shortcut) : null;
        Path stage = directory.resolveSibling(directory.getFileName() + ".staging-" + UUID.randomUUID());
        Path backup = directory.resolveSibling(directory.getFileName() + ".backup-" + UUID.randomUUID());
        boolean installed = false;
        try {
            copyTree(source, stage);
            Files.writeString(stage.resolve(MARKER), IDENTITY);
            if (exists(directory)) Files.move(directory, backup);
            Files.move(stage, directory);
            installed = true;
            Files.createDirectories(shortcut.getParent());
            desktop.save(shortcut, executable);
        } catch (IOException | RuntimeException failure) {
            // Restore the app before touching a potentially locked shortcut.
            try {
                if (installed) deleteTree(directory);
                if (exists(backup)) Files.move(backup, directory);
            } catch (IOException rollback) { failure.addSuppressed(rollback); }
            if (installed) {
                try {
                    if (oldShortcut == null) Files.deleteIfExists(shortcut);
                    else Files.write(shortcut, oldShortcut);
                } catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        } finally {
            deleteTree(stage);
        }
        deleteTree(backup);
    }

    void uninstall(Path directory, Path shortcut) throws IOException {
        assertOwned(directory);
        desktop.assertStopped(directory);
        Path executable = directory.resolve("Jasper.exe");
        assertPlainTree(shortcut);
        if (executable.toString().equalsIgnoreCase(desktop.target(shortcut))) Files.delete(shortcut);
        desktop.removeLogin(executable);
        deleteTree(directory);
    }

    static boolean windows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows"); }
    static boolean exists(Path path) { return Files.exists(path, LinkOption.NOFOLLOW_LINKS); }
    static boolean linked(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) return true;
        return windows() && WindowsDesktop.reparsePoint(path);
    }
    static void assertPlainTree(Path path) throws IOException {
        if (!exists(path)) return;
        if (linked(path)) throw new IOException("Refusing to modify a linked installation: " + path);
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (var children = Files.newDirectoryStream(path)) {
                for (Path child : children) assertPlainTree(child);
            }
        }
    }
    static void assertOwned(Path directory) throws IOException {
        if (!exists(directory)) return;
        assertPlainTree(directory);
        Path marker = directory.resolve(MARKER);
        if (!Files.isRegularFile(marker) || !IDENTITY.equals(Files.readString(marker)))
            throw new IOException("Refusing to replace or remove an unrecognized directory: " + directory);
    }
    static void copyTree(Path source, Path target) throws IOException {
        Files.copy(source, target);
        if (Files.isDirectory(source)) {
            try (var children = Files.newDirectoryStream(source)) {
                for (Path child : children) copyTree(child, target.resolve(child.getFileName()));
            }
        }
    }
    static void deleteTree(Path path) throws IOException {
        if (!exists(path)) return;
        if (!linked(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (var children = Files.newDirectoryStream(path)) {
                for (Path child : children) deleteTree(child);
            }
        }
        Files.delete(path);
    }
}
