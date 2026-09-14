package dev.jasper.app.vault;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

final class DeviceAccessSupport {
    static final String SERVICE = "dev.jasper.vault.remembered.v1";
    static final int MAX_BYTES = 2560;
    private DeviceAccessSupport() {}
    static String id(String id) {
        Objects.requireNonNull(id, "vaultId");
        if (!UUID.fromString(id).toString().equals(id))
            throw new IllegalArgumentException("Canonical vault UUID required");
        return id;
    }
    static void payload(byte[] data) {
        Objects.requireNonNull(data, "payload");
        if (data.length < 1 || data.length > MAX_BYTES)
            throw new IllegalArgumentException("Invalid remembered access size");
    }
    static int size(long size) throws IOException {
        if (size < 1 || size > MAX_BYTES)
            throw new IOException("Invalid remembered access size");
        return (int) size;
    }
    static IOException failure(String platform, long code) {
        return new IOException(platform + " credential store failed (" + code + ")");
    }
}
