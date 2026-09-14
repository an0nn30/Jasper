package dev.jasper.app.vault;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowsDeviceAccessStoreTest {
    static final String ID = "12345678-1234-1234-1234-123456789abc";
    @Test void missingIsDifferentFromFailure() throws Exception {
        FakeCredentialCalls api = new FakeCredentialCalls();
        api.on("CredReadW", a -> 0);
        WindowsDeviceAccessStore store = new WindowsDeviceAccessStore(api);
        assertTrue(store.available());
        api.error = 1168;
        assertNull(store.read(ID));
        api.error = 5;
        assertThrows(IOException.class, () -> store.read(ID));
        api.on("CredDeleteW", a -> 0);
        assertThrows(IOException.class, () -> store.delete(ID));
        api.error = 1168;
        store.delete(ID);
    }
    @Test void binaryReadCopiesMaximumAndZerosBeforeFree() throws Exception {
        FakeCredentialCalls api = new FakeCredentialCalls();
        byte[] data = new byte[2560];
        data[0] = 0; data[1] = -1; data[2559] = 5;
        try (Memory blob = new Memory(2560)) {
            blob.write(0, data, 0, data.length);
            WindowsDeviceAccessStore.Credential c = new WindowsDeviceAccessStore.Credential();
            c.blob = blob; c.blobSize = 2560; c.write();
            api.on("CredReadW", a -> { ((PointerByReference) a[3]).setValue(c.getPointer()); return 1; });
            int[] frees = {0};
            api.on("CredFree", a -> {
                assertEquals(c.getPointer(), a[0]);
                frees[0]++;
                byte expected = (byte) (frees[0] == 1 ? 0 : 0x7f);
                byte[] cleared = new byte[2560];
                if (expected != 0) java.util.Arrays.fill(cleared, expected);
                assertArrayEquals(cleared, blob.getByteArray(0, 2560));
                return null;
            });
            WindowsDeviceAccessStore store = new WindowsDeviceAccessStore(api);
            assertArrayEquals(data, store.read(ID));
            blob.setMemory(0, 2560, (byte) 0x7f);
            c.blobSize = 2561; c.write();
            assertThrows(IOException.class, () -> store.read(ID));
            assertEquals(2, frees[0]);
        }
    }
    @Test void writeUsesLocalPersistenceAndOpaqueBlob() throws Exception {
        FakeCredentialCalls api = new FakeCredentialCalls();
        byte[] data = new byte[2560];
        data[0] = 0; data[1] = -1; data[2559] = 9;
        api.on("CredWriteW", a -> {
            WindowsDeviceAccessStore.Credential c = new WindowsDeviceAccessStore.Credential((Pointer) a[0]);
            assertEquals(1, c.type); assertEquals(2, c.persist);
            assertEquals(2560, c.blobSize);
            assertEquals(DeviceAccessSupport.SERVICE + "/" + ID, c.targetName.toString());
            assertArrayEquals(data, c.blob.getByteArray(0, c.blobSize));
            return 1;
        });
        WindowsDeviceAccessStore store = new WindowsDeviceAccessStore(api);
        byte[] original = data.clone();
        store.write(ID, data);
        assertArrayEquals(original, data);
        assertThrows(IllegalArgumentException.class, () -> store.write(ID, new byte[2561]));
        assertThrows(IllegalArgumentException.class, () -> store.read("not-a-uuid"));
    }
}
