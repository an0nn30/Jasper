package dev.jasper.app.vault;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinuxDeviceAccessStoreTest {
    static final String ID = WindowsDeviceAccessStoreTest.ID;
    static final class Fixture implements AutoCloseable {
        final FakeCredentialCalls secret = new FakeCredentialCalls(), glib = new FakeCredentialCalls(), objects = new FakeCredentialCalls();
        final Memory node = new Memory(3L * Native.POINTER_SIZE);
        final Pointer item = Pointer.createConstant(222), table = Pointer.createConstant(333);
        final Map<String, String> attributes = new HashMap<>();
        Fixture() {
            node.clear(); node.setPointer(0, item);
            glib.on("g_str_hash", a -> Pointer.createConstant(10));
            glib.on("g_str_equal", a -> Pointer.createConstant(11));
            glib.on("g_hash_table_new", a -> table);
            glib.on("g_hash_table_insert", a -> {
                assertEquals(table, a[0]);
                attributes.put(((Pointer) a[1]).getString(0, "UTF-8"), ((Pointer) a[2]).getString(0, "UTF-8")); return 1;
            });
            glib.on("g_hash_table_unref", a -> null);
            glib.on("g_list_free", a -> null);
            objects.on("g_object_unref", a -> null);
            secret.on("secret_service_search_sync", a -> {
                assertEquals(6, a[3]);
                assertEquals(Map.of("application", DeviceAccessSupport.SERVICE, "vault-id", ID), attributes);
                return node;
            });
            secret.on("secret_item_get_locked", a -> 0);
        }
        LinuxDeviceAccessStore store() { return new LinuxDeviceAccessStore(secret, glib, objects); }
        @Override public void close() { node.close(); }
    }
    @Test void readsBinaryAndReleasesSecretAndList() throws Exception {
        try (Fixture f = new Fixture(); Memory bytes = new Memory(3)) {
            byte[] expected = {0, -1, 9}; bytes.write(0, expected, 0, 3);
            f.secret.on("secret_item_load_secret_sync", a -> 1);
            f.secret.on("secret_item_get_secret", a -> Pointer.createConstant(444));
            f.secret.on("secret_value_get", a -> { ((NativeLongByReference) a[1]).setValue(new NativeLong(3)); return bytes; });
            f.secret.on("secret_value_unref", a -> null);
            assertArrayEquals(expected, f.store().read(ID));
            assertTrue(f.secret.calls.contains("secret_value_unref"));
            assertTrue(f.objects.calls.contains("g_object_unref"));
            assertTrue(f.glib.calls.contains("g_list_free"));
        }
    }
    @Test void lockedEntryIsFailureForReadAndDelete() throws Exception {
        try (Fixture f = new Fixture()) {
            f.secret.on("secret_item_get_locked", a -> 1);
            assertThrows(IOException.class, () -> f.store().read(ID));
            assertThrows(IOException.class, () -> f.store().delete(ID));
            assertFalse(f.secret.calls.contains("secret_item_delete_sync"));
            assertEquals(2, f.objects.calls.stream().filter("g_object_unref"::equals).count());
        }
    }
    @Test void absentIsNullAndDeleteIsIdempotent() throws Exception {
        try (Fixture f = new Fixture()) {
            f.secret.on("secret_service_search_sync", a -> null);
            assertNull(f.store().read(ID)); f.store().delete(ID);
            assertFalse(f.secret.calls.contains("secret_item_delete_sync"));
        }
    }
    @Test void deleteRemovesActualMatchedItem() throws Exception {
        try (Fixture f = new Fixture()) {
            f.secret.on("secret_item_delete_sync", a -> { assertEquals(f.item, a[0]); return 1; });
            f.store().delete(ID);
            assertTrue(f.secret.calls.contains("secret_item_delete_sync"));
        }
    }
    @Test void writeUsesBinaryValueAndPersistentCollection() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] expected = {0, -1, 9};
            f.secret.on("secret_value_new", a -> {
                assertArrayEquals(expected, ((Pointer) a[0]).getByteArray(0, 3));
                assertEquals("application/octet-stream", a[2]); return Pointer.createConstant(444);
            });
            f.secret.on("secret_password_storev_binary_sync", a -> { assertEquals("default", a[2]); return 1; });
            f.secret.on("secret_value_unref", a -> null);
            f.store().write(ID, expected);
            assertTrue(f.secret.calls.contains("secret_value_unref"));
        }
    }
    @Test void nativeErrorIsFreedWithoutLoggingMessage() throws Exception {
        try (Fixture f = new Fixture(); Memory error = new Memory(16)) {
            error.clear(); error.setInt(4, 42);
            f.secret.on("secret_service_search_sync", a -> { ((PointerByReference) a[5]).setValue(error); return null; });
            f.glib.on("g_error_free", a -> { assertEquals(error, a[0]); return null; });
            IOException failure = assertThrows(IOException.class, () -> f.store().read(ID));
            assertEquals("Linux credential store failed (42)", failure.getMessage());
            assertTrue(f.glib.calls.contains("g_error_free"));
        }
    }
}
