package dev.jasper.app.vault;

import java.io.IOException;

public interface DeviceAccessStore {
    boolean available();
    byte[] read(String vaultId) throws IOException;
    void write(String vaultId, byte[] payload) throws IOException;
    void delete(String vaultId) throws IOException;
}
