package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.palette.PaletteContext;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.Palette;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteStep;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;

/**
 * One plugin's palette scopes, adapted onto the application's scope contract. Every call into the
 * plugin is contained: a failing scope shows no rows, an unavailable row, no step or nothing done.
 * The plugin's own row travels as the app row's token and comes back to the plugin unchanged.
 */
final class HostedPalette implements Palette {
    private final String pluginId;
    private final CapabilityGate gate;
    private final Contributions model;
    private final Containment containment;
    private final HostedUi ui;
    private final HostedTerminals terminals;
    private boolean audited;

    HostedPalette(String pluginId, CapabilityGate gate, Contributions model, Containment containment, HostedUi ui, HostedTerminals terminals) {
        this.pluginId = pluginId; this.gate = gate; this.model = model; this.containment = containment; this.ui = ui; this.terminals = terminals;
    }

    private void requireContribute() {
        gate.require(Capabilities.PALETTE_CONTRIBUTE);
        if (!audited) { audited = true; gate.audit(Capabilities.PALETTE_CONTRIBUTE, "used the command palette"); }
    }

    @Override public Subscription register(PaletteScope scope) {
        ui.guard("register");
        Objects.requireNonNull(scope, "scope");
        requireContribute();
        ScopeSpec spec = Objects.requireNonNull(scope.spec(), "spec");
        if (!spec.id().startsWith(pluginId + "."))
            throw new IllegalArgumentException("A scope id must start with " + pluginId + ".: " + spec.id());
        spec.shortcutActionId().ifPresent(ui::requireOwn);
        dev.jasper.app.lifecycle.Subscription added = model.addScope(new Adapted(scope, spec));
        return ui.tracked(added::close);
    }

    @Override public void open(WindowHandle window, String scopeId, Optional<String> query, Optional<String> rowId) {
        ui.guard("open");
        Objects.requireNonNull(window, "window");
        requireContribute();
        model.requestPalette(new Contributions.PaletteRequest(window.id(), Objects.requireNonNull(scopeId, "scopeId"),
            Objects.requireNonNull(query, "query"), Objects.requireNonNull(rowId, "rowId")));
    }

    private PaletteQuery query(PaletteContext context) {
        var windowId = context.target().windowId().orElseThrow(() -> new IllegalStateException("The palette has no window"));
        return new PaletteQuery(terminals.windowHandle(windowId), context.target().paneId().map(terminals::paneHandle),
            context.maxResults(), context.macOs());
    }

    private static dev.jasper.app.palette.PaletteRow appRow(PaletteRow row) {
        return new dev.jasper.app.palette.PaletteRow(row.id(), row.title(), row.detail().orElse(null), row.tag().orElse(null),
            row.icon().orElse(null), row.enabled(), row);
    }

    private static dev.jasper.app.palette.PaletteStep.Result appResult(PaletteStep.Result result) {
        if (result.error().isPresent()) return dev.jasper.app.palette.PaletteStep.Result.error(result.error().get());
        if (result.reopenScopeId().isPresent())
            return dev.jasper.app.palette.PaletteStep.Result.reopen(result.reopenScopeId().get(), result.reopenRowId().orElse(null), result.reopenQuery().orElse(null));
        return dev.jasper.app.palette.PaletteStep.Result.done();
    }

    /** The application's view of one plugin scope. */
    private final class Adapted implements dev.jasper.app.palette.PaletteScope {
        private final PaletteScope scope;
        private final ScopeSpec spec;
        private final List<dev.jasper.app.palette.PaletteVerb> verbs;

        Adapted(PaletteScope scope, ScopeSpec spec) {
            this.scope = scope; this.spec = spec;
            this.verbs = spec.verbs().stream().map(verb -> new dev.jasper.app.palette.PaletteVerb(verb.id(), verb.label())).toList();
        }

        @Override public String id() { return spec.id(); }
        @Override public String label() { return spec.label(); }
        @Override public Icon icon() { return spec.icon().orElse(null); }
        @Override public String description() { return spec.description(); }
        @Override public String placeholder() { return spec.placeholder(); }
        @Override public List<String> aliases() { return spec.aliases(); }
        @Override public List<dev.jasper.app.palette.PaletteVerb> verbs() { return verbs; }
        @Override public boolean monospaceRows() { return spec.monospaceRows(); }
        @Override public Optional<String> shortcutActionId() { return spec.shortcutActionId(); }

