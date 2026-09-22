package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.Palette;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Objects;
import java.util.Optional;

/** One plugin's palette registrations; the host keeps the scopes and answers queries through them. */
final class FakePalette implements Palette {
    /** A registered scope and the context that owns it. */
    record Registered(FakePluginContext context, PaletteScope scope, ScopeSpec spec) { }

    private final FakePluginHost host;
    private final FakePluginContext context;

    FakePalette(FakePluginHost host, FakePluginContext context) { this.host = host; this.context = context; }

    private void requireCapability() {
        if (!context.plugin().capabilities().contains(Capabilities.PALETTE_CONTRIBUTE))
            throw new MissingCapabilityException(context.plugin().id(), Capabilities.PALETTE_CONTRIBUTE);
    }

    @Override public Subscription register(PaletteScope scope) {
        context.requireOpen();
        Objects.requireNonNull(scope, "scope");
        requireCapability();
        ScopeSpec spec = Objects.requireNonNull(scope.spec(), "spec");
        if (!spec.id().startsWith(context.plugin().id() + "."))
            throw new IllegalArgumentException("A scope id must start with " + context.plugin().id() + ".: " + spec.id());
        spec.shortcutActionId().ifPresent(context.ui::requireOwn);
        if (host.scopes.containsKey(spec.id())) throw new IllegalArgumentException("Scope already contributed: " + spec.id());
        var registered = new Registered(context, scope, spec);
        host.scopes.put(spec.id(), registered);
        return () -> host.scopes.remove(spec.id(), registered);
    }

    @Override public void open(WindowHandle window, String scopeId, Optional<String> query, Optional<String> rowId) {
        context.requireOpen();
        Objects.requireNonNull(window, "window"); Objects.requireNonNull(scopeId, "scopeId");
        requireCapability();
        host.paletteOpens.add(window.id() + " " + scopeId + " " + query.orElse("-") + " " + rowId.orElse("-"));
    }

    /** Drops this plugin's scopes at teardown. */
    void closeAll() { host.scopes.values().removeIf(registered -> registered.context() == context); }
}
