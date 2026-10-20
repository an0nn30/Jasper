# SDK 0.16.1: clipboard and Tools menu — design

Date: 2026-10-13. Status: approved in conversation; awaiting review of this written spec.
Branch: `claude/sdk-clipboard` (worktree `.claude/worktrees/sdk-clipboard`), from main `bfc0ba6d`.

Amended on 2026-10-15 by the [Enterprise presentation design](2026-10-15-jasper-enterprise-presentation-design.md): Remote's entries live in the Session and Transfer standard menus; everything else in this document stands.

## Why

Porting an external utility plugin (an encrypt/decrypt dialog) to the SDK showed two gaps the 0.16 kit does not
cover:

- **No clipboard access.** Five bundled plugins and the application each call the AWT clipboard directly, with
  different or no failure handling: FileServer and Indexer swallow failures, Remote lets them escape, Vault keeps its
  own timed-clearing `SecretClipboard`, and the application wraps its own "Copy report". An exception from an AWT
  clipboard call inside a plugin's handler reaches the user as nothing copied and no explanation.
- **No home for utility commands.** `StandardMenu` offers File, Edit, View, Pane and Tab; a plugin whose command is a
  tool must create a top-level menu of its own.

This is the first of three pieces agreed with the user: (1) this spec; (2) publishing the SDK and testkit with a
`dev.jasper.plugin` Gradle convention for external plugin builds; (3) moving the external plugin to SDK 0.16.1. Each
gets its own spec or design.

## Scope

In: the clipboard methods on `Platform`, the `clipboard.read` capability, the host clipboard service, the testkit fake
and contract cases, moving FileServer, Indexer, Remote and Vault onto them, `StandardMenu.TOOLS`, renaming View ›
Tools to View › Tool Windows, the Sample plugin's demo placement, documentation and SDK version 0.16.1.

Out: the Editor plugin's clipboard (custom EDITOR content with Vim registers and the Linux selection clipboard, kept
as is); a kit-level declarative copy button; clipboard formats other than plain text; a user setting for the secret
clear delay; publishing and the Gradle convention (piece 2).

## Clipboard

### SDK

`dev.jasper.sdk.ui.Platform` gains three abstract methods (Jasper is an MVP: no default methods for older hosts):

```java
/** Puts text on the system clipboard. UI thread. A failure is posted in your plugin's notification group.
 *  @return whether the text is on the clipboard */
boolean copy(String text);

/** As copy, for text that should not linger, such as a password or a decrypted value: Jasper clears the clipboard
 *  30 seconds later, when your plugin stops and when Jasper quits, if the clipboard still holds this text.
 *  @return whether the text is on the clipboard */
boolean copySecret(String text);

/** The clipboard's text. Needs clipboard.read. UI thread.
 *  @return the text, or empty when the clipboard holds no text or cannot be read */
Optional<String> clipboardText();
```

- `null` text throws `NullPointerException`; empty text is allowed and copies an empty string.
- Calls off the UI thread throw `IllegalStateException`, as the rest of the plugin UI surface expects.
- `dev.jasper.sdk.Capabilities` gains `CLIPBOARD_READ = "clipboard.read"`, included in `ALL`.
  `clipboardText()` without it throws `MissingCapabilityException`. Writing needs no capability.

### Host

One application-wide host clipboard service in `jasper-app` backs every plugin's `Platform` and the application's own
"Copy report" (`JasperApplication.copyReport`), replacing that direct AWT call.

- **Write.** Sets a `StringSelection`. `IllegalStateException` (the clipboard is held by another program) or
  `HeadlessException` returns `false` and posts an error notification titled "Could not copy" with the cause in the
  calling plugin's default group (the application's own copy keeps its current "Could not copy the report"
  notification). Nothing reaches the plugin as an exception.
- **Secret.** After a successful `copySecret`, the service remembers that text as the one pending secret, with its
  owning plugin, and arms a 30-second timer. Any later copy through the service, by any plugin or the application,
  replaces or ends the pending secret (a later plain copy ends it). When the timer fires, when the owning plugin stops,
  or when Jasper quits, the service clears the clipboard (writes an empty string) only if the clipboard's text still
  equals the pending secret; otherwise it leaves the clipboard alone. The delay is 30 seconds, Vault's current value.
- **Read.** `clipboardText()` checks `clipboard.read` through the plugin's capability gate and audits the read as
  terminal selection reads are audited: plugin id and character count, never the text. Unreadable or non-text content
  returns empty.
- **Consent.** `DescriptorParser.CAPABILITIES` accepts `clipboard.read`; `pluginmanager.Capabilities.describe` shows
  "Read text you copied to the clipboard (clipboard.read)".

### Moving the bundled plugins

