package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LoginItemTest {
    @TempDir Path home;

    private static final String MAC_APP = "/Applications/Jasper.app/Contents/MacOS/Jasper";

    @Test void macOsWritesALaunchAgentAndRunsNothing() {
        var plan = LoginItem.plan("Mac OS X", true, MAC_APP, Path.of("/Users/example"));
        assertThat(plan.file()).isEqualTo(
            Path.of("/Users/example/Library/LaunchAgents/dev.jasper.background.plist"));
        assertThat(plan.contents())
            .contains("<string>dev.jasper.background</string>")
            .contains("<string>" + MAC_APP + "</string>")
            .contains("<string>--background</string>")
            .contains("<key>RunAtLoad</key>\n    <true/>")
            .contains("<key>KeepAlive</key>\n    <false/>")
            .contains("<string>Aqua</string>");
        // launchd loads the agent at the next login, which is all this feature wants: the process
        // is already running when the setting is enabled.
        assertThat(plan.commands()).isEmpty();
    }

    @Test void macOsDisablingDeletesThePlistAndCarriesNoContents() {
        var plan = LoginItem.plan("Mac OS X", false, MAC_APP, Path.of("/Users/example"));
        assertThat(plan.file())
            .isEqualTo(Path.of("/Users/example/Library/LaunchAgents/dev.jasper.background.plist"));
        assertThat(plan.contents()).isNull();
        assertThat(plan.commands()).isEmpty();
    }

    @Test void windowsWritesARunValueAndDeletesIt() {
        String exe = "C:\\Program Files\\Jasper\\Jasper.exe";
        var on = LoginItem.plan("Windows 11", true, exe, Path.of("C:\\Users\\example"));
        assertThat(on.file()).isNull();
        // The interior quotes are escaped so that ProcessBuilder's own quoting of this argument
        // (it contains a space, from "Program Files") reproduces "<exe>" --background verbatim on
        // reg.exe's side rather than a corrupted argument. See LoginItem.plan's comment.
        assertThat(on.commands()).containsExactly(java.util.List.of("reg", "add",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            "/v", "Jasper", "/t", "REG_SZ", "/d", "\\\"" + exe + "\\\" --background", "/f"));
        var off = LoginItem.plan("Windows 11", false, exe, Path.of("C:\\Users\\example"));
        assertThat(off.commands()).containsExactly(java.util.List.of("reg", "delete",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "Jasper", "/f"));
    }

    @Test void anUninstalledJasperRegistersNothingOnAnyPlatform() {
        // No jpackage.app-path means a development run: residency still works, autostart cannot.
        for (String os : new String[]{"Mac OS X", "Windows 11", "Linux"}) {
            for (boolean enabled : new boolean[]{true, false}) {
                assertThat(LoginItem.plan(os, enabled, null, home)).as(os).isEqualTo(LoginItem.Plan.NONE);
                assertThat(LoginItem.plan(os, enabled, "  ", home)).as(os).isEqualTo(LoginItem.Plan.NONE);
            }
        }
    }

    @Test void anAppPathWithXmlMetacharactersCannotBreakThePlist() {
        var plan = LoginItem.plan("Mac OS X", true, "/Apps/A&B <beta>/Jasper", Path.of("/Users/example"));
        assertThat(plan.contents())
            .contains("<string>/Apps/A&amp;B &lt;beta&gt;/Jasper</string>")
            .doesNotContain("<beta>");
    }

    @Test void applyingWritesDeletesAndIsSafeToRepeat() throws Exception {
        Path plist = home.resolve("Library/LaunchAgents/dev.jasper.background.plist");
        LoginItem.apply(new LoginItem.Plan(plist, "<plist/>", java.util.List.of()));
        assertThat(plist).exists().content().isEqualTo("<plist/>");
        LoginItem.apply(new LoginItem.Plan(plist, "<plist/>", java.util.List.of()));
        assertThat(plist).exists();
        LoginItem.apply(new LoginItem.Plan(plist, null, java.util.List.of()));
        assertThat(plist).doesNotExist();
        LoginItem.apply(new LoginItem.Plan(plist, null, java.util.List.of()));  // Already gone.
        assertThat(plist).doesNotExist();
        LoginItem.apply(LoginItem.Plan.NONE);  // Does nothing at all.
        assertThat(Files.exists(home.resolve("Library"))).isTrue();
    }

    @Test void applyDoesNotThrowWhenTheExecutableDoesNotExist() {
        // ProcessBuilder.start throws IOException here; apply must log and carry on. A launch is
        // never worth failing over a login item.
        var missing = java.util.List.of("jasper-no-such-command-" + java.util.UUID.randomUUID());
        assertThatNoException().isThrownBy(() ->
            LoginItem.apply(new LoginItem.Plan(null, null, java.util.List.of(missing))));
    }

    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    @Test void applyToleratesACommandThatExitsNonzero() {
        // `reg delete` exits nonzero when the value is already absent, which is the state we want.
        // Nothing here touches the registry or launchd: it is a shell that exits 1 and nothing else.
        assertThatNoException().isThrownBy(() -> LoginItem.apply(new LoginItem.Plan(null, null,
            java.util.List.of(java.util.List.of("/bin/sh", "-c", "exit 1")))));
    }

    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    @Test void onlyACommandNamedDeleteIsToleratedWhenItExitsNonzero() {
        // Nothing here touches the registry or launchd: it is a shell that exits 1 and nothing
        // else. Whether "delete" is one of the command's own words is what run() now uses to
        // decide leniency, rather than guessing leniency from the exit code alone.
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(LoginItem.class.getName());
        java.util.logging.Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        java.util.List<java.util.logging.LogRecord> records = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.setLevel(java.util.logging.Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            // No "delete" anywhere: a real failure, and it must be loud.
            LoginItem.apply(new LoginItem.Plan(null, null,
                java.util.List.of(java.util.List.of("/bin/sh", "-c", "exit 1"))));
            // "delete" is one of the command's own words, the same shape as `reg delete`: tolerated.
            LoginItem.apply(new LoginItem.Plan(null, null,
                java.util.List.of(java.util.List.of("/bin/sh", "-c", "exit 1", "delete"))));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
        assertThat(records).extracting(java.util.logging.LogRecord::getLevel)
            .as("one WARNING for the real failure, one INFO for the delete-shaped tolerated case")
            .containsExactly(java.util.logging.Level.WARNING, java.util.logging.Level.INFO);
    }

    @Test void applyDoesNotThrowWhenTheFileCannotBeWritten() throws Exception {
        // A regular file where the parent directory should be: createDirectories fails.
        Path blocked = home.resolve("blocked");
        Files.createFile(blocked);
        Path target = blocked.resolve("dev.jasper.background.plist");
        assertThatNoException().isThrownBy(() ->
            LoginItem.apply(new LoginItem.Plan(target, "<plist/>", java.util.List.of())));
        assertThat(target).doesNotExist();
        // And the delete direction is just as forgiving.
        assertThatNoException().isThrownBy(() ->
            LoginItem.apply(new LoginItem.Plan(target, null, java.util.List.of())));
    }

    @Test void linuxUsesXdgAutostartAndTheInstalledExecutable() {
        Path xdg = home.resolve("xdg config");
        var plan = LoginItem.plan("Linux", true, "/opt/Jasper Work/bin/Jasper", home,
            Map.of("XDG_CONFIG_HOME", xdg.toString()));
        assertThat(plan.file()).isEqualTo(xdg.resolve("autostart/dev.jasper.background.desktop"));
        assertThat(plan.commands()).isEmpty();
        assertThat(plan.contents()).isEqualTo("""
            [Desktop Entry]
            Type=Application
            Name=Jasper
            Exec="/opt/Jasper Work/bin/Jasper" --background
            TryExec=/opt/Jasper Work/bin/Jasper
            Terminal=false
            """);
        assertThat(LoginItem.plan("Linux", false, "/opt/Jasper Work/bin/Jasper", home,
            Map.of("XDG_CONFIG_HOME", xdg.toString())).contents()).isNull();
    }

    @Test void unusableXdgRootsFallBackToTheUsersConfigDirectory() {
        for (String root : List.of("", "relative", "bad\0root")) {
            var plan = LoginItem.plan("Linux", true, "/opt/jasper-workstation/bin/Jasper", home,
                Map.of("XDG_CONFIG_HOME", root));
            assertThat(plan.file()).isEqualTo(home.resolve(".config/autostart/dev.jasper.background.desktop"));
        }
    }

    @Test void desktopExecQuotesMetacharactersWithoutAShell() {
        assertThat(LoginItem.execToken("/opt/a b/Jasper")).isEqualTo("\"/opt/a b/Jasper\"");
        assertThat(LoginItem.execToken("/opt/100%/Jasper")).isEqualTo("\"/opt/100%%/Jasper\"");
        String encoded = LoginItem.execToken("/opt/$x`q\"\\/Jasper");
        assertThat(encoded).isEqualTo("\"/opt/\\\\$x\\\\`q\\\\\"\\\\\\\\/Jasper\"");
        assertThat(LoginItem.plan("Linux", true, "/opt/$x/Jasper", home).contents())
            .contains("TryExec=/opt/$x/Jasper\n").doesNotContain("sh -c", "OnlyShowIn=");
    }

    @Test void invalidExecutablesCannotInjectDesktopEntries() {
        for (String executable : List.of("relative/Jasper", "/opt/a\nTerminal=true", "/opt/a=b/Jasper"))
            assertThatIllegalArgumentException().isThrownBy(() ->
                LoginItem.plan("Linux", true, executable, home));
    }

    @Test void linuxCanRemoveItsEntryEvenAfterTheExecutableDisappears() {
        var on = LoginItem.plan("Linux", true, "/missing/Jasper", home);
        LoginItem.apply(on);
        assertThat(on.file()).exists();
        LoginItem.apply(LoginItem.plan("Linux", false, "/missing/Jasper", home));
        assertThat(on.file()).doesNotExist();
    }

    @Test void aFailedReplacementLeavesNoTemporaryFile() throws Exception {
        Path parent = home.resolve("autostart");
        Path target = parent.resolve("dev.jasper.background.desktop");
        Files.createDirectories(target);
        Path existing = target.resolve("existing");
        Files.writeString(existing, "preserved");
        assertThatNoException().isThrownBy(() ->
            LoginItem.apply(new LoginItem.Plan(target, "replacement", List.of())));
        assertThat(existing).content().isEqualTo("preserved");
        try (var files = Files.list(parent)) {
            assertThat(files).containsExactly(target);
        }
    }

    @EnabledOnOs(OS.LINUX)
    @EnabledIfSystemProperty(named = "jasper.test.desktopEntryParser", matches = "true")
    @Test void aRealDesktopParserPreservesTheExecutableAndBackgroundArgument() throws Exception {
        Path directory = home.resolve("space % $ ` quote\" back\\slash");
        Files.createDirectories(directory);
        Path executable = directory.resolve("Jasper");
        Files.writeString(executable, "#!/bin/sh\nprintf '%s\\n' \"$#\" \"$1\" > \"$JASPER_LOGIN_TEST_OUTPUT\"\n");
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        Path output = home.resolve("arguments.txt");
        var plan = LoginItem.plan("Linux", true, executable.toString(), home,
            Map.of("XDG_CONFIG_HOME", home.resolve("test-xdg").toString()));
        LoginItem.apply(plan);
        Path log = home.resolve("gio.log");
        ProcessBuilder builder = new ProcessBuilder("gio", "launch", plan.file().toString())
            .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("JASPER_LOGIN_TEST_OUTPUT", output.toString());
        Process process = builder.start();
        try {
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).as(Files.readString(log)).isZero();
            String expected = "1\n--background\n";
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ((!Files.exists(output) || !Files.readString(output).equals(expected))
                && System.nanoTime() < deadline)
                Thread.sleep(10);
            assertThat(Files.readString(output)).isEqualTo(expected);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
