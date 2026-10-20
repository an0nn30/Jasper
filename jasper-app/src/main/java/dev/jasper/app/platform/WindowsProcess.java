package dev.jasper.app.platform;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * This process's memory and scheduling on Windows, through the JDK's foreign function interface (kernel32 only).
 * A resident Jasper spends hours with no window, and Windows treats such a process as background work: it may run it
 * throttled (EcoQoS) and, when memory runs short, trim its working set down to almost nothing, so the next window is
 * built from page faults at reduced speed. {@link #keepResponsive()} asks for neither; {@link #sample()} shows whether it
 * happened anyway. Every method is safe from any thread and never throws.
 */
public final class WindowsProcess {
    private static final System.Logger LOG = System.getLogger(WindowsProcess.class.getName());
    /** The floor never holds more than this, however large the settled process is. */
    static final long FLOOR_CAP_BYTES = 320L << 20;
    /** Below this a floor is not worth setting: the process has already been trimmed, or the reading is wrong. */
    static final long FLOOR_MINIMUM_BYTES = 64L << 20;
    // PROCESS_INFORMATION_CLASS ProcessPowerThrottling, and PROCESS_POWER_THROTTLING_STATE's version and speed bit.
    private static final int PROCESS_POWER_THROTTLING = 4;
    private static final int POWER_THROTTLING_VERSION = 1;
    private static final int POWER_THROTTLING_EXECUTION_SPEED = 0x1;
    // Both limits soft: Windows may still trim below the floor when memory is genuinely scarce.
    private static final int QUOTA_LIMITS_HARDWS_MIN_DISABLE = 0x2;
    private static final int QUOTA_LIMITS_HARDWS_MAX_DISABLE = 0x8;
    /** PROCESS_MEMORY_COUNTERS on 64-bit Windows: two DWORDs, then eight SIZE_Ts. */
    private static final long COUNTERS_SIZE = 72;

    /** Page faults since the process started, and the bytes it has in memory now. */
    public record Sample(long pageFaults, long workingSetBytes) { }

    private WindowsProcess() { }

    /** Whether this is Windows, the only system these calls exist on. */
    public static boolean supported(String osName) {
        return osName != null && osName.startsWith("Windows");
    }

    /** This process's figures now; null outside Windows or when they cannot be read. */
    public static Sample sample() {
        if (!supported(System.getProperty("os.name"))) return null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment counters = arena.allocate(COUNTERS_SIZE, 8);
            counters.set(ValueLayout.JAVA_INT, 0, (int) COUNTERS_SIZE);
            int read = (int) Calls.GET_MEMORY_INFO.invokeExact(currentProcess(), counters, (int) COUNTERS_SIZE);
            if (read == 0) return null;
            // cb at 0, PageFaultCount at 4, PeakWorkingSetSize at 8, WorkingSetSize at 16.
            return new Sample(Integer.toUnsignedLong(counters.get(ValueLayout.JAVA_INT, 4)), counters.get(ValueLayout.JAVA_LONG, 16));
        } catch (Throwable unavailable) {
            return null;
        }
    }

    /**
     * For a resident process once start-up has settled: opts out of execution-speed throttling, which costs nothing
     * while idle and lets the next window build at full speed, and sets a soft working-set floor at what the process
     * holds now (none below 64 MB, at most 320 MB), so Windows trims other
     * processes first. Neither adds memory. Does nothing outside Windows; each failure is only logged.
     */
    public static void keepResponsive() {
        if (!supported(System.getProperty("os.name"))) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment state = arena.allocate(12, 4);
            state.set(ValueLayout.JAVA_INT, 0, POWER_THROTTLING_VERSION);
            state.set(ValueLayout.JAVA_INT, 4, POWER_THROTTLING_EXECUTION_SPEED); // ControlMask: this policy...
            state.set(ValueLayout.JAVA_INT, 8, 0);                                // StateMask: ...turned off.
            int set = (int) Calls.SET_INFORMATION.invokeExact(currentProcess(), PROCESS_POWER_THROTTLING, state, 12);
            if (set == 0) LOG.log(System.Logger.Level.INFO, "Windows declined to exempt the resident Jasper from throttling");
        } catch (Throwable failure) {
            LOG.log(System.Logger.Level.INFO, "Could not exempt the resident Jasper from throttling", failure);
        }
        Sample now = sample();
        long floor = now == null ? 0 : floor(now.workingSetBytes());
        if (floor == 0) return;
        try {
            int set = (int) Calls.SET_WORKING_SET.invokeExact(currentProcess(), floor, 2 * floor,
                QUOTA_LIMITS_HARDWS_MIN_DISABLE | QUOTA_LIMITS_HARDWS_MAX_DISABLE);
            if (set == 0) LOG.log(System.Logger.Level.INFO, "Windows declined the resident Jasper's working-set floor");
            else LOG.log(System.Logger.Level.INFO, "Resident Jasper keeps a soft working-set floor of " + (floor >> 20) + " MB");
        } catch (Throwable failure) {
            LOG.log(System.Logger.Level.INFO, "Could not set the resident Jasper's working-set floor", failure);
        }
    }

    /** The floor for a process holding {@code workingSetBytes}: capped, or 0 when too small to be worth one. */
    static long floor(long workingSetBytes) {
        return workingSetBytes < FLOOR_MINIMUM_BYTES ? 0 : Math.min(workingSetBytes, FLOOR_CAP_BYTES);
    }

    /** GetCurrentProcess's constant pseudo-handle, which needs no closing. */
    private static MemorySegment currentProcess() {
        return MemorySegment.ofAddress(-1L);
    }

    /** Resolved on first use, which only ever happens on Windows. */
    private static final class Calls {
        static final MethodHandle GET_MEMORY_INFO;
        static final MethodHandle SET_INFORMATION;
        static final MethodHandle SET_WORKING_SET;

        static {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel32 = kernel32();
            ValueLayout handle = ValueLayout.ADDRESS, dword = ValueLayout.JAVA_INT, size = ValueLayout.JAVA_LONG;
            // BOOL K32GetProcessMemoryInfo(HANDLE, PPROCESS_MEMORY_COUNTERS, DWORD)
            GET_MEMORY_INFO = linker.downcallHandle(kernel32.find("K32GetProcessMemoryInfo").orElseThrow(),
                FunctionDescriptor.of(dword, handle, ValueLayout.ADDRESS, dword));
            // BOOL SetProcessInformation(HANDLE, PROCESS_INFORMATION_CLASS, LPVOID, DWORD)
            SET_INFORMATION = linker.downcallHandle(kernel32.find("SetProcessInformation").orElseThrow(),
                FunctionDescriptor.of(dword, handle, dword, ValueLayout.ADDRESS, dword));
            // BOOL SetProcessWorkingSetSizeEx(HANDLE, SIZE_T, SIZE_T, DWORD)
            SET_WORKING_SET = linker.downcallHandle(kernel32.find("SetProcessWorkingSetSizeEx").orElseThrow(),
                FunctionDescriptor.of(dword, handle, size, size, dword));
        }

        @SuppressWarnings("restricted") // The packaged launcher explicitly enables native access.
        private static SymbolLookup kernel32() {
            return SymbolLookup.libraryLookup("kernel32", Arena.global());
        }
    }
}
