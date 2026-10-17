package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.RemoteLocation;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Spec 10.4: the frame title from the look's template, as the Workstation mocks show it. */
class WindowTitleTest {
    static final TitleFormat WORKSTATION = new TitleFormat("Workstation — {session} — {directory}");
    static final Path HOME = Path.of(System.getProperty("user.home"));

    static PaneSnapshot pane(Optional<Path> directory, Optional<String> provider, Optional<RemoteLocation> remote, String shell) {
        return new PaneSnapshot("", directory, 80, 24, true, PaneSnapshot.State.RUNNING, OptionalInt.empty(), provider, remote, shell);
    }

    @Test void theDefaultTemplateIsTodaysLabel() {
        assertThat(WindowTitle.format(new TitleFormat("{context}"), "dustin@~", "Local: zsh", "~")).isEqualTo("dustin@~");
    }

    @Test void theWorkstationTemplateReadsAsTheMocks() {
        assertThat(WindowTitle.format(WORKSTATION, "x", "Mac", "~")).isEqualTo("Workstation — Mac — ~");
        assertThat(WindowTitle.format(WORKSTATION, "x", "Local: zsh", "~")).isEqualTo("Workstation — Local: zsh — ~");
    }

    @Test void anEmptyTokenGoesWithItsSeparator() {
        assertThat(WindowTitle.format(WORKSTATION, "x", "Mac", "")).isEqualTo("Workstation — Mac");
        assertThat(WindowTitle.format(WORKSTATION, "x", "", "~")).isEqualTo("Workstation — ~");
        assertThat(WindowTitle.format(WORKSTATION, "x", "  ", "")).isEqualTo("Workstation");
        assertThat(WindowTitle.format(new TitleFormat("{session} | {directory}"), "x", "", "~/src")).isEqualTo("~/src");
    }

    @Test void theTokensComeFromThePaneOrTheDocument() {
        var local = pane(Optional.of(HOME.resolve("src")), Optional.empty(), Optional.empty(), "zsh");
        assertThat(WindowTitle.session(local)).isEqualTo("Local: zsh");
        assertThat(WindowTitle.directory(local, HOME)).isEqualTo("~/src");
        assertThat(WindowTitle.directory(pane(Optional.of(HOME), Optional.empty(), Optional.empty(), "zsh"), HOME)).isEqualTo("~");
        var provided = pane(Optional.empty(), Optional.of("dev.jasper.remote"), Optional.empty(), "Mac");
        assertThat(WindowTitle.session(provided)).as("a saved identity names the session").isEqualTo("Mac");
        assertThat(WindowTitle.directory(provided, HOME)).as("no reported directory yet").isEmpty();
        var reported = pane(Optional.empty(), Optional.of("dev.jasper.remote"), Optional.of(new RemoteLocation("mbp", "/srv/www")), "Mac");
        assertThat(WindowTitle.directory(reported, HOME)).isEqualTo("/srv/www");
        var osc = pane(Optional.empty(), Optional.empty(), Optional.of(new RemoteLocation("build-01", "/tmp")), "zsh");
        assertThat(WindowTitle.session(osc)).as("an OSC host names a remote pane").isEqualTo("build-01");
        assertThat(WindowTitle.session(null)).isEmpty();
        assertThat(WindowTitle.directory(null, HOME)).isEmpty();
        assertThat(WindowTitle.session(pane(Optional.empty(), Optional.empty(), Optional.empty(), "bad\u0007name"))).isEqualTo("Local: bad name");
    }
}
