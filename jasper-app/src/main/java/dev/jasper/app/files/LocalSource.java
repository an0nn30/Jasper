package dev.jasper.app.files;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The local disk as a source with one always-connected root. */
public final class LocalSource implements ChooserSource {
    private final LocalBrowser browser;

    public LocalSource(LocalBrowser browser) { this.browser = Objects.requireNonNull(browser); }

    @Override public String id() { return Location.LOCAL; }
    @Override public String heading() { return "This computer"; }
    @Override public List<Root> roots() { return List.of(new Root(Location.LOCAL, "This computer", "", true)); }
    @Override public Runnable onChanged(Runnable listener) { return () -> { }; }
    @Override public CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow) { return CompletableFuture.completedFuture(browser); }
}
