package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.RemoteLocation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JRootPane;
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
        assertThat(WindowTitle.format(new TitleFormat("Corp Terminal"), "x", "Mac", "~")).as("no tokens: the title as it is")
            .isEqualTo("Corp Terminal");
        assertThat(WindowTitle.format(new TitleFormat("{session} at work"), "x", "", "~")).isEqualTo("at work");
        assertThat(WindowTitle.format(new TitleFormat("{session} — Corp"), "x", "", "~")).isEqualTo("Corp");
        assertThat(WindowTitle.format(new TitleFormat("Workstation — {session} (corp)"), "x", "", "~")).isEqualTo("Workstation (corp)");
    }

    @Test void theContextIsTodaysLabelAsItIs() {
        assertThat(WindowTitle.format(new TitleFormat("{context}"), " notes.md ", "", "")).isEqualTo(" notes.md ");
        assertThat(WindowTitle.format(new TitleFormat("Jasper — {context}"), "  ", "", "")).isEqualTo("Jasper");
    }

    /** The frame title through the window's own wiring: the template from its look and the active pane's tokens. */
    @Test void theFrameTitleFillsTheLooksTemplateFromTheActivePane() throws Exception {
        try {
            DesktopTestSupport.edt(() -> {
                var chrome = WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.UNIFIED,
                    WorkspaceLook.Stripes.RAILS, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false,
                    WorkspaceLook.FrameStyle.STANDARD, WorkspaceLook.Icons.NEW_UI), WORKSTATION);
                var owner = WindowShellTest.window(true, chrome);
                var titles = new ArrayList<String>();
                WindowContent.mount(new JRootPane(), owner, new WindowLook(chrome, false, true), titles::add);
                owner.update();
                assertThat(titles).isNotEmpty();
                assertThat(titles.getLast()).isEqualTo("Workstation — Local: idle-test-session — ~");
            });
        } finally {
            DesktopTestSupport.closeOwners();
        }
    }

    /** Spec 10.4: a hosted document gives {@code {session}} its title and {@code {directory}} its location. */
    @Test void aHostedDocumentFillsTheTemplateWithItsTitleAndLocation() throws Exception {
        try {
            DesktopTestSupport.edt(() -> {
                var chrome = WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.UNIFIED,
                    WorkspaceLook.Stripes.RAILS, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false,
                    WorkspaceLook.FrameStyle.STANDARD, WorkspaceLook.Icons.NEW_UI), WORKSTATION);
                var owner = WindowShellTest.window(true, chrome);
                var titles = new ArrayList<String>();
                WindowContent.mount(new JRootPane(), owner, new WindowLook(chrome, false, true), titles::add);
                var model = new dev.jasper.app.contributions.Contributions();
                owner.connectContributions(model);
                var entry = model.tabs().open("dev.editor", "notes", "notes.md", owner.id(), new javax.swing.JTextArea("text"));
                entry.presentation("notes.md", "", new javax.swing.ImageIcon(), false, "/srv/notes", "mbp");
                owner.update();
                assertThat(owner.selectedContent()).isSameAs(entry);
                assertThat(titles.getLast()).isEqualTo("Workstation — notes.md — /srv/notes");
                entry.presentation("notes.md", "", new javax.swing.ImageIcon(), false, "", "");
                owner.update();
                assertThat(titles.getLast()).as("no location: no trailing separator").isEqualTo("Workstation — notes.md");
            });
        } finally {
            DesktopTestSupport.closeOwners();
        }
    }

    @Test void theTokensComeFromThePane() {
        var local = pane(Optional.of(HOME.resolve("src")), Optional.empty(), Optional.empty(), "zsh");
        assertThat(WindowTitle.session(local)).isEqualTo("Local: zsh");
        assertThat(WindowTitle.directory(local, HOME)).isEqualTo("~/src");
        assertThat(WindowTitle.directory(pane(Optional.of(HOME), Optional.empty(), Optional.empty(), "zsh"), HOME)).isEqualTo("~");
        var provided = pane(Optional.empty(), Optional.of("dev.jasper.remote"), Optional.empty(), "Mac");
        assertThat(WindowTitle.session(provided)).as("a saved identity names the session").isEqualTo("Mac");
        assertThat(WindowTitle.directory(provided, HOME)).as("no reported directory yet").isEmpty();
        var reported = pane(Optional.empty(), Optional.of("dev.jasper.remote"), Optional.of(new RemoteLocation("mbp", "/srv/www")), "Mac");
        assertThat(WindowTitle.directory(reported, HOME)).isEqualTo("/srv/www");
        var unnamed = pane(Optional.empty(), Optional.of("dev.jasper.remote"), Optional.of(new RemoteLocation("mbp", "/srv/www")), " ");
        assertThat(WindowTitle.session(unnamed)).as("a provided session without a saved identity: its reported host, as the header")
            .isEqualTo("mbp");
        assertThat(WindowTitle.session(pane(Optional.empty(), Optional.of("dev.jasper.remote"), Optional.empty(), ""))).isEmpty();
        var osc = pane(Optional.empty(), Optional.empty(), Optional.of(new RemoteLocation("build-01", "/tmp")), "zsh");
        assertThat(WindowTitle.session(osc)).as("an OSC host names a remote pane").isEqualTo("build-01");
        assertThat(WindowTitle.session(null)).isEmpty();
        assertThat(WindowTitle.directory(null, HOME)).isEmpty();
        assertThat(WindowTitle.session(pane(Optional.empty(), Optional.empty(), Optional.empty(), "bad\u0007name"))).isEqualTo("Local: bad name");
    }
}
