package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.commandline.CommandInstall.Kind;
import dev.jasper.app.commandline.CommandInstall.State;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class LinkPlacementTest {
    @TempDir Path dir;

    private Path client() throws IOException {
        Path client = dir.resolve("Jasper.app/Contents/Resources/bin/jasper");
        Files.createDirectories(client.getParent());
        return Files.writeString(client, "#!/bin/sh\n");
    }

    @Test void installsALinkUninstallsItAndSaysWhereItStands() throws IOException {
        Path client = client();
        Path link = dir.resolve("bin/jasper");
        var placement = new LinkPlacement(link, client, null);
        assertThat(placement.state()).isEqualTo(new State(Kind.NOT_INSTALLED, "Not installed"));
        placement.install(false);
        assertThat(Files.readSymbolicLink(link)).isEqualTo(client);
        assertThat(placement.state()).isEqualTo(new State(Kind.INSTALLED, "Installed at " + link));
        placement.install(false);
        placement.uninstall();
        assertThat(Files.exists(link, LinkOption.NOFOLLOW_LINKS)).isFalse();
        assertThat(placement.state().kind()).isEqualTo(Kind.NOT_INSTALLED);
    }

    @Test void anotherProgramIsReplacedOnlyWhenAskedAndNeverRemoved() throws IOException {
        Path client = client();
        Path link = dir.resolve("bin/jasper");
        Files.createDirectories(link.getParent());
        Files.writeString(link, "someone else's");
        var placement = new LinkPlacement(link, client, null);
        assertThat(placement.state()).isEqualTo(new State(Kind.OTHER_PROGRAM, link + " is another program"));
        assertThatIllegalStateException().isThrownBy(() -> placement.install(false));
        placement.uninstall();
        assertThat(Files.readString(link)).isEqualTo("someone else's");
        placement.install(true);
        assertThat(Files.readSymbolicLink(link)).isEqualTo(client);
    }

    @Test void aLinkToAnotherProgramNamesItsTarget() throws IOException {
        Path other = Files.writeString(dir.resolve("other"), "x");
        Path link = dir.resolve("bin/jasper");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, other);
        assertThat(new LinkPlacement(link, client(), null).state())
            .isEqualTo(new State(Kind.OTHER_PROGRAM, link + " is another program (" + other + ")"));
    }

    @Test void aLinkToAMovedJasperPointsNowhereAndInstallReplacesIt() throws IOException {
        Path client = client();
        Path link = dir.resolve("bin/jasper");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, dir.resolve("Old.app/Contents/Resources/bin/jasper"));
        var placement = new LinkPlacement(link, client, null);
        assertThat(placement.state()).isEqualTo(new State(Kind.MISSING_TARGET, link + " points to a missing file"));
        placement.install(false);
        assertThat(Files.readSymbolicLink(link)).isEqualTo(client);
    }

    @Test void aFolderItCannotWriteGoesThroughTheAdministratorPrompt() throws IOException {
        Path client = client();
        Path locked = Files.createDirectory(dir.resolve("locked"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        Path link = locked.resolve("jasper");
        var commands = new ArrayList<String>();
        try {
            new LinkPlacement(link, client, commands::add).install(false);
            assertThat(commands).containsExactly("mkdir -p '" + locked + "' && ln -sfn '" + client + "' '" + link + "'");
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test void withoutAnInstalledJasperNothingIsAvailable() {
        var placement = new LinkPlacement(dir.resolve("bin/jasper"), null, null);
        assertThat(placement.available()).isFalse();
        assertThat(placement.state()).isEqualTo(new State(Kind.UNAVAILABLE, "Needs an installed Jasper"));
        assertThatThrownBy(() -> placement.install(false)).isInstanceOf(IOException.class);
    }

    @Test void theHintNamesAFolderThatIsNotOnPath() {
        var placement = new LinkPlacement(dir.resolve("bin/jasper"), null, null);
        assertThat(placement.hint("/usr/bin:" + dir.resolve("bin"))).isEmpty();
        assertThat(placement.hint("/usr/bin"))
            .contains(dir.resolve("bin") + " is not on your PATH; add it to use jasper in other terminals");
    }
}
