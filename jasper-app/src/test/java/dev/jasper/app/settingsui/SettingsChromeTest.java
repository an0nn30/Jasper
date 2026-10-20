package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.presentation.SettingsChrome;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Spec 9.1 and 12.1: the dialog hands its Settings projections to every page; without them it runs the default look. */
class SettingsChromeTest {
    /** The Enterprise look's projection (spec 9.1's table). */
    static final SettingsChrome CLASSIC = new SettingsChrome(false, true, false,
        Set.of("window.native_decorations", "ui.animations.enabled", "window.rail_width"), true);
    static final Path FILE = Path.of("/fixture/config.toml");

    static AuxiliaryWindows windows() {
        return new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { },
            () -> new Rectangle(0, 0, 1000, 700), (title, initial) -> Optional.empty()));
    }

    static ConfigService.State state() {
        var parsed = ConfigLoader.parse(FILE, "", true);
        return new ConfigService.State(parsed.snapshot(), parsed.diagnostics(), FILE, true, parsed.legacyKeys(), parsed.deadTables(),
            parsed.values(), parsed.rejected());
    }

    /** Hooks over a fixed state, a test kit and {@code chrome} and {@code composition}. */
    static SettingsDialog.Hooks hooks(SettingsChrome chrome, Composition composition) {
        var state = state();
        return new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state, listener -> new Subscription(() -> { }),
            () -> { }, () -> { }, uri -> { }, (parent, count) -> true, KitTestSupport::context, chrome, composition);
    }

    @Test void theDialogHandsItsProjectionsToEveryPage() throws Exception {
        var contexts = new ArrayList<PageContext>();
        var dialog = new SettingsDialog(windows(), context -> {
            contexts.add(context);
            return List.<SettingsPage>of(new GroupPage("appearance", "Appearance", Optional.empty(), false, List::of, context));
        }, hooks(CLASSIC, Composition.CLASSIC));
        try {
            edt(() -> dialog.open(null, "appearance"));
            assertThat(contexts).singleElement().satisfies(context -> {
                assertThat(context.chrome()).isEqualTo(CLASSIC);
                assertThat(context.composition()).isEqualTo(Composition.CLASSIC);
                assertThat(context.withDraft(context.draft()).chrome()).isEqualTo(CLASSIC);
                assertThat(context.withDraft(context.draft()).composition()).isEqualTo(Composition.CLASSIC);
            });
            assertThat(dialog.chrome()).isEqualTo(CLASSIC);
        } finally { edt(dialog::cancel); }
    }

    @Test void withoutAPresentationTheDialogAndItsPagesRunTheDefaultLook() throws Exception {
        var state = state();
        var hooks = new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state, listener -> new Subscription(() -> { }),
            () -> { }, () -> { }, uri -> { }, (parent, count) -> true, KitTestSupport::context);
        boolean mac = com.formdev.flatlaf.util.SystemInfo.isMacOS;
        var standard = Projections.standard(new Projections.Host(Projections.Platform.of(System.getProperty("os.name", "")),
            mac && com.formdev.flatlaf.util.SystemInfo.isMacFullWindowContentSupported));
        assertThat(hooks.chrome()).isEqualTo(standard.settings());
        assertThat(hooks.composition()).isEqualTo(Composition.ROWS);
        assertThat(PageContext.standard()).isEqualTo(standard);
        var context = new PageContext(new SettingsDraft(state.values()), id -> { }, run -> run.run(), (title, initial) -> Optional.empty(), () -> { });
        assertThat(context.chrome()).isEqualTo(standard.settings());
        assertThat(context.composition()).isEqualTo(Composition.ROWS);
        assertThat(context.chrome().inlineReset()).isTrue();
    }
}
