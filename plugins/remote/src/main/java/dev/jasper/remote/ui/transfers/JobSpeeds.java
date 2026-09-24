package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.TransferState;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-transfer smoothed payload speed; a transfer that is not copying has none and starts over. */
final class JobSpeeds {
    private record Sample(long bytes, long nanos, double speed) { }
    private final Map<UUID, Sample> samples = new HashMap<>();

    double update(UUID id, TransferState state, long doneBytes, long nanos) {
        if (state != TransferState.RUNNING) { samples.remove(id); return 0; }
        var last = samples.get(id);
        double speed = 0;
        if (last != null && nanos > last.nanos() && doneBytes >= last.bytes()) {
            double instant = (doneBytes - last.bytes()) * 1_000_000_000.0 / (nanos - last.nanos());
            speed = last.speed() == 0 ? instant : last.speed() * .7 + instant * .3;
        }
        samples.put(id, new Sample(doneBytes, nanos, speed));
        return speed;
    }

    void retain(Set<UUID> ids) { samples.keySet().retainAll(ids); }
}
