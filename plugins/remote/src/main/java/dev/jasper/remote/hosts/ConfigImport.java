package dev.jasper.remote.hosts;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Pure import preview. A host is created only after Vault has supplied its credentials. */
public final class ConfigImport {
    public record Candidate(UUID id, String name, String hostname, int port, String username,
            List<Path> identities, Optional<UUID> jump, Optional<RemoteHost> previous, List<String> errors) {
        public Candidate { identities = List.copyOf(identities); errors = List.copyOf(errors); }
    }
    private ConfigImport() {}
    public static List<Candidate> plan(SshConfig.Parsed parsed, List<RemoteHost> existing, Path home, String localUser) {
        Map<String, RemoteHost> old = new LinkedHashMap<>();
        Map<String, UUID> ids = new LinkedHashMap<>();
        existing.forEach(h -> { old.put(h.name().toLowerCase(Locale.ROOT), h); ids.put(h.name().toLowerCase(Locale.ROOT), h.id()); });
        parsed.entries().forEach(e -> ids.computeIfAbsent(e.alias().toLowerCase(Locale.ROOT), k -> UUID.randomUUID()));
        var rows = new ArrayList<Candidate>();
        var seen = new LinkedHashSet<String>();
        for (var entry : parsed.entries()) {
            String name = entry.alias(), alias = name.toLowerCase(Locale.ROOT);
            if (!seen.add(alias)) continue;
            String host = entry.hostname().orElse(name), user = entry.user().orElse(localUser);
            int port = entry.port().orElse(22);
            var errors = new ArrayList<String>();
            var paths = new LinkedHashSet<Path>();
            for (String expression : entry.identityFiles()) {
                try { paths.add(IdentityPaths.resolve(expression, home, localUser, host, user, port)); }
                catch (IllegalArgumentException invalid) { errors.add("IdentityFile: " + invalid.getMessage()); }
            }
            Optional<UUID> jump = Optional.empty();
            String expression = entry.proxyJump().orElse("none");
            if (!expression.equalsIgnoreCase("none")) {
                if (expression.isBlank() || expression.contains("@") || expression.contains(":") || expression.contains(",") || expression.chars().anyMatch(Character::isWhitespace))
                    errors.add("Unsupported ProxyJump expression: " + expression);
                else {
                    UUID target = ids.get(expression.toLowerCase(Locale.ROOT));
                    if (target == null) errors.add("ProxyJump host missing: " + expression);
                    else jump = Optional.of(target);
                }
            }
            if (user.isBlank()) errors.add("A username is required");
            rows.add(new Candidate(ids.get(alias), name, host, port, user, List.copyOf(paths), jump, Optional.ofNullable(old.get(alias)), errors));
        }
        return List.copyOf(rows);
    }
    public static RemoteHost bind(Candidate c, Auth auth, Instant now) {
        if (!c.errors().isEmpty()) throw new IllegalArgumentException(String.join("; ", c.errors()));
        if (auth instanceof Auth.Agent) throw new IllegalArgumentException("Imported hosts require Vault credentials");
        var old = c.previous().orElse(null);
        return new RemoteHost(c.id(), c.name(), c.hostname(), c.port(), c.username(), auth,
            old == null ? "" : old.group(), old != null && old.favorite(), c.jump(), old == null ? now : old.created(), now);
    }
}
