package dev.jasper.app.vault;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;

final class WindowsDeviceAccessStore implements DeviceAccessStore {
    private final NativeCredentialCalls api;
    WindowsDeviceAccessStore(NativeCredentialCalls api) { this.api = api; }
    @Override public boolean available() { return true; }
    @Structure.FieldOrder({"flags", "type", "targetName", "comment", "timeLow", "timeHigh",
        "blobSize", "blob", "persist", "attributeCount", "attributes", "targetAlias", "userName"})
    public static class Credential extends Structure {
        public int flags, type;
        public WString targetName, comment;
        public int timeLow, timeHigh, blobSize;
        public Pointer blob;
        public int persist, attributeCount;
        public Pointer attributes;
        public WString targetAlias, userName;
        public Credential() {}
        public Credential(Pointer pointer) { super(pointer); read(); }
    }
    private WString target(String id) { return new WString(DeviceAccessSupport.SERVICE + "/" + DeviceAccessSupport.id(id)); }
    @Override public byte[] read(String id) throws IOException {
        PointerByReference output = new PointerByReference();
        if (api.integer("CredReadW", target(id), 1, 0, output) == 0) {
            int error = api.lastError();
            if (error == 1168) return null;
            throw DeviceAccessSupport.failure("Windows", error);
        }
        Pointer result = output.getValue();
        if (result == null) throw DeviceAccessSupport.failure("Windows", -1);
        try {
            Credential credential = new Credential(result);
            int size = DeviceAccessSupport.size(credential.blobSize);
            if (credential.blob == null) throw DeviceAccessSupport.failure("Windows", -1);
            try {
                return credential.blob.getByteArray(0, size);
            } finally {
                credential.blob.clear(size);
            }
        } finally { api.nothing("CredFree", result); }
    }
    @Override public void write(String id, byte[] data) throws IOException {
        WString target = target(id);
        DeviceAccessSupport.payload(data);
        try (Memory blob = new Memory(data.length)) {
            blob.write(0, data, 0, data.length);
            try {
                Credential credential = new Credential();
                credential.type = 1;
                credential.targetName = target;
                credential.userName = new WString("Jasper");
                credential.persist = 2;
                credential.blobSize = data.length;
                credential.blob = blob;
                credential.write();
                if (api.integer("CredWriteW", credential.getPointer(), 0) == 0)
                    throw DeviceAccessSupport.failure("Windows", api.lastError());
            } finally { blob.clear(); }
        }
    }
    @Override public void delete(String id) throws IOException {
        if (api.integer("CredDeleteW", target(id), 1, 0) == 0) {
            int error = api.lastError();
            if (error != 1168) throw DeviceAccessSupport.failure("Windows", error);
        }
    }
}
