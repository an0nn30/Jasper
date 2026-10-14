package dev.jasper.app.vmoptions;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * One reading of this JVM's memory, in bytes, and the status bar indicator's text for it (spec section 5.3).
 *
 * @param heapUsed heap in use
 * @param heapCommitted heap the JVM has committed
 * @param heapMax the heap limit, or {@code -1} when the JVM names none
 * @param nonHeapUsed non-heap memory in use (metaspace, code cache)
 */
public record HeapReading(long heapUsed, long heapCommitted, long heapMax, long nonHeapUsed) {
    /** This JVM's memory now. */
    public static HeapReading current() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        return new HeapReading(heap.getUsed(), heap.getCommitted(), heap.getMax(), memory.getNonHeapMemoryUsage().getUsed());
    }

    /** The indicator's text: {@code <used> of <max>M}, in MiB. */
    public String label() { return mib(heapUsed) + " of " + mib(limit()) + "M"; }

    /** The indicator's tooltip: heap used, committed and maximum, and non-heap use. */
    public String tooltip() {
        return "Heap: " + mib(heapUsed) + " MiB used, " + mib(heapCommitted) + " MiB committed, " + mib(limit())
            + " MiB maximum. Non-heap: " + mib(nonHeapUsed) + " MiB used. Click to run garbage collection.";
    }

    /** The heap limit in bytes: the maximum, else what is committed when the JVM names no maximum. */
    long limit() { return heapMax >= 0 ? heapMax : heapCommitted; }

    private static long mib(long bytes) { return Math.max(0, bytes) >> 20; }
}
