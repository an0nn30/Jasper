# Connection progress overlay

**Approved:** 2026-09-22, including the explicit Cancel button. Native execution requested.

## User intent

Replace Remote's separate connection-progress window with an overlay inside the owning Jasper
window. The progress card stays at the exact center of that window while connecting, including
when the window is resized or moved. It has no title bar, native close button, dragging or resizing.
It cannot be dismissed by clicking outside it or pressing Escape. This applies to tab, split and
reconnect attempts. Tabs/splits still open only after the SSH shell is ready.

An explicit Cancel button cancels the attempt
and removes the overlay. Failure replaces progress with Retry and Close. Success removes the overlay.
Closing the owner or stopping Remote cancels the attempt and releases its resources.

## Host-owned surface

The SDK currently provides native dialogs and windows only. Add a small overlay factory to the
existing `Windows` facade, returning the existing `PluginDialog` lifetime/content handle. Both the
application and SDK testkit implement it; Remote requires the updated SDK version. Existing dialog
and window behavior remains intact.

The application hosts the content in the owning terminal window's layered pane, as it does for the
command palette. Unlike the palette's upper anchor, this card is centered both horizontally and
vertically. A dimmed backdrop blocks input to the underlying terminal. Layout is recomputed on
resize and content changes, and constrained to the available area. There is no extra native window.

The host owns overlay focus, input containment, cleanup and focus restoration. At most one progress
overlay is visible in a window; a second request must not obscure or orphan an existing attempt.
Required native Vault and host-key prompts remain usable above the progress overlay. Owner close,
plugin shutdown and successful handoff all remove listeners/input capture as well as the card.

## Remote integration

Keep `ConnectAttempt` as the connection/cancellation owner and `ConnectionPanel` as passive Swing
content. Replace the native progress-dialog request with the new overlay request. Keep bounded
connection/authentication timeouts, retry semantics, prepared-shell ownership and existing-pane
reconnect behavior. Group editing and other dialogs are unchanged.

## Verification

Headless tests cover exact centering and recentering, small-window bounds, background input
containment, no Escape/outside dismissal, explicit cancellation when enabled, focus restoration,
owner/plugin cleanup, and repeated open/close. SDK app/testkit coverage verifies the new factory and
ownership constraints. Remote regressions verify overlay use, no early tab, failure/retry, cancellation
and reconnect. Inspect a headless render, then run `./gradlew check :jasper-app:installDist`.
Native GUI checks remain user-run under AGENTS.md.
