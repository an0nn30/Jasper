package dev.jasper.app.vault;

import java.io.IOException;
import java.util.Locale;
import java.util.function.BiFunction;

public final class DeviceAccessStores {
    private DeviceAccessStores() {}
    public static DeviceAccessStore system() {
        return create(System.getProperty("os.name", ""), NativeCredentialCalls::new);
    }
    static DeviceAccessStore create(String osName, BiFunction<String, Boolean, NativeCredentialCalls> load) {
        String os = osName.toLowerCase(Locale.ROOT);
        try {
            if (os.startsWith("windows")) {
                NativeCredentialCalls api = load.apply("Advapi32", true);
                api.require("CredReadW", "CredWriteW", "CredDeleteW", "CredFree");
                return new WindowsDeviceAccessStore(api);
            }
            if (os.startsWith("mac") || os.equals("darwin")) {
                NativeCredentialCalls api = load.apply("Security", false);
                NativeCredentialCalls cf = load.apply("CoreFoundation", false);
                api.require("SecItemCopyMatching", "SecItemUpdate", "SecItemAdd", "SecItemDelete");
                cf.require("CFDictionaryCreateMutable", "CFDictionarySetValue", "CFStringCreateWithCString",
                    "CFDataCreate", "CFDataGetLength", "CFDataGetBytePtr", "CFGetTypeID", "CFDataGetTypeID", "CFRelease");
                for (String symbol : new String[]{"kSecClass", "kSecClassGenericPassword", "kSecAttrService",
                        "kSecAttrAccount", "kSecReturnData", "kSecMatchLimit", "kSecMatchLimitOne", "kSecValueData", "kSecAttrLabel"})
                    api.symbol(symbol);
                cf.symbol("kCFBooleanTrue");
                return new MacDeviceAccessStore(api, cf);
            }
            if (os.startsWith("linux")) {
                NativeCredentialCalls api = load.apply("secret-1", false);
                NativeCredentialCalls glib = load.apply("glib-2.0", false);
                NativeCredentialCalls objects = load.apply("gobject-2.0", false);
                api.require("secret_service_search_sync", "secret_item_get_locked", "secret_item_load_secret_sync",
                    "secret_item_get_secret", "secret_value_get", "secret_value_new", "secret_value_unref",
                    "secret_password_storev_binary_sync", "secret_item_delete_sync");
                glib.require("g_hash_table_new", "g_hash_table_insert", "g_hash_table_unref", "g_str_hash",
                    "g_str_equal", "g_list_free", "g_error_free");
                objects.require("g_object_unref");
                return new LinuxDeviceAccessStore(api, glib, objects);
            }
        } catch (LinkageError | RuntimeException unavailable) {
            // Do not expose arbitrary native exception text or silently fall back to a file.
            return new Unavailable();
        }
        return new Unavailable();
    }
    private static final class Unavailable implements DeviceAccessStore {
        @Override public boolean available() { return false; }
        private IOException failure() { return new IOException("OS credential storage is unavailable on this device"); }
        @Override public byte[] read(String id) throws IOException { DeviceAccessSupport.id(id); throw failure(); }
        @Override public void write(String id, byte[] payload) throws IOException {
            DeviceAccessSupport.id(id); DeviceAccessSupport.payload(payload); throw failure();
        }
        @Override public void delete(String id) throws IOException { DeviceAccessSupport.id(id); throw failure(); }
    }
}
