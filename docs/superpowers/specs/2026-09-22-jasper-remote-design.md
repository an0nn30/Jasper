# Jasper Remote (SSH, tunnels, SFTP) — Design

**Status:** Approved 2026-09-22 (user decisions recorded in section 2). Consumes the Credential Vault
(`2026-09-22-jasper-vault-design.md`) and realises the SSH side of the SDK design's interop sketch
and failure walkthroughs (`2026-09-21-jasper-plugin-sdk-design.md`, section 8).

**Scope:** One bundled plugin, `dev.jasper.remote` ("Remote"), delivered in three plans that each
leave it installable and runnable: **7a SSH** (saved hosts, `~/.ssh/config` import, host-key trust,
Vault or SSH-agent authentication, ProxyJump, shared per-host sessions, a hosts panel, editor,
palette scope, actions and status item), **7b tunnels**, **7c SFTP**. Sections 3 to 8 are binding
for 7a; section 9 fixes the shape of 7b and 7c at design level and their plans fill in the rest.

**Not in scope:** ask-every-time passwords; a live overlay of `~/.ssh/config`; writing to anything
under `~/.ssh`; workspace restore of remote tabs; an exported API (nothing consumes one yet;
"which session is behind this pane" stays internal until something does); X11, agent forwarding,
keyboard-interactive beyond the password fallback; Kerberos.

---

## 1. Why

Jasper's terminal already runs plugin-provided sessions (`SessionSpec`, `PendingSession`,
`TerminalConnection`, exactly-once `close`, reconnect isolation, remote-directory provenance), the
Vault hands out credentials under per-plugin grants, and the SDK has panels, dialogs, palette scopes
and status items. TermLab's ssh/tunnels/sftp plugins and the earlier Moray SSH design (Codex, on
`codex/rail-vault-implementation`, whose hosts-panel mock governs section 7) proved the feature set:
saved hosts with groups and favorites, credential references instead of copies, MINA sshd, an own
`known_hosts` with Cancel / Connect once / Trust, loopback servers for tests. This spec ports that
onto the SDK with no application code.

## 2. Decisions

1. **One plugin, three plans.** `dev.jasper.remote` covers SSH, tunnels and SFTP; each slice ships
   runnable on its own, SSH first. Chosen over three plugins with a cross-plugin API.
2. **Authentication: Vault credential or the system SSH agent.** No ask-every-time password.
3. **Hosts live in the plugin's own `hosts.toml`, plus an explicit import from `~/.ssh/config`.**
   Nothing is read from `~/.ssh` silently except the agent socket and (read-only) `known_hosts`.
4. **UI for 7a:** the hosts panel from the mock, a palette scope, a status item, a "Split with same
   host" action, an SSH menu.
5. **Trust:** Jasper's own `known_hosts` plus read-only matching against `~/.ssh/known_hosts`
   (hashed entries included); a mismatch in either file rejects the connection.
6. **ProxyJump in 7a**, through saved hosts, each hop with its own credential and host-key check.
7. **One shared session per host, multiplexed channels** (ControlMaster style), reference-counted
   with a short linger; shells, tunnels and SFTP are channels on it.

8. **Recorded plan 7a deviations:** Connect in split replaces new-window connection because the SDK
   cannot create terminal windows; collapse state is one saved set. Credential lookup precedes TCP
   connection because MINA needs the username; credentials close immediately after authentication.
   Tests publish a fake Vault API to avoid touching the real keychain. The execution review required
   a small SDK addition: `Panels.toggle(panelId, window)` in 0.7.2, implemented by app and testkit,
   so the Hosts action can create its panel lazily and toggle it; Remote requires SDK 0.7.2.

### Approved UX follow-up (2026-09-22)

The user requested a renameable default group, a compact connection-progress window before opening
a terminal, and larger searchable host cards. The cards additionally show cached detected OS/IP and
an active-session indicator; double-click/Enter focuses the most recently used running pane, while
explicit Connect/new-tab/split actions create another session. Default-group naming is persisted in
panel state, applying to existing and new ungrouped hosts. The loader offers Cancel and Retry;
reconnect retains the existing pane and uses the same loader.

