package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.Sizes;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Turns transfers that last more than a second into SDK activities with progress and a Cancel that drops the client. UI thread. */
public final class TransferActivities {
    static final Duration AFTER = Duration.ofSeconds(1);
    private final Function<ActivitySpec, ActivityHandle> begin;
    private final String activateActionId;
    private final Map<Long, ServerEvent.TransferStarted> pending = new HashMap<>();
    private final Map<Long, ActivityHandle> handles = new HashMap<>();

    public TransferActivities(Function<ActivitySpec, ActivityHandle> begin, String activateActionId) {
        this.begin = begin;
        this.activateActionId = activateActionId;
    }

    public void accept(ServerEvent event) {
        switch (event) {
            case ServerEvent.TransferStarted started -> pending.put(started.id(), started);
            case ServerEvent.TransferProgress progress -> progress(progress);
            case ServerEvent.Transferred done -> {
                pending.remove(done.id());
                ActivityHandle handle = handles.remove(done.id());
                if (handle == null) return;
                if (done.complete()) handle.succeed(Sizes.human(done.bytes()) + " in " + Sizes.seconds(done.took()));
                else handle.fail("Stopped after " + Sizes.human(done.bytes()));
            }
            default -> { }
        }
    }

    private void progress(ServerEvent.TransferProgress progress) {
        ServerEvent.TransferStarted started = pending.get(progress.id());
        if (started == null) return;
        ActivityHandle handle = handles.get(progress.id());
        if (handle == null) {
            if (Duration.between(started.at(), progress.at()).compareTo(AFTER) < 0) return;
            handle = begin.apply(new ActivitySpec(title(started), started.remote(), Optional.of(activateActionId), Optional.of(started.cancel())));
            handles.put(progress.id(), handle);
        }
        String detail = Sizes.human(progress.bytes()) + (progress.total() > 0 ? " of " + Sizes.human(progress.total()) : "");
        if (progress.total() > 0) handle.progress(Math.min(1.0, progress.bytes() / (double) progress.total()), detail);
        else handle.detail(detail);
    }

    static String title(ServerEvent.TransferStarted started) {
        String name = started.path().substring(started.path().lastIndexOf('/') + 1);
        return (started.direction() == ServerEvent.Direction.DOWNLOAD ? "Sending " : "Receiving ") + name;
    }
}
