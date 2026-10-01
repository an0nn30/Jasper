# Remote tunnels plan 7b-3 — handoff for the next agent

**Status:** Design settled 2026-10-01; **the plan itself is not written yet.** Branch `claude/tunnels-7b3`, cut from
`origin/main` at `5fc76c7d`; this file is its only commit. Plans 7b-1 (SDK 0.10.2 connection activities and `Platform.browse`)
and 7b-2 (tunnels you run by hand, Remote 0.10.0) are merged to main and pushed.

**Your job:**
1. Write plan 7b-3, "tunnels that stay up", with `superpowers:writing-plans`. Save it to
   `docs/superpowers/plans/2026-10-01-jasper-remote-plan-7b3-tunnels-stay-up.md`. Every step carries complete code, uses TDD
   and ends in one commit per task. Use the 7b-2 plan as the format template.
2. Self-review the plan against the spec, commit it on this branch, and offer the user subagent-driven execution
   (`superpowers:subagent-driven-development`; 7b-2 ran that way).

Section 6 below is the task breakdown, already worked out against the code. Turn it into plan text. Do not re-design it
unless the code has moved.

---

## 1. Read these first (binding, in this order)

1. `AGENTS.md` (repo root). It covers the build, never launching the GUI, never committing on `main`, the
   `Co-Authored-By` trailer, source hygiene and the architecture rules. The Remote plugin may use the Vault only through
   `dev.jasper.vault.api`, and only optionally. Remote's code tolerates the Vault being absent (`NoClassDefFoundError` guards in
   `RemotePlugin.start`).
2. `docs/STATUS.md`, top entries: the 7b-2 entry lists every deviation and follow-up already recorded.
3. **The spec:** `docs/superpowers/specs/2026-09-30-jasper-remote-tunnels-design.md`. It was approved by the user, and these
   sections bind 7b-3:
   - §2 decisions 3, 6, 8 and 9: start Manually or When Jasper starts; Buddy notices; Buddy posts only trouble; unattended
     connections never prompt.
   - §3: states `Reconnecting` and `WaitingForVault`; losing the session; coordinated per-host reconnect; at launch.
   - §7: the unattended lease; the Vault `credentialIfAllowed`; the SSH agent being transient when unattended.
   - §8, "What Remote posts": `TunnelNotices`.
   - §9: permanent versus transient failures, and `Failures.retryable`.
   - §11: the testing list for reconnect, launch and the Vault.
   - §12: the plan 7b-3 scope.
4. The 7b-2 plan, as the template for shape and level of detail:
   `docs/superpowers/plans/2026-09-30-jasper-remote-plan-7b2-tunnels-by-hand.md`.
5. `docs/remote.md` § Tunnels: the user guide 7b-3 must update.
6. Related specs only if needed: `docs/superpowers/specs/2026-09-22-jasper-remote-design.md` (Remote: hosts, connections,
   trust, auth) and `docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md` (SDK).

## 2. Versions and constraints for 7b-3

- **Vault 0.5.0.** The Vault is already 0.4.0 on main, so `credentialIfAllowed` makes it 0.5.0, not the 0.4.0 the spec names.
- **Remote 0.11.0.** Remote is 0.10.0 on main. Its `requires` entry for the Vault stays `>=0.2`, optional. Remote catches
  `NoSuchMethodError` from an older Vault.
- **SDK: none.** The 0.10.2 connection API (`Activities.connection`, `ConnectionSpec`, `ConnectionHandle`, `ConnectionEvent`)
  already exists. Keep whatever `sdk = ` range main has when you start: the user's local, unpushed main moves every plugin to
  `sdk = ">=0.12.0, <0.13"` (theme work). If main has that by the time you merge, take it and do not fight it.
- Only `plugins/remote`, `plugins/vault` and docs change. App tests that count Remote's contributions
  (`jasper-app/.../plugins/BundledSamplePluginTest`, `RemoteShortcutConfigTest`) change only if 7b-3 adds a panel, action
  or status item, and it adds none.
