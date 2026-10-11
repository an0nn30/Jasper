package dev.jasper.app.application;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.settingsui.*;
import dev.jasper.app.themesui.ThemeImport;
import dev.jasper.app.windows.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.config.CoreSettings.*;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

/** Exercises the same reload callback supplied by JasperApplication, including its real config publication. */
class ImportThemePolicyTest {
    @TempDir Path home;
    @Test void importReloadKeepsFollowSystemAndExplicitSelectionCommitsItsCatalogOrigin() throws Exception {
        var config = home.resolve("config.toml");
        String saved = "ui.theme.name='Jasper Dark'\nui.theme.source='bundled'\nui.theme.follow_system=true\n";
        Files.writeString(config, saved);
        var source = home.resolve("incoming.toml");
        Files.writeString(source, "name='Imported Example'\nextends='Jasper Dark'\n");
        var service = new ConfigService(config, true);
        var themes = new AtomicReference<ThemeController>();
        var catalog = ThemeCatalog.beside(config);
        service.watchChanges(() -> { var before = catalog.latest(); catalog.scan(); return !before.equals(catalog.latest()); }).get(10, TimeUnit.SECONDS);
        var controller = new AtomicReference<ConfigurationController>();
        var windows = new AtomicReference<AuxiliaryWindows>();
        var imports = new AtomicReference<ThemeImport>();
        var draft = new SettingsDraft(service.initialState().values());
        var reload = new AtomicReference<CompletableFuture<ConfigService.State>>();
        try {
            edt(() -> {
                themes.set(new ThemeController());
                controller.set(new ConfigurationController(themes.get(), service, catalog, ignored -> {}));
                windows.set(new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
                    () -> {}, () -> {}, () -> {}, ignored -> {}, () -> new Rectangle(0,0,600,600), (title,path) -> Optional.empty())));
                var owner = windows.get().window("settings", "Settings", new Dimension(600,600), false); owner.show();
                var context = new PageContext(draft, ignored -> {}, Runnable::run, (title,path) -> Optional.of(source),
                    () -> {}, () -> owner, dev.jasper.app.testsupport.KitTestSupport::context);
                imports.set(new ThemeImport(context, home, windows.get(), () -> {
                    var future = controller.get().reload(); reload.set(future); return future.thenApply(ignored -> null);
                }, Executors.newSingleThreadExecutor()));
                imports.get().start();
            });
            await(() -> windows.get().open().size() == 2);
            edt(() -> buttons(windows.get().open().getLast().holder()).stream().filter(button -> button.getText().equals("Import")).findFirst().orElseThrow().doClick());
            await(() -> reload.get() != null);
            reload.get().get(10, TimeUnit.SECONDS);
            await(() -> !imports.get().busy());
            assertThat(Files.readString(config)).isEqualTo(saved);
            edt(() -> {
                assertThat(catalog.latest().find("Imported Example")).isPresent();
                assertThat(controller.get().state().values().get(UI_THEME_FOLLOW_SYSTEM)).isTrue();
                assertThat(controller.get().state().values().get(UI_THEME_NAME)).isEqualTo("Jasper Dark");
                assertThat(controller.get().state().values().get(UI_THEME_SOURCE)).isEqualTo(ThemeSource.BUNDLED);
                assertThat(draft.modified()).isFalse();
                draft.rebase(controller.get().state().values());
                draft.stageAll(Map.of(UI_THEME_NAME, Optional.of("Imported Example"), UI_THEME_SOURCE, Optional.of(ThemeSource.CATALOG), UI_THEME_FOLLOW_SYSTEM, Optional.of(false)));
                reload.set(controller.get().save(draft.edit()));
            });
            reload.get().get(10, TimeUnit.SECONDS);
            edt(() -> {
                assertThat(controller.get().state().values().get(UI_THEME_FOLLOW_SYSTEM)).isFalse();
                assertThat(controller.get().state().values().get(UI_THEME_NAME)).isEqualTo("Imported Example");
                assertThat(controller.get().state().values().get(UI_THEME_SOURCE)).isEqualTo(ThemeSource.CATALOG);
            });
        } finally {
            edt(() -> { if(imports.get()!=null)imports.get().close(); if(windows.get()!=null)windows.get().close(); if(controller.get()!=null)controller.get().close(); if(themes.get()!=null)themes.get().close(); });
            service.close();
        }
    }
    private static java.util.List<JButton> buttons(Container root) {
        var result = new ArrayList<JButton>();
        for(var child:root.getComponents()) { if(child instanceof JButton button)result.add(button); if(child instanceof Container nested)result.addAll(buttons(nested)); }
        return result;
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long end = System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        var done = new AtomicBoolean();
        while(System.nanoTime()<end) { edt(() -> done.set(condition.getAsBoolean())); if(done.get())return; Thread.sleep(10); }
        throw new AssertionError("Import state did not arrive");
    }
}
