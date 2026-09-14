package dev.jasper.app.vault;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class LinuxDeviceAccessStore implements DeviceAccessStore {
    private final NativeCredentialCalls secret, glib, objects;
    LinuxDeviceAccessStore(NativeCredentialCalls secret, NativeCredentialCalls glib, NativeCredentialCalls objects) {
        this.secret = secret; this.glib = glib; this.objects = objects;
    }
    @Override public boolean available() { return true; }
    private void error(PointerByReference error) throws IOException {
        Pointer value = error.getValue();
        if (value != null) {
            int code;
            try { code = value.getInt(4); }
            finally { glib.nothing("g_error_free", value); error.setValue(null); }
            throw DeviceAccessSupport.failure("Linux", code);
        }
    }
    private Pointer required(Pointer pointer) throws IOException {
        if (pointer == null) throw DeviceAccessSupport.failure("Linux", -1);
        return pointer;
    }
    private final class Scope implements AutoCloseable {
        final Pointer attributes;
        final List<Memory> strings = new ArrayList<>();
        Pointer list;
        Scope() throws IOException {
            attributes = required(glib.pointer("g_hash_table_new", glib.function("g_str_hash"), glib.function("g_str_equal")));
        }
        void identify(String id) {
            attribute("application", DeviceAccessSupport.SERVICE);
            attribute("vault-id", id);
        }
        Memory string(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            Memory memory = new Memory(bytes.length + 1L);
            memory.write(0, bytes, 0, bytes.length); memory.setByte(bytes.length, (byte) 0);
            strings.add(memory); return memory;
        }
        void attribute(String key, String value) {
            glib.integer("g_hash_table_insert", attributes, string(key), string(value));
        }
        void search() throws IOException {
            PointerByReference failure = new PointerByReference();
            list = secret.pointer("secret_service_search_sync", null, null, attributes, 6, null, failure);
            error(failure);
            for (Pointer node = list; node != null; node = node.getPointer(Native.POINTER_SIZE)) {
                if (secret.integer("secret_item_get_locked", node.getPointer(0)) != 0)
                    throw DeviceAccessSupport.failure("Linux", -2);
            }
        }
        @Override public void close() {
            for (Pointer node = list; node != null; node = node.getPointer(Native.POINTER_SIZE))
                objects.nothing("g_object_unref", node.getPointer(0));
            if (list != null) glib.nothing("g_list_free", list);
            glib.nothing("g_hash_table_unref", attributes);
            for (Memory memory : strings) memory.close();
        }
    }
    @Override public byte[] read(String id) throws IOException {
        DeviceAccessSupport.id(id);
        try (Scope scope = new Scope()) {
            scope.identify(id); scope.search();
            if (scope.list == null) return null;
            if (scope.list.getPointer(Native.POINTER_SIZE) != null)
                throw DeviceAccessSupport.failure("Linux", -3);
            Pointer item = scope.list.getPointer(0);
            PointerByReference failure = new PointerByReference();
            int loaded = secret.integer("secret_item_load_secret_sync", item, null, failure);
            error(failure);
            if (loaded == 0) throw DeviceAccessSupport.failure("Linux", -1);
            Pointer value = required(secret.pointer("secret_item_get_secret", item));
            try {
                NativeLongByReference length = new NativeLongByReference();
                Pointer data = required(secret.pointer("secret_value_get", value, length));
                return data.getByteArray(0, DeviceAccessSupport.size(length.getValue().longValue()));
            } finally { secret.nothing("secret_value_unref", value); }
        }
    }
    @Override public void write(String id, byte[] payload) throws IOException {
        DeviceAccessSupport.id(id); DeviceAccessSupport.payload(payload);
        try (Scope scope = new Scope(); Memory bytes = new Memory(payload.length)) {
            scope.identify(id);
            bytes.write(0, payload, 0, payload.length);
            Pointer value;
            try { value = required(secret.pointer("secret_value_new", bytes, new NativeLong(payload.length), "application/octet-stream")); }
            finally { bytes.clear(); }
            try {
                PointerByReference failure = new PointerByReference();
                int stored = secret.integer("secret_password_storev_binary_sync", null, scope.attributes,
                    "default", "Jasper remembered vault access", value, null, failure);
                error(failure);
                if (stored == 0) throw DeviceAccessSupport.failure("Linux", -1);
            } finally { secret.nothing("secret_value_unref", value); }
        }
    }
    @Override public void delete(String id) throws IOException {
        DeviceAccessSupport.id(id);
        try (Scope scope = new Scope()) {
            scope.identify(id); scope.search();
            for (Pointer node = scope.list; node != null; node = node.getPointer(Native.POINTER_SIZE)) {
                PointerByReference failure = new PointerByReference();
                int deleted = secret.integer("secret_item_delete_sync", node.getPointer(0), null, failure);
                error(failure);
                if (deleted == 0) throw DeviceAccessSupport.failure("Linux", -1);
            }
        }
    }
}
