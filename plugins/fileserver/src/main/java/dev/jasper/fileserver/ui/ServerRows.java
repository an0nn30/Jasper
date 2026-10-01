package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The panel's rows and the words the panel, editor and palette use for servers. */
public final class ServerRows {
    public sealed interface Row { }
    public record Problem(String message) implements Row { }
    public record Header(String title) implements Row { }
    public record Server(ServerRegistry.View view) implements Row { }

    private ServerRows() { }

    /** The file problem first, then saved servers in order, then a "Temporary" header and the temporary servers. */
    public static List<Row> rows(List<ServerRegistry.View> views, Optional<String> problem) {
        var rows = new ArrayList<Row>();
        problem.ifPresent(message -> rows.add(new Problem(message)));
        views.stream().filter(view -> !view.temporary()).forEach(view -> rows.add(new Server(view)));
        var temporary = views.stream().filter(ServerRegistry.View::temporary).toList();
        if (!temporary.isEmpty()) {
            rows.add(new Header("Temporary"));
            temporary.forEach(view -> rows.add(new Server(view)));
        }
        return List.copyOf(rows);
    }

    public static String access(ServerDefinition definition) {
        String who = switch (definition.login()) {
            case Login.Anonymous anonymous -> definition.protocol() == Protocol.SFTP ? "keys only"
                : definition.protocol() == Protocol.TFTP ? "no login" : "anonymous";
            case Login.Session session -> "session password";
            case Login.Vault vault -> "Vault account";
        };
        return (definition.writable() ? "writable" : "read-only") + " · " + who + (definition.tls() ? " · TLS" : "");
    }

    public static List<String> warnings(ServerDefinition definition) {
        return warnings(definition.protocol(), definition.tls(), definition.login(), definition.writable(), definition.bind());
    }

    public static List<String> warnings(Protocol protocol, boolean tls, Login login, boolean writable, Bind bind) {
        var warnings = new ArrayList<String>();
        if ((protocol == Protocol.HTTP || protocol == Protocol.FTP) && !tls && !(login instanceof Login.Anonymous))
            warnings.add("Plain " + protocol.label() + " sends the password unencrypted");
        if (writable && login instanceof Login.Anonymous && protocol != Protocol.SFTP)
            warnings.add(protocol == Protocol.TFTP ? "TFTP has no login: anyone who can reach it can change files" : "Anyone who can reach it can change files");
        if (writable && !(bind instanceof Bind.Loopback)) warnings.add("Writable and reachable from other computers");
        return List.copyOf(warnings);
    }

    /** The folder with the home folder shortened to {@code ~}. */
    public static String folder(Path folder, Path home) {
        if (folder.equals(home)) return "~";
        if (folder.startsWith(home)) return "~" + File.separator + home.relativize(folder);
        return folder.toString();
    }

    public static String state(ServerState state) {
        return switch (state.phase()) {
            case STOPPED -> "Stopped";
            case STARTING -> "Starting…";
            case RUNNING -> "Running";
            case STOPPING -> "Stopping…";
            case FAILED -> "Failed: " + state.reason().orElse("unknown");
            case WAITING_FOR_VAULT -> "Waiting for the Vault to unlock";
        };
    }

    /** A short state for a palette row. */
    public static String tag(ServerState state) {
        return switch (state.phase()) {
            case STOPPED -> "Stopped";
            case STARTING -> "Starting";
            case RUNNING -> "Running";
            case STOPPING -> "Stopping";
            case FAILED -> "Failed";
            case WAITING_FOR_VAULT -> "Waiting";
        };
    }

    public static String firewallHint(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        String firewall = os.contains("mac") ? "the macOS Application Firewall" : os.contains("win") ? "Windows Defender Firewall" : "ufw or firewalld";
        return "If other computers can't connect, check your firewall (" + firewall + ").";
    }
}