- No GUI launches. Hand GUI checks to the user (`docs/remote.md` "Native acceptance for tunnels").
- Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, or whatever attribution your session gives.
  Work in a git worktree, never in the main checkout; other sessions switch branches there.
- `./gradlew check` does **not** rerun `:jasper-app:test` when only a staged plugin jar changed. Always also run
  `./gradlew :jasper-app:test --rerun` before reporting counts. Counts come from the XML in `*/build/test-results/test/` and
  `plugins/*/build/test-results/test/`.

## 3. How 7b-2 was executed (repeat it)

- `superpowers:subagent-driven-development` with a ledger in `.superpowers/sdd/<plan-basename>/progress.md`, which is
  git-ignored scratch.
- The pre-flight conflict table is written into the ledger.
- Implementers: the cheapest model for tasks whose plan text has complete code (transcription), a mid model for
  integration and concurrency. Reviewers: mid-tier, with the most capable model for the manager and concurrency tasks and
  the final whole-branch review.
- Every ruling goes in the ledger with its cost if wrong. Deviations go in the plan banner and in STATUS.
- Before the wiring task, merge main into the branch, since main moves fast. Expect a `docs/STATUS.md` conflict at the
  final merge; keep both entries.

## 4. Code facts verified at `5fc76c7d` (re-check line numbers before quoting anchors)

**`plugins/remote/src/main/java/dev/jasper/remote/client/Connections.java` (577 lines).**
- Registry state is UI-thread-only.
- Constructors: a 9-argument one with `Optional<Function<UUID, CF<Optional<Credential>>>> credentials` and
  `Function<Question, CF<Decision>> prompt`. It delegates to a 9-argument one with
  `Optional<BiFunction<WindowHandle, UUID, CF<…>>> credentials` and `BiFunction<WindowHandle, Question, CF<Decision>> prompt`.
- `lease(identity, owner, status)` is public and delegates to private `lease(identity, owner, status, boolean dedicated)`.
  `shell(...)` calls `lease(identity(id), owner, status, dedicated)`.
- Pipeline: `prepare(identity, owner)` → `prepareHop(...)`. A hop with a blank username fetches its credential here through
  `credentialFor(hop.host(), owner)` to learn the username. Then `acquire(...)`: a `Shared` keyed by `ConnectionIdentity`
  (a shared session, joined by later leases) → `authenticate(...)` → `credentialFor` (Agent, or Vault through
  `acquireCredential`, which says "Credential denied" when the result is empty) → `connect(...)` in the background.
- `existingLease(identity)` borrows an already authenticated shared session without prompting; the identity must be resolved.
- `Shared.verifier = new HostKeyVerifier(trust, question -> …ask(this, question, answer)…)`. `ask` prompts the open owner
  window, if any.
- Failure text comes from `Failures.message`; `lease` wraps with `failure(shared, problem)`, which is
  `new Failure(message(problem), problem)` and keeps the cause.
- `evict` completes `ready` with `Failure("connection lost")`. A session close listener calls `evict`.
- The lines to mark temporary in task 2, all currently `new Failures.Failure(...)`:
  - `fail(fresh, new Failures.Failure("connection lost"))`, when the session is not open after connect.
  - `"SSH agent not available"` in `credentialFor`.
  - `"SSH agent has no usable keys; load a key or choose Vault."` and `"SSH agent unavailable: " + …` in `connect`.
  - `"Timed out after " + authTimeout + " s"` in `connect`'s auth catch.
  - `evict`'s `"connection lost"`.
- `connect`'s auth catch checks `session.getAttribute(HostKeyVerifier.REJECTION)` and throws `Failure(rejection)`.

**`client/HostKeyVerifier.java`.** `ask` calls `prompt.apply(question).get(timeout)`. Any exception sets
`REJECTION = "Host key not trusted"`. A mismatch sets `REJECTION = "Host key rejected: fingerprint changed (known …, offered …)"`.