        private Optional<PaletteVerb> own(dev.jasper.app.palette.PaletteVerb verb) {
            return spec.verbs().stream().filter(candidate -> candidate.id().equals(verb.id())).findFirst();
        }

        private static Optional<PaletteRow> own(dev.jasper.app.palette.PaletteRow row) {
            return row.token() instanceof PaletteRow original ? Optional.of(original) : Optional.empty();
        }

        /** Runs {@code call} inside containment; {@code fallback} when it throws. */
        private <T> T contained(String what, T fallback, Callable<T> call) {
            var result = new AtomicReference<>(fallback);
            containment.attempt(pluginId, "scope " + spec.id() + " " + what, () -> { result.set(call.call()); return null; });
            return result.get();
        }

        @Override public void activated(PaletteContext context) {
            contained("activated", null, () -> { scope.activated(query(context)); return null; });
        }

        @Override public dev.jasper.app.palette.PaletteResults search(String query, PaletteContext context) {
            PaletteResults results = contained("search", PaletteResults.none(), () -> scope.search(query, query(context)));
            var rows = new ArrayList<dev.jasper.app.palette.PaletteRow>();
            for (PaletteRow row : results.rows()) {
                if (rows.size() == context.maxResults()) break;
                rows.add(appRow(row));
            }
            return new dev.jasper.app.palette.PaletteResults(rows, results.sectionLabel().orElse(null), results.initialSelectionId().orElse(null));
        }

        @Override public boolean available(dev.jasper.app.palette.PaletteRow row, dev.jasper.app.palette.PaletteVerb verb, PaletteContext context) {
            Optional<PaletteRow> original = own(row); Optional<PaletteVerb> chosen = own(verb);
            if (original.isEmpty() || chosen.isEmpty()) return false;
            return contained("available", false, () -> scope.available(original.get(), chosen.get(), query(context)));
        }

        @Override public dev.jasper.app.palette.PaletteStep step(dev.jasper.app.palette.PaletteRow row, dev.jasper.app.palette.PaletteVerb verb, PaletteContext context) {
            Optional<PaletteRow> original = own(row); Optional<PaletteVerb> chosen = own(verb);
            if (original.isEmpty() || chosen.isEmpty()) return null;
            Optional<PaletteStep> step = contained("step", Optional.empty(), () -> scope.step(original.get(), chosen.get(), query(context)));
            return step.map(this::appStep).orElse(null);
        }

        private dev.jasper.app.palette.PaletteStep appStep(PaletteStep step) {
            List<dev.jasper.app.palette.PaletteStep.Field> fields = step.fields().stream()
                .map(field -> new dev.jasper.app.palette.PaletteStep.Field(field.name(), field.label(), field.prefill())).toList();
            return new dev.jasper.app.palette.PaletteStep(step.title(), fields, (values, done) -> {
                // A completion that throws before answering would leave the form waiting; answer for it.
                if (!containment.run(pluginId, "scope " + spec.id() + " step", () -> step.complete().accept(values, result -> done.accept(appResult(result)))))
                    done.accept(dev.jasper.app.palette.PaletteStep.Result.error("The plugin could not complete this step."));
            });
        }

        @Override public void execute(dev.jasper.app.palette.PaletteRow row, dev.jasper.app.palette.PaletteVerb verb, PaletteContext context) {
            Optional<PaletteRow> original = own(row); Optional<PaletteVerb> chosen = own(verb);
            if (original.isEmpty() || chosen.isEmpty()) return;
            containment.run(pluginId, "scope " + spec.id() + " execute", () -> scope.execute(original.get(), chosen.get(), query(context)));
        }

        @Override public dev.jasper.app.lifecycle.Subscription onChanged(Runnable listener) {
            Subscription registration = contained("onChanged", null, () -> scope.onChanged(listener));
            return new dev.jasper.app.lifecycle.Subscription(() -> { if (registration != null) registration.close(); });
        }
    }
}
