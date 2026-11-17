package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.FileAssociations;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * One plugin's {@code Platform.fileAssociations()}: the platform layer's facade with its values mapped one to one (the
 * bundle id or ProgId of an application stays behind) and each future completed on the UI thread. The facade is asked at
 * each call, so a replaced one is followed. A completion after the plugin closed runs nothing and leaves the plugin's
 * future incomplete. A facade future that completes exceptionally, which it is built not to, becomes
 * {@link FileAssociations.Outcome.Failed} for each extension, never an exceptional completion.
 */
final class HostedFileAssociations implements FileAssociations {
    private static final System.Logger LOG = System.getLogger(HostedFileAssociations.class.getName());

    private final String pluginId;
    private final Supplier<dev.jasper.app.platform.FileAssociations> facade;
    private final BooleanSupplier open;
    private final BooleanSupplier onUi;
    private final Function<Runnable, Subscription> changes;

    /**
     * @param pluginId the plugin's id, for messages
     * @param facade the platform layer's facade as of now
     * @param open whether the plugin is still running
     * @param onUi whether the calling thread is the UI thread
     * @param changes registers a handler for a change the facade reports and gives the registration; the caller runs the
     *     handler in containment and ends the registration with the plugin
     */
    HostedFileAssociations(String pluginId, Supplier<dev.jasper.app.platform.FileAssociations> facade, BooleanSupplier open,
                           BooleanSupplier onUi, Function<Runnable, Subscription> changes) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.facade = Objects.requireNonNull(facade, "facade");
        this.open = Objects.requireNonNull(open, "open");
        this.onUi = Objects.requireNonNull(onUi, "onUi");
        this.changes = Objects.requireNonNull(changes, "changes");
    }

    private void require(String what) {
        if (!open.getAsBoolean()) throw new IllegalStateException("Plugin context is closed: " + pluginId);
        if (!onUi.getAsBoolean()) throw new IllegalStateException("FileAssociations." + what + " must be called on the UI thread: " + pluginId);
    }

    @Override public Optional<Unavailable> unavailable() {
        require("unavailable");
        return facade.get().unavailable().map(reason -> Unavailable.valueOf(reason.name()));
    }

    @Override public CompletableFuture<List<FileTypeStatus>> status(List<String> extensions) {
        require("status");
        List<String> asked = List.copyOf(extensions);
        return adapt(facade.get().status(asked), open,
            statuses -> statuses.stream().map(HostedFileAssociations::status).toList(),
            detail -> asked.stream().map(extension -> new FileTypeStatus(extension, List.of())).toList());
    }

    @Override public CompletableFuture<Claimability> claimability(String extension) {
        require("claimability");
        Objects.requireNonNull(extension, "extension");
        return adapt(facade.get().claimability(extension), open, HostedFileAssociations::claimable,
            detail -> new Claimability.NotClaimable(Refusal.NO_SYSTEM_TYPE));
    }

    @Override public CompletableFuture<List<TypeResult>> claim(List<String> extensions) {
        require("claim");
        List<String> asked = List.copyOf(extensions);
        return adapt(facade.get().claim(asked), open, HostedFileAssociations::results, detail -> failed(asked, detail));
    }

    @Override public CompletableFuture<List<TypeResult>> restore(List<String> extensions) {
        require("restore");
        List<String> asked = List.copyOf(extensions);
        return adapt(facade.get().restore(asked), open, HostedFileAssociations::results, detail -> failed(asked, detail));
    }

    @Override public CompletableFuture<Void> forget(String extension) {
        require("forget");
        Objects.requireNonNull(extension, "extension");
        return adapt(facade.get().forget(extension), open, done -> null, detail -> null);
    }

    @Override public Subscription onChanged(Runnable handler) {
        require("onChanged");
        Objects.requireNonNull(handler, "handler");
        return changes.apply(() -> { if (open.getAsBoolean()) handler.run(); });
    }

    /**
     * Completes a new future on the UI thread with {@code map} of the source's answer, or with {@code failure} of the
     * reason when the source failed or {@code map} threw; never exceptionally. Nothing completes once {@code open} says
     * the plugin closed.
     */
    static <A, S> CompletableFuture<S> adapt(CompletableFuture<A> source, BooleanSupplier open, Function<A, S> map,
                                            Function<String, S> failure) {
        CompletableFuture<S> answer = new CompletableFuture<>();
        source.whenComplete((value, problem) -> SwingUtilities.invokeLater(() -> {
            if (!open.getAsBoolean()) return;
            S mapped;
            try {
                if (problem != null) throw problem instanceof java.util.concurrent.CompletionException wrapped && wrapped.getCause() != null
                    ? wrapped.getCause() : problem;
                mapped = map.apply(value);
            } catch (Throwable thrown) {
                LOG.log(System.Logger.Level.WARNING, "A file association call failed", thrown);
                mapped = failure.apply(String.valueOf(thrown.getMessage()));
            }
            answer.complete(mapped);
        }));
        return answer;
    }

    private static List<TypeResult> failed(List<String> extensions, String detail) {
        List<TypeResult> out = new ArrayList<>();
        for (String extension : extensions) out.add(new TypeResult(extension, "", new Outcome.Failed(detail)));
        return out;
    }

    private static List<TypeResult> results(List<dev.jasper.app.platform.FileAssociations.TypeResult> results) {
        return results.stream().map(result -> new TypeResult(result.extension(), result.type(), outcome(result.outcome()))).toList();
    }

    private static FileTypeStatus status(dev.jasper.app.platform.FileAssociations.ExtensionStatus status) {
        return new FileTypeStatus(status.extension(), status.types().stream()
            .map(type -> new TypeState(type.type(), handler(type.handler()), type.previous().map(HostedFileAssociations::handler))).toList());
    }

    static Handler handler(dev.jasper.app.platform.FileAssociations.Handler handler) {
        return switch (handler) {
            case dev.jasper.app.platform.FileAssociations.Handler.Jasper jasper -> new Handler.Jasper();
            case dev.jasper.app.platform.FileAssociations.Handler.Other other -> new Handler.Other(other.name());
            case dev.jasper.app.platform.FileAssociations.Handler.None none -> new Handler.None();
            case dev.jasper.app.platform.FileAssociations.Handler.Unknown unknown -> new Handler.Unknown();
        };
    }

    static Claimability claimable(dev.jasper.app.platform.FileAssociations.Claimability claimability) {
        return switch (claimability) {
            case dev.jasper.app.platform.FileAssociations.Claimability.Claimable claimable ->
                new Claimability.Claimable(claimable.types(), claimable.declares());
            case dev.jasper.app.platform.FileAssociations.Claimability.NotClaimable not ->
                new Claimability.NotClaimable(Refusal.valueOf(not.refusal().name()));
        };
    }

    static Outcome outcome(dev.jasper.app.platform.FileAssociations.Outcome outcome) {
        return switch (outcome) {
            case dev.jasper.app.platform.FileAssociations.Outcome.Done done -> new Outcome.Done();
            case dev.jasper.app.platform.FileAssociations.Outcome.InSettings settings -> new Outcome.InSettings();
            case dev.jasper.app.platform.FileAssociations.Outcome.NoPrevious none -> new Outcome.NoPrevious();
            case dev.jasper.app.platform.FileAssociations.Outcome.PreviousGone gone -> new Outcome.PreviousGone();
            case dev.jasper.app.platform.FileAssociations.Outcome.Refused refused -> new Outcome.Refused(Refusal.valueOf(refused.refusal().name()));
            case dev.jasper.app.platform.FileAssociations.Outcome.Failed failed -> new Outcome.Failed(failed.detail());
        };
    }
}
