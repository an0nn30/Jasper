package dev.jasper.sdk.testing.contract;

/** A subcommand call a harness made. */
public interface ContractCall {
    /** Waits up to five seconds for the exit code. */
    int exitCode();

    /** Whether the call has completed. */
    boolean finished();

    /** What the handler wrote to standard output. */
    String out();

    /** What the handler and the host wrote to standard error. */
    String err();

    /** As if the caller went away; returns once the handler's cancellation listeners have run. */
    void cancel();
}
