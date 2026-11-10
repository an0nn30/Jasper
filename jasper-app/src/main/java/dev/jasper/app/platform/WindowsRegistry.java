package dev.jasper.app.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * The registry and shell calls under {@code HKEY_CURRENT_USER} that file associations and the default-browser check
 * use, through the Windows API. Windows only; elsewhere every call but {@link #userChoice} throws {@link IOException}.
 */
final class WindowsRegistry {
    private static final System.Logger LOG = System.getLogger(WindowsRegistry.class.getName());
    private static final int ERROR_SUCCESS = 0;
    private static final int ERROR_FILE_NOT_FOUND = 2;
    private static final int REG_SZ = 1;
    private static final int KEY_READ = 0x20019;
    private static final int SHCNE_ASSOCCHANGED = 0x08000000;
    private static final int SHCNF_IDLIST = 0;
    private static final int ASSOCF_NONE = 0;
    private static final int ASSOCSTR_FRIENDLYAPPNAME = 4;
    private static final int S_OK = 0;
    private static final int BUFFER_CHARS = 1024;

    private WindowsRegistry() { }

    /** A string value under {@code HKEY_CURRENT_USER}, unexpanded; empty when the key or value does not exist. */
    static Optional<String> read(String key, String name) throws IOException {
        return WindowsUserPath.read(key, name);
    }

    /** Whether a key exists under {@code HKEY_CURRENT_USER}. */
    static boolean exists(String key) throws IOException {
        requireWindows();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment opened = arena.allocate(ValueLayout.ADDRESS);
            int status = (int) Calls.REG_OPEN_KEY_EX.invokeExact(Calls.HKEY_CURRENT_USER,
                arena.allocateFrom(key, StandardCharsets.UTF_16LE), 0, KEY_READ, opened);
            if (status == ERROR_FILE_NOT_FOUND) return false;
            if (status != ERROR_SUCCESS) throw new IOException("RegOpenKeyExW failed with " + status);
            int closed = (int) Calls.REG_CLOSE_KEY.invokeExact(opened.get(ValueLayout.ADDRESS, 0));
            if (closed != ERROR_SUCCESS) LOG.log(System.Logger.Level.DEBUG, "RegCloseKey failed with " + closed);
            return true;
        } catch (IOException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("Could not open HKEY_CURRENT_USER\\" + key, failure);
        }
    }

    /** Writes a string value ({@code REG_SZ}) under {@code HKEY_CURRENT_USER}, creating the key when needed. */
    static void writeString(String key, String name, String value) throws IOException {
        requireWindows();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocateFrom(value, StandardCharsets.UTF_16LE);
            int status = (int) Calls.REG_SET_KEY_VALUE.invokeExact(Calls.HKEY_CURRENT_USER,
                arena.allocateFrom(key, StandardCharsets.UTF_16LE), arena.allocateFrom(name, StandardCharsets.UTF_16LE),
                REG_SZ, data, (int) data.byteSize());
            if (status != ERROR_SUCCESS) throw new IOException("RegSetKeyValueW failed with " + status);
        } catch (IOException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("Could not write HKEY_CURRENT_USER\\" + key, failure);
        }
    }

    /** Tells Explorer that file associations changed ({@code SHCNE_ASSOCCHANGED}); a native failure is only logged. */
    static void changeNotify() throws IOException {
        requireWindows();
        try {
            Calls.SH_CHANGE_NOTIFY.invokeExact(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, MemorySegment.NULL, MemorySegment.NULL);
        } catch (Throwable failure) {
            LOG.log(System.Logger.Level.DEBUG, "Could not announce the changed file associations", failure);
        }
    }

    /** The display name of the application that opens {@code extension} (with its dot); empty when Windows has none. */
    static Optional<String> friendlyAppName(String extension) throws IOException {
        requireWindows();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate((long) BUFFER_CHARS * 2);
            MemorySegment length = arena.allocateFrom(ValueLayout.JAVA_INT, BUFFER_CHARS);
            int result = (int) Calls.ASSOC_QUERY_STRING.invokeExact(ASSOCF_NONE, ASSOCSTR_FRIENDLYAPPNAME,
                arena.allocateFrom(extension, StandardCharsets.UTF_16LE), MemorySegment.NULL, buffer, length);
            if (result != S_OK) return Optional.empty();
            String name = buffer.getString(0, StandardCharsets.UTF_16LE);
            return name.isBlank() ? Optional.empty() : Optional.of(name);
        } catch (Throwable failure) {
            throw new IOException("Could not ask for the application that opens " + extension, failure);
        }
    }

    /**
     * The user's choice under {@code baseKey}: the {@code ProgId} value of {@code UserChoiceLatest}, else of its
     * {@code ProgId} subkey, else of {@code UserChoice}; the first found decides.
     *
     * @param registry reads a string value, empty when the key or value is missing, and throws
     *     {@link UncheckedIOException} when the read fails
     * @return the chosen ProgId; empty when no choice was made
     * @throws UncheckedIOException when a read fails
     */
    static Optional<String> userChoice(String baseKey, BiFunction<String, String, Optional<String>> registry) {
        for (String key : List.of(baseKey + "\\UserChoiceLatest", baseKey + "\\UserChoiceLatest\\ProgId", baseKey + "\\UserChoice")) {
            Optional<String> progId = registry.apply(key, "ProgId");
            if (progId.isPresent()) return progId;
        }
        return Optional.empty();
    }

    private static void requireWindows() throws IOException {
        if (!WindowsProcess.supported(System.getProperty("os.name"))) throw new IOException("The registry is a Windows feature");
    }

    /** Resolved on first use, which only happens on Windows. */
    @SuppressWarnings("restricted") // The packaged launcher enables native access.
    private static final class Calls {
        /** {@code (HKEY)(ULONG_PTR)(LONG)0x80000001}: sign-extended on 64-bit Windows. */
        static final MemorySegment HKEY_CURRENT_USER = MemorySegment.ofAddress(0xFFFF_FFFF_8000_0001L);
        static final MethodHandle REG_OPEN_KEY_EX;
        static final MethodHandle REG_CLOSE_KEY;
        static final MethodHandle REG_SET_KEY_VALUE;
        static final MethodHandle SH_CHANGE_NOTIFY;
        static final MethodHandle ASSOC_QUERY_STRING;

        static {
            Linker linker = Linker.nativeLinker();
            SymbolLookup advapi32 = SymbolLookup.libraryLookup("advapi32", Arena.global());
            SymbolLookup shell32 = SymbolLookup.libraryLookup("shell32", Arena.global());
            SymbolLookup shlwapi = SymbolLookup.libraryLookup("shlwapi", Arena.global());
            REG_OPEN_KEY_EX = linker.downcallHandle(advapi32.find("RegOpenKeyExW").orElseThrow(), FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS));
            REG_CLOSE_KEY = linker.downcallHandle(advapi32.find("RegCloseKey").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            REG_SET_KEY_VALUE = linker.downcallHandle(advapi32.find("RegSetKeyValueW").orElseThrow(), FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            SH_CHANGE_NOTIFY = linker.downcallHandle(shell32.find("SHChangeNotify").orElseThrow(), FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            ASSOC_QUERY_STRING = linker.downcallHandle(shlwapi.find("AssocQueryStringW").orElseThrow(), FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        }
    }
}