**`client/Failures.java`.** `Failure` is `final` today, and only `message(...)` exists.

**`tunnels/TunnelManager.java` (364 lines, UI-thread-only).**
- Its constructor is `(Connections, Supplier<Duration> timeout, Executor background, Executor ui)`; there is no scheduler yet.
- `start(tunnel, owner)`: binds the `Listener` first (a REMOTE tunnel binds its relay on `127.0.0.1:0`), then
  `connections.lease(...)`, then `leased(...)`.
- `leased`: watches the session (`run.watch`) and today calls `fail(run, "Connection lost")` on close. LOCAL and DYNAMIC
  tunnels go `up`. A REMOTE tunnel first checks the same-port guard (`remotePortHeld`), then sends
  `startRemotePortForwarding` through `ordered(session, …)`, the per-session FIFO.
- Connection threads (`carry`, `socks`, `relay`) close a client at once while `run.session == null`. This is how "accepted
  and closed while not Up" already works.
- `Run.track/untrack/stop` manage the tracked ends. Also present: `known()`, `failed()` (counts Failed only), `quick`
  forwards and `saved(id)`.

**`tunnels/TunnelState.java`.** States are `Stopped`, `Starting(stage)`, `Up(since, boundPort, counters)` and
`Failed(reason)`; `running()` means Starting or Up. Exhaustive switches over `TunnelState` exist in `TunnelRows.detail`,
`TunnelRows.stateText` and `TunnelsPanel.dotColor`; new states break their compilation until handled.

**`tunnels/Tunnel.java`.** `withEdited(...)` keeps `start`; nothing sets `start` yet; add `withStart(TunnelStart)`.
`TunnelStart` is `MANUAL | LAUNCH` and is already persisted by `TunnelFile`.

**`ui/tunnels/TunnelsUi.java`.**
- `refresh()` sets the status text through `TunnelRows.status(active, failing)` with `failing = manager.failed()`, sets the
  warning icon and enables Stop All.
- `storeChanged()` stops ids that are no longer listed and restarts a tunnel whose forwarding changed through
  `manager.start(now, owner())`.
- `hostsChanged()` is called from `RemotePlugin`'s `store.onChanged`.

**`RemotePlugin.java`.**
- It builds `Connections` with `credentialSource = (owner, id) -> vault.get().credential(owner, id)` and
  `this::askHostKey`, which answers CANCEL when the owner is null.
- It builds `new TunnelManager(connections, () -> settings.connection().connectTimeout(), context.background(), ui)`.
- `schedule` (a `BiFunction<Duration, Runnable, Runnable>`, a one-shot Swing Timer) is a field.
- It already subscribes to `VaultApi.LOCK_STATE_CHANGED` and calls `refreshPanels()`.
- Load order: `store.load(); tunnelStore.load();`. Both stores call their listeners on the first read, file or no file.
- `stop()` closes `tunnelsUi` before `connections`.

**Vault (`plugins/vault`).**
- `api/VaultApi.java` has `lockState()`, `credential(id)` and `credential(owner, id)`, which prompt for an unlock and a grant.
- `service/VaultService.ConsumerApi` checks `lock.vault().grants().contains(new Grant(consumer.id(), id))`; one-time grants
  live in `oneTime`.
- `VaultPlugin` publishes `LOCK_STATE_CHANGED`.
- Tests: `plugins/vault/src/test/java/dev/jasper/vault/service/VaultServiceTest.java`. Its `service(dir)` and `populate()`
  fixtures and `GrantPrompt.answer(ALWAYS)` show how to create an "Always allow" grant.

**Test fixtures (Remote).**
- `client/LoopbackServer`: an embedded MINA server; password `deploy`/`s3cret`; forwarding allowed; `hostPublicKey()`.
- `client/ConnectionsTest`: its `connections(dir, agent)` helper records `questions` and `issued` credentials.
- `tunnels/TunnelManagerTest`: real loopback server and `EchoServer`. It has a fake UI executor and the helpers `onUi`,
  `await`, `up`, `viaServer`, `refused` and `closedPort`. Connections is built with the 1-argument prompt form.
  `aSlowCancelStillRunsBeforeTheNextRequestForTheSamePort` builds its own `TunnelManager`, so update its constructor too.
