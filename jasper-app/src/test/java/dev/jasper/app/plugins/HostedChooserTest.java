package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.windows.*;
import dev.jasper.app.files.*;
import dev.jasper.sdk.files.*;
import dev.jasper.sdk.Variant;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class HostedChooserTest {
    private final TerminalFixture fixture = new TerminalFixture();
    private final HostedTerminals terminals = new HostedTerminals("dev.x.tool", new CapabilityGate("dev.x.tool", Set.of()),
        fixture.registry, Runnable::run, () -> true, () -> true,
        new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null, name -> new javax.swing.ImageIcon()));
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicReference<Runnable> during = new AtomicReference<>(() -> {});
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final List<PathChoice> requests = new ArrayList<>();
    private List<Location> chosenLocations;
    private final Contributions contributions = new Contributions();
    private List<Path> selected = List.of(Path.of("/one").toAbsolutePath(), Path.of("/two").toAbsolutePath());
    private final AuxiliaryWindows windows = new AuxiliaryWindows(UiState.inMemory(),
        surface -> new AuxiliarySurface.Shell(() -> {}, () -> {}, () -> {}, title -> {},
            () -> new Rectangle(0,0,100,100), (title,initial) -> Optional.empty()),
        (request, cancellation) -> { requests.add(request); cancellation.accept(() -> disposed.set(true)); during.get().run();
            return chosenLocations != null ? chosenLocations : selected.stream().map(Location::local).toList(); });
    private final HostedUi ui = new HostedUi("dev.x.tool", contributions, new Containment(() -> true),
        Runnable::run, () -> true, open::get, getClass().getClassLoader(), () -> Variant.DARK, h -> () -> {}, windows, terminals);

    @Test void ownedDocumentPinsAcrossChooserAndOwnerClosureDiscardsResult() {
        var containment=new Containment(() -> true);
        var kit=new HostedKit("dev.x.tool",open::get,ui::kitThemes,ui.appearance(),containment);ui.kit(kit);
        java.util.function.Function<Boolean, UUID> window = first -> {
            UUID id=first?fixture.addMainWindow():fixture.addWindow();contributions.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(id,e -> {},e -> {},e -> {},() -> {}));return id;
        };
        UUID main=window.apply(true);
        contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, main::equals, tab -> contributions.tabs().move(tab, window.apply(false))));
        var disposed=new java.util.concurrent.atomic.AtomicInteger();
        var view=kit.create(HostedKitTest.custom(dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR,"doc"),e -> {});
        view.registerContent(new dev.jasper.sdk.ui.CustomContent("doc",dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR,new javax.swing.JTextArea(),disposed::incrementAndGet));
        var tab=ui.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("doc","Document"),null,view);
        during.set(() -> {tab.moveToNewWindow();assertThat(tab.window().isMain()).isTrue();assertThat(tab.requestClose().join()).isFalse();});
        assertThat(ui.windows().chooseFiles(tab,"Choose",Optional.empty())).hasSize(2);
        tab.moveToNewWindow();assertThat(tab.window().isMain()).isFalse();
        during.set(tab::close);
        assertThat(ui.windows().chooseFiles(tab,"Choose",Optional.empty())).isEmpty();
        assertThat(disposed.get()).isEqualTo(1);kit.close();ui.closeAll();
    }

    @Test void terminalOwnerSelectsMultipleFilesOrOneDirectory() {
        var owner = terminals.windowHandle(fixture.addWindow());
        assertThat(ui.windows().chooseFiles(owner, "Upload", Optional.empty())).containsExactly(Path.of("/one").toAbsolutePath(),Path.of("/two").toAbsolutePath());
        assertThat(requests.getFirst().terminalOwner()).isEqualTo(owner.id());
        assertThat(disposed).isTrue();
        selected = List.of(Path.of("/dest").toAbsolutePath());
        assertThat(ui.windows().chooseDirectory(owner, "Download", Optional.empty())).contains(Path.of("/dest").toAbsolutePath());
        assertThat(requests.getLast().mode()).isEqualTo(PathChoice.Mode.DIRECTORY);
        selected = List.of(Path.of("/notes/new.toml").toAbsolutePath());
        assertThat(ui.windows().chooseSaveFile(owner, "Save As", Optional.of(Path.of("/notes/old.toml").toAbsolutePath()))).contains(Path.of("/notes/new.toml").toAbsolutePath());
        assertThat(requests.getLast().mode()).isEqualTo(PathChoice.Mode.SAVE);
        assertThat(requests.getLast().initial()).contains(Location.local(Path.of("/notes/old.toml")));
        selected = List.of();
        assertThat(ui.windows().chooseFiles(owner, "Cancelled", Optional.empty())).isEmpty();
    }

    @Test void pluginStopDisposesThePickerAndDiscardsItsResult() {
        var terminal = terminals.windowHandle(fixture.addWindow());
        during.set(() -> { open.set(false); ui.closeAll(); assertThat(disposed).isTrue(); });
        assertThat(ui.windows().chooseFiles(terminal, "Upload", Optional.empty())).isEmpty();
    }

    @Test void terminalOwnerClosingWhilePickerPumpsDisposesIt() {
        var id = fixture.addWindow();
        var pane = fixture.addPane(fixture.addTab(id, "Shell"), "Shell", null);
        during.set(() -> { fixture.closePane(pane); assertThat(disposed).isTrue(); });
        assertThat(ui.windows().chooseFiles(terminals.windowHandle(id), "Upload", Optional.empty())).isEmpty();
    }

    @Test void foreignAndDeadOwnersAreRejectedBeforeOpening() {
        var foreign = new TerminalFixture();
        var other = new HostedTerminals("dev.x.tool", new CapabilityGate("dev.x.tool", Set.of()), foreign.registry,
            Runnable::run, () -> true, () -> true, new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null, name -> new javax.swing.ImageIcon()));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.windows().chooseFiles(other.windowHandle(foreign.addWindow()), "Upload", Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.windows().chooseFiles(terminals.windowHandle(UUID.randomUUID()), "Upload", Optional.empty()));
        assertThat(requests).isEmpty();
    }

    @Test void choicesCarryTheFocusedPanesFolder() {
        var id = fixture.addWindow();
        var pane = fixture.addPane(fixture.addTab(id, "Shell"), "Shell", Path.of("/work/project"));
        fixture.focusPane(pane);
        ui.windows().chooseFiles(terminals.windowHandle(id), "Open", Optional.empty());
        assertThat(requests.getLast().terminalFolder()).contains(Location.local(Path.of("/work/project")));
        assertThat(requests.getLast().initial()).isEmpty();
    }

    @Test void chooseOffersTheNamedSourcesStartsWhereASourceLocatesThePaneAndReturnsItsLocations() {
        var id = fixture.addWindow();
        var pane = fixture.addPane(fixture.addTab(id, "ssh"), "ssh", null);
        fixture.focusPane(pane);
        var remote = new Location("dev.x.tool.hosts", "box", "/srv");
        ChooserSource hosts = new ChooserSource() {
            @Override public String id() { return "dev.x.tool.hosts"; }
            @Override public String heading() { return "Remote"; }
            @Override public List<Root> roots() { return List.of(new Root("box", "Box", "", true)); }
            @Override public Runnable onChanged(Runnable listener) { return () -> { }; }
            @Override public java.util.concurrent.CompletableFuture<Browser> connect(String root, Optional<UUID> owner) { return new java.util.concurrent.CompletableFuture<>(); }
            @Override public Optional<Location> locate(UUID paneId) { return paneId.equals(pane) ? Optional.of(remote) : Optional.empty(); }
        };
        contributions.addFileSource(hosts);
        chosenLocations = List.of(remote.at("/srv/app.conf"));
        var choice = new FileChoice(terminals.windowHandle(id), "Open", FileChoice.Mode.OPEN, false, Optional.empty(), Optional.empty(),
            Set.of("dev.x.tool.hosts"));
        assertThat(ui.windows().choose(choice)).containsExactly(new FileLocation.Sourced("dev.x.tool.hosts", "box", "/srv/app.conf"));
        assertThat(requests.getLast().sources()).containsExactly(hosts);
        assertThat(requests.getLast().terminalFolder()).contains(remote);
        ui.windows().choose(new FileChoice(terminals.windowHandle(id), "Open", FileChoice.Mode.OPEN, false, Optional.empty(), Optional.empty(), Set.of()));
        assertThat(requests.getLast().sources()).as("sources the caller did not name are not offered").isEmpty();
        assertThat(ui.windows().chooseFiles(terminals.windowHandle(id), "Open", Optional.empty())).as("the Path API drops remote results").isEmpty();
    }
}
