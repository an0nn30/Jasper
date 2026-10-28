# Linux tray popup correction proposal — 2026-10-28

**Status: NOT APPROVED; NOT IMPLEMENTED.** Task 8's ownerless `JDialog(Window.Type.POPUP)` fails repeated focus on isolated Fedora 44 aarch64, JBR b635.75, LXDE/Openbox with no workspace window. First Return selection succeeds; the next popup maps without focus. Native logs show focus requests using AWT's prior user timestamp while tray input arrives on the independent XCB connection. Public `requestFocus`, root-pane focus after opened and `toFront` do not repair it. The pinned peer's public focus paths converge on cached user time.

A managed `Window.Type.UTILITY` characterization restores repeated focus/actions but visibly adds a **Jasper tray menu** taskbar button. It does not satisfy the current contract.

The candidate correction retains one transient, undecorated, managed ownerless JDialog and the shared JPopupMenu, adding explicit `_NET_WM_STATE_SKIP_TASKBAR` and `_NET_WM_STATE_SKIP_PAGER` hints to **that popup's own** native window through the existing owned XCB connection. It would narrowly amend the prohibition on touching JBR-created native windows; application/workspace windows, JBR's display/event queue and native focus remain outside this boundary. Approval is required before implementation or probing.

Pinned JNA 5.14.0 has public `Native.getWindowID(Window)`. Its source requires a heavyweight, displayable window and uses its existing native window-handle function. This supports obtaining a packed dialog's XID before map; Linux/JBR runtime behavior remains unproven. No new dependency or private JDK access is proposed.

EDT would create/pack the dialog and capture its XID plus popup/host/controller epoch. A bounded worker operation would preserve existing state atoms, add the two hints and confirm a reply barrier before EDT show admission. Keep the peer alive until every admitted native operation completes or its connection termination is confirmed, including after cancellation: destroying/recreating it earlier could reuse its XID for another window. Invalidation immediately drops actions/show; failure never shows. EDT never waits, and disposal occurs once after completion. No accumulating windows/commands or permanent owner is permitted. Verify hints survive JBR's map-time writes.

Before routing, repeat the real popup gate with LXDE/XFCE tray and taskbar plugins, Openbox/xfwm4 as applicable, and JBR scales1/2: reopen focus, all four exactly-once actions, vetoed Quit reopen, keyboard traversal, Escape/outside/focus loss, host loss, queued-show close, edge placement, one displayable dialog, actual no-taskbar/no-pager screenshots and native state, baseline listeners/menu path, bounded cleanup/JVM exit. Physical mixed-monitor acceptance remains pending.

Keeping pure Swing with the managed dialog would require accepting its visible taskbar entry; that user-visible concession is not recommended. A JBR fix for the POPUP timestamp path is another option, but none was demonstrated.