- `FakeVault` (Remote tests): always unlocked, every credential granted, `state` field.
- `TunnelsPluginTest`: `FakePluginHost`, `TestRemotePlugin(Runnable::run, …, scheduled::add-style scheduler, …)`,
  `RemotePluginTest.settle`, `host.connectionLog()`.
- `FakePluginHost(Path dataRoot)` puts each plugin's data in `<dataRoot>/<id>/data`. Pre-write `hosts.toml`
  (`HostFile.format`), `tunnels.toml` (`TunnelFile.format`) and `known_hosts` (`KnownHosts.trust`) there to test at-launch
  behaviour.

## 5. Pitfalls already learned in 7b-2 (do not rediscover)

- **MINA's `DefaultForwarder.startRemotePortForwarding`** removes the mapping **by port** on any failure and sends
  `cancel-tcpip-forward`. That is why `TunnelManager` has the same-port guard and runs remote requests and cancels
  through `ordered`. Keep both on the reconnect path.
- **MINA's test server** drops a forwarded connection when the client half-closes. In tests that go through a server
  port, write the bytes and read them back with `readNBytes`; do not half-close. `TunnelManagerTest.viaServer` does this.
- **The SSH client's forwarding filter** accepts only `forwarded-tcpip` channels to a loopback target. Do not widen it.
- **Swing:** any label that shows user text sets `putClientProperty("html.disable", true)` **before** its text. A hidden
  child is laid out at 0x0 headlessly, which makes a wrapped `JTextArea` balloon.
- **Data files:** tunnels.toml entries without an id get a name-derived id. Saves are refused while skipped entries exist.

## 6. The design: ten tasks

### Task 1 — Vault 0.5.0: `credentialIfAllowed`
- `VaultApi`: add the following, documented as "never prompts; never uses a one-time grant":
  ```java
  default CompletableFuture<Optional<Credential>> credentialIfAllowed(UUID id) {
      return CompletableFuture.completedFuture(Optional.empty());
  }
  ```
- `VaultService.ConsumerApi`: when the vault is UNLOCKED and the persisted grants contain
  `new Grant(consumer.id(), id)`, return `copyOf(id)`. Otherwise return empty. Never touch `oneTime` and never show a prompt.
- Tests in `VaultServiceTest`:
  - Without a grant the result is empty and no grant prompt appears.
  - After `ALWAYS` it returns the secret.
  - After `ALLOW_ONCE` it is still empty.
  - While locked it is empty.
- Set `plugin.toml` to `version = "0.5.0"`, and add a sentence to `docs/credential-vault.md`.

### Task 2 — `Failures`: kinds and `retryable`
- `Failure` becomes non-final, with a package-private constructor `(message, cause, boolean temporary)` and a
  `temporary()` accessor. Add the factories `Failures.temporary(String)` and `Failures.temporary(String, Throwable)`.
- New kinds:
  - `NeedsPrompt extends Failure`: an unattended request needed a person.
  - `VaultLocked extends Failure`, with the message "Waiting for the Vault".
- Constants:
  - `UNTRUSTED_HOST = "Host key not yet trusted — start it by hand once"`
  - `NEEDS_GRANT = "Needs the Vault's permission — start it by hand once and choose Always allow"`
- `retryable(Throwable)`:
  1. Unwrap `CompletionException` and `ExecutionException`.
  2. A `Failure` is retryable when it is `temporary()`, or when it has a cause and that cause is retryable.
  3. Otherwise these cause types are retryable: `UnknownHostException`, `UnresolvedAddressException`,
     `NoRouteToHostException`, `ConnectException`, `SocketTimeoutException` and `TimeoutException`.
  4. Otherwise retryable when the message contains timeout, timed out, connection refused, refused the network connection,
     no route to host, connection lost, closed or reset.
