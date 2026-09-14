package dev.jasper.app.vault;
public record VaultSettings(int autoLockMinutes, int rememberDays) {
    public static final VaultSettings DEFAULT = new VaultSettings(15, 7);
    public VaultSettings {
        if (autoLockMinutes < 0 || autoLockMinutes > 1440 || rememberDays < 1 || rememberDays > 365)
            throw new IllegalArgumentException("Invalid vault duration");
    }
}
