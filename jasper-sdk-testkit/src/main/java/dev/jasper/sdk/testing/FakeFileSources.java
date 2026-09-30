package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.files.FileSource;
import dev.jasper.sdk.files.FileSourceSpec;
import dev.jasper.sdk.files.FileSources;
import java.util.Objects;

/** One plugin's file sources, stored in the host in registration order; they vanish when the plugin stops. */
final class FakeFileSources implements FileSources {
    /** A registration as the host keeps it. */
    record Registered(FakePluginContext context, FileSource source, FileSourceSpec spec) { }

    private final FakePluginHost host;
    private final FakePluginContext context;

    FakeFileSources(FakePluginHost host, FakePluginContext context) { this.host = host; this.context = context; }

    @Override public Subscription register(FileSource source) {
        context.requireOpen();
        Objects.requireNonNull(source, "source");
        if (!context.plugin().capabilities().contains(Capabilities.FILES_SOURCE))
            throw new MissingCapabilityException(context.plugin().id(), Capabilities.FILES_SOURCE);
        FileSourceSpec spec = Objects.requireNonNull(source.spec(), "spec");
        if (!spec.id().startsWith(context.plugin().id() + "."))
            throw new IllegalArgumentException("A file source id must start with " + context.plugin().id() + ".: " + spec.id());
        if (host.fileSources.containsKey(spec.id())) throw new IllegalArgumentException("File source already registered: " + spec.id());
        var registered = new Registered(context, source, spec);
        host.fileSources.put(spec.id(), registered);
        Subscription subscription = () -> host.fileSources.remove(spec.id(), registered);
        context.own(subscription);
        return subscription;
    }

    void closeAll() { host.fileSources.values().removeIf(registered -> registered.context() == context); }
}