- `waitsForVault(t)` is true when `VaultLocked` is anywhere in the cause chain. `needsPerson(t)` is true when `NeedsPrompt`
  or `VaultLocked` is.
- Mark the Connections lines listed in §4 temporary; their messages do not change.
- Tests in `FailuresTest`: a classification table.
- Tests in `ConnectionsTest`, end to end:
  - A closed port is retryable.
  - A wrong password is not ("Authentication failed (tried password)").
  - `Auth.AGENT` with no agent gives "SSH agent not available" and is retryable.

### Task 3 — `Connections.leaseUnattended`
- Add a 10-argument constructor: the BiFunction form plus `Optional<Function<UUID, CF<Optional<Credential>>>> unattendedCredentials`.
  The existing BiFunction constructor delegates to it with `Optional.empty()`.
- Add `public CompletableFuture<SessionLease> leaseUnattended(ConnectionIdentity identity, Consumer<String> status)`, which
  calls the private `lease(identity, null, status, false, /*unattended*/ true)`. `prepare`, `prepareHop`, `credentialFor`,
  `acquireCredential` and `acquire` (including the parent hop) each take an `unattended` parameter.
- `Shared` gets `final boolean unattended`. When it is set, the verifier's prompt function returns
  `failedFuture(new Failures.NeedsPrompt(UNTRUSTED_HOST))` and nothing is asked.
- `HostKeyVerifier` gets a new attribute, `REFUSAL` (an `AttributeKey<Failures.Failure>`). In `ask`, when the
  `ExecutionException` cause is a `Failures.Failure`, set `REJECTION` to its message and `REFUSAL` to the failure itself.
  In `connect`'s auth catch, throw `REFUSAL` before checking `REJECTION`, so the failure keeps its kind.
- Unattended credentials:
  - Use `unattendedCredentials` when present; when it is missing, fail with `NeedsPrompt(NEEDS_GRANT)`.
  - An empty result fails with `NeedsPrompt(NEEDS_GRANT)` instead of "Credential denied".
  - A `VaultLocked` failure from the source passes through.
- **An interactive lease that joined an unattended `Shared`.** When that Shared fails with `needsPerson`, release and
  start a fresh interactive `lease(identity, owner, status, dedicated, false)` (`again(...)`). Settle the original result
  from it, and let cancelling the original cancel the retry. This is spec §7, "never failed because an unattended one was
  in flight".
- Tests in `ConnectionsTest`. Switch the helper to the 10-argument constructor, with a volatile `unattended` field read
  through a lambda.
  - An unknown host key fails unattended with `UNTRUSTED_HOST`, and `questions` stays empty.
  - The silent source is used; an empty result fails with `NEEDS_GRANT`; the prompting source is never called; with a
    grant the lease succeeds.
  - `VaultLocked` passes through, and `waitsForVault` is true.
  - An interactive lease that joined an unattended attempt asks for itself. Hold the silent future open until both
    requests are queued, then complete it empty; the interactive lease still succeeds through the prompting source.

### Task 4 — Model, `Backoff` and row text
- `TunnelState`:
  - Add `record Reconnecting(int attempt, Instant nextAt, String reason)`, with attempt at least 1 and non-null parts.
  - Add `record WaitingForVault()` and the constant `WAITING_FOR_VAULT`.
  - `running()` is true for everything that is not Stopped or Failed; reconnecting and waiting tunnels keep their listener.
  - Add `troubled()`: Reconnecting, WaitingForVault or Failed.
