package dev.jasper.app.launch;

import java.util.Objects;

/**
 * One installed WSL distribution.
 *
 * @param name the name {@code wsl.exe -d} takes
 * @param version 1 or 2: WSL 2 runs in a VM, WSL 1 translates system calls
 * @param isDefault whether a plain {@code wsl.exe} starts it
 */
public record WslDistro(String name, int version, boolean isDefault) {
    public WslDistro {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("A WSL distribution needs a name.");
        if (version != 1 && version != 2) throw new IllegalArgumentException("WSL version must be 1 or 2.");
    }
}
