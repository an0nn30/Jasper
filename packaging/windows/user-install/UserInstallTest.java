import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Standalone headless regression suite; the fake desktop keeps filesystem/rollback tests portable. */
public final class UserInstallTest {
    static int checks;
    static void equal(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    interface Operation { void run() throws Exception; }
    static void fails(Operation operation) throws Exception {
        checks++;
        try { operation.run(); } catch (Exception expected) { return; }
        throw new AssertionError("Expected refusal");
    }
    static final class FakeDesktop implements UserInstall.Desktop {
        String login;
        boolean stopped = true;
        boolean failSave;
        public void assertStopped(Path directory) throws IOException {
            if (!stopped) throw new IOException("running");
        }
        public String target(Path shortcut) throws IOException {
            return Files.exists(shortcut) ? Files.readString(shortcut) : null;
        }
        public void save(Path shortcut, Path executable) throws IOException {
            if (failSave) {
                Files.writeString(shortcut, "partial shortcut");
                throw new IOException("shortcut denied");
            }
            Files.writeString(shortcut, executable.toString());
        }
        public void removeLogin(Path executable) {
            if (("\"" + executable + "\" --background").equals(login)) login = null;
        }
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("Jasper install test ");
        try {
            Path source = root.resolve("image"), directory = root.resolve("JasperApp");
            Path shortcut = root.resolve("Programs/Jasper.lnk"), data = root.resolve("jasper/config.toml");
            for (String file : new String[]{"Jasper.exe", "app/Jasper.cfg", "runtime/bin/java.exe",
                    "app/plugins/example/example.jar"}) {
                Path path = source.resolve(file);
                Files.createDirectories(path.getParent());
                Files.writeString(path, "fixture");
            }
            Files.createDirectories(data.getParent());
            Files.writeString(data, "user data");
            FakeDesktop desktop = new FakeDesktop();
            var installer = new UserInstall(desktop);
            installer.install(source, directory, shortcut);
            equal(directory.resolve("Jasper.exe").toString(), desktop.target(shortcut));
            equal("fixture", Files.readString(directory.resolve("app/plugins/example/example.jar")));
            Files.writeString(source.resolve("Jasper.exe"), "updated");
            installer.install(source, directory, shortcut);
            equal("updated", Files.readString(directory.resolve("Jasper.exe")));

            Files.writeString(source.resolve("Jasper.exe"), "rollback");
            desktop.failSave = true;
            fails(() -> installer.install(source, directory, shortcut));
            equal("updated", Files.readString(directory.resolve("Jasper.exe")));
            equal(directory.resolve("Jasper.exe").toString(), desktop.target(shortcut));
            desktop.failSave = false;
            fails(() -> installer.install(source, directory, shortcut, image -> { throw new IllegalStateException("Cygwin failed"); }));
            equal("updated", Files.readString(directory.resolve("Jasper.exe")));
            equal(directory.resolve("Jasper.exe").toString(), desktop.target(shortcut));
            equal("user data", Files.readString(data));
            desktop.stopped = false;
            fails(() -> installer.install(source, directory, shortcut));
            fails(() -> installer.uninstall(directory, shortcut));
            desktop.stopped = true;
            desktop.login = "other installation";
            installer.uninstall(directory, shortcut);
            equal(false, Files.exists(directory));
            equal(false, Files.exists(shortcut));
            equal("other installation", desktop.login);
            equal("user data", Files.readString(data));
            installer.uninstall(directory, shortcut);

            installer.install(source, directory, shortcut);
            desktop.login = "\"" + directory.resolve("Jasper.exe") + "\" --background";
            installer.uninstall(directory, shortcut);
            equal(null, desktop.login);
            desktop.failSave = true;
            fails(() -> installer.install(source, directory, shortcut));
            equal(false, Files.exists(directory));
            equal(false, Files.exists(shortcut));
            desktop.failSave = false;
            Path incomplete = root.resolve("incomplete");
            Files.createDirectory(incomplete);
            fails(() -> installer.install(incomplete, directory, shortcut));
            equal(false, Files.exists(directory));

            Files.createDirectory(directory);
            Files.writeString(directory.resolve("keep"), "foreign");
            fails(() -> installer.install(source, directory, shortcut));
            fails(() -> installer.uninstall(directory, shortcut));
            equal("foreign", Files.readString(directory.resolve("keep")));
            UserInstall.deleteTree(directory);
            Files.writeString(shortcut, "another executable");
            fails(() -> installer.install(source, directory, shortcut));
            installer.uninstall(directory, shortcut);
            equal("another executable", Files.readString(shortcut));
            Files.delete(shortcut);

            // Unix symlinks cover portable refusal; Windows junctions are exercised below.
            if (!UserInstall.windows()) {
                Files.createSymbolicLink(directory, data.getParent());
                fails(() -> installer.install(source, directory, shortcut));
                fails(() -> installer.uninstall(directory, shortcut));
                Files.delete(directory);
            }
            equal("user data", Files.readString(data));
            if (UserInstall.windows()) windows(root, source, directory, shortcut);
            System.out.println("User install tests: " + checks + " checks passed");
        } finally { UserInstall.deleteTree(root); }
    }

    static void windows(Path root, Path source, Path directory, Path shortcut) throws Exception {
        String key = "Software\\JasperInstallTest-" + java.util.UUID.randomUUID();
        try (var desktop = new WindowsDesktop(key)) {
            var installer = new UserInstall(desktop);
            installer.install(source, directory, shortcut);
            equal(directory.resolve("Jasper.exe").toString(), desktop.target(shortcut));
            installer.install(source, directory, shortcut);
            String previous = Files.readString(directory.resolve("Jasper.exe"));
            Files.writeString(source.resolve("Jasper.exe"), "native rollback");
            Files.setAttribute(shortcut, "dos:readonly", true);
            try {
                fails(() -> installer.install(source, directory, shortcut));
                equal(previous, Files.readString(directory.resolve("Jasper.exe")));
            } finally { Files.setAttribute(shortcut, "dos:readonly", false); }
            com.sun.jna.platform.win32.Advapi32Util.registryCreateKey(
                    com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, key);
            com.sun.jna.platform.win32.Advapi32Util.registrySetStringValue(
                    com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, key, "Jasper",
                    "\"" + directory.resolve("Jasper.exe") + "\" --background");
            installer.uninstall(directory, shortcut);
            equal(false, com.sun.jna.platform.win32.Advapi32Util.registryValueExists(
                    com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, key, "Jasper"));
            equal(false, Files.exists(shortcut));
            // mklink is a cmd built-in; a directory junction needs neither scripts nor elevation.
            Process junction = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J",
                    directory.toString(), root.resolve("jasper").toString()).inheritIO().start();
            equal(0, junction.waitFor());
            try {
                fails(() -> installer.install(source, directory, shortcut));
                fails(() -> installer.uninstall(directory, shortcut));
            } finally { Files.delete(directory); }
        } finally {
            if (com.sun.jna.platform.win32.Advapi32Util.registryKeyExists(
                    com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, key))
                com.sun.jna.platform.win32.Advapi32Util.registryDeleteKey(
                        com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, key);
        }
    }
}