OS discovery occurs after a successful connection (including key authentication), using fixed,
bounded read-only exec requests over that transport. It does not initiate background logins or
write commands into the interactive shell. Facts are best-effort, stored separately from host
configuration, and associated with the authenticated endpoint snapshot.

The user's subsequent styling revision replaces rounded cards with the original tree-style list,
using slightly larger text and more row padding. The row retains the session count; OS/IP moves to
the selected-host details and tooltip and stays searchable. Group renaming and last-session
activation remain unchanged. A separate centered connection-overlay design is pending review.

## 3. Shape

```
plugins/remote/                     module :jasper-plugin-remote
  build.gradle.kts                  compileOnly jasper-sdk and :jasper-plugin-vault (its api package only);
                                    implementation org.apache.sshd:sshd-core:2.19.0 (brings sshd-common),
                                    org.slf4j:slf4j-api:2.x + org.slf4j:slf4j-jdk-platform-logging (MINA logs
                                    into System.Logger); org.tomlj:tomlj:1.1.1 (hosts.toml); 7c adds sshd-sftp
  src/main/resources/plugin.toml    id = "dev.jasper.remote", name = "Remote", entry = "dev.jasper.remote.RemotePlugin",
                                    capabilities = ["terminal.open", "session.provide", "terminal.observe", "palette.contribute"],
                                    requires = [{ id = "dev.jasper.vault", version = ">=0.1", optional = true }]
  src/main/resources/settings.toml  the example settings file (section 6)
  src/main/java/dev/jasper/remote/
    hosts/     RemoteHost, Auth, HostStore (hosts.toml, polling), SshConfig (parser), ConfigImport (mapping)
    trust/     KnownHosts (both files, match/mismatch/unknown), TrustDecision
    agent/     AgentClient (SSH_AUTH_SOCK / named pipe), AgentIdentity, the two protocol messages
    client/    SshClients (one MINA SshClient), Connections (registry), ConnectPipeline, HostKeyVerifier,
               VaultIdentities, ShellChannels (channel to TerminalConnection), JumpChain
    ui/        HostsPanel, HostCard, HostEditor, ImportDialog, HostKeyDialog, RemoteScope, RemoteStatus
    RemotePlugin.java
```

`requires` is optional because agent-only hosts need no Vault. Without the Vault, hosts with a
Vault credential show "needs Credential Vault" and `Connect` fails with that message; the editor
offers only "SSH agent". The plugin never imports anything outside `dev.jasper.vault.api`;
`gradle/plugin-architecture.gradle.kts` records `":jasper-plugin-remote" to listOf("dev.jasper.vault.api")`.

## 4. Hosts

**Model.**

```
RemoteHost(UUID id, String name, String hostname, int port, String username, Auth auth,
           String group, boolean favorite, Optional<UUID> jump, Instant created, Instant updated)
Auth = Vault(UUID credentialId) | Agent
```

`name` non-blank and unique (case-insensitive); `hostname` non-blank; `port` 1–65535, default 22;
`group` may be empty ("Other" in the panel). `username`: required for `Agent` and for a Vault
standalone key; for a Vault login the login's username is the default and a non-empty host value
overrides it. `jump` names another saved host; chains are followed recursively; a cycle or a
dangling reference is rejected at save and reported at connect ("jump host missing").

**Store.** `plugins/dev.jasper.remote/data/hosts.toml`:

```toml
# Jasper Remote hosts. Edit freely; Jasper rewrites this file when you save from the panel.
[[host]]
id = "5f2c…"                  # stable; keep it when editing by hand
name = "api-prod-01"
hostname = "api-01.prod.example"
port = 22
username = "deploy"
auth = "vault"                # or "agent"
credential = "9a1e…"          # Vault credential id, auth = "vault" only
group = "Production"
favorite = true
jump = "b77d…"                # another host's id, optional
created = 2026-09-22T10:00:00Z
updated = 2026-09-22T10:00:00Z
```

Read with tomlj, written by the plugin's own writer (deterministic order, temp file and rename),
polled once a second for outside edits like `snippets.toml`. A file that fails to parse keeps the
last good hosts in memory and shows one error row ("hosts.toml has errors: …") in the panel and the
palette until it parses again; a save while the file is broken is refused with a notice. No secrets
ever enter the file. Group collapse state lives in one shared set in `data/panel-state.toml` (window ids do not survive restart); it is a convenience, so a missing or broken file means "all expanded".

