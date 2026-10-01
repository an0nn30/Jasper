# Jasper Remote tunnels (7b) — Design

**Status:** Approved in brainstorming 2026-09-30 (user decisions recorded in section 2); written spec awaiting
the user's review. Replaces the one-paragraph tunnels sketch in section 9 of the
[Remote design](2026-09-22-jasper-remote-design.md), whose sections 3 to 8 (hosts, connections, trust,
authentication, shared sessions) this design builds on unchanged except where section 7 says so.

**Scope:** SSH port forwarding in the bundled Remote plugin (`dev.jasper.remote`): saved tunnels of three kinds
(local `-L`, remote `-R`, dynamic `-D` SOCKS5), quick unsaved forwards, a Tunnels rail panel, an editor, a palette
scope, a status item, actions, live counters, reconnect with backoff, start at launch, and Buddy notices. It needs
two small API additions: SDK 0.10.2 (connection activities, `Platform.browse`) and one Vault API method (a
credential fetch that never prompts). Three plans (section 12).

**Not in scope:** VS Code-style detection of ports printed in a terminal; importing `LocalForward`,
`RemoteForward` or `DynamicForward` from `~/.ssh/config`; SOCKS4/4a, SOCKS authentication, SOCKS BIND and UDP;
remote dynamic forwarding (`-R` with only a port); Unix-domain socket forwards; X11 and agent forwarding; a
dedicated SSH connection per tunnel; remembering "was running" beyond the at-launch setting; a quit confirmation
for running tunnels; grouping healthy connections into one Buddy card.

---

## 1. Why

Tunnels are the last of the three Remote slices. The plumbing is already there: `Connections.lease` hands out
reference-counted leases on the shared per-host MINA session, ProxyJump already forwards a port on a session, and
Buddy already models connections (`BuddyNotice.Kind.CONNECTION`, states UP / DEGRADED / DOWN) with no producer.
TermLab's old tunnels plugin (`/Users/dustin/projects/TermLab/plugins/tunnels`, read-only reference) had only
local and remote kinds, MINA's built-in forwarders and a health-monitor thread per tunnel; it informs the model,
not the design.

## 2. Decisions

1. **Tunnels have their own lifecycle.** A tunnel goes up and down on its own: starting one never opens a
   terminal, closing terminals never stops one, and stopping one never touches a terminal. A tunnel still goes
   through a saved host, which is where its credential, host-key trust and jump chain live.
2. **All three kinds:** local, remote and dynamic (SOCKS5).
3. **Start options: Manually or When Jasper starts.** No "with host" option. An at-launch tunnel never prompts.
4. **Quick forwards:** "Forward a Port…" creates an unsaved tunnel that runs like any other, disappears when
   stopped or when Jasper quits, and can be saved.
5. **Its own rail panel,** "Tunnels"; the Sessions panel is unchanged.
6. **Extras in this release:** Open in Browser and Copy Address; live counters (open connections, bytes each
   way, uptime); Buddy notices when tunnels drop, reconnect or give up.
7. **Own forwarding engine over MINA channels** (approach B, chosen over MINA's built-in forwarders, which count
   no bytes and unbind the local port whenever the connection drops).
8. **Buddy posts only trouble,** one card per host, never a card for something you did by hand.
9. **Unattended connections never prompt,** which needs a non-interactive lease in `Connections` and a
   non-prompting credential fetch in the Vault API.

## 3. Model and lifecycle

**Model.**

```
Tunnel(UUID id, String name, UUID hostId, TunnelKind kind, String bindAddress, int bindPort,
       String targetHost, int targetPort, TunnelStart start, Instant created, Instant updated)
TunnelKind  = LOCAL | REMOTE | DYNAMIC
TunnelStart = MANUAL | LAUNCH
```

- `name` non-blank and unique among saved tunnels (case-insensitive). A quick forward has no saved name; it is
  shown as its listen address (`localhost:8080`) and "Save as Tunnel…" asks for one.
