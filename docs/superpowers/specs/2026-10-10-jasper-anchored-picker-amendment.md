# Anchored credential picker amendment — 2026-10-10

Controller-approved amendment under the user's continuous theme-engine/mock-completion authorization. Derived from actual existing ownership and the supplied combo_box_mock.png. Task1 implementation is independently approved through83d2435c; picker implementation and native acceptance remain outstanding. This supplements the [theme-engine design](2026-10-04-jasper-theme-engine-design.md) and splits Plan3 Task2 into two runnable deliverables.

## Required visible result

The real credential selection flow renders the board's empty, selected, open, filtering, no-match and disabled states through shared kit components. COMPACT height22 logical applies to the actual identity-free ImportPanel per-candidate credential field; DEFAULT28 applies to Add Host. Captions use actual credential kind/name/username/fingerprint metadata. Do not add fictional Home Lab/Work storage groups. Board annotations are not UI. No resized candidate or separate gallery-only picker establishes compliance.

## Ownership and contracts

1. Add an opaque host-issued UiAnchor from an eligible, live, mounted control. It delegates only attachment authority, not component access, terminal identity or secret access. Validate source host/kit/view/node/mount generation. Same-id theme updates retain it; hide, disable, remove, retype, native reparent, view disposal or plugin stop invalidate it. Coverage by its owned child dialog alone does not invalidate it.
2. Add a nonblocking PluginPopover on Windows. Its content must belong to the calling producer kit; foreign anchor capability is not permission to mount another kit's view. Host owns native geometry/fitting/focus/lifetime; jasper-ui owns component paint and menu interaction. SDK remains JDK-only, with app and testkit implementations. Fabricated/cross-host/stale capabilities fail before mounting.
3. Permit validated UiAnchor as a native child DialogSpec owner so Vault unlock/creation belongs to the actual modal consumer. Do not expose foreign PluginDialog instances or impersonate a terminal. Owner loss closes the presentation; already shared unlock keeps its owner and existing waiter semantics. A newly anchored unlock must not divert to a manager's inline unlock.
4. Extend VaultApi additively with current selection and allow-clear request data plus Chosen/Cleared/Cancelled results. Old modal pick(WindowHandle) remains compatible. Only actual Vault-owned live selection invokes the existing consumer-bound one-time grant. Metadata enumeration, clear, cancellation, and creation alone cannot grant. Settle selection before presentation close to prevent reentrant cancellation. No grant-by-UUID public shortcut.
5. Add reusable rich PICKER face/density and shared menu anatomy where existing descriptors cannot express the reference. Preserve old constructors and module/SDK/host/fake parity. Current-value check and keyboard-active row are distinct. Search, IME, Enter, Escape-first dismissal, focus restoration, query/caret/selection/scroll survive theme changes. Clear is an explicit action; outside dismissal cancels.
6. Extract only the Vault-private creation presenter needed by the real footer from existing EntryEditor/manager plumbing. Login/path-key creation retains existing storage behavior and native ownership; path-key is not managed-key import. The footer must work now, returning to the pending picker/query after save or cancel. Saving alone does not pick or grant. Task4 later finishes sheet appearance and rechecks both consumers.

## Consumer lifetimes

Task2a uses ImportPanel's existing controller-owned Map<UUID,CredentialDescriptor>. The field projects that selection; no second authoritative map or fake last-selected append state. Preserve identity-free eligibility, explicit Update, Enter-no-import, graph/conflict checks and separate managed import REVIEW/durable grant transaction.

The nonblocking route requires narrow operation-token repairs in ConfigImportController. Install PICK/IMPORT identity, source panel/revision/candidate and cancellation before a potentially reentrant Vault call. Only matching completion clears pending. Replacing a PICK cancels its predecessor; IMPORT/save rejects replacement. Refresh cancels PICK, invalidates old callbacks and preview, clears choice projections, and leaves old rows inert until a successful new preview; reload failure exposes retry/cancel without reactivating stale rows. Late completion cannot select, show an error, clear a new operation or grant through a cancelled request. Close invalidates before cancellation. Preserve current durable commit semantics.

Task2b consumes these contracts in HostEditor/RemotePlugin. Auth switch, replacement and close cancel the pending request; hidden draft values remain available when switching back, and submission uses only active auth. CHOOSE uses rich DEFAULT28; ADD_KEY remains managed-key append with ordered selection/reordering. Clearing a single credential does not delete ordered keys.

## Runnable task split

- Task2a: complete SDK/host/fake anchor/popover, shared face/menu, Vault producer/unlock/real creation, compact ImportPanel consumer, lifetime fixes, meaningful contracts and actual staged integration. It must run fully without Task2b or Task4.
- Task2b: grouped Add Host Connection/Authentication/Options, default picker integration, ordered keys and lifetime handling. Reuse Task2a rendering/producer.
- Task4: Vault manager, sheets and canonical legacy modal picker appearance; rerun compact/default picker→creation→picker with final sheets.

## Required evidence

Cover spoof/stale/cross-host/hidden/disabled/reparent capabilities, owned-child behavior, close/stop, replacement and arbitrary-thread/reentrant completions; one-time first fetch then normal second-fetch consent, other-consumer isolation, and no grants from clear/cancel/create. Preserve real import REVIEW and durable authorization separately. Actual mounted owners provide all six compact states, long/enlarged/short-view/theme cases, then controller captures real windows/popovers and compares exact-scale crops. Disabled comes from actual review/completion, not a fixture-only setter. Recompare default Add Host after shared changes.

Only synthetic supplied sshDir/config, explicit User, .invalid HostName and no IdentityFile. Never read, copy or symlink the user's ~/.ssh/config. Native launches must additionally isolate JVM user.home or use a verified existing owner injection before Import; jasper.home alone is insufficient. No host connections are needed.

Plan deviation: Task2 expands into two runnable tasks because the reference's popup anatomy crosses the existing Vault producer/Remote consumer boundary. This deliberately adds a narrow generic attachment capability instead of weakening view ownership or replacing Vault consent with consumer metadata selection. Cost if wrong: extra SDK/host/fake lifecycle/API surface and import-controller changes; tests and per-task review must validate that boundary before subsequent consumers depend on it.
