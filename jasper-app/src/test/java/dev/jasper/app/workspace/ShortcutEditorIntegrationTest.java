package dev.jasper.app.workspace;

import dev.jasper.app.application.ConfigurationTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.shortcuthelp.ShortcutHelp;
import dev.jasper.app.shortcuthelp.ShortcutTestSupport;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.Action;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class ShortcutEditorIntegrationTest {
    @TempDir Path directory;

    @Test void commandsOpensOneEditorAndSavingChangesBothWindowsAndDisk() throws Exception {
        Path file = directory.resolve("config.toml");
        try (var service = new ConfigService(file, true)) {
            WindowContent[] owners = new WindowContent[2];
            AuxiliaryWindows[] windows = new AuxiliaryWindows[1];
            ConfigurationTestSupport[] config = new ConfigurationTestSupport[1];
            var saved = new CompletableFuture<Void>();
            try {
                edt(() -> {
                    var themes = new ThemeController();
                    config[0] = new ConfigurationTestSupport(themes, service);
                    var contributions = new Contributions();
                    windows[0] = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
                        () -> {}, () -> {}, () -> {}, title -> {}, () -> new Rectangle(0, 0, 1000, 640), (title, initial) -> Optional.empty()));
                    var help = new ShortcutHelp(windows[0], contributions, () -> config[0].snapshot().bindings(true), Map::of, true,
                        changes -> config[0].saveKeybindings(changes, List.of()).whenComplete((value, failure) -> {
                            if (failure == null) saved.complete(null); else saved.completeExceptionally(failure);
                        }));
                    config[0].onSnapshot(snapshot -> help.refresh());
                    contributions.addAction("app.shortcuts", "Keyboard Shortcuts…", null, List.of("keymap"), Optional.empty(), ignored -> help.open());
                    for (int i = 0; i < owners.length; i++) {
                        owners[i] = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
                        var root = new JRootPane(); root.setContentPane(owners[i]); owners[i].installRootBindings(root);
                        config[0].register(owners[i]); owners[i].connectContributions(contributions);
                    }
                    var palette = owners[0].commandPalette();
                    palette.open(PaletteScope.COMMANDS_ID);
                    palette.component().queryField().setText("keyboard shortcuts");
                    assertThat(PaletteTestSupport.selectedRow(palette.component()).id()).isEqualTo("app.shortcuts");
                    PaletteTestSupport.executeSelected(palette.component());
                    assertThat(palette.isOpen()).isFalse();
                    assertThat(windows[0].open()).hasSize(1);
                    help.open(); assertThat(windows[0].open()).hasSize(1);
                    ShortcutTestSupport.save(windows[0].open().getFirst(), "New tab", KeyStroke.getKeyStroke("ctrl alt N"));
                });
                saved.get(5, TimeUnit.SECONDS);
                edt(() -> {}); // Drain publication and the editor's completion callback.
                edt(() -> {
                    for (var owner : owners)
                        assertThat(owner.action(ActionId.NEW_TAB).getValue(Action.ACCELERATOR_KEY)).isEqualTo(KeyStroke.getKeyStroke("ctrl alt N"));
                    assertThat(ShortcutTestSupport.editing(windows[0].open().getFirst())).isFalse();
                });
                assertThat(Files.readString(file)).contains("ctrl+alt+n");
            } finally {
                edt(() -> {
                    if (windows[0] != null) windows[0].close();
                    for (var owner : owners) if (owner != null) owner.close();
                    if (config[0] != null) config[0].close();
                });
            }
        }
    }
}
