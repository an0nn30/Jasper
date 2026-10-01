package dev.jasper.remote.tunnels;

/** Owner-package access to counters for UI tests in other packages. */
public final class TunnelsEngineTestAccess {
    private TunnelsEngineTestAccess() { }

    /** Sets what a running tunnel would have counted. */
    public static void count(Counters counters, int open, long received, long sent) {
        for (int i = 0; i < open; i++) counters.opened();
        counters.received(received);
        counters.sent(sent);
    }

    /** Records a refused connection, as the engine does. */
    public static void refuse(Counters counters, String reason) { counters.refused(reason); }
}
