package dev.jasper.remote;

import dev.jasper.remote.hosts.*;
import dev.jasper.remote.ui.ImportPanel;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.vault.api.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Owns one config preview, Vault consent and the subsequent conflict-checked host save. */
final class ConfigImportController implements AutoCloseable {
    private final PluginContext context;
    private final Optional<VaultApi> vault;
    private final HostStore store;
    private final Path sshDir;
    private final Executor ui;
    private WindowHandle owner;
    private PluginDialog dialog;
    private ImportPanel panel;
    private Subscription lifetime;
    private boolean closed, saving;
    private long revision;
    private CompletableFuture<?> pending;
    private List<ConfigImport.Candidate> selected = List.of();
    private final Map<UUID, CredentialDescriptor> choices = new HashMap<>();
    private final Map<Path, UUID> sourceIds = new LinkedHashMap<>();
    ConfigImportController(PluginContext context, Optional<VaultApi> vault, HostStore store, Path sshDir, Executor ui) {
        this.context = context; this.vault = vault; this.store = store; this.sshDir = sshDir; this.ui = ui;
    }
    ImportPanel panel() { return closed ? null : panel; }
    void show(WindowHandle owner) {
        this.owner = owner;
        lifetime = context.events().subscribe(TerminalEvents.WINDOW_CLOSED, e -> { if (e.windowId().equals(owner.id())) close(); });
        dialog = context.windows().dialog(new DialogSpec("Import from ~/.ssh/config", owner, true));
        dialog.onClosed(this::close); refresh();
    }
    private boolean live(long version) { return !closed && owner.isOpen() && revision == version; }
    private void refresh() {
        long version = ++revision; choices.clear(); sourceIds.clear();
        store.reload().whenComplete((ignored, failure) -> ui.execute(() -> {
            if (!live(version)) return;
            if (failure != null) {
                if (panel != null) panel.failed("Hosts could not be read. Fix hosts.toml and refresh.");
                else { context.notices().error("Hosts could not be read. Fix hosts.toml before importing."); close(); }
                return;
            }
            readPreview(version);
        }));
    }
    private void readPreview(long version) {
        var existing = store.hosts();
        context.background().execute(() -> {
            try {
                var parsed = SshConfig.parse(sshDir.resolve("config"));
                // Resolve ~ against the real home, independently of the config file location.
                var rows = ConfigImport.plan(parsed, existing, Path.of(System.getProperty("user.home")), System.getProperty("user.name", ""));
                ui.execute(() -> {
                    if (!live(version)) return;
                    panel = new ImportPanel(rows, parsed.skipped(), this::importSelected, this::choose, this::refresh, this::close);
                    dialog.setContent(panel); dialog.show();
                });
            } catch (Exception unreadable) {
                ui.execute(() -> { if (live(version)) { context.notices().error("Could not read SSH config"); close(); } });
            }
        });
    }
    private static boolean eligible(CredentialDescriptor d) { return d.kind() == Kind.ACCOUNT_PASSWORD || d.kind() == Kind.SSH_KEY && d.managedKey(); }
    private void choose(ConfigImport.Candidate row) {
        if (vault.isEmpty()) { panel.failed("Install Credential Vault 0.2 or newer to import hosts"); return; }
        long version = revision;
        try {
            var request = vault.orElseThrow().pick(owner); pending = request;
            if (!live(version)) { request.cancel(false); return; }
            request.whenComplete((choice, failure) -> ui.execute(() -> {
                if (!live(version)) return;
                pending = null;
                if (failure != null) { panel.failed("Could not choose a Vault credential"); return; }
                choice.ifPresent(d -> {
                    if (!eligible(d)) { panel.failed("Choose a stored Vault key or password; import file-based keys first"); return; }
                    choices.put(row.id(), d); panel.credential(row.id(), d.name());
                });
            }));
        } catch (RuntimeException problem) { if (live(version)) panel.failed("Could not choose a Vault credential"); }
    }
    private void importSelected(List<ConfigImport.Candidate> rows) {
        if (closed || saving || pending != null && !pending.isDone()) return;
        if (vault.isEmpty()) { panel.failed("Install Credential Vault 0.2 or newer to import hosts"); return; }
        try {
            if (rows.isEmpty()) throw new IllegalArgumentException("Select at least one host");
            selected = List.copyOf(rows);
            var provisional = new ArrayList<RemoteHost>();
            for (var row : selected) {
                if (row.identities().isEmpty() && !choices.containsKey(row.id())) throw new IllegalArgumentException("Choose a Vault credential for " + row.name());
                // Validate graph before asking Vault. IDs here are never saved.
                Auth auth = row.identities().isEmpty() ? new Auth.Vault(choices.get(row.id()).id()) : new Auth.VaultKeys(List.of(row.id()));
                provisional.add(ConfigImport.bind(row, auth, Instant.now()));
            }
            var selectedIds = selected.stream().map(ConfigImport.Candidate::id).collect(java.util.stream.Collectors.toSet());
            var graph = new ArrayList<>(store.hosts().stream().filter(h -> !selectedIds.contains(h.id())).toList()); graph.addAll(provisional);
            HostFile.validate(graph);
            var byId = new HashMap<UUID, RemoteHost>(); graph.forEach(h -> byId.put(h.id(), h));
            for (var row : provisional) {
                var hop = row;
                while (hop.jump().isPresent()) {
                    hop = byId.get(hop.jump().orElseThrow());
                    if (hop.auth() instanceof Auth.Agent) throw new IllegalArgumentException("Select and import the jump host too: " + hop.name());
                }
            }
            var sources = new ArrayList<SshKeySource>();
            for (var row : selected) for (var path : row.identities()) {
                if (!sourceIds.containsKey(path)) sourceIds.put(path, UUID.randomUUID());
                if (sources.stream().noneMatch(s -> s.path().equals(path))) sources.add(new SshKeySource(sourceIds.get(path), path.getFileName().toString(), path));
            }
            var picked = selected.stream().filter(c -> c.identities().isEmpty()).map(c -> choices.get(c.id()).id()).distinct().toList();
            panel.busy(); long version = revision;
            var request = vault.orElseThrow().importSshKeys(owner, sources, picked); pending = request;
            if (!live(version)) { request.cancel(false); return; }
            request.whenComplete((answer, failure) -> ui.execute(() -> {
                if (!live(version)) return;
                pending = null;
                if (failure != null) { panel.failed(safeImportMessage(failure)); return; }
                if (answer.isEmpty()) { panel.editable(); return; }
                try {
                    var replacements = bindSelected(answer.orElseThrow()); saving = true;
                    store.importSelected(selected.stream().flatMap(c -> c.previous().stream()).toList(), replacements)
                        .whenComplete((ignored, saveFailure) -> ui.execute(() -> {
                            saving = false;
                            if (saveFailure != null) {
                                context.notices().error("Keys were saved in Vault; hosts were not updated. Refresh the import and retry.");
                                if (live(version)) panel.failed("Hosts were not saved. Refresh the preview and retry.");
                            } else if (live(version)) {
                                long updates = selected.stream().filter(c -> c.previous().isPresent()).count();
                                panel.completed((selected.size() - updates) + " new, " + updates + " updated SSH hosts saved using Vault credentials");
                            }
                        }));
                } catch (RuntimeException invalid) { panel.failed("Vault credentials could not be bound to these hosts. Refresh and retry."); }
            }));
        } catch (IllegalArgumentException invalid) { panel.failed(invalid.getMessage()); }
        catch (RuntimeException problem) { panel.failed("Could not start Vault import"); }
    }
    private List<RemoteHost> bindSelected(SshKeyImportResult answer) {
        var hosts = new ArrayList<RemoteHost>();
        for (var row : selected) {
            Auth auth;
            if (row.identities().isEmpty()) {
                UUID id = choices.get(row.id()).id();
                var d = answer.selectedCredentials().stream().filter(c -> c.id().equals(id) && eligible(c)).findFirst().orElseThrow();
                auth = d.managedKey() ? new Auth.VaultKeys(List.of(id)) : new Auth.Vault(id);
            } else {
                var ids = new ArrayList<UUID>();
                for (Path path : row.identities()) {
                    var d = answer.keys().get(sourceIds.get(path));
                    if (d == null || !d.managedKey() || d.kind() != Kind.SSH_KEY) throw new IllegalArgumentException("Missing managed key");
                    ids.add(d.id());
                }
                auth = new Auth.VaultKeys(ids);
            }
            hosts.add(ConfigImport.bind(row, auth, Instant.now()));
        }
        return List.copyOf(hosts);
    }
    private static String safeImportMessage(Throwable failure) {
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) failure = failure.getCause();
        // Only top-level API messages; parser causes are intentionally not rendered.
        if (failure instanceof IllegalArgumentException || failure instanceof IllegalStateException || failure instanceof UnsupportedOperationException)
            return Objects.toString(failure.getMessage(), "Could not import credentials into Vault");
        return "Could not import credentials into Vault";
    }
    public void close() {
        if (closed) return; closed = true; revision++;
        if (pending != null) pending.cancel(false);
        if (lifetime != null) lifetime.close();
        if (dialog != null) dialog.close();
    }
}
