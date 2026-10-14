package dev.jasper.app.platform;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The current user's own {@code Path} in the registry ({@code HKEY_CURRENT_USER\Environment}), read and written
 * unexpanded (its {@code %VARIABLES%} kept) through the Windows API. Windows only; elsewhere every call throws.
 */
public final class WindowsUserPath {
    private static final System.Logger LOG = System.getLogger(WindowsUserPath.class.getName());
    private static final String KEY = "Environment";
    private static final String VALUE = "Path";
    private static final int ERROR_SUCCESS = 0;
    private static final int ERROR_FILE_NOT_FOUND = 2;
    private static final int REG_EXPAND_SZ = 2;
    private static final int RRF_RT_REG_SZ = 0x2;
    private static final int RRF_RT_REG_EXPAND_SZ = 0x4;
    private static final int RRF_NOEXPAND = 0x10000000;
    private static final int WM_SETTINGCHANGE = 0x001A;
    private static final int SMTO_ABORTIFHUNG = 0x0002;

    private WindowsUserPath() { }

    /** The user's own {@code Path}; empty when they have none. */
    public static Optional<String> read() throws IOException { return read(KEY, VALUE); }

    /** Replaces the user's own {@code Path}, kept expandable, and tells running programs so new terminals see it. */
    public static void write(String value) throws IOException {
        write(KEY, VALUE, value);
        broadcast();
    }

    /** A string value under {@code HKEY_CURRENT_USER}, unexpanded; empty when it does not exist. */
    static Optional<String> read(String key, String name) throws IOException {
        requireWindows();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment wideKey = arena.allocateFrom(key, StandardCharsets.UTF_16LE);
            MemorySegment wideName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
            MemorySegment size = arena.allocate(ValueLayout.JAVA_INT);
            int flags = RRF_RT_REG_SZ | RRF_RT_REG_EXPAND_SZ | RRF_NOEXPAND;
            int status = (int) Calls.REG_GET_VALUE.invokeExact(Calls.HKEY_CURRENT_USER, wideKey, wideName, flags,
                MemorySegment.NULL, MemorySegment.NULL, size);
            if (status == ERROR_FILE_NOT_FOUND) return Optional.empty();
            if (status != ERROR_SUCCESS) throw new IOException("RegGetValueW failed with " + status);
            MemorySegment data = arena.allocate(size.get(ValueLayout.JAVA_INT, 0) + 2L);
            status = (int) Calls.REG_GET_VALUE.invokeExact(Calls.HKEY_CURRENT_USER, wideKey, wideName, flags,
                MemorySegment.NULL, data, size);
            if (status != ERROR_SUCCESS) throw new IOException("RegGetValueW failed with " + status);
            return Optional.of(data.getString(0, StandardCharsets.UTF_16LE));
        } catch (IOException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("Could not read HKEY_CURRENT_USER\\" + key, failure);
        }
    }

    /** Writes an expandable string value under {@code HKEY_CURRENT_USER}, creating the key when needed. */
    static void write(String key, String name, String value) throws IOException {
        requireWindows();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocateFrom(value, StandardCharsets.UTF_16LE);
            int status = (int) Calls.REG_SET_KEY_VALUE.invokeExact(Calls.HKEY_CURRENT_USER,
                arena.allocateFrom(key, StandardCharsets.UTF_16LE), arena.allocateFrom(name, StandardCharsets.UTF_16LE),
                REG_EXPAND_SZ, data, (int) data.byteSize());
            if (status != ERROR_SUCCESS) throw new IOException("RegSetKeyValueW failed with " + status);
        } catch (IOException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("Could not write HKEY_CURRENT_USER\\" + key, failure);
        }
    }

    /** Removes a key under {@code HKEY_CURRENT_USER} and everything below it; for tests that write a scratch key. */
    static void delete(String key) throws IOException {
        requireWindows();
        try (Arena arena = Arena.ofConfined()) {
            int status = (int) Calls.REG_DELETE_TREE.invokeExact(Calls.HKEY_CURRENT_USER, arena.allocateFrom(key, StandardCharsets.UTF_16LE));
            if (status != ERROR_SUCCESS && status != ERROR_FILE_NOT_FOUND) throw new IOException("RegDeleteTreeW failed with " + status);
        } catch (IOException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("Could not delete HKEY_CURRENT_USER\\" + key, failure);
        }
    }

    /** WM_SETTINGCHANGE for "Environment", so Explorer and new terminals read the new Path. */
    private static void broadcast() {
        try (Arena arena = Arena.ofConfined()) {
            long answered = (long) Calls.SEND_MESSAGE_TIMEOUT.invokeExact(Calls.HWND_BROADCAST, WM_SETTINGCHANGE, 0L,
                arena.allocateFrom(KEY, StandardCharsets.UTF_16LE), SMTO_ABORTIFHUNG, 5000, arena.allocate(ValueLayout.JAVA_LONG));
            if (answered == 0) LOG.log(System.Logger.Level.DEBUG, "Not every window heard about the new Path");
        } catch (Throwable unavailable) {
            LOG.log(System.Logger.Level.DEBUG, "Could not announce the new Path", unavailable);
        }
    }

    private static void requireWindows() throws IOException {
        if (!WindowsProcess.supported(System.getProperty("os.name"))) throw new IOException("The user Path is a Windows registry value");
    }

    /** Resolved on first use, which only happens on Windows. */
    @SuppressWarnings("restricted") // The packaged launcher enables native access.
    private static final class Calls {
        /** {@code (HKEY)(ULONG_PTR)(LONG)0x80000001}: sign-extended on 64-bit Windows. */
        static final MemorySegment HKEY_CURRENT_USER = MemorySegment.ofAddress(0xFFFF_FFFF_8000_0001L);
        static final MemorySegment HWND_BROADCAST = MemorySegment.ofAddress(0xFFFF);
        static final MethodHandle REG_GET_VALUE;
        static final MethodHandle REG_SET_KEY_VALUE;
        static final MethodHandle REG_DELETE_TREE;
        static final MethodHandle SEND_MESSAGE_TIMEOUT;

        static {
            Linker linker = Linker.nativeLinker();
            SymbolLookup advapi32 = SymbolLookup.libraryLookup("advapi32", Arena.global());
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
            REG_GET_VALUE = linker.downcallHandle(advapi32.find("RegGetValueW").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            REG_SET_KEY_VALUE = linker.downcallHandle(advapi32.find("RegSetKeyValueW").orElseThrow(), FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            REG_DELETE_TREE = linker.downcallHandle(advapi32.find("RegDeleteTreeW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            SEND_MESSAGE_TIMEOUT = linker.downcallHandle(user32.find("SendMessageTimeoutW").orElseThrow(), FunctionDescriptor.of(
                ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        }
    }
}
