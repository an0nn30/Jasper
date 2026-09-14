package dev.jasper.app.vault;

import com.sun.jna.Pointer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceAccessStoresTest {
    @Test void unsupportedPlatformAndMissingLibraryNeverFallBack() {
        DeviceAccessStore unsupported = DeviceAccessStores.create("FreeBSD", (name, windows) -> {
            fail("Unsupported platform must not load libraries"); return null;
        });
        assertFalse(unsupported.available());
        assertThrows(IOException.class, () -> unsupported.read(WindowsDeviceAccessStoreTest.ID));
        assertThrows(IOException.class, () -> unsupported.write(WindowsDeviceAccessStoreTest.ID, new byte[]{1}));
        DeviceAccessStore missing = DeviceAccessStores.create("Linux", (name, windows) -> {
            throw new UnsatisfiedLinkError("Synthetic missing libsecret");
        });
        assertFalse(missing.available());
        assertThrows(IOException.class, () -> missing.delete(WindowsDeviceAccessStoreTest.ID));
    }
    @Test void platformConstructionResolvesSymbolsWithoutCallingCredentialFunctions() {
        for (String os : List.of("Mac OS X", "Windows 11", "Linux", "Darwin")) {
            List<String> libraries = new ArrayList<>();
            DeviceAccessStore store = DeviceAccessStores.create(os, (name, windows) -> {
                libraries.add(name);
                assertEquals(os.startsWith("Windows"), windows);
                return new NativeCredentialCalls() {
                    @Override void require(String... names) {}
                    @Override Pointer symbol(String name) { return Pointer.createConstant(1); }
                    @Override Object invoke(String name, Class<?> result, Object... args) {
                        throw new AssertionError("Factory must not invoke native functions: " + name);
                    }
                };
            });
            assertTrue(store.available());
            if (os.startsWith("Windows")) {
                assertInstanceOf(WindowsDeviceAccessStore.class, store);
                assertEquals(List.of("Advapi32"), libraries);
            } else if (os.equals("Linux")) {
                assertInstanceOf(LinuxDeviceAccessStore.class, store);
                assertEquals(List.of("secret-1", "glib-2.0", "gobject-2.0"), libraries);
            } else {
                assertInstanceOf(MacDeviceAccessStore.class, store);
                assertEquals(List.of("Security", "CoreFoundation"), libraries);
            }
        }
    }
    @Test void missingRequiredFunctionDisablesEveryPlatformWithoutExposingLoaderText() {
        for (String os : List.of("Windows 11", "Mac OS X", "Linux")) {
            DeviceAccessStore store = DeviceAccessStores.create(os, (name, windows) -> new NativeCredentialCalls() {
                @Override void require(String... names) { throw new UnsatisfiedLinkError("sensitive loader detail"); }
                @Override Object invoke(String name, Class<?> result, Object... args) { throw new AssertionError(name); }
            });
            assertFalse(store.available());
            IOException failure = assertThrows(IOException.class, () -> store.read(WindowsDeviceAccessStoreTest.ID));
            assertEquals("OS credential storage is unavailable on this device", failure.getMessage());
            assertNull(failure.getCause());
        }
    }
    @Test void missingMacGlobalDisablesAdapterWithoutInvokingCredentialFunctions() {
        DeviceAccessStore store = DeviceAccessStores.create("Darwin", (name, windows) -> new NativeCredentialCalls() {
            @Override void require(String... names) {}
            @Override Pointer symbol(String symbol) { throw new IllegalArgumentException("missing global " + symbol); }
            @Override Object invoke(String name, Class<?> result, Object... args) { throw new AssertionError(name); }
        });
        assertFalse(store.available());
    }
    @Test void unavailableAdapterPreservesInputContract() {
        DeviceAccessStore store = DeviceAccessStores.create("unknown", (name, windows) -> { throw new AssertionError(name); });
        assertThrows(IllegalArgumentException.class, () -> store.read("not a UUID"));
        assertThrows(IllegalArgumentException.class, () -> store.write(WindowsDeviceAccessStoreTest.ID, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> store.write(WindowsDeviceAccessStoreTest.ID, new byte[2561]));
        assertThrows(IOException.class, () -> store.write(WindowsDeviceAccessStoreTest.ID, new byte[2560]));
    }
}