**Import.** `SshConfig` parses `~/.ssh/config` (and one level of `Include`, globbed): `Host`,
`HostName`, `Port`, `User`, `ProxyJump`, `IdentityFile`; the first value wins per OpenSSH's rule;
`Host` entries with wildcards (`*`, `?`, `!`) and `Match` blocks are skipped and listed as "not
importable". `ConfigImport` maps each entry to a `RemoteHost`: name = alias, hostname = `HostName`
or the alias, port, username = `User` or empty; `IdentityFile` becomes `Vault(key)` when the
SHA256 fingerprint of `<IdentityFile>.pub` (read locally, `~` expanded) equals the subtitle of one
of the Vault's `SSH_KEY` descriptors, else `Agent` with a note ("key not in the vault: uses the
agent"); `ProxyJump` resolves to an imported or existing host by alias or name, else is dropped
with a note. The dialog lists every
entry with a checkbox, the mapped fields and notes; entries whose name already exists are unchecked
and marked "exists"; Import writes the checked ones in one save.

## 5. Connections

**Registry.** `Connections` keeps at most one live MINA `ClientSession` per host id. Its state is
touched on the UI thread only; work runs on `context.background()` and completes back on the UI
thread; MINA's I/O threads never touch plugin state directly. Each open channel holds one
reference; when the count reaches zero a linger timer (`session_linger_seconds`, default 5) closes
the session unless a new channel arrives first. A session that dies (MINA close event, keepalive
failure) is evicted at once; every channel's `exited` completes exceptionally with "connection
lost", so each pane shows the disconnected banner with Reconnect. The next request reconnects.

**Pipeline** (`ConnectPipeline`, one per request, cancellable):

