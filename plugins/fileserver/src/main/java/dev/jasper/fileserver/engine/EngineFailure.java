package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.definition.Protocol;
import java.io.IOException;
import java.util.Locale;

/** A server that could not start, with a message for the panel. */
public final class EngineFailure extends Exception {
    public EngineFailure(String message) { super(message); }
    public EngineFailure(String message, Throwable cause) { super(message, cause); }

    /** The message for a listening socket that could not be bound. */
    public static EngineFailure bind(IOException failure, int port, String address, Protocol protocol) {
        String text = String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);
        if (text.contains("in use"))
            return new EngineFailure("Port " + port + " is already in use on " + address, failure);
        if (text.contains("permission") || text.contains("access permissions") || text.contains("forbidden"))
            return new EngineFailure("Port " + port + " needs administrator rights on this system — use a port above 1024 (for example "
                + protocol.suggestedPort(port) + ")", failure);
        return new EngineFailure("Could not listen on port " + port + ": " + failure.getMessage(), failure);
    }
}
