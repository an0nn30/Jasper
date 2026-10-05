package dev.jasper.app.workspace;

import dev.jasper.app.application.ConfigurationTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.shortcuthelp.KeymapPage;
import dev.jasper.app.shortcuthelp.ShortcutTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Action;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class ShortcutEditorIntegrationTest {
    @TempDir Path directory;

    @Test void thePaletteOpensKeymapAndApplyChangesBothWindowsAndDisk() throws Exception {
        Path file = directory.resolve("config.toml");
        try (var service = new ConfigService(file, true)) {
            WindowContent[] owners = new WindowContent[2];
            KeymapPage[] page = new KeymapPage[1];
            ConfigurationTestSupport[] config = new ConfigurationTestSupport[1];
            var applied = new AtomicReference<CompletableFuture<Void>>();
            int[] opened = {0};
            try {
                edt(() -> {
                    var themes = new ThemeController();
                    config[0] = new ConfigurationTestSupport(themes, service);
                    var contributions = new Contributions();
                    page[0] = new KeymapPage(new KeymapPage.Hooks(contributions, () -> config[0].snapshot().keybindings(), Map::of, true,
                        changes -> config[0].saveKeybindings(changes, List.of())),
                        new PageContext(null, id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { }, () -> null, dev.jasper.app.testsupport.KitTestSupport::context));
                    page[0].component();
                    config[0].onSnapshot(snapshot -> page[0].refresh());
                    contributions.addAction("app.shortcuts", "Keyboard Shortcuts…", null, List.of("keymap"), Optional.empty(),
                        ignored -> opened[0]++);
                    for (int i = 0; i < owners.length; i++) {
                        owners[i] = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
                        var root = new JRootPane(); root.setContentPane(owners[i]); owners[i].installRootBindings(root);
                        config[0].register(owners[i]); owners[i].connectContributions(contributions);
                    }
                    var palette = owners[0].commandPalette();
                    palette.open(PaletteScope.COMMANDS_ID);
                    dev.jasper.app.palette.PaletteTestSupport.query(palette.component()).setText("keyboard shortcuts");
                    assertThat(PaletteTestSupport.selectedRow(palette.component()).id()).isEqualTo("app.shortcuts");
                    PaletteTestSupport.executeSelected(palette.component());
                    assertThat(palette.isOpen()).isFalse();
                    assertThat(opened[0]).isEqualTo(1);
                    ShortcutTestSupport.set(page[0], "New tab", KeyStroke.getKeyStroke("ctrl alt N"));
                });
                assertThat(Files.exists(file) ? Files.readString(file) : "").as("Set writes nothing").doesNotContain("ctrl+alt+n");
                edt(() -> {
                    assertThat(page[0].modified()).isTrue();
                    assertThat(ShortcutTestSupport.editing(page[0])).isFalse();
                    applied.set(page[0].apply());
                });
                applied.get().get(5, TimeUnit.SECONDS);
                edt(() -> {}); // Drain publication.
                edt(() -> {
                    for (var owner : owners)
                        assertThat(owner.action(ActionId.NEW_TAB).getValue(Action.ACCELERATOR_KEY)).isEqualTo(KeyStroke.getKeyStroke("ctrl alt N"));
                    assertThat(page[0].modified()).isFalse();
                });
                assertThat(Files.readString(file)).contains("ctrl+alt+n");
            } finally {
                edt(() -> {
                    if (page[0] != null) page[0].dispose();
                    for (var owner : owners) if (owner != null) owner.close();
                    if (config[0] != null) config[0].close();
                });
            }
        }
    }
}