- `hostId` names a saved host. A missing host is allowed in the file and shown as "Host missing — edit to choose
  another"; such a tunnel cannot start.
- `bindAddress` non-blank: `localhost`, an IP literal, or `*` for all interfaces. For LOCAL and DYNAMIC it is an
  address of this computer; for REMOTE it is sent to the server as the `tcpip-forward` bind address
  (`*` is sent as the empty string, RFC 4254's "all addresses").
- `bindPort` 0 to 65535; 0 means any free port (the row shows the port it got).
- `targetHost` and `targetPort` (1 to 65535) are required for LOCAL and REMOTE and absent (empty, 0) for DYNAMIC.
  For LOCAL the target is resolved by the server; for REMOTE by this computer.
- Defaults for a new tunnel: LOCAL, `localhost`, target host `localhost`, MANUAL.

**States** (`TunnelState`, sealed):

| State | Meaning | Row detail |
|---|---|---|
| `Stopped` | not running | "via <host>" |
| `Starting(stage)` | "Binding…", "Connecting…", "Verifying host key…", "Authenticating…", "Requesting port on the server…" | the stage |
| `Up(since, boundPort, counters, warning)` | forwarding | counters and uptime, or the warning (section 9) |
| `Reconnecting(attempt, nextAt, reason)` | lost its session, listener still bound | "reconnecting in 8 s (attempt 3)" |
| `WaitingForVault` | needs a credential while the Vault is locked, unattended | "waiting for the Vault" |
| `Failed(reason)` | stopped by a permanent failure | the reason |

**Start** (from the panel, the palette, the editor's Save of a running tunnel, or at launch):

1. The host must exist, else `Failed("Host missing")`.
2. **Bind first.** LOCAL and DYNAMIC bind their listener; REMOTE binds its loopback relay. A local bind failure
   is reported at once, before any credential prompt.
3. **Lease** the host's session through `Connections` (section 7): shared with any open terminal to the same
   host, otherwise opened for the tunnel and kept only while tunnels or terminals use it. Interactive when you
   started it by hand (prompts go to the window you started it from; closing that window cancels a start still
   in progress); unattended at launch and on reconnect.
4. REMOTE only: request the server-side port (`startRemotePortForwarding`, bound to the relay).
5. `Up`.

**Stop** runs exactly once: close the listener or relay, every open socket and channel through the tunnel,
cancel the server-side forward, release the lease. Terminals on the same session are unaffected. Stop on a
`Failed` tunnel just returns it to `Stopped`. Deleting a running tunnel stops it first. Quitting Jasper stops
every tunnel (`RemotePlugin.stop()` stops tunnels before `connections.close()`).

**Losing the session** (MINA close event or keepalive eviction) moves every tunnel on it to `Reconnecting`.
Listeners and relays stay bound, so the local port stays yours; a client that connects meanwhile is accepted and
closed at once. Reconnect is **coordinated per host identity**: the tunnels that lost the same session share one
backoff schedule (`Backoff`: 1, 2, 4, 8, 15, then every 30 s, until stopped) and one unattended connection
attempt; once it succeeds, each tunnel takes its own lease on the new session (`existingLease`) and REMOTE
tunnels request their port again. A transient failure schedules the next attempt; a permanent one moves every
tunnel of that attempt to `Failed` (section 9). A server that refuses a REMOTE port right after a reconnect
usually still holds the old session's forward, so that refusal is transient for that tunnel ("waiting for the
server to release port 8080") and retried on the same schedule; on a fresh start it is permanent.

**At launch** every `LAUNCH` tunnel starts unattended once the plugin has loaded its stores. A tunnel whose
unattended connection finds the Vault locked goes to `WaitingForVault` and retries when `VaultApi.LOCK_STATE_CHANGED`
reports unlocked; the same applies to a reconnect that finds the Vault locked.

**Editing.** Saving a running tunnel whose forwarding changed (host, kind, addresses, ports) stops and starts it;
a name-only or start-option change does not. Editing a host leaves running tunnels on their current session until
they restart, as terminals do. Deleting a host names the tunnels that use it in the confirmation ("2 tunnels use
this host"); they stay, marked "Host missing".

## 4. Engine

All engine code is in `dev.jasper.remote.tunnels`, JDK and MINA only, never Swing.

- **Listener** (LOCAL, DYNAMIC). JDK `ServerSocketChannel`s bound at Start and closed at Stop or `Failed`, never
  on reconnect. `localhost` binds both 127.0.0.1 and ::1 where the machine has them, like OpenSSH; `*` binds
  0.0.0.0 and ::. Port 0 binds IPv4 first, then the same port on IPv6 as a best effort. One virtual thread per
  bound socket accepts. While the tunnel is not `Up`, an accepted socket is closed at once.
- **Per connection** (LOCAL): `session.createDirectTcpipChannel(originator, target)`, opened with the connect
  timeout, then two virtual threads pump bytes (`Pump`): socket to the channel's inverted input, channel's
  inverted output to the socket, 32 KiB buffers, half-close in each direction (a finished direction sends EOF or
  `shutdownOutput`; both finished closes both ends). Backpressure comes from the blocking streams and the SSH
  channel window.
- **SOCKS5** (DYNAMIC): `Socks5` is a pure codec for the greeting (method 0, no authentication; anything else
  answers "no acceptable methods") and the CONNECT request (IPv4, IPv6 and domain addresses). The destination
  becomes the `direct-tcpip` target, so a domain name resolves on the server (the `socks5h` behaviour). Replies:
  success once the channel opens; host unreachable when the open fails; command not supported for BIND and UDP
  ASSOCIATE; address type not supported for anything else. Then it pumps like LOCAL.
- **Relay** (REMOTE). `RemoteRelay` binds `127.0.0.1:0` at Start (never on reconnect) and
  `startRemotePortForwarding(bind, relay)` asks the server to listen. Each connection the server forwards arrives
  at the relay, which connects to the target with the connect timeout and pumps. Port 0 reports the port the
  server chose. The relay listens on loopback only and reaches nothing the local user could not reach directly.
- **Counters** (`Counters`): `LongAdder`s for bytes received and sent (from the client's point of view), the
  number of open connections, and `since` (the last time the tunnel came up). Counters reset on Start, not on
  reconnect; uptime restarts on reconnect.
- **Warning:** a failed channel open (the server refused `direct-tcpip`, or the target was unreachable) is
  counted and sets the `Up` state's warning until the next successful open (section 9).

`TunnelManager` owns tunnels, states and reconnect scheduling on the UI thread, like `Connections`. Engine threads
report through the UI executor; MINA's I/O threads never touch manager state. Time comes from the injected
scheduler Remote already uses (`BiFunction<Duration, Runnable, Runnable>`), so tests never sleep.

## 5. Persistence

`plugins/dev.jasper.remote/data/tunnels.toml`, read with tomlj and written by the plugin's own writer
(deterministic order, temp file and rename), polled once a second for outside edits — the `hosts.toml` pattern:

```toml
# Jasper Remote tunnels. Edit freely; Jasper rewrites this file when you save from the panel.
[[tunnel]]
id = "…"                      # stable; keep it when editing by hand
name = "pg-prod"
host = "5f2c…"                # a saved host's id
kind = "local"                # local | remote | dynamic
bind_address = "localhost"    # localhost, an IP address, or * for all interfaces
bind_port = 5432              # 0 = any free port
target_host = "db.internal"   # local and remote only
target_port = 5432            # local and remote only
start = "manual"              # manual | launch
created = 2026-09-30T10:00:00Z
updated = 2026-09-30T10:00:00Z
```

A file that fails to parse keeps the last good tunnels in memory, shows one error row ("tunnels.toml has
errors: …") in the panel and the palette until it parses again, and refuses saves with a notice. An outside edit
to a running tunnel's forwarding restarts it, like the editor. Quick forwards are never written until saved. No
new settings: timeouts and keepalive come from Remote's existing Connection settings.

## 6. UI

```
┌ Tunnels ───────────────────── + ┐
│ Filter tunnels…                  │
│ ● pg-prod                     ■  │
│   L  localhost:5432 → db.internal:5432
│   via db-primary · 3 conns · ↓12 MB ↑1.4 MB · 2h 14m
│ ○ grafana                     ▶  │
│   L  localhost:3000 → localhost:3000
│   via monitoring                 │
│ ◐ socks                       ■  │
│   D  localhost:1080 · SOCKS5     │
│   via bastion · reconnecting in 8 s (attempt 4)
│ ✕ api-dev                     ▶  │
│   R  server *:8080 → localhost:8080
│   via web-01 · The server refused port 8080: already in use or not allowed
│ ─ Quick forwards ──────────────  │
│ ● localhost:8080              ■  │
│   L  localhost:8080 → localhost:8080
│   via web-01 · 1 conn · unsaved  │
└─────────────────────────────────┘
```

- **Panel** (`Panels.register`, `Anchor.LEFT`, title "Tunnels", toggle action `dev.jasper.remote.tunnels`, rail
  icon `BundledIcon.ACTIONS_SWAP_PANELS` unless the GUI check prefers another catalog icon; one instance per
  window over the one `TunnelManager`). Header: title and `+` (New Tunnel…). A filter field matching name, host
  name, addresses and ports. Saved tunnels sorted by name, stable when they start or stop; then a "Quick
  forwards" section, present only when there are some. Rows: a state dot painted in the theme's success,
  warning, error or disabled colour; the name; a start/stop button (▶ for Stopped and Failed, ■ otherwise); the
  kind letter and address mapping; "via <host>" and the state detail. The tooltip spells everything out. The
  panel repaints once a second through a Swing timer that runs only while the panel is showing and a tunnel is
  `Up`.
- **Keys and mouse:** double-click edits; Enter or Space starts or stops the selection; Delete deletes after
  confirmation.
- **Context menu:** Start / Stop; Open in Browser (a running LOCAL tunnel: `http://localhost:<port>` through
  `Platform.browse`); Copy Address (`localhost:5432`, the SOCKS address, or `<server>:<port>` for REMOTE);
  Edit…; Duplicate; Save as Tunnel… (quick forwards); Delete….
- **Empty state:** "No tunnels yet — add one with +, or use Forward a Port… from an SSH pane."
- **Tunnel editor** (window-modal dialog, the host editor's shape): Name; Via host (combo of saved hosts);
  Type (Local / Remote / Dynamic SOCKS) with one plain line explaining the chosen type; Listen on (address and
  port, labelled "this computer" or "the server") with "Allow other computers to connect" (`*`), whose warning
  says the port is then reachable from the network and, for Remote, that the server's `GatewayPorts` must allow
  it; Forward to (host and port, "as seen from <host>" or "as seen from this computer"; hidden for Dynamic);
  Start (Manually / When Jasper starts); a preview sentence ("localhost:5432 on this computer → db.internal:5432
  as seen from db-primary"); Save, Cancel. Inline validation per section 3; another saved tunnel with the same
  kind side, listen address and port is a warning, not an error (both may exist; they cannot run at once).
- **Forward a Port…** (dialog): Remote port; Local port (defaults to the same number, or the next free port if
  that one is taken); Target host in a collapsed "More" section (default `localhost`); host from the invoking
  SSH pane, or a host combo when there is none; "Keep as a saved tunnel" (asks for a name). OK starts a LOCAL
  quick forward at once, interactively. Other kinds use the full editor.
- **Status item** (right, priority 59): "1 tunnel" / "N tunnels" counting tunnels not `Stopped` or `Failed`,
  hidden when that is zero and nothing is failing. While any tunnel is `Reconnecting`, `WaitingForVault` or
  `Failed`, a warning icon and "· N failing". The tooltip lists the tunnels and their states. Its action is
  `dev.jasper.remote.tunnels` (toggle the panel).
- **Palette scope** `dev.jasper.remote.tunnels.scope`, label "Tunnels", aliases `tunnel`, `tunnels`, `fwd`,
  `forward`, in the All tab. Rows are saved tunnels and quick forwards (title name, detail mapping and state, tag
  host name); verbs **Start or stop** (Enter), **Open in browser** (Cmd/Ctrl+Enter; an error result on a row that
  is not a running local tunnel), **Edit tunnel…** (Shift+Enter); the error row when `tunnels.toml` is broken.
- **Actions:** `dev.jasper.remote.tunnels` "Toggle Tunnels" (rail button, View › Tools, no default binding);
  `dev.jasper.remote.tunnels.show` "Show Tunnels" (reveals rather than toggles; Buddy's card activation);
  `dev.jasper.remote.tunnels.new` "New Tunnel…"; `dev.jasper.remote.tunnels.forward` "Forward a Port…"
  (also on the terminal context menu; prefilled from an SSH pane); `dev.jasper.remote.tunnels.stop-all` "Stop All
  Tunnels" (enabled while any tunnel is not `Stopped`). The SSH menu gets a separator and Toggle Tunnels, New
  Tunnel…, Forward a Port…, Stop All Tunnels.

## 7. Unattended connections

At launch and on reconnect nobody asked for a prompt, so none may appear.

- **`Connections` gains an unattended lease** (the existing `lease` stays the interactive one). An unattended
  request never shows a dialog: an unknown host key fails with "Host key not yet trusted — start it by hand
  once"; a locked Vault fails with a distinct `VaultLocked` failure the manager turns into `WaitingForVault`; the
  credential comes from the Vault's non-prompting fetch. An interactive request is never failed because an
  unattended one for the same identity was in flight: when the attempt it joined fails for want of a prompt, the
  interactive request starts its own interactive attempt.
- **Vault API addition** (Vault 0.4.0): `default CompletableFuture<Optional<Credential>>
  credentialIfAllowed(UUID id)` on `VaultApi` returns the credential when the Vault is unlocked and Remote holds
  an "Always allow" grant, and empty otherwise without showing anything. Remote checks `lockState()` first
  (locked means `WaitingForVault`); empty from an unlocked Vault means "Needs the Vault's permission — start it
  by hand once and choose Always allow". A Remote running against an older Vault catches `NoSuchMethodError`
  and treats it the same way. Remote's optional `requires` range stays as it is.
- **SSH agent** hosts need nothing new; an agent that is not running yet ("SSH agent not available") is transient
  when unattended, because agents such as 1Password's may start after Jasper.

## 8. Buddy and SDK 0.10.2

**SDK 0.10.2** (additive; existing activities unchanged):

- `ConnectionEvent(UUID id, String sourcePluginId, String title, State state, String detail,
  Optional<String> activateActionId)` with `State { UP, DEGRADED, DOWN, CLOSED }`, on a new topic
  `Activities.CONNECTIONS` (`"jasper.connection"`). A separate topic rather than new `ActivityEvent.State`
  constants, so no exhaustive switch breaks.
- `ConnectionSpec(String title, String detail, ConnectionEvent.State state, Optional<String> activateActionId)`;
  the initial state is UP, DEGRADED or DOWN.
- `Activities.connection(ConnectionSpec)` returns a `ConnectionHandle`: `id()`, `up(detail)`, `degraded(detail)`,
  `down(detail)`, `close()`. Safe from any thread; a state change is always delivered and in order, while
  detail-only reports coalesce like activity progress; `close()` publishes CLOSED exactly once and later calls
  are ignored; the runtime closes a plugin's open handles when it stops. Throws `IllegalStateException` when
  the context is closed.
- `Platform.browse(URI)`: opens an `http` or `https` URI in the default browser in the background (the app's
  existing opener); a failure goes through `Notices`; other schemes throw `IllegalArgumentException`.
- Testkit: `FakePluginHost` records connection events and browsed URIs. The contract suite covers the handle
  lifecycle (one CLOSED, calls after close ignored, stop closes open handles, coalesced details, ordered states)
  and `browse`'s scheme check.

**App bridge.** `PluginRuntime` subscribes to `CONNECTIONS` as it does to `TOPIC`, and `ActivityNotifier` maps a
state change to `deck.post` of a `Kind.CONNECTION` notice (a re-post is new news, so a dropped connection speaks
up again even after it was acknowledged), a detail-only change to the card's detail supplier (no new attention),
and CLOSED to `dismiss`. **Card activation** resolves `activateActionId` when it names an action registered by the
source plugin: raise the application, invoke it in the active window (`WindowContributions.invoke`), then
acknowledge the card; any other id falls back to raising the application, as every click does today. Task cards
get the same activation, which `ActivitySpec` has promised since the actions API landed.

**What Remote posts** (`TunnelNotices`), with `activateActionId = dev.jasper.remote.tunnels.show`:

- **Session lost:** one card per host identity, "Tunnels via <host>", DEGRADED, detail "pg-prod, grafana ·
  reconnecting in 8 s (attempt 3)", updated as attempts go by.
- **Everything came back:** the card is closed. The problem is gone, so there is nothing to keep.
- **A permanent failure after a drop, or at launch:** the card becomes DOWN naming the failed tunnels and any that
  came back ("api-dev: Host key rejected: fingerprint changed · pg-prod is back").
- **Tunnels waiting for the Vault** (at launch or on a reconnect): one DEGRADED card "Tunnels waiting for the
  Vault", detail "grafana, socks · unlock the Vault to start them"; closed once none is waiting.
- **Anything started, stopped or retried by hand posts nothing.** Retrying, stopping or deleting a tunnel removes
  it from the card that names it; a card naming nobody is closed.

## 9. Errors

Connection-stage failures keep `Failures`' wording. `Failures` gains `retryable(Throwable)`, so the manager never
parses messages:

- **Permanent** (to `Failed`, never retried): "Port 5432 is already in use on this computer"; "Port 80 needs
  administrator rights"; "Cannot listen on 192.168.1.5: not an address of this computer"; "The server refused port
  8080: already in use or not allowed" (on a fresh start); "Host key rejected: fingerprint changed"; "Host key not
  yet trusted — start it by hand once"; "Authentication failed (tried …)"; "Credential denied"; "needs Credential
  Vault"; "Needs the Vault's permission — start it by hand once and choose Always allow"; "jump host missing";
  "Host missing".
- **Transient** (retried with backoff when unattended; `Failed` with the same text when a manual start fails the
  first time): "Could not resolve host …", "No route to host …", "Connection refused", "Timed out after 10 s",
  "Connection lost", "SSH agent not available", and a REMOTE port refused right after a reconnect.
- **Per-connection failures** never fail the tunnel: it stays `Up` and its row shows the warning "Connections are
  being refused: <reason>" (for example "administratively prohibited", which usually means the server's
  `AllowTcpForwarding` is off) until the next connection opens. A SOCKS client gets the matching SOCKS reply.
- Store write failures use `notices().error`, as for hosts.

## 10. Threading and packages

- `TunnelManager`, `TunnelNotices` and all UI run on the UI thread; the engine runs on virtual threads and reports
  through the UI executor; MINA callbacks hop to the UI executor before touching state. Every wait uses the
  connection settings' timeouts.
- `dev.jasper.remote.tunnels` (no Swing): `Tunnel`, `TunnelKind`, `TunnelStart`, `TunnelFile`, `TunnelStore`,
  `Listener`, `Socks5`, `RemoteRelay`, `Pump`, `Counters`, `TunnelState`, `Backoff`, `TunnelManager`. It depends on
  `client` (leases, `Failures`) and `hosts` (values).
- `dev.jasper.remote.ui.tunnels`: `TunnelsUi` (panel registration per window, status item, actions, menu),
  `TunnelsPanel`, `TunnelRows`, `TunnelEditor`, `ForwardDialog`, `TunnelsScope`, `TunnelNotices`. It never touches
  MINA.
- `RemotePlugin` wires `TunnelsUi` beside `SftpUi`. Remote becomes 0.9.0 with `sdk = ">=0.10.2, <0.11"`; its
  manifest description mentions tunnels. No new capability: the SDK gates neither activities nor listening
  sockets.

## 11. Testing

- **Pure:** `TunnelFile` round trip, validation and a broken file; `Socks5` byte for byte, including rejected
  methods, BIND, UDP ASSOCIATE and bad address types; `Backoff`; state transitions; `retryable`; row and status
  text.
- **Engine and manager** against the embedded loopback MINA `SshServer` the Remote tests already use, with
  forwarding allowed and a loopback echo server as the target: LOCAL round trip with exact byte counts; DYNAMIC
  through the JDK's own SOCKS client (`Proxy.Type.SOCKS`, a real third-party client); REMOTE by connecting to
  the server-side port; port in use; Stop closing live connections; a forwarding filter that refuses
  (`Up` with the warning); the server dropping the session (`Reconnecting`, then `Up` on the **same local port
  without unbinding**); a REMOTE port refused after reconnect, then freed; an authentication failure never
  retried; two tunnels to one host making **one** connection attempt; an at-launch tunnel with a fake locked
  Vault (`WaitingForVault`, then `Up` on the unlock event); an unknown host key failing unattended without a
  prompt. Backoff runs on the injected scheduler.
- **`FakePluginHost`** (with the real Vault plugin beside Remote, as today): panel rows and filter, editor
  validation, Forward a Port… prefill, status item text and visibility, actions' enabled state, palette rows and
  verbs, the connection events of section 8, `browse` recorded.
- **App and SDK:** the bridge's mapping (state change re-posts, detail-only does not, CLOSED dismisses);
  activation invokes the source plugin's action and acknowledges, a foreign or unknown id only raises the app;
  the contract cases of section 8.
- **Native acceptance** (user-run): LOCAL to a real database; DYNAMIC as a browser's SOCKS proxy; REMOTE from the
  server; Wi-Fi off and on, and sleep and wake; an at-launch tunnel with a locked Vault; the macOS firewall prompt
  with "Allow other computers to connect"; the Buddy cards and their activation.

## 12. Plans

Each plan leaves the app runnable.

- **7b-1 — SDK 0.10.2:** connection activities, `Platform.browse`, the app bridge with card activation (task cards
  included), testkit and contract cases, SDK docs.
- **7b-2 — Tunnels you run by hand:** model and store, the engine for all three kinds, `TunnelManager` without
  reconnect (a lost session sends its tunnels to `Failed("Connection lost")` and closes their listeners), the
  panel, the editor, Forward a Port… and quick forwards, the palette scope, the status item, actions and SSH menu,
  Open in Browser and Copy Address, the host-delete interplay, Remote 0.9.0. `tunnels.toml` keeps the `start`
  field, but the editor's Start option and starting at launch arrive with 7b-3. Needs 7b-1 for `browse`.
- **7b-3 — Tunnels that stay up:** the Vault's `credentialIfAllowed` (Vault 0.4.0), the unattended lease,
  `Failures.retryable`, per-host reconnect with backoff (listeners kept bound), at-launch start and
  `WaitingForVault`, `TunnelNotices`, docs (`docs/remote.md` tunnels guide and native acceptance).

## 13. Self-review record

- Placeholders: none. The rail icon names a catalog icon with a GUI check, not a placeholder.
- Consistency: section 3's "bind first" is what lets section 9 report port conflicts before any prompt, and what
  keeps the port through section 3's reconnect; section 7's unattended lease is the only path at launch and on
  reconnect, so section 2's "never prompts" holds; section 8's "posts only trouble" matches the status item and
  panel covering healthy tunnels.
- Refinement since brainstorming: a REMOTE port refused right after a reconnect is transient (the server usually
  still holds the old session's forward) instead of permanent; "SSH agent not available" is transient when
  unattended.
- Scope: three plans; 7b-2 needs 7b-1; 7b-3 needs 7b-2.
