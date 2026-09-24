package dev.jasper.remote.client;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/** The user-facing text for every way a connection can fail (spec section 5). */
public final class Failures {
    /** Carries a finished message; the pipeline throws these once it knows what to say. */
    public static final class Failure extends RuntimeException {
        public Failure(String message) { super(message); }
        public Failure(String message, Throwable cause) { super(message, cause); }
    }

    private Failures() { }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) if (type.isInstance(current)) return true;
        return false;
    }

    public static String message(Throwable failure, Duration connectTimeout, String hostname) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof Failure ready) return ready.getMessage();
        if (hasCause(cause, UnknownHostException.class) || hasCause(cause, UnresolvedAddressException.class)) return "Could not resolve host " + hostname;
        String text = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
        // macOS reports a LAN address the app may not reach (Local Network privacy) as EHOSTUNREACH.
        if (hasCause(cause, NoRouteToHostException.class) || text.contains("no route to host"))
            return "No route to host " + hostname + ". On macOS, allow Jasper under System Settings > Privacy & Security > Local Network.";
        if (cause instanceof ConnectException || text.contains("connection refused")) return "Connection refused";
        if (cause instanceof TimeoutException || text.contains("timeout") || text.contains("timed out")) return "Timed out after " + connectTimeout.toSeconds() + " s";
        if (text.contains("connection lost") || text.contains("closed") || text.contains("reset")) return "Connection lost";
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
