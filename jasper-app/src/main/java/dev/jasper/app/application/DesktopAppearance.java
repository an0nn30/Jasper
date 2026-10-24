package dev.jasper.app.application;

import dev.jasper.app.appearance.DesktopPalette;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemePick;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.platform.DesktopColors;
import dev.jasper.app.platform.DesktopObservation;
import dev.jasper.app.platform.DesktopObservation.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** EDT-owned desktop demand and transactional publication. Native sampling and theme generation run on workers. */
final class DesktopAppearance implements AutoCloseable {
    private record Key(Source source, Polarity polarity) { }
    private record Demand(Set<Source> sources, boolean dark) { }
    private record Applied(ConfigSnapshot config, ThemeSelection selection, boolean dark, DesktopPalette.SourceMode source) { }
    private final ThemeController themes;
    private final DesktopColors desktop;
    private final String currentDesktop;
    private final Executor worker, edt;
    private final Runnable unsubscribe, unsubscribeResample;
    private final Map<Key, Palette> cache = new HashMap<>();
    private final List<Preview> previews = new ArrayList<>();
    private final Set<Runnable> listeners = new LinkedHashSet<>();
    private DesktopObservation observation;
    private ConfigSnapshot config;
    private ThemeSelection selection;
    private Demand demand;
    private long revision, generation;
    private boolean closed;
    private DesktopPalette.Result current;
    private Consumer<ThemeController.InstallationFailure> failure = problem -> System.getLogger(DesktopAppearance.class.getName())
        .log(System.Logger.Level.WARNING, "Could not apply desktop colors", problem);

