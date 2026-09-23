package dev.jasper.remote.hosts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Turns parsed config entries into hosts the user can tick in the import dialog. */
public final class ConfigImport {
    public record Candidate(SshConfig.Entry entry, RemoteHost host, boolean exists, List<String> notes) { }

    private ConfigImport() { }

    /**
     * @param vaultKeysByFingerprint the Vault's SSH_KEY descriptors, fingerprint (subtitle) to id
     * @param publicKeyLine          reads {@code <IdentityFile>.pub}; empty when absent
     */
    public static List<Candidate> plan(SshConfig.Parsed parsed, List<RemoteHost> existing, Map<String, UUID> vaultKeysByFingerprint,
                                       Function<Path, Optional<String>> publicKeyLine, Path home, String localUser) {
        Map<String, UUID> idsByName = new HashMap<>();
        for (RemoteHost host : existing) idsByName.put(host.name().toLowerCase(Locale.ROOT), host.id());
        record Draft(SshConfig.Entry entry, UUID id, String hostname, int port, String username, Auth auth, List<String> notes) { }
        var drafts = new ArrayList<Draft>();
        for (SshConfig.Entry entry : parsed.entries()) {
            var notes = new ArrayList<String>();
            Auth auth = Auth.AGENT;
            if (entry.identityFile().isPresent()) {
                Path key = expand(entry.identityFile().get(), home);
                Optional<String> fingerprint = publicKeyLine.apply(key.resolveSibling(key.getFileName() + ".pub")).flatMap(Fingerprints::ofPublicKeyLine);
                UUID vaultKey = fingerprint.map(vaultKeysByFingerprint::get).orElse(null);
                if (vaultKey != null) auth = new Auth.Vault(vaultKey);
                else notes.add("key not in the vault: uses the agent");
            }
            String username = entry.user().orElse("");
            if (username.isEmpty() && auth instanceof Auth.Agent) { username = localUser; notes.add("no User: uses your local username"); }
            UUID id = UUID.randomUUID();
            drafts.add(new Draft(entry, id, entry.hostname().orElse(entry.alias()), entry.port().orElse(22), username, auth, notes));
            idsByName.putIfAbsent(entry.alias().toLowerCase(Locale.ROOT), id);
        }
        var candidates = new ArrayList<Candidate>();
        for (Draft draft : drafts) {
            Optional<UUID> jump = Optional.empty();
            if (draft.entry().proxyJump().isPresent()) {
                String first = draft.entry().proxyJump().get().split(",")[0].strip();
                String name = first.contains("@") ? first.substring(first.indexOf('@') + 1) : first;
                UUID target = idsByName.get(name.toLowerCase(Locale.ROOT));
                if (target != null && !target.equals(draft.id())) jump = Optional.of(target);
                else draft.notes().add("ProxyJump " + first + " dropped: no such host");
            }
            boolean exists = existing.stream().anyMatch(host -> host.name().equalsIgnoreCase(draft.entry().alias()));
            var host = new RemoteHost(draft.id(), draft.entry().alias(), draft.hostname(), draft.port(), draft.username(), draft.auth(), "", false, jump,
                java.time.Instant.now(), java.time.Instant.now());
            candidates.add(new Candidate(draft.entry(), host, exists, List.copyOf(draft.notes())));
        }
        return List.copyOf(candidates);
    }

    static Path expand(String file, Path home) {
        if (file.equals("~")) return home;
        if (file.startsWith("~/")) return home.resolve(file.substring(2));
        return Path.of(file);
    }
}
