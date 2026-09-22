package dev.jasper.vault.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The platform's own tool, run as a process: macOS {@code security} generic passwords, Linux
 * {@code secret-tool}, Windows PowerShell with DPAPI over a file in the data directory. The 32 bytes
 * travel base64-encoded. A missing or failing tool is an {@link IOException}; {@link DeviceSecrets} then
 * falls back to the file store.
 */
public final class KeychainStore implements DeviceSecretStore {
    public static final String SERVICE = "Jasper Credential Vault";
    public static final String ACCOUNT = "device-secret";
    private static final int TIMEOUT_SECONDS = 15;

    /** One tool invocation; {@code stdin} is written to the process when not null. */
    public record Command(List<String> arguments, String stdin) { }
    /** What the tool produced; stderr is merged into {@code stdout}. */
    public record Output(int exit, String stdout) { }

    private enum Platform { MAC, LINUX, WINDOWS }

    private final Platform platform;
    private final Function<Command, Output> tool;
    private final Path windowsFile;
    private final String service;

    public KeychainStore(String osName, Function<Command, Output> tool, Path windowsFile) { this(osName, tool, windowsFile, SERVICE); }

    /** {@code service} names the keychain item; tests use a throwaway one. */
    public KeychainStore(String osName, Function<Command, Output> tool, Path windowsFile, String service) {
        String os = osName.toLowerCase(Locale.ROOT);
        this.platform = os.contains("mac") || os.contains("darwin") ? Platform.MAC : os.contains("win") ? Platform.WINDOWS : Platform.LINUX;
        this.tool = tool; this.windowsFile = windowsFile; this.service = service;
    }

    /** The real thing: the platform tool as a child process, with a bounded wait. */
    public static KeychainStore forPlatform(Path dataDirectory) {
        return new KeychainStore(System.getProperty("os.name", ""), processTool(), dataDirectory.resolve("device.dpapi"));
    }

    static Function<Command, Output> processTool() {
        return command -> {
            try {
                Process process = new ProcessBuilder(command.arguments()).redirectErrorStream(true).start();
                try (var stdin = process.getOutputStream()) { if (command.stdin() != null) stdin.write(command.stdin().getBytes(StandardCharsets.UTF_8)); }
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException(command.arguments().get(0) + " did not finish"); }
                return new Output(process.exitValue(), output);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new UncheckedIOException(new IOException("interrupted"));
            }
        };
    }

    @Override public Optional<byte[]> read() throws IOException {
        return switch (platform) {
            case MAC -> {
                Output out = run(List.of("security", "find-generic-password", "-s", service, "-a", ACCOUNT, "-w"), null);
                if (out.exit() == 44) yield Optional.empty();   // errSecItemNotFound
                yield Optional.of(decode(require(out, "security")));
            }
            case LINUX -> {
                Output out = run(List.of("secret-tool", "lookup", "service", key(), "account", ACCOUNT), null);
                if (out.exit() == 1 && out.stdout().isBlank()) yield Optional.empty();
                yield Optional.of(decode(require(out, "secret-tool")));
            }
            case WINDOWS -> {
                if (!Files.isRegularFile(windowsFile)) yield Optional.empty();
                Output out = run(powershell("[Convert]::ToBase64String([System.Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes('"
                    + quote(windowsFile) + "'), $null, 'CurrentUser'))"), null);
                yield Optional.of(decode(require(out, "powershell")));
            }
        };
    }

    @Override public void write(byte[] secret) throws IOException {
        String encoded = Base64.getEncoder().encodeToString(secret);
        switch (platform) {
            case MAC -> require(run(List.of("security", "add-generic-password", "-U", "-s", service, "-a", ACCOUNT, "-w", encoded), null), "security");
            case LINUX -> require(run(List.of("secret-tool", "store", "--label=" + service, "service", key(), "account", ACCOUNT), encoded), "secret-tool");
            case WINDOWS -> require(run(powershell("[IO.File]::WriteAllBytes('" + quote(windowsFile) + "', [System.Security.Cryptography.ProtectedData]::Protect([Convert]::FromBase64String('"
                + encoded + "'), $null, 'CurrentUser'))"), null), "powershell");
        }
    }

    @Override public void delete() throws IOException {
        switch (platform) {
            case MAC -> run(List.of("security", "delete-generic-password", "-s", service, "-a", ACCOUNT), null);
            case LINUX -> run(List.of("secret-tool", "clear", "service", key(), "account", ACCOUNT), null);
            case WINDOWS -> Files.deleteIfExists(windowsFile);
        }
    }

    @Override public String description() { return "keychain"; }

    private String key() { return service.toLowerCase(Locale.ROOT).replace(' ', '-'); }
    private static List<String> powershell(String script) { return List.of("powershell", "-NoProfile", "-Command", "Add-Type -AssemblyName System.Security; " + script); }
    private static String quote(Path path) { return path.toString().replace("'", "''"); }

    private Output run(List<String> arguments, String stdin) throws IOException {
        try { return tool.apply(new Command(arguments, stdin)); }
        catch (UncheckedIOException failure) { throw failure.getCause(); }
    }

    private static String require(Output out, String toolName) throws IOException {
        if (out.exit() != 0) throw new IOException(toolName + " failed (exit " + out.exit() + "): " + out.stdout().strip());
        return out.stdout().strip();
    }

    private static byte[] decode(String base64) throws IOException {
        try { return Base64.getDecoder().decode(base64); }
        catch (IllegalArgumentException bad) { throw new IOException("The stored device secret is not base64"); }
    }
}