- `Backoff.delay(int attempt)` (attempt ≥ 1): 1, 2, 4, 8 and 15 s, then 30 s. Pure; `BackoffTest`.
- `Tunnel.withStart(TunnelStart)`: keeps everything else and sets `updated` to now.
- `TunnelRows`:
  - `detail` for Reconnecting: "via h · reconnecting in N s (attempt k) · reason". The seconds are rounded up; at 0 s or
    less it reads "reconnecting… (attempt k)". Use a package-private helper `reconnecting(state, now)`.
  - `detail` for WaitingForVault: "via h · waiting for the Vault".
  - `stateText`: "reconnecting" and "waiting for the Vault".
- `TunnelsPanel.dotColor`: both new states use `Actions.Yellow`.
- Tests: `TunnelRowsTest` additions, plus a `TunnelFileTest` round trip of `withStart(LAUNCH)`.

### Task 5 — `TunnelManager`: reconnect with backoff
- Add a fifth constructor parameter, `BiFunction<Duration, Runnable, Runnable> schedule` (the task may run on any thread).
  Update `RemotePlugin`, which passes its `schedule`, and the slow-cancel test.
- **On session close**, the watch calls `lost(run, "Connection lost")`. It does three things:
  1. Sets `run.unattended = true`.
  2. Detaches: sets `session = null`, closes the tracked ends but **keeps the listener**, unwatches, releases the lease,
     and sets `remoteBound = null` (the server's forward died with its session).
  3. Joins the run to the host's recovery.
- **`Recovery`**, one per `hostId`:
  - Fields: `members`, `attempts`, `nextAt`, `reason`, `cancelTimer` and `trying`.
  - Its state is `Reconnecting(attempts, …)` while an attempt is under way, else `Reconnecting(attempts + 1, nextAt, reason)`.
  - `join` starts a wait when the recovery is idle. A run joining mid-attempt is picked up when that attempt finishes.
- **`scheduleNext`**: `Backoff.delay(attempts + 1)`, then `schedule.apply(delay, () -> ui.execute(attempt))`, guarded by
  the recovery still being current.
- **`attempt`**:
  1. `attempts++`.
  2. `connections.identity(hostId)`. A missing host gives up with "Host missing".
  3. `connections.leaseUnattended(...)`.
  4. On success, remove the recovery. Each member takes `connections.existingLease(lease.identity())`, then attaches and
     runs `connected(run, again = true)`. A member that gets no lease rejoins. Finally close the attempt's lease.
  5. On failure: if `waitsForVault`, every member goes to a `waiting` set as `WAITING_FOR_VAULT`. If `retryable`,
     schedule the next wait. Otherwise fail every member.
- **REMOTE after a reconnect.** If the guard or the server refuses the port, detach and rejoin with the reason "waiting
  for the server to release port N". In the answer handler, ignore an answer for a session the run no longer holds
  (`run.leased != session`).
- **`up`** resets `attempts` and clears a stale per-connection warning (`Counters.cleared()`). The `since` timestamp
  restarts; the counters object carries on.
- **`fail`** records the id in `unattendedFailures` when `run.unattended`. `stop` clears it.
- New public methods:
  - `failing()`: counts states that are `troubled()`.
  - `failedUnattended(UUID)`.
  - `vaultUnlocked()`: waiting runs rejoin, and their recoveries attempt now.
- `teardown` first calls `forget(run)`: it leaves `waiting` and its recovery, and a recovery left empty cancels its timer
  and attempt. `close()` cancels every recovery.
- Tests, rewriting `losingTheSessionFailsTheTunnelAndFreesThePort`. Fixtures: a fake scheduler that records delays, a
  `retry()` helper that runs pending tasks, `dropSessions()`, a `LoopbackServer.logins` counter, and a volatile
  `vaultLocked` and `password` behind the unattended source.
  - **Same port, never unbound.** The tunnel comes back on the same port, and a client that connects meanwhile is accepted
    and closed. Counters are the same object, and no question is asked.
  - **One login for two tunnels.** Two tunnels through one host make one attempt: logins go from 1 to 2, and there is one
    timer.
  - **Backoff, then a permanent failure.** Delays are 1, 2 and 4 s; then a wrong password gives Failed with no further
    timer, `failedUnattended` is true and the port closes.
  - **REMOTE asks for its port again.** A squatter socket on the server port makes the refusal show as "waiting for the
    server to release port P". Once the squatter is freed, the tunnel is Up on the same port. Bind the squatter in a retry
    loop while the old forward is released.
  - **Vault locked.** The tunnel goes to `WAITING_FOR_VAULT` with no timer; `vaultUnlocked` brings it Up.
  - **Stop while reconnecting** cancels the timer, and the port closes.

### Task 6 — `TunnelManager`: start at launch
- `launch(List<Tunnel>)` starts every `LAUNCH` tunnel with `start(tunnel, null, true)`, which leases through
  `leaseUnattended`. The public `start(tunnel, owner)` calls `start(tunnel, owner, false)`.
- In `leased`, an unattended run's failure goes like this: `waitsForVault` → waiting; `retryable` → join the recovery;
  otherwise fail. A busy port or a missing host at launch fails, and the failure counts as unattended.
- Tests:
  - A locked Vault at launch: `WAITING_FOR_VAULT`, then Up on unlock. The listener stays bound, and neither a question
    nor the interactive credential source is used.
  - An untrusted host fails with `UNTRUSTED_HOST`.
  - An unreachable host: Reconnecting, then Up.
  - A MANUAL tunnel is ignored by `launch`.
  - A manual start to a down host fails at once with no timer.

### Task 7 — `TunnelNotices` (`ui.tunnels`, pure apart from the handles)
- Constructor: `TunnelNotices(Function<ConnectionSpec, ConnectionHandle> announce, String activateActionId)`. Its main
  method is `update(List<Tunnel> tunnels, Function<UUID, TunnelState> states, Predicate<UUID> failedUnattended, Function<UUID, Optional<String>> hostNames)`.
- **Host cards.** There is one card per host, titled "Tunnels via <name>", or "Tunnels via a missing host".
  - Members are the tunnels that are Reconnecting or failed unattended, plus earlier members that have since come back (Up).
  - A member leaves the card when it is Stopped, Starting (a manual retry), failed by hand, waiting for the Vault, or
    deleted.
  - When nobody on the card is still reconnecting or failed, the card closes.
  - Any member failed: DOWN, with the detail "name: reason · x is back · y reconnecting".
  - Otherwise: DEGRADED, with the detail "names · reason · reconnecting (attempt k)". There is **no countdown**, because the
    card updates only on changes. Record this as a spec deviation.
- **Vault card.** "Tunnels waiting for the Vault", DEGRADED, with the detail "names · unlock the Vault to start them". It
  closes when nobody is waiting.
- Name each tunnel with `TunnelRows.title`. The activation action is `TunnelsUi.SHOW`.
- `post` calls `down` or `degraded` only when the state or detail changed. `close()` closes every handle.
- Test: a pure unit test with recording handles that walks every rule above.

### Task 8 — The editor's Start option
- `TunnelEditor` gets a Start row with two radio buttons: "Manually" and "When Jasper starts". The latter shows the hint
  "Starts without asking: start it by hand once to trust the host key and choose Always allow in the Vault." (set
  `html.disable` before the text).
- Preselect from the tunnel being edited. Save applies `.withStart(...)`. A name-only or start-only change does not restart
  a running tunnel, because `sameForwarding` ignores `start`.
- Duplicate keeps MANUAL, since two LAUNCH copies would fight for the port; document this.
- Tests: `TunnelEditorTest`.

### Task 9 — Wiring (Remote 0.11.0)
- **`TunnelsUi`:**
  - Add a `TunnelNotices`, built with `spec -> context.activities().connection(spec)` and the action `SHOW`. Call its
    `update(...)` in `refresh()` and close it in `close()`.
  - Use `manager.failing()` for the status item and Stop All.
  - Change `hostsChanged()` to `hostsChanged(boolean readable)`. Launch exactly once, when `hosts.toml` was readable and
    `tunnels.toml` parsed (`store.error().isEmpty()`), by calling `manager.launch(store.tunnels())`.
  - Add `vaultUnlocked()`, which forwards to the manager.
- **`RemotePlugin`:**
  - Add an unattended source, `id -> unattendedCredential(vault, id)`. It fails with `VaultLocked` when the lock state is
    LOCKED; otherwise it returns `vault.credentialIfAllowed(id)`, and when that throws `NoSuchMethodError` it returns empty.
    Pass the source to the 10-argument `Connections` constructor.
  - Call `tunnelsUi.hostsChanged(store.error().isEmpty())`.
  - On `LOCK_STATE_CHANGED` with UNLOCKED, call `tunnelsUi.vaultUnlocked()`.
  - Set `plugin.toml` to `version = "0.11.0"` and mention reconnect and start-at-launch in the description.
- **`FakeVault`:**
  - Add an `always` set of grants and a `credentialIfAllowed` method.
  - Add `setState(LockState)`, which publishes `LOCK_STATE_CHANGED`; the plugin id `dev.jasper.vault` owns the topic.
  - The `Started` helper in `TunnelsPluginTest` grants Always for its credential.
- **Tests in `TunnelsPluginTest`.** Use `FakePluginHost(dir.resolve("home"))` with pre-written data files.
  - A LAUNCH tunnel with a locked Vault waits (`WAITING_FOR_VAULT`).
    - The card "Tunnels waiting for the Vault" is DEGRADED, its activation action is SHOW, no window opens, and the status
      reads "1 tunnel · 1 failing".
    - After `setState(UNLOCKED)` the tunnel is Up and a round trip works; the card is CLOSED.
  - A dropped session gives a DEGRADED "Tunnels via prod" card. Running `scheduled` brings the tunnel Up, the card is
    CLOSED, and no window opens.
  - A manual start that fails posts no connection event.

### Task 10 — Docs and STATUS
- **`docs/remote.md` § Tunnels:**
  - Replace "In this release a tunnel that loses its connection stops; start it again." with the reconnect behaviour.
  - Cover the Start option and at-launch rules (no prompts; trust the host and grant Always first), the Vault wait, and
    the Buddy cards.
  - Change the toml comment `start = "manual"` to `# manual | launch`.
  - Native acceptance: Wi-Fi off and on, sleep and wake, a locked Vault at launch, and the cards with their activation.
- **`docs/credential-vault.md`:** mention `credentialIfAllowed` (Task 1).
- **STATUS entry**, with counts from the XML after `check` and `:jasper-app:test --rerun`.
- **Plan banner.**

## 7. Deviations and gaps to record (already decided)

- **Versions:** Vault 0.5.0, not the spec's 0.4.0; Remote 0.11.0.
- **Buddy cards:** the DEGRADED detail has no countdown ("reconnecting (attempt k)"), because cards update on changes only.
- **Row text:** the reason is shown after the reconnect text, which is needed for "waiting for the server to release port N".
- **Port 0:** a Reconnecting row shows the saved port, so a tunnel saved with port 0 reads "any port" while its listener
  is still bound.
- **SSH agent:** "SSH agent has no usable keys" also counts as transient, because 1Password's agent offers no keys while
  locked.
- **Vault without a vault file** (`NO_VAULT`): the unattended source returns empty, so the tunnel says `NEEDS_GRANT`.
- **Duplicate** keeps start MANUAL.

## 8. Open follow-ups to leave alone unless asked

These are in STATUS from 7b-2:
- Toggle Tunnels has no default binding (⌥⌘4 / Alt+4 needs the user's approval).
- The Tunnels panel's row operations are not yet Keymap panel actions.
- "Allow other computers to connect" does not follow a typed `*`, `0.0.0.0` or LAN address.
- HostStore has the same id-less and skipped-entry defects.
- The shared `ConfirmPanel` label lacks `html.disable`.
- Half-close through a remote tunnel is untested against OpenSSH.
