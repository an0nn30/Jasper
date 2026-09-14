package dev.jasper.app.vault;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MacDeviceAccessStoreTest {
    static final String ID = WindowsDeviceAccessStoreTest.ID;
    static final class Fixture {
        final FakeCredentialCalls api = new FakeCredentialCalls();
        final FakeCredentialCalls cf = new FakeCredentialCalls();
        final Map<String, Pointer> symbols = new HashMap<>();
        final Map<Pointer, Object> values = new HashMap<>();
        long next = 10000;
        Pointer allocate(Object value) { Pointer p = Pointer.createConstant(++next); values.put(p, value); return p; }
        Fixture() {
            for (String key : new String[]{"kSecClass", "kSecClassGenericPassword", "kSecAttrService",
                    "kSecAttrAccount", "kSecReturnData", "kSecMatchLimit", "kSecMatchLimitOne",
                    "kSecValueData", "kSecAttrLabel"}) {
                Pointer p = allocate(key); symbols.put(key, p); api.on(key, a -> p);
            }
            cf.on("kCFBooleanTrue", a -> Pointer.createConstant(1));
            cf.on("CFDictionaryCreateMutable", a -> allocate(new HashMap<Pointer, Pointer>()));
            cf.on("CFStringCreateWithCString", a -> allocate(a[1]));
            cf.on("CFDictionarySetValue", a -> { dictionary((Pointer) a[0]).put((Pointer) a[1], (Pointer) a[2]); return null; });
            cf.on("CFRelease", a -> { values.remove((Pointer) a[0]); return null; });
        }
        @SuppressWarnings("unchecked") Map<Pointer, Pointer> dictionary(Pointer p) {
            return (Map<Pointer, Pointer>) values.get(p);
        }
        void assertIdentity(Pointer p) {
            Map<Pointer, Pointer> q = dictionary(p);
            assertEquals(symbols.get("kSecClassGenericPassword"), q.get(symbols.get("kSecClass")));
            assertEquals(DeviceAccessSupport.SERVICE, values.get(q.get(symbols.get("kSecAttrService"))));
            assertEquals(ID, values.get(q.get(symbols.get("kSecAttrAccount"))));
        }
        MacDeviceAccessStore store() { return new MacDeviceAccessStore(api, cf); }
    }
    @Test void scopesIdentityAndFreesBinaryRead() throws Exception {
        Fixture f = new Fixture();
        try (Memory bytes = new Memory(3)) {
            bytes.write(0, new byte[]{0, -1, 7}, 0, 3);
            f.api.on("SecItemCopyMatching", a -> {
                f.assertIdentity((Pointer) a[0]);
                ((PointerByReference) a[1]).setValue(f.allocate("returned data")); return 0;
            });
            f.cf.on("CFGetTypeID", a -> new NativeLong(19));
            f.cf.on("CFDataGetTypeID", a -> new NativeLong(19));
            f.cf.on("CFDataGetLength", a -> new NativeLong(3));
            f.cf.on("CFDataGetBytePtr", a -> bytes);
            assertArrayEquals(new byte[]{0, -1, 7}, f.store().read(ID));
            assertEquals(4, f.cf.calls.stream().filter("CFRelease"::equals).count());
        }
    }
    @Test void missingDenialAndDeleteAreDistinct() throws Exception {
        Fixture f = new Fixture();
        f.api.on("SecItemCopyMatching", a -> -25300);
        assertNull(f.store().read(ID));
        f.api.on("SecItemCopyMatching", a -> -25293);
        assertThrows(IOException.class, () -> f.store().read(ID));
        f.api.on("SecItemDelete", a -> { f.assertIdentity((Pointer) a[0]); return -25300; });
        f.store().delete(ID);
        f.api.on("SecItemDelete", a -> -25293);
        assertThrows(IOException.class, () -> f.store().delete(ID));
    }
    @Test void addRaceRetriesUpdateWithoutDeletingOldEntry() throws Exception {
        Fixture f = new Fixture();
        byte[] payload = {0, -1, 7};
        f.cf.on("CFDataCreate", a -> {
            assertArrayEquals(payload, ((Pointer) a[1]).getByteArray(0, 3));
            return f.allocate("data");
        });
        AtomicInteger updates = new AtomicInteger();
        f.api.on("SecItemUpdate", a -> { f.assertIdentity((Pointer) a[0]); return updates.incrementAndGet() == 1 ? -25300 : 0; });
        f.api.on("SecItemAdd", a -> { f.assertIdentity((Pointer) a[0]); return -25299; });
        f.store().write(ID, payload);
        assertEquals(2, updates.get());
        assertFalse(f.api.calls.contains("SecItemDelete"));
    }
    @Test void invalidNativeSizeStillReleasesResult() throws Exception {
        Fixture f = new Fixture();
        f.api.on("SecItemCopyMatching", a -> { ((PointerByReference) a[1]).setValue(f.allocate("data")); return 0; });
        f.cf.on("CFGetTypeID", a -> new NativeLong(19));
        f.cf.on("CFDataGetTypeID", a -> new NativeLong(19));
        f.cf.on("CFDataGetLength", a -> new NativeLong(4097));
        assertThrows(IOException.class, () -> f.store().read(ID));
        assertEquals(4, f.cf.calls.stream().filter("CFRelease"::equals).count());
    }
}