    DesktopAppearance(ThemeController themes, DesktopColors desktop, String currentDesktop, Executor worker) {
        this(themes, desktop, currentDesktop, worker, SwingUtilities::invokeLater);
    }
    DesktopAppearance(ThemeController themes, DesktopColors desktop, String currentDesktop, Executor worker, Executor edt) {
        this.themes = Objects.requireNonNull(themes); this.desktop = Objects.requireNonNull(desktop);
        this.currentDesktop = currentDesktop; this.worker = Objects.requireNonNull(worker); this.edt = Objects.requireNonNull(edt);
        observation = desktop.current();
        current = new DesktopPalette.Result(themes.current().chrome(), Optional.empty(), "Desktop palette pending");
        unsubscribe = desktop.subscribe(value -> edt.execute(() -> observed(value)));
        unsubscribeResample = desktop.subscribeResample(() -> edt.execute(this::resamplePreviews));
    }
    void onFailure(Consumer<ThemeController.InstallationFailure> listener) { failure = Objects.requireNonNull(listener); }
    void update(ConfigSnapshot config) {
        update(config, ThemeSelection.of(themes.library(), config.darkTheme(), config.lightTheme(), config.terminalScheme(), (key, message) -> { }));
    }
    void update(ConfigSnapshot config, ThemeSelection selection) {
        requireEdt(); if (closed) return;
        this.config = Objects.requireNonNull(config); this.selection = Objects.requireNonNull(selection);
        revision++;
        syncDemand(); resolve();
    }
    void reload() {
        requireEdt(); if (closed) return;
        revision++; previews.forEach(preview -> preview.sampled = false);
        desktop.reload(); syncDemand(); resolve();
    }
    DesktopPalette.Result current() { requireEdt(); return current; }
    Runnable subscribe(Runnable listener) {
        requireEdt(); Objects.requireNonNull(listener);
        if (!closed) listeners.add(listener);
        return () -> { requireEdt(); listeners.remove(listener); };
    }
    AutoCloseable preview(DesktopPalette.SourceMode source, boolean dark, Consumer<DesktopPalette.Result> callback) {
        requireEdt(); Objects.requireNonNull(source); Objects.requireNonNull(callback);
        if (closed) return () -> { };
        var preview = new Preview(source, dark, callback); previews.add(preview);
        revision++; syncDemand(); resolve();
        return () -> {
            requireEdt();
            if (!previews.remove(preview)) return;
            revision++; syncDemand(); resolve();
        };
    }
    private boolean dark() {
        return config.appearance() == AppearanceMode.DARK || config.appearance() == AppearanceMode.SYSTEM
            && observation.portal().appearance() != Polarity.LIGHT;
    }
    private Applied applied() {
        if (config == null) return null;
        return new Applied(config, selection, dark(), DesktopPalette.SourceMode.valueOf(config.desktopSource().toUpperCase(Locale.ROOT)));
    }
    /** Every retained polarity gets a bounded refresh even when the active polarity is content-equal. */
    private void resamplePreviews() {
        if (closed || previews.isEmpty()) return;
        // The supervisor already samples the current polarity at this boundary. An equal reply is suppressed,
        // so it must not remain ahead of the other polarity in the pending queue.
        previews.forEach(preview -> preview.sampled = demand != null && preview.dark == demand.dark());
        revision++;
        syncDemand(); resolve();
    }
    private void observed(DesktopObservation value) {
        if (closed) return;
        boolean portalChanged = !observation.portal().equals(value.portal());
        observation = value;
        for (Palette palette : value.palettes().values()) {
            if (palette.generation() == generation && DesktopPalette.usable(palette.appearance() == Polarity.DARK, palette)) {
                Palette previous = cache.put(new Key(palette.source(), palette.appearance()), palette);
                boolean changed = previous == null || !previous.colors().equals(palette.colors())
                    || !previous.implementation().equals(palette.implementation()) || !previous.version().equals(palette.version());
                if (changed) for (Preview preview : previews)
                    if (preview.dark != (palette.appearance() == Polarity.DARK)
                            && DesktopPalette.preferredSources(preview.source, currentDesktop).contains(palette.source())) preview.sampled = false;
            }
        }
        if (portalChanged) previews.forEach(preview -> preview.sampled = false);
        for (Preview preview : previews) {
            if (demand != null && preview.dark == demand.dark() && DesktopPalette.preferredSources(preview.source, currentDesktop).stream()
                    .allMatch(source -> value.palettes().containsKey(source) && value.palettes().get(source).generation() == generation))
                preview.sampled = true;
        }
        revision++;
        syncDemand(); resolve();
    }
    /** One polarity at a time; preview replies populate only their cache, then the saved demand resumes. */
    private void syncDemand() {
        Applied applied = applied();
        Preview pending = previews.stream().filter(preview -> !preview.sampled).findFirst().orElse(null);
        boolean appliedDesktop = applied != null && applied.selection().theme(applied.dark()) instanceof ThemePick.Desktop;
        boolean dark = pending != null ? pending.dark : appliedDesktop ? applied.dark()
            : !previews.isEmpty() ? previews.getFirst().dark : applied != null && applied.dark();
        var sources = EnumSet.noneOf(Source.class);
        if (pending != null) sources.addAll(DesktopPalette.preferredSources(pending.source, currentDesktop));
        if (appliedDesktop)
            sources.addAll(DesktopPalette.preferredSources(applied.source(), currentDesktop));
        for (Preview preview : previews)
            sources.addAll(DesktopPalette.preferredSources(preview.source, currentDesktop));
        var next = new Demand(Set.copyOf(sources), dark);
        if (!next.equals(demand)) { demand = next; desktop.request(++generation, next.sources(), dark); }
    }
    private DesktopObservation input(boolean dark) {
        var palettes = new EnumMap<Source, Palette>(Source.class);
        Polarity polarity = dark ? Polarity.DARK : Polarity.LIGHT;
        for (Source source : Source.values()) {
            Palette cached = cache.get(new Key(source, polarity));
            if (cached != null) palettes.put(source, cached);
            Palette latest = observation.palettes().get(source);
            if (latest != null && latest.appearance() == polarity && latest.generation() == generation
                    && DesktopPalette.usable(dark, latest)) palettes.put(source, latest);
            else if (latest != null && latest.appearance() == polarity && latest.generation() == generation && cached != null)
                palettes.put(source, new Palette(cached.generation(), source, polarity, cached.colors(), cached.implementation(),
                    cached.version(), latest.reason().isBlank() ? "Desktop toolkit palette unavailable; using last good palette" : latest.reason()));
        }
        return new DesktopObservation(observation.portal(), palettes);
    }
    private void resolve() {
        if (closed) return;
        long ticket = revision;
        Portal portal = observation.portal();
        Applied applied = applied();
        if (applied != null && !(applied.selection().theme(applied.dark()) instanceof ThemePick.Desktop)) {
            try { themes.configure(applied.dark(), applied.selection(), applied.config().uiFont()); }
            catch (ThemeController.InstallationFailure problem) { failure.accept(problem); }
        }
        DesktopObservation appliedInput = applied == null ? null : input(applied.dark());
        var requests = List.copyOf(previews);
        var inputs = requests.stream().map(preview -> input(preview.dark)).toList();
        worker.execute(() -> {
            DesktopPalette.Result result = applied != null && applied.selection().theme(applied.dark()) instanceof ThemePick.Desktop
                ? resolved(applied.dark(), applied.source(), appliedInput) : null;
            var previewResults = new ArrayList<DesktopPalette.Result>();
            for (int i = 0; i < requests.size(); i++) {
                Preview preview = requests.get(i);
                previewResults.add(resolved(preview.dark, preview.source, inputs.get(i)));
            }
            edt.execute(() -> {
                if (closed || revision != ticket || !desktop.current().portal().equals(portal)) return;
                if (result != null) {
                    try {
                        themes.configure(applied.dark(), applied.selection(), applied.config().uiFont(), result.theme());
                        if (!current.equals(result)) {
                            current = result;
                            for (Runnable listener : List.copyOf(listeners)) if (listeners.contains(listener)) notify(listener);
                        }
                    } catch (ThemeController.InstallationFailure problem) { failure.accept(problem); }
                }
                for (int i = 0; i < requests.size(); i++) {
                    Preview preview = requests.get(i); DesktopPalette.Result value = previewResults.get(i);
                    if (previews.contains(preview) && !value.equals(preview.delivered)) {
                        preview.delivered = value; notify(() -> preview.callback.accept(value));
                    }
                }
            });
        });
    }
    private DesktopPalette.Result resolved(boolean dark, DesktopPalette.SourceMode source, DesktopObservation input) {
        var result = DesktopPalette.resolve(dark, source, currentDesktop, input);
        String status = result.status();
        if (result.source().isPresent()) {
            var palette = input.palettes().get(result.source().get());
            if (!palette.reason().isBlank()) status += "; " + palette.reason();
        }
        if (!input.portal().reason().isBlank()) status += "; " + input.portal().reason();
        return new DesktopPalette.Result(result.theme(), result.source(), status);
    }
    private static void notify(Runnable listener) {
        try { listener.run(); }
        catch (RuntimeException failure) { System.getLogger(DesktopAppearance.class.getName()).log(System.Logger.Level.WARNING, "Desktop appearance listener failed", failure); }
    }
    @Override public void close() {
        requireEdt(); if (closed) return;
        closed = true; revision++; unsubscribe.run(); unsubscribeResample.run(); previews.clear(); listeners.clear(); cache.clear(); desktop.close();
        if (worker instanceof ExecutorService service) service.shutdown();
    }
    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Desktop appearance requires the EDT");
    }
    private static final class Preview {
        final DesktopPalette.SourceMode source; final boolean dark; final Consumer<DesktopPalette.Result> callback;
        boolean sampled; DesktopPalette.Result delivered;
        Preview(DesktopPalette.SourceMode source, boolean dark, Consumer<DesktopPalette.Result> callback) {
            this.source = source; this.dark = dark; this.callback = callback;
        }
    }
}
