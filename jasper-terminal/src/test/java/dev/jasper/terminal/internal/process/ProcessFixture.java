package dev.jasper.terminal.internal.process;

import com.pty4j.PtyProcess;

/** Test-only access to a controlled native process. */
public final class ProcessFixture {
    private ProcessFixture() {}
    public static PtyChild child(PtyProcess process) { return new PtyChild(process); }
}
