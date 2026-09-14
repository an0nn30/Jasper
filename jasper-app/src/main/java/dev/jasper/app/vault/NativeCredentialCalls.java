package dev.jasper.app.vault;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.util.Map;

// Concrete injectable boundary; no library singleton or eager platform initialization.
class NativeCredentialCalls {
    private final NativeLibrary library;
    private final int convention;
    NativeCredentialCalls(String name, boolean windows) {
        library = NativeLibrary.getInstance(name, Map.of("string-encoding", "UTF-8"));
        convention = windows ? Function.ALT_CONVENTION : Function.C_CONVENTION;
    }
    NativeCredentialCalls() { library = null; convention = 0; }
    Object invoke(String name, Class<?> result, Object... args) {
        return library.getFunction(name, convention).invoke(result, args);
    }
    int integer(String name, Object... args) { return (Integer) invoke(name, Integer.class, args); }
    Pointer pointer(String name, Object... args) { return (Pointer) invoke(name, Pointer.class, args); }
    long nativeLong(String name, Object... args) {
        return ((NativeLong) invoke(name, NativeLong.class, args)).longValue();
    }
    void nothing(String name, Object... args) { invoke(name, Void.TYPE, args); }
    Pointer symbol(String name) { return library.getGlobalVariableAddress(name).getPointer(0); }
    Pointer function(String name) { return library.getFunction(name, convention); }
    int lastError() { return Native.getLastError(); }
    void require(String... names) { for (String name : names) library.getFunction(name, convention); }
}
