package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.windows.*;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.WindowSpec;
import java.awt.Dimension;
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
        new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null));
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicReference<Runnable> during = new AtomicReference<>(() -> {});
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final List<PathChoice> requests = new ArrayList<>();
    private List<Path> selected = List.of(Path.of("/one"), Path.of("/two"));
    private final AuxiliaryWindows windows = new AuxiliaryWindows(UiState.inMemory(),
        surface -> new AuxiliarySurface.Shell(() -> {}, () -> {}, () -> {}, title -> {},
            () -> new Rectangle(0,0,100,100), (title,initial) -> Optional.empty()),
        (request, cancellation) -> { requests.add(request); cancellation.accept(() -> disposed.set(true)); during.get().run(); return selected; });
    private final HostedUi ui = new HostedUi("dev.x.tool", new Contributions(), new Containment(() -> true),
        Runnable::run, () -> true, open::get, getClass().getClassLoader(), () -> Variant.DARK, h -> () -> {}, windows, terminals);

    @Test void terminalOwnerSelectsMultipleFilesOrOneDirectory() {
        var owner = terminals.windowHandle(fixture.addWindow());
        assertThat(ui.windows().chooseFiles(owner, "Upload", Optional.empty())).containsExactly(Path.of("/one"),Path.of("/two"));
        assertThat(requests.getFirst().terminalOwner()).isEqualTo(owner.id());
        assertThat(disposed).isTrue();
        selected = List.of(Path.of("/dest"));
        assertThat(ui.windows().chooseDirectory(owner, "Download", Optional.empty())).contains(Path.of("/dest"));
        assertThat(requests.getLast().directory()).isTrue();
        selected = List.of();
        assertThat(ui.windows().chooseFiles(owner, "Cancelled", Optional.empty())).isEmpty();
    }

    @Test void auxiliaryOwnerCloseAndPluginStopDisposeAndDiscardResults() {
        var owner = ui.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(500,400), true));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.windows().chooseFiles(owner, "Upload", Optional.empty()));
        owner.show();
        during.set(owner::close);
        assertThat(ui.windows().chooseFiles(owner, "Upload", Optional.empty())).isEmpty();
        assertThat(disposed).isTrue();
        var terminal = terminals.windowHandle(fixture.addWindow());
        disposed.set(false);
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
            Runnable::run, () -> true, () -> true, new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.windows().chooseFiles(other.windowHandle(foreign.addWindow()), "Upload", Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.windows().chooseFiles(terminals.windowHandle(UUID.randomUUID()), "Upload", Optional.empty()));
        assertThat(requests).isEmpty();
    }
}
