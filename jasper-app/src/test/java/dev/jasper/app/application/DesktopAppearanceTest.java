package dev.jasper.app.application;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.platform.DesktopColorsHarness;
import dev.jasper.app.platform.DesktopEnvironment;
import dev.jasper.app.platform.DesktopObservation.Source;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.SemanticColor;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class DesktopAppearanceTest {
    @Test void publicationDuringInitializationReachesAuthoredMatchSystem() {
        openingPublication(false);
    }
    @Test void anOlderQueuedCallbackCannotReplaceTheNewerInitialSnapshot() {
        openingPublication(true);
    }
    private static void openingPublication(boolean newerSnapshot) {
        try (var input = new DesktopColorsHarness(); var themes = ThemeController.start(true,
                ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH), Kit.DEFAULT,
                kit -> theme -> true, failure -> { throw failure; }).controller()) {
            input.portal(1, true, "");
            var dispatch = new ArrayDeque<Runnable>();
            try (var owner = new DesktopAppearance(themes, input.service, () -> DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "GNOME")), Runnable::run, dispatch::add, () -> {
                var before = input.service.current();
                input.portal(2, newerSnapshot, "");
                if (newerSnapshot) input.portal(3, false, "");
                return newerSnapshot ? input.service.current() : before;
            })) {
                owner.update(ConfigSnapshot.defaults().toBuilder().appearance(AppearanceMode.SYSTEM).build());
                while (!dispatch.isEmpty()) {
                    dispatch.remove().run();
                    if (newerSnapshot) assertThat(themes.current().chrome().dark()).isFalse();
                }
                assertThat(themes.current().chrome().dark()).isFalse();
                assertThat(input.launches()).containsExactly("jasper-desktop-portal");
            }
        }
    }

    @Test void initialUnavailablePaletteRetainsCapabilityAndReason() {
        try (var f = new Fixture()) {
            f.owner.update(config("gtk4", true)); f.drain();
            f.nativeInput.unavailable(Source.GTK4, "libadwaita", "1.9.1 (GTK 4.22.4)", "display unavailable"); f.drain();
            assertThat(f.owner.current().source()).isEmpty();
            assertThat(f.owner.current().status()).contains("libadwaita", "1.9.1 (GTK 4.22.4)", "display unavailable", "Slate Cobalt");
        }
    }

    @Test void authoredOnlyConfigStartsNoToolkitAndDesktopDemandStopsAfterSwitchingBack() {
        try (var f = new Fixture()) {
            f.owner.update(ConfigSnapshot.defaults()); f.drain();
            assertThat(f.nativeInput.launches()).containsExactly("jasper-desktop-portal");
            f.owner.update(config("gtk3", true)); f.drain();
            f.nativeInput.awaitToolkit(Source.GTK3, true);
            f.owner.update(ConfigSnapshot.defaults()); f.drain();
            f.nativeInput.awaitToolkit(Source.GTK3, false);
        }
    }
    @Test void sourceChangeRejectsOldQueuedResolutionAndClearsAttribution() {
        try (var f = new Fixture()) {
            f.owner.update(config("gtk3", true)); f.drain();
            f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", "");
            f.edt(); f.work(); // GTK3 result is queued for EDT.
            f.owner.update(config("gtk4", true)); f.drain();
            assertThat(f.owner.current().source()).isEmpty();
            assertThat(f.themes.current().chrome().semantic().color(SemanticColor.SURFACE_APP))
                .isNotEqualTo(new java.awt.Color(26, 26, 26));
        }
    }
    @Test void previewUsesItsOwnSourceAndPolarityAndNeverInstallsIntoApplication() throws Exception {
        try (var f = new Fixture()) {
            f.owner.update(config("gtk3", true)); f.drain();
            f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", ""); f.drain();
            var committed = f.themes.current(); int before = f.installs.get();
            var previews = new ArrayList<DesktopPalette.Result>();
            var demand = f.owner.preview(DesktopPalette.SourceMode.GTK4, false, previews::add); f.drain();
            f.nativeInput.palette(Source.GTK4, false, "0.95,0.95,0.95,1", ""); f.drain();
            assertThat(previews.getLast().source()).contains(Source.GTK4);
            assertThat(previews.getLast().theme().dark()).isFalse();
            assertThat(f.themes.current()).isEqualTo(committed);
            assertThat(f.installs.get()).isEqualTo(before);
            demand.close(); f.drain();
            f.nativeInput.awaitToolkit(Source.GTK3, true);
            f.nativeInput.awaitToolkit(Source.GTK4, false);
        }
    }
    @Test void temporaryFailureKeepsRawCacheAndCurrentPortalWinsWhileStatusRecoveryDoesNotReinstall() {
        try (var f = new Fixture()) {
            f.owner.update(config("gtk3", true)); f.drain();
            f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", ""); f.drain();
            var before = f.owner.current(); int installs = f.installs.get();
            f.nativeInput.fail(Source.GTK3); f.drain();
            assertThat(f.owner.current().theme()).isEqualTo(before.theme());
            assertThat(f.owner.current().status()).isNotEqualTo(before.status()).contains("last good");
            assertThat(f.installs.get()).isEqualTo(installs);
            f.nativeInput.portal(2, true, ",\"accent\":\"0.8,0.2,0.4,1\",\"highContrast\":true"); f.drain();
            assertThat(f.owner.current().theme().semantic().color(SemanticColor.ACCENT))
                .isNotEqualTo(before.theme().semantic().color(SemanticColor.ACCENT));
            assertThat(DesktopPaintPairs.validate(f.owner.current().theme(), true)).isEmpty();
        }
    }
    @Test void changingPolarityCannotReuseDarkLastGoodSnapshot() {
        try (var f = new Fixture()) {
            f.owner.update(config("gtk3", true)); f.drain();
            f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", ""); f.drain();
            f.owner.update(config("gtk3", false)); f.drain();
            assertThat(f.owner.current().theme().dark()).isFalse();
            assertThat(f.owner.current().source()).isEmpty();
        }
    }
    @Test void automaticUsesAlternateWhilePreferredHasNoPalette() {
        try (var f = new Fixture()) {
            f.owner.update(config("auto", true)); f.drain();
            f.nativeInput.unavailable(Source.QT6, "Qt 6", "6.2.4", "fixture unavailable");
            f.nativeInput.unavailable(Source.QT5, "Qt 5", "5.15.2", "fixture unavailable"); f.drain();
            f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", ""); f.drain();
            assertThat(f.owner.current().source()).contains(Source.GTK3);
            f.nativeInput.palette(Source.GTK4, true, "0.2,0.2,0.2,1", ""); f.drain();
            assertThat(f.owner.current().source()).contains(Source.GTK4);
        }
    }
    @Test void failedInstallRetainsCurrentResultAndReloadRetriesEqualRequest() {
        try (var f = new Fixture()) {
            f.owner.update(config("gtk3", true)); f.drain();
            var before = f.owner.current(); f.fail.set(true);
            f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", ""); f.drain();
            assertThat(f.owner.current()).isSameAs(before);
            assertThat(f.failures).hasSize(1);
            f.fail.set(false); f.owner.reload(); f.drain();
            assertThat(f.owner.current().source()).contains(Source.GTK3);
        }
    }
    @Test void closeBeforeWorkerReturnsPreventsInstallationAndPreviewCallback() throws Exception {
        try (var f = new Fixture()) {
            f.owner.update(config("gtk3", true));
            var previews = new ArrayList<DesktopPalette.Result>();
            f.owner.preview(DesktopPalette.SourceMode.GTK4, false, previews::add);
            int before = f.installs.get(); f.owner.close(); f.drain();
            assertThat(f.installs.get()).isEqualTo(before);
            assertThat(previews).isEmpty();
            f.nativeInput.awaitToolkit(Source.GTK3, false);
            f.nativeInput.awaitToolkit(Source.GTK4, false);
        }
    }


    @Test void portalAndPaletteUpdatesDoNotWriteConfigOrRebaseDirtyDraft(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home) throws Exception {
        var file = home.resolve("config.toml");
        String text = "[ui]\nappearance = 'system'\n[ui.theme]\ndark = 'desktop-colors'\nlight = 'desktop-colors'\ndesktop_source = 'gtk3'\n";
        java.nio.file.Files.writeString(file, text);
        var service = java.util.concurrent.CompletableFuture.supplyAsync(() -> new ConfigService(file, false)).get();
        try (var f = new Fixture(); var controller = new ConfigurationController(f.themes, service, path -> {},
                dev.jasper.app.platform.SystemAppearance.Observation.dark(), null, LookMotion.STANDARD, f.owner)) {
            var states = new ArrayList<ConfigService.State>(); controller.onState(states::add);
            var draft = new dev.jasper.app.settingsui.SettingsDraft(service.initialState().values());
            draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44));
            f.drain(); f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", ""); f.drain();
            var palette = f.themes.current().chrome(); int before = f.installs.get();
            controller.systemAppearanceChanged(dev.jasper.app.platform.SystemAppearance.Observation.light());
            assertThat(f.installs.get()).isEqualTo(before);
            assertThat(f.themes.current().chrome()).isSameAs(palette);
            f.nativeInput.portal(3, false, ""); f.drain();
            assertThat(f.themes.current().chrome().dark()).isFalse();
            assertThat(states).isEmpty();
            assertThat(draft.modified()).isTrue();
            assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);
            assertThat(java.nio.file.Files.readString(file)).isEqualTo(text);
        }
    }

    @Test void bootstrapFailureClosesDesktopServiceBeforeAnyLaterNativeReply() {
        try (var input = new DesktopColorsHarness(); var history = new dev.jasper.app.commands.CommandHistory()) {
            input.portal(1, true, "");
            assertThatThrownBy(() -> new JasperApplication(null, null, history, null, () -> {}, null, null,
                dev.jasper.app.plugins.PluginSelection.none(), kit -> theme -> false, () -> input.service))
                .isInstanceOf(ThemeController.InstallationFailure.class);
            input.service.request(1, Set.of(Source.GTK3), true); input.pump();
            assertThat(input.launches()).containsExactly("jasper-desktop-portal");
        }
    }


    @Test void aPreviewKeepsItsToolkitDemandUntilDisposedEvenWithAuthoredAppliedTheme() throws Exception {
        try (var f = new Fixture()) {
            f.owner.update(ConfigSnapshot.defaults()); f.drain();
            var demand = f.owner.preview(DesktopPalette.SourceMode.GTK4, false, value -> {}); f.drain();
            f.nativeInput.palette(Source.GTK4, false, "0.95,0.95,0.95,1", ""); f.drain();
            // A serviced preview is still an open live demand.
            assertThat(f.nativeInput.service.current().palettes()).containsKey(Source.GTK4);
            f.nativeInput.awaitToolkit(Source.GTK4, true);
            demand.close(); f.drain(); f.nativeInput.awaitToolkit(Source.GTK4, false);
            assertThat(f.nativeInput.launches()).contains("jasper-desktop-portal");
        }
    }

    @Test void ownerChangeRejectsThePreviousOwnersQueuedAccentResolution() {
        try (var f = new Fixture()) {
            f.owner.update(config("gtk3", true)); f.drain();
            f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", ""); f.drain();
            f.nativeInput.portal(5, true, ",\"accent\":\"0.8,0.2,0.4,1\""); f.edt(); f.work();
            int before = f.installs.get();
            f.nativeInput.portal(6, false, ",\"accent\":\"0.2,0.6,0.8,1\""); f.edt();
            assertThat(f.installs.get()).as("old owner completion queued before the new observation callback").isEqualTo(before);
            f.drain();
            assertThat(f.owner.current().theme().dark()).isTrue(); // Explicit Dark wins over new portal Light.
            assertThat(f.owner.current().theme().semantic().color(SemanticColor.ACCENT)).isNotEqualTo(new java.awt.Color(204, 51, 102));
        }
    }

    @Test void serviceRecoveryRefreshesStatusWithoutInstallingAnEqualPalette() {
        try (var f = new Fixture()) {
            f.owner.update(config("gtk3", true)); f.drain();
            f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", ""); f.drain();
            f.nativeInput.fail(Source.GTK3); f.drain();
            String failed = f.owner.current().status(); int before = f.installs.get();
            var changes = new AtomicInteger(); f.owner.subscribe(changes::incrementAndGet);
            f.nativeInput.retry();
            f.nativeInput.awaitToolkit(Source.GTK3, true);
            f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", ""); f.drain();
            assertThat(f.owner.current().status()).isNotEqualTo(failed);
            assertThat(changes.get()).isEqualTo(1);
            assertThat(f.installs.get()).isEqualTo(before);
        }
    }


    @Test void anUnusableNativeWindowCannotDisplaceTheLastGoodRawCache() {
        try (var f = new Fixture()) {
            f.owner.update(config("gtk3", true)); f.drain();
            f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", ""); f.drain();
            var before = f.owner.current().theme();
            f.nativeInput.palette(Source.GTK3, true, "0.9,0.9,0.9,1", "unusable window"); f.drain();
            assertThat(f.owner.current().source()).contains(Source.GTK3);
            assertThat(f.owner.current().theme()).isEqualTo(before);
        }
    }


    @Test void anAuthoredInstallFailureRemainsInConfigurationDiagnostics(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home) throws Exception {
        var file = home.resolve("config.toml"); java.nio.file.Files.writeString(file, "[ui]\nappearance = 'dark'\n");
        var service = java.util.concurrent.CompletableFuture.supplyAsync(() -> new ConfigService(file, false)).get();
        try (var f = new Fixture(); var controller = new ConfigurationController(f.themes, service, path -> {},
                dev.jasper.app.platform.SystemAppearance.Observation.dark(), null, LookMotion.STANDARD, f.owner)) {
            var next = service.initialState();
            // Force a different appearance while retaining the same service and its normal publication boundary.
            f.themes.configure(false, TerminalSchemeChoice.MATCH);
            f.fail.set(true); controller.accept(next);
            assertThat(controller.shown().diagnostics()).anySatisfy(problem -> assertThat(problem.message()).contains("keeping the current appearance"));
        }
    }


    @Test void toolkitChangesResampleAnOpenPreviewOfTheOtherPolarity() throws Exception {
        try (var f = new Fixture()) {
            f.owner.update(config("gtk4", true)); f.drain();
            f.nativeInput.palette(Source.GTK4, true, "0.1,0.1,0.1,1", ""); f.drain();
            var previews = new ArrayList<DesktopPalette.Result>();
            var demand = f.owner.preview(DesktopPalette.SourceMode.GTK4, false, previews::add); f.drain();
            f.nativeInput.palette(Source.GTK4, false, "0.95,0.95,0.95,1", ""); f.drain();
            f.nativeInput.awaitRequested(Source.GTK4, true);
            f.nativeInput.palette(Source.GTK4, true, "0.2,0.2,0.2,1", ""); f.drain();
            f.nativeInput.awaitRequested(Source.GTK4, false);
            f.nativeInput.palette(Source.GTK4, false, "0.85,0.85,0.85,1", ""); f.drain();
            assertThat(previews.getLast().theme().semantic().color(SemanticColor.SURFACE_APP)).isEqualTo(new java.awt.Color(217, 217, 217));
            assertThat(f.themes.current().chrome().dark()).isTrue();
            demand.close();
        }
    }


    @Test void unchangedDarkSamplesStillRefreshTheRetainedLightPreviewAtTheBoundedSamplingInterval() throws Exception {
        try (var f = new Fixture()) {
            f.nativeInput.portal(1, true, ""); f.drain();
            f.owner.update(config("gtk4", true)); f.drain();
            f.nativeInput.palette(Source.GTK4, true, "0.1,0.1,0.1,1", ""); f.drain();
            var darkDemand = f.owner.preview(DesktopPalette.SourceMode.GTK4, true, value -> { }); f.drain();
            var previews = new ArrayList<DesktopPalette.Result>();
            var demand = f.owner.preview(DesktopPalette.SourceMode.GTK4, false, previews::add); f.drain();
            f.nativeInput.palette(Source.GTK4, false, "0.95,0.95,0.95,1", ""); f.drain();
            f.nativeInput.awaitRequested(Source.GTK4, true);
            f.nativeInput.palette(Source.GTK4, true, "0.1,0.1,0.1,1", ""); f.drain();
            int installs = f.installs.get(), delivered = previews.size();
            long samples = f.nativeInput.sampleCount(Source.GTK4);
            f.nativeInput.advance(29); f.drain();
            assertThat(f.nativeInput.sampleCount(Source.GTK4)).isEqualTo(samples);
            f.nativeInput.advance(1); f.drain();
            // The periodic Dark reply is exactly unchanged; only the unobserved Light colors changed.
            // The preview may already supersede this in-flight generation, so no accepted publication is required.
            f.nativeInput.reply(Source.GTK4, true, "0.1,0.1,0.1,1", ""); f.drain();
            f.nativeInput.awaitRequested(Source.GTK4, false);
            f.nativeInput.palette(Source.GTK4, false, "0.85,0.85,0.85,1", ""); f.drain();
            f.nativeInput.awaitRequested(Source.GTK4, true);
            f.nativeInput.palette(Source.GTK4, true, "0.1,0.1,0.1,1", ""); f.drain();
            assertThat(previews).hasSize(delivered + 1);
            assertThat(previews.getLast().theme().semantic().color(SemanticColor.SURFACE_APP))
                .isEqualTo(new java.awt.Color(217, 217, 217));
            assertThat(f.installs.get()).isEqualTo(installs);
            assertThat(f.nativeInput.sampleCount(Source.GTK4)).isEqualTo(samples + 3);
            f.drain();
            assertThat(f.nativeInput.sampleCount(Source.GTK4)).as("no reply-triggered sampling loop").isEqualTo(samples + 3);
            demand.close(); darkDemand.close();
        }
    }

    @Test void suppliedEnvironmentIsCapturedUntilReloadAndReloadRejectsQueuedWorkerTickets() throws Exception {
        var environment = new AtomicReference<>(DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "GNOME")));
        var reads = new AtomicInteger();
        try (var f = new Fixture(() -> { reads.incrementAndGet(); return environment.get(); })) {
            var saved = config("auto", true);
            f.owner.update(saved); f.drain();
            f.nativeInput.palette(Source.GTK4, true, "0.1,0.1,0.1,1", ""); f.drain();
            assertThat(f.owner.current().source()).contains(Source.GTK4);
            environment.set(DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "LXQt", "QT_QPA_PLATFORMTHEME", "qt5ct")));
            f.owner.update(saved); f.drain();
            assertThat(reads.get()).isEqualTo(1);
            assertThat(f.owner.current().source()).contains(Source.GTK4);
            f.nativeInput.palette(Source.QT5, true, "0.2,0.2,0.2,1", "");
            f.nativeInput.unavailable(Source.QT6, "Qt 6", "6.2.4", "fixture unavailable");
            f.nativeInput.unavailable(Source.GTK3, "GTK 3", "3.24", "fixture unavailable"); f.drain();
            var previews = new ArrayList<DesktopPalette.Result>();
            var preview = f.owner.preview(DesktopPalette.SourceMode.AUTO, true, previews::add);
            try {
                f.drain(); previews.clear();
                f.nativeInput.palette(Source.GTK4, true, "0.15,0.15,0.15,1", "queued source update"); f.edt();
                Runnable oldWorker = f.worker.removeLast();
                var before = f.owner.current();
                f.owner.reload();
                oldWorker.run(); f.edt();
                assertThat(f.owner.current()).isSameAs(before);
                assertThat(previews).isEmpty();
                f.drain();
                assertThat(reads.get()).isEqualTo(2);
                assertThat(f.owner.current().source()).contains(Source.QT5);
                assertThat(f.owner.current().theme().semantic().color(SemanticColor.SURFACE_APP)).isEqualTo(new java.awt.Color(51, 51, 51));
                assertThat(previews.getLast().source()).contains(Source.QT5);
                assertThat(saved.desktopSource()).isEqualTo("auto");
                // Reapplying the saved setting retains Automatic and the reloaded context.
                f.owner.update(saved); f.drain();
                assertThat(f.owner.current().source()).contains(Source.QT5);
                assertThat(reads.get()).isEqualTo(2);
            } finally { preview.close(); }
        }
    }
    @Test void reloadChangesCandidateDemandForApplicationAndPreviewTogether() throws Exception {
        var environment = new AtomicReference<>(DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "GNOME")));
        try (var f = new Fixture(environment::get)) {
            f.owner.update(config("gtk3", true)); f.drain();
            var preview = f.owner.preview(DesktopPalette.SourceMode.QT, true, value -> { });
            try {
                f.drain();
                f.nativeInput.awaitToolkit(Source.QT6, true); f.nativeInput.awaitToolkit(Source.QT5, true);
                f.nativeInput.awaitToolkit(Source.KDE5, false); f.nativeInput.awaitToolkit(Source.KDE6, false);
                environment.set(DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "KDE", "KDE_SESSION_VERSION", "5")));
                f.owner.reload(); f.drain();
                f.nativeInput.awaitToolkit(Source.KDE5, true); f.nativeInput.awaitToolkit(Source.KDE6, true);
                f.nativeInput.palette(Source.GTK3, true, "0.1,0.1,0.1,1", ""); f.drain();
                assertThat(f.owner.current().source()).contains(Source.GTK3);
            } finally { preview.close(); }
        }
    }

    static ConfigSnapshot config(String source, boolean dark) {
        return ConfigSnapshot.defaults().toBuilder().darkTheme("desktop-colors").lightTheme("desktop-colors")
            .desktopSource(source).appearance(dark ? AppearanceMode.DARK : AppearanceMode.LIGHT).build();
    }
    static final class Fixture implements AutoCloseable {
        final DesktopColorsHarness nativeInput = new DesktopColorsHarness();
        final ArrayDeque<Runnable> worker = new ArrayDeque<>(), dispatch = new ArrayDeque<>();
        final AtomicInteger installs = new AtomicInteger(); final AtomicBoolean fail = new AtomicBoolean();
        final List<ThemeController.InstallationFailure> failures = new ArrayList<>();
        final ThemeController themes = ThemeController.start(true, ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH), Kit.DEFAULT,
            kit -> theme -> { installs.incrementAndGet(); return !fail.get(); }, failure -> { throw failure; }).controller();
        final DesktopAppearance owner;
        Fixture() { this(() -> DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "GNOME"))); }
        Fixture(java.util.function.Supplier<DesktopEnvironment> environment) {
            owner = new DesktopAppearance(themes, nativeInput.service, environment, worker::add, dispatch::add);
            owner.onFailure(failures::add);
        }
        void edt() { while (!dispatch.isEmpty()) dispatch.remove().run(); }
        void work() { while (!worker.isEmpty()) worker.remove().run(); }
        void drain() { for (int i = 0; i < 5; i++) { nativeInput.pump(); edt(); work(); edt(); } }
        @Override public void close() { owner.close(); drain(); nativeInput.close(); themes.close(); }
    }
}
