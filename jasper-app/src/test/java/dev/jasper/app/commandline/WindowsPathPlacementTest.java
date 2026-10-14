package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commandline.CommandInstall.Kind;
import dev.jasper.app.commandline.CommandInstall.State;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WindowsPathPlacementTest {
    private static final Path BIN = Path.of("C:\\Program Files\\Jasper\\bin");

    @Test void entriesAreComparedWithoutCaseQuotesOrATrailingSeparator() {
        assertThat(WindowsPathPlacement.contains("C:\\Windows;\"c:\\program files\\jasper\\bin\\\"", BIN)).isTrue();
        assertThat(WindowsPathPlacement.contains("C:\\Windows", BIN)).isFalse();
        assertThat(WindowsPathPlacement.contains("", BIN)).isFalse();
    }

    @Test void addingAppendsOnceAndRemovingTakesOnlyThatEntry() {
        assertThat(WindowsPathPlacement.added("", BIN)).isEqualTo(BIN.toString());
        assertThat(WindowsPathPlacement.added("%USERPROFILE%\\bin", BIN)).isEqualTo("%USERPROFILE%\\bin;" + BIN);
        assertThat(WindowsPathPlacement.added("A;", BIN)).isEqualTo("A;" + BIN);
        assertThat(WindowsPathPlacement.added("A;" + BIN, BIN)).isEqualTo("A;" + BIN);
        assertThat(WindowsPathPlacement.removed("A;" + BIN + ";B", BIN)).isEqualTo("A;B");
        assertThat(WindowsPathPlacement.removed(BIN.toString(), BIN)).isEmpty();
        assertThat(WindowsPathPlacement.removed("A;;B", BIN)).isEqualTo("A;;B");
    }

    @Test void installAndUninstallEditTheUsersPath() throws IOException {
        var stored = new AtomicReference<Optional<String>>(Optional.of("%USERPROFILE%\\bin"));
        var placement = new WindowsPathPlacement(BIN, stored::get, value -> stored.set(Optional.of(value)));
        assertThat(placement.state().kind()).isEqualTo(Kind.NOT_INSTALLED);
        placement.install(false);
        assertThat(stored.get()).contains("%USERPROFILE%\\bin;" + BIN);
        assertThat(placement.state()).isEqualTo(new State(Kind.INSTALLED, "Installed: " + BIN + " is on your Path"));
        assertThat(placement.hint("")).contains("Open a new terminal to use it");
        placement.uninstall();
        assertThat(stored.get()).contains("%USERPROFILE%\\bin");
    }

    @Test void aUserWithoutAPathOfTheirOwnGetsOne() throws IOException {
        var stored = new AtomicReference<Optional<String>>(Optional.empty());
        new WindowsPathPlacement(BIN, stored::get, value -> stored.set(Optional.of(value))).install(false);
        assertThat(stored.get()).contains(BIN.toString());
    }

    @Test void withoutAnInstalledJasperNothingIsAvailable() {
        var placement = new WindowsPathPlacement(null, Optional::empty, value -> { });
        assertThat(placement.available()).isFalse();
        assertThat(placement.state().kind()).isEqualTo(Kind.UNAVAILABLE);
    }
}
