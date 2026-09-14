package dev.jasper.app.vault;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class MacDeviceAccessStore implements DeviceAccessStore {
    private static final int MISSING = -25300, DUPLICATE = -25299;
    private final NativeCredentialCalls security, cf;
    MacDeviceAccessStore(NativeCredentialCalls security, NativeCredentialCalls cf) {
        this.security = security; this.cf = cf;
    }
    @Override public boolean available() { return true; }
    private final class Scope implements AutoCloseable {
        final List<Pointer> owned = new ArrayList<>();
        Pointer own(Pointer pointer) throws IOException {
            if (pointer == null) throw DeviceAccessSupport.failure("macOS", -1);
            owned.add(pointer); return pointer;
        }
        Pointer dictionary() throws IOException {
            return own(cf.pointer("CFDictionaryCreateMutable", null, new NativeLong(0), null, null));
        }
        Pointer string(String value) throws IOException {
            return own(cf.pointer("CFStringCreateWithCString", null, value, 0x08000100));
        }
        void set(Pointer dictionary, String key, Pointer value) {
            cf.nothing("CFDictionarySetValue", dictionary, security.symbol(key), value);
        }
        Pointer query(String id) throws IOException {
            Pointer query = dictionary();
            set(query, "kSecClass", security.symbol("kSecClassGenericPassword"));
            set(query, "kSecAttrService", string(DeviceAccessSupport.SERVICE));
            set(query, "kSecAttrAccount", string(DeviceAccessSupport.id(id)));
            return query;
        }
        Pointer data(byte[] payload) throws IOException {
            try (Memory memory = new Memory(payload.length)) {
                memory.write(0, payload, 0, payload.length);
                try { return own(cf.pointer("CFDataCreate", null, memory, new NativeLong(payload.length))); }
                finally { memory.clear(); }
            }
        }
        @Override public void close() {
            for (int i = owned.size() - 1; i >= 0; i--) cf.nothing("CFRelease", owned.get(i));
        }
    }
    @Override public byte[] read(String id) throws IOException {
        DeviceAccessSupport.id(id);
        try (Scope scope = new Scope()) {
            Pointer query = scope.query(id);
            scope.set(query, "kSecReturnData", cf.symbol("kCFBooleanTrue"));
            scope.set(query, "kSecMatchLimit", security.symbol("kSecMatchLimitOne"));
            PointerByReference output = new PointerByReference();
            int status = security.integer("SecItemCopyMatching", query, output);
            if (status == MISSING) return null;
            if (status != 0) throw DeviceAccessSupport.failure("macOS", status);
            Pointer data = scope.own(output.getValue());
            if (cf.nativeLong("CFGetTypeID", data) != cf.nativeLong("CFDataGetTypeID"))
                throw DeviceAccessSupport.failure("macOS", -1);
            int size = DeviceAccessSupport.size(cf.nativeLong("CFDataGetLength", data));
            Pointer bytes = cf.pointer("CFDataGetBytePtr", data);
            if (bytes == null) throw DeviceAccessSupport.failure("macOS", -1);
            return bytes.getByteArray(0, size);
        }
    }
    @Override public void write(String id, byte[] payload) throws IOException {
        DeviceAccessSupport.id(id); DeviceAccessSupport.payload(payload);
        try (Scope scope = new Scope()) {
            Pointer query = scope.query(id), changes = scope.dictionary(), data = scope.data(payload);
            scope.set(changes, "kSecValueData", data);
            int status = security.integer("SecItemUpdate", query, changes);
            if (status == MISSING) {
                Pointer add = scope.query(id);
                scope.set(add, "kSecValueData", data);
                scope.set(add, "kSecAttrLabel", scope.string("Jasper remembered vault access"));
                status = security.integer("SecItemAdd", add, null);
                if (status == DUPLICATE) status = security.integer("SecItemUpdate", query, changes);
            }
            if (status != 0) throw DeviceAccessSupport.failure("macOS", status);
        }
    }
    @Override public void delete(String id) throws IOException {
        DeviceAccessSupport.id(id);
        try (Scope scope = new Scope()) {
            int status = security.integer("SecItemDelete", scope.query(id));
            if (status != 0 && status != MISSING) throw DeviceAccessSupport.failure("macOS", status);
        }
    }
}