1. Resolve the host and its jump chain (`jump` first, recursively; cycles were rejected at save).
2. For each hop, get-or-connect its session:
   - **Connect** (`connect_timeout_seconds`, default 10). A hop after the first is reached through
     the previous hop's session: a local port forward from an ephemeral loopback port to
     `hostname:port` is opened on that session and the new session connects to the loopback port;
     the verifier and the auth still use the hop's real name and port.
   - **Host key** (`KnownHosts`): the key is matched against `data/known_hosts` and, when
     `read_user_known_hosts` is true, `~/.ssh/known_hosts`, both in OpenSSH format with hashed
     entries supported. Match → proceed. **Mismatch in either file → reject** with "Host key
     rejected: fingerprint changed" and both SHA256 fingerprints. Unknown → a window-modal dialog
     (host, port, key algorithm, SHA256 fingerprint) with **Cancel**, **Connect once**, **Trust and
     connect**; the last appends one line to `data/known_hosts` (temp file and rename, re-read and
     re-checked before the write so two first contacts cannot disagree). An own file that fails to
     parse refuses every connection ("known_hosts is corrupt") rather than counting as empty.
   - **Authenticate** (`auth_timeout_seconds`, default 30). `Auth.Vault(id)`: on the UI thread,
     `VaultApi.credential(id)` (the Vault's own unlock and grant prompts apply); with the
     credential, public-key auth first when it has a key (the key file parsed by MINA with the
     passphrase held only in memory), then password when it has one; the credential is closed as
     soon as MINA has what it needs. `Auth.Agent`: identities from `AgentClient`, each offered as a
     MINA public-key identity whose signing goes back to the agent. Failure lists what was tried:
     "Authentication failed (tried publickey, password)".
   - Keepalive: server-alive requests every `keepalive_seconds` (default 30), unanswered ones
     evict the session.
3. Open a shell channel on the target session: PTY `xterm-256color`, env `TERM=xterm-256color` and
   `COLORTERM=truecolor`, initial size from `PendingSession.columns()/rows()`; then
   `pending.attach(new TerminalConnection(channelStdout, channelStdin, windowChange, exitStatus, close))`
   where `close` closes the channel and releases its reference.

`pending.status` reports each stage ("Connecting…", "Verifying host key…", "Authenticating…",
"Opening shell…"). `pending.onCancelled` cancels whatever the pipeline is waiting on: the Vault
future (which withdraws the Vault prompt), the MINA connect/auth future, the host-key dialog. A
connect that completes after cancellation still calls `attach`; the app rejects it and invokes
`close`, which releases the reference like any other channel. A Vault lock never touches live
sessions; a pipeline waiting on the Vault gets the unlock prompt from the Vault itself.

**Agent client.** `AgentClient` speaks only `SSH_AGENTC_REQUEST_IDENTITIES` (11) /
`SSH_AGENT_IDENTITIES_ANSWER` (12) and `SSH_AGENTC_SIGN_REQUEST` (13) / `SSH_AGENT_SIGN_RESPONSE`
(14), with the RSA SHA-2 flags, over a JDK Unix-domain `SocketChannel` at `SSH_AUTH_SOCK` on
macOS/Linux or the `\\.\pipe\openssh-ssh-agent` named pipe on Windows; one short-lived connection
per request. It is presented to MINA through its `SshAgent` interface. No socket, or a refused one,
fails the hop with "SSH agent not available"; `use_ssh_agent = false` does the same without trying.

**Failure copy** through `pending.fail`: "Could not resolve host <name>", "Connection refused",
"Timed out after 10 s", "Host key rejected: fingerprint changed", "Authentication failed (tried …)",
"Credential denied", "Vault locked — unlock and Reconnect", "needs Credential Vault", "SSH agent
not available", "jump host missing". Store and trust-file write failures use `notices().error`.

## 6. Settings

`plugins/dev.jasper.remote/dev.jasper.remote.toml`, seeded from the plugin's `settings.toml`:

```toml
# Settings for Remote. Jasper reads this file live.
# connect_timeout_seconds = 10
# auth_timeout_seconds = 30
# keepalive_seconds = 30          # server-alive interval; 0 disables
# session_linger_seconds = 5      # how long an idle shared session stays open after its last channel
# read_user_known_hosts = true    # also match host keys against ~/.ssh/known_hosts (never written)
# use_ssh_agent = true            # offer identities from SSH_AUTH_SOCK / the OpenSSH named pipe
```

Live changes apply to the next connection; the keepalive interval applies to new sessions.

## 7. UI (plan 7a)

- **Hosts panel** (`Panels.register`, `Anchor.LEFT`, one instance per window over the shared
  store; the rail button is the `dev.jasper.remote.hosts` action): header "SSH hosts" with Add and
  Import; a search field matching name, hostname, username and group; groups with counts,
  collapsible; hosts sorted favorites first then by name; ungrouped hosts in a trailing "Other"
  section; a star toggles favorite. Selecting a host shows the card: name, `user@host:port`, the
  credential label (the Vault descriptor's name, "SSH agent", "Vault locked", "needs Credential
  Vault" or "credential missing"), the jump host if any, Edit and Connect. Double-click or Connect
  opens a tab in that window (`terminals().openTab(window, OpenRequest.session(spec))`) titled with
  the host name, `ExitPolicy.KEEP_OPEN`. A locked or absent Vault does not disable Connect; the
  pipeline reports. Row context menu: Connect, Connect in split, Edit…, Duplicate, Delete…
  (confirmation), Favorite. Empty state: "No hosts yet — Add or Import from ~/.ssh/config". A search
  with no match says so and never changes saved collapse state; filtering reveals matching groups.
- **Host editor** (window-modal dialog): Name, Hostname, Port, Username, Group (editable combo of
  existing groups), Favorite, Authentication (radio: *Vault credential* with Choose… running
  `VaultApi.pick` and the chosen descriptor's name; *SSH agent*), Jump host (combo of the other
  saved hosts, none by default), Save, Cancel. Inline validation per section 4; choosing a Vault
  credential needs the Vault present (else that radio is disabled with "needs Credential Vault") and
  the pick prompt unlocks it.
- **Host-key dialog** and **Import dialog** as in sections 5 and 4.
- **Palette scope** `dev.jasper.remote.scope`, label "SSH", aliases `ssh`, `remote`, `hosts`;
  rows are hosts (title name, detail `user@host:port`, tag group), favorites first, matched on name,
  hostname, username and group; verbs **Connect** (Enter, into the palette's window),
  **Connect in split** (Cmd/Ctrl+Enter), **Edit host…** (Shift+Enter);
  `shortcutActionId = dev.jasper.remote.connect`; the error row when `hosts.toml` is broken.
- **Actions**: `dev.jasper.remote.connect` "Connect to SSH Host…" (default `cmd+shift+h`) opens the
  palette in the scope; `dev.jasper.remote.hosts` "SSH Hosts" toggles the panel (rail button);
  `dev.jasper.remote.split` "Split with Same Host" is enabled only while the active pane is one of
  this plugin's (`PaneInfo.providerPluginId`) and still connected, and calls `terminals().split(pane,
  Direction.RIGHT, OpenRequest.session(spec))` for a second shell channel on the same session;
  `dev.jasper.remote.import` "Import from ~/.ssh/config…". An **SSH** menu lists the four.
- **Status item** (right, priority 60): "N SSH sessions", hidden at zero, counting live shell
  channels across windows; its action is `dev.jasper.remote.hosts`.

## 8. Threading, errors, testing

- The SDK is used on the UI thread only. The pipeline runs on `background()`; MINA futures and
  listeners hop to the UI thread (`SwingUtilities.invokeLater` in production, an injected executor
  in tests) before touching the registry. Every wait has the timeout from section 6.
- Unit tests: `HostStore` round trip, polling and a broken file; `SshConfig` and `ConfigImport`;
  `KnownHosts` match / mismatch / unknown with plain and hashed entries and a corrupt own file;
  `AgentClient` against a fake agent (a test `ServerSocketChannel` that answers the two messages);
  `Connections` reference counting, linger and eviction with the session factory injected.
- Integration tests: an embedded MINA `SshServer` on loopback with an echo shell — password, key
  and agent authentication; unknown / Connect once / Trust / mismatch; resize reaching the PTY;
  exit status; cancel during connect and a late `attach` rejected; shared session reuse, linger and
  eviction when the server drops; two servers for ProxyJump. No external host is contacted.
- `FakePluginHost` tests start the real Vault plugin beside Remote (`testImplementation
  project(":jasper-plugin-vault")`): credential fetch with a grant, the panel's rows and card, the
  editor, the palette rows and verbs, the actions' enabled state, the status count.
- Native acceptance (user-run): a real host, a real agent, a real bastion, the keychain prompt.

## 9. Tunnels (7b) and SFTP (7c)

- **Tunnels.** `Tunnel(UUID id, String name, UUID hostId, Kind LOCAL|REMOTE|DYNAMIC, String
  bindAddress, int bindPort, String targetHost, int targetPort, boolean autoStart)` in
  `data/tunnels.toml`; a "Tunnels" tab in the panel with per-row start/stop and state (stopped,
  starting, up, failed with reason); `DYNAMIC` is a SOCKS5 listener on the bind port; tunnels are
  channels on the shared session (a tunnel keeps its host's session alive while up); auto-start
  tunnels start at plugin start and reconnect with backoff; a status item "N tunnels"; a palette
  verb Start/Stop. ProxyJump works unchanged.
- **SFTP.** `sshd-sftp` over the shared session; a two-pane browser (local, remote) in a bottom or
  right panel; "Open SFTP here" on a remote pane using `PaneInfo.remoteDirectory`; transfers run
  as `activities().begin(...)` so Buddy shows them; collision dialog (replace, skip, rename);
  remote writes to a temp name then rename.

## 10. Plans

- **7a SSH**: module, hosts store, import, trust, agent client, registry and pipeline with ProxyJump,
  session provider, panel, editor, dialogs, palette scope, actions, menu, status item, docs.
- **7b Tunnels**, **7c SFTP**: as section 9, one plan each.

## 11. Self-review record

- Placeholders: none. The `IdentityFile` import rule matches on the public key's fingerprint,
  which the Vault already exposes as an `SSH_KEY` descriptor's subtitle, so no Vault API change is
  needed.
- Consistency: section 5's reference counting is what section 7's Split with Same Host and section
  9's tunnels rely on; section 7's Connect never checks the Vault so section 5's pipeline is the one
  place that reports Vault state; `requires optional` (section 3) is honoured by every "needs
  Credential Vault" branch.
- Scope: three plans; 7b and 7c cannot start before 7a.