| Plugin | Today | After |
|---|---|---|
| FileServer | `FileServerPlugin.copy` (swallows failures); `FileServerPlugin.pasted` for the server editor's paste-a-key source | `platform().copy`; `platform().clipboardText()`, manifest declares `clipboard.read` |
| Indexer | `IndexerPlugin.copy` | `platform().copy` |
| Remote | `SftpUi` and `TunnelsUi` copy lambdas | `platform().copy` |
| Vault | `ui/SecretClipboard` (copy, copySecret, 30-second clear, clear on close) | `platform().copySecret` and `platform().copy`; `SecretClipboard` and its tests are deleted |
| Editor | `ui/SystemClipboard` | unchanged (out of scope) |

Each moved plugin declares `sdk = ">=0.16.1, <0.17"`. Their existing clipboard behaviour is kept, except that copy
failures, which FileServer and Indexer swallowed and Remote let escape, now produce the host's notification.

## Tools menu

- `StandardMenu.TOOLS` joins FILE, EDIT, VIEW, PANE and TAB; `MenuTarget.Slot.TOOLS` mirrors it. A plugin adds to it
  like any standard menu: `context.menus().standard(StandardMenu.TOOLS).add(actionId)`. Each plugin's entries form
  their own section, separated from others, in plugin load order.
- `WindowChrome` places a **Tools** menu after Tab and before plugins' own top-level menus. Jasper contributes nothing
  to it, so the menu is added to the menu bar when the first plugin entry appears and removed when the last goes
  (not hidden: hidden menus in the macOS screen menu bar are unreliable). The in-window bar and the native macOS bar
  behave the same.
- The View submenu that lists tool windows' toggle actions (`Contributions.rebuildPanelsMenu`) is renamed from
  **Tools** to **Tool Windows**, as in IntelliJ, so two menus are not both called Tools.
- The Sample plugin moves its demo action's standard placement from View to Tools (its top-level Sample menu and
  terminal context entry stay), so a development run shows the menu; the authoring guide's compiled example follows.
- Remote's SSH menu is unchanged: it is a feature menu, not a tool.

## Version

`JasperSdk.VERSION` becomes `0.16.1` (additive changes take a patch, as 0.15.3 to 0.15.5 did). Plugins that keep
`>=0.16.0, <0.17` load unchanged.

## Testkit

- `FakePluginHost.clipboard()` returns a `FakeClipboard` shared by every fake plugin's `Platform`:
  `text()` (what the clipboard holds), `setText(String)` (as if the user copied), `copies()` (lines
  `copy|<plugin id>|<text>` and `secret|<plugin id>|<text>`), `failWrites(boolean)` (a held clipboard: copies return
  false and post the host's notification), and `expireSecrets()` (runs the 30-second clear now). Stopping a plugin
  clears its pending secret as the application does.
- `FakeNotifications` records the failure notification the same way it records others.

## Testing

Test-first throughout.

- **Contract suite** (`jasper-sdk-testkit` test fixtures, run against the application host and the fake): copy then
  read with the capability; read without it throws; a secret is cleared on expiry only if unchanged; a later copy
  prevents the clear; a held clipboard returns false and notifies; stopping the owning plugin clears its secret;
  off-UI-thread calls throw.
- **Application:** the host service against a private `java.awt.datatransfer.Clipboard` (headless) with an injected
  timer, including quit clearing and the application's own report copy; consent text for `clipboard.read`;
  `DescriptorParser` accepting it; Tools menu absent with no entries, present after Tab with one, gone after the last
  is removed; View › Tool Windows title.
- **Moved plugins:** their clipboard tests assert on `FakeClipboard`; FileServer's paste-a-key source with and without
  clipboard text; Vault's palette copy of a secret is a `secret|…` copy and is cleared by `expireSecrets()`.
- **Documentation:** the compiled authoring examples (including the Sample's Tools placement) build and pass.

`./gradlew check` in the worktree is the gate (architecture, Javadoc doclint, documentation examples). No GUI launch.

## Documentation

- `docs/plugin-authoring.md`: a Clipboard section (the three methods, UI thread, failures, secret clearing,
  `clipboard.read`); Tools among the placements; a `clipboard.read` row in the capability table; View › Tool Windows
  where it says View → Tools; `host.clipboard()` under Testing; the manifest example's capability list.
- `jasper-sdk/README.md`: an SDK 0.16.1 section.
- `docs/sdk-architecture.md`: View › Tool Windows where it says View → Tools.
- `docs/credential-vault.md`: the 30-second clearing is done by Jasper's clipboard.
- `docs/STATUS.md`: an entry for this work.

## Left to the user

Native checks after `./gradlew check` passes: a development run shows **Tools › Run Sample Activity** after Tab, and
**View › Tool Windows** lists the tool windows; Vault's copied secret is cleared after 30 seconds and survives a later
copy of other text; FileServer's copy buttons and paste-a-key still work; macOS native menu bar shows Tools only while
a plugin has entries.
