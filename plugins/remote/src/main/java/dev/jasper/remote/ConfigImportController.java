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
    private Operation pending;
    private boolean refreshing;
    private enum Phase { PICK, IMPORT }
    private final class Operation {
        final long version=revision;final ImportPanel source=panel;final Phase phase;
        CompletableFuture<?> future;dev.jasper.sdk.ui.UiAnchor anchor;Subscription anchorClosed;
        Operation(Phase phase){this.phase=phase;}
        boolean live(){return pending==this&&ConfigImportController.this.live(version)&&panel==source&&!refreshing;}
        void returned(CompletableFuture<?> future){this.future=future;if(!live())future.cancel(false);}
        void detach(){if(anchorClosed!=null)anchorClosed.close();if(anchor!=null)anchor.close();}
    }
    private void cancelPick(){var operation=pending;if(operation==null||operation.phase!=Phase.PICK)return;pending=null;operation.detach();if(operation.future!=null)operation.future.cancel(false);}
    private void finish(Operation operation){if(pending==operation)pending=null;operation.detach();}
    private boolean sourceLive(long version,ImportPanel source){return live(version)&&panel==source&&!refreshing;}

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
        if(closed||saving||pending!=null&&pending.phase==Phase.IMPORT)return;
        cancelPick();long version = ++revision;refreshing=true; choices.clear(); sourceIds.clear();
        if(panel!=null)panel.refreshing();
        store.reload().whenComplete((ignored, failure) -> ui.execute(() -> {
            if (!live(version)) return;
            if (failure != null) {
                if (panel != null) panel.failed("Hosts could not be read. Fix hosts.toml and refresh.");
                else {
                    context.notifications().group().error("Could not import hosts", "Hosts could not be read. Fix hosts.toml before importing.", failure);
                    close();
                }
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
                    // A refresh replaces the preview: the dialog disposes the old view, and the old panel goes inert.
                    var previous = panel;
                    var source=new ImportPanel[1];
                    panel = new ImportPanel(context.kit(), rows, parsed.skipped(), chosen -> {if(sourceLive(version,source[0]))importSelected(chosen);},
                        row -> {if(sourceLive(version,source[0]))choose(row);}, () -> {if(!closed&&owner.isOpen()&&panel==source[0])refresh();}, this::close);
                    source[0]=panel;refreshing=false;
                    dialog.setContent(panel.view());
                    if (previous != null) previous.close();
                    dialog.show();
                });
            } catch (Exception unreadable) {
                ui.execute(() -> {
                    if (!live(version)) return;
                    if(panel!=null){panel.failed("SSH config could not be read. Fix it and refresh.");return;}
                    context.notifications().group().error("Could not read the SSH config", sshDir.resolve("config") + ": "
                        + (unreadable.getMessage() == null ? unreadable.getClass().getSimpleName() : unreadable.getMessage()), unreadable);
                    close();
                });
            }
        });
    }
    private static boolean eligible(CredentialDescriptor d) { return d.kind() == Kind.ACCOUNT_PASSWORD || d.kind() == Kind.SSH_KEY && d.managedKey(); }
    private void choose(ConfigImport.Candidate row) {
        if(closed||refreshing||saving||pending!=null&&pending.phase==Phase.IMPORT||!row.errors().isEmpty()||!row.identities().isEmpty())return;
        if (vault.isEmpty()) { panel.failed("Install Credential Vault 0.2 or newer to import hosts"); return; }
        cancelPick();var operation=new Operation(Phase.PICK);pending=operation;
        try {
            operation.anchor=panel.anchor(row.id());
            operation.anchorClosed=operation.anchor.onClosed(()->{if(pending==operation)cancelPick();});
            if(!operation.live())return;
            var current=Optional.ofNullable(choices.get(row.id())).map(CredentialDescriptor::id);
            var request=vault.orElseThrow().pick(owner,new CredentialPickRequest(operation.anchor,current,true));operation.returned(request);
            request.whenComplete((choice,failure)->ui.execute(()->{
                if(!operation.live())return;
                // Remove identity before closing its anchor, whose cancellation handler can reenter.
                finish(operation);
                if(failure!=null){if(!cancelled(failure))panel.failed("Could not choose a Vault credential");return;}
                switch(choice){
                    case CredentialPickResult.Chosen chosen -> {
                        var d=chosen.credential();if(!eligible(d)){panel.failed("Choose a stored Vault key or password; import file-based keys first");return;}
                        choices.put(row.id(),d);panel.credential(row.id(),d);
                    }
                    case CredentialPickResult.Cleared ignored -> {choices.remove(row.id());panel.credential(row.id(),null);}
                    case CredentialPickResult.Cancelled ignored -> {}
                }
            }));
        }catch(RuntimeException problem){if(operation.live()){finish(operation);panel.failed("Could not choose a Vault credential");}}
    }
    private static boolean cancelled(Throwable failure){while(failure instanceof java.util.concurrent.CompletionException&&failure.getCause()!=null)failure=failure.getCause();return failure instanceof java.util.concurrent.CancellationException;}
    private void importSelected(List<ConfigImport.Candidate> rows) {
        if (closed || refreshing || saving || pending != null) return;
        if (vault.isEmpty()) { panel.failed("Install Credential Vault 0.2 or newer to import hosts"); return; }
        try {
            if (rows.isEmpty()) throw new IllegalArgumentException("Select at least one host");
            var selected = List.copyOf(rows);
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
            var operation=new Operation(Phase.IMPORT);pending=operation;
            var chosenSnapshot=Map.copyOf(choices);var sourceSnapshot=Map.copyOf(sourceIds);
            panel.busy(); long version = revision;
            CompletableFuture<Optional<SshKeyImportResult>> request;
            try{request=vault.orElseThrow().importSshKeys(owner,sources,picked);operation.returned(request);}
            catch(RuntimeException failure){finish(operation);throw failure;}
            request.whenComplete((answer, failure) -> ui.execute(() -> {
                if (!operation.live()) return;
                if (failure != null) {finish(operation);if(cancelled(failure))panel.editable();else panel.failed(safeImportMessage(failure));return;}
                if (answer.isEmpty()) {finish(operation);panel.editable();return;}
                try {
                    var replacements = bindSelected(answer.orElseThrow(),selected,chosenSnapshot,sourceSnapshot); saving = true;
                    store.importSelected(selected.stream().flatMap(c -> c.previous().stream()).toList(), replacements)
                        .whenComplete((ignored, saveFailure) -> ui.execute(() -> {
                            saving = false;finish(operation);
                            if (saveFailure != null) {
                                context.notifications().group().error("Hosts were not updated",
                                    "Keys were saved in Vault, but hosts.toml was not. Refresh the import and retry.", saveFailure);
                                if (live(version)) panel.failed("Hosts were not saved. Refresh the preview and retry.");
                            } else if (live(version)) {
                                long updates = selected.stream().filter(c -> c.previous().isPresent()).count();
                                panel.completed((selected.size() - updates) + " new, " + updates + " updated SSH hosts saved using Vault credentials");
                            }
                        }));
                } catch (RuntimeException invalid) {finish(operation); panel.failed("Vault credentials could not be bound to these hosts. Refresh and retry."); }
            }));
        } catch (IllegalArgumentException invalid) { panel.failed(invalid.getMessage()); }
        catch (RuntimeException problem) { panel.failed("Could not start Vault import"); }
    }
    private List<RemoteHost> bindSelected(SshKeyImportResult answer,List<ConfigImport.Candidate> selected,Map<UUID,CredentialDescriptor> choices,Map<Path,UUID> sourceIds) {
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
        var operation=pending;pending=null;if(operation!=null){operation.detach();if(operation.future!=null)operation.future.cancel(false);}
        if (lifetime != null) lifetime.close();
        if (panel != null) panel.close();
        if (dialog != null) dialog.close();
    }
}
