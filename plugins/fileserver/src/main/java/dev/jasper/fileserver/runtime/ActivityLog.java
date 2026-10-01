package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.Sizes;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

/** One server's recent activity, newest last, in memory only. UI thread. */
public final class ActivityLog {
    public record Entry(Instant at, String text) { }

    private final int capacity;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    public ActivityLog(int capacity) { this.capacity = Math.max(1, capacity); }

    /** Logs the event if people would care about it; true when it was logged. */
    public boolean add(ServerEvent event) {
        Optional<String> text = describe(event);
        if (text.isEmpty()) return false;
        entries.addLast(new Entry(event.at(), text.get()));
        while (entries.size() > capacity) entries.removeFirst();
        return true;
    }

    public List<Entry> entries() { return List.copyOf(entries); }
    public void clear() { entries.clear(); }

    /** Client-supplied text with control characters (a newline would plant a fake log line) shown as '?'. */
    static String plain(String text) {
        var out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) out.append(Character.isISOControl(text.charAt(i)) ? '?' : text.charAt(i));
        return out.toString();
    }

    public static Optional<String> describe(ServerEvent event) {
        String who = event.remote().isEmpty() ? "" : plain(event.remote()) + " ";
        return switch (event) {
            case ServerEvent.Connected connected -> Optional.of(who + "connected");
            case ServerEvent.LoginSucceeded login -> Optional.of(who + "login " + plain(login.user()));
            case ServerEvent.LoginFailed login -> Optional.of(who + "login failed" + (login.user().isEmpty() ? "" : " for " + plain(login.user())));
            case ServerEvent.Changed changed -> Optional.of(who + plain(changed.description()));
            case ServerEvent.Failed failed -> Optional.of(who + "error: " + plain(failed.message()));
            case ServerEvent.Transferred done -> Optional.of(who + (done.direction() == ServerEvent.Direction.DOWNLOAD ? "↓ " : "↑ ")
                + plain(done.path()) + "  " + Sizes.human(done.bytes()) + "  " + Sizes.seconds(done.took()) + (done.complete() ? "" : "  (stopped)"));
            case ServerEvent.TransferStarted started -> Optional.empty();
            case ServerEvent.TransferProgress progress -> Optional.empty();
        };
    }
}
