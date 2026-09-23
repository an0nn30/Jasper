package dev.jasper.remote.sftp;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.client.Connections;
import dev.jasper.sdk.terminal.WindowHandle;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** UI-owned lease acquisition and background subsystem opening, including cancellation before open completes. */
public final class EndpointFactory {
    private final Connections connections;
    private final Executor background, ui;
    private final Supplier<Duration> timeout;
    public EndpointFactory(Connections connections, Executor background, Executor ui, Supplier<Duration> timeout) {
        this.connections = connections; this.background = background; this.ui = ui; this.timeout = timeout;
    }
    /** Invoke on UI; absent remote identity selects this machine. */
    public CompletableFuture<FileEndpoint> open(Optional<ConnectionIdentity> identity, WindowHandle owner, Consumer<String> status) {
        if (identity.isEmpty()) return CompletableFuture.completedFuture(new LocalEndpoint());
        var result = new CompletableFuture<FileEndpoint>();
        var opening = new AtomicReference<SftpEndpoint>();
        var request = connections.lease(identity.orElseThrow(), owner, status);
        result.whenComplete((endpoint, problem) -> {
            if (result.isCancelled()) {
                request.cancel(true);
                var current = opening.get(); if (current != null) current.abort();
            }
        });
        request.whenComplete((lease, failure) -> ui.execute(() -> {
            if (failure != null) { result.completeExceptionally(failure); return; }
            if (result.isDone()) { lease.close(); return; }
            Duration deadline = timeout.get();
            try {
                background.execute(() -> {
                    try {
                        var endpoint = new SftpEndpoint(lease, deadline, born -> {
                            opening.set(born); if (result.isCancelled()) born.abort();
                        });
                        ui.execute(() -> { if (!result.complete(endpoint)) endpoint.abort(); });
                    } catch (Exception problem) { lease.close(); ui.execute(() -> result.completeExceptionally(problem)); }
                });
            } catch (RuntimeException rejected) { lease.close(); result.completeExceptionally(rejected); }
        }));
        return result;
    }
}
