# Moray credential vault — product design

> **Provenance (2026-09-14):** copied from `codex/rail-vault-design` where it was written under the Moray name and approved on 2026-09-13. Prose is unchanged; "Moray" means Jasper. The [2026-09-14 credential vault design](2026-09-14-jasper-credential-vault-design.md) records which parts are superseded (rail/toolbar entry points, FlatLaf styling, host associations).

**Date:** 2026-09-13
**Status:** Conversation decisions approved; consolidated product spec awaiting review. Implementation planning follows separately.

## Scope and delivery order

The user requested a window-level rail for SSH, SFTP and Tunnels, based on their supplied mock, and confirmed that the first SSH delivery must make real connections. They then made a credential vault a prerequisite. Delivery order is rail and panel layout, credential vault, then SSH hosts and working connections. SFTP, tunnels and sharing follow separately. This user-requested sequence supersedes the earlier requirement to wait for the Phase 1 daily-use gate before starting SSH planning; it does not declare that gate passed.

Keep the existing terminal renderer, tabs, split ownership, Swing/FlatLaf, Java 25 on JBR, and module dependency rules. No plugin framework is needed. Each runnable deliverable gets its own implementation plan. The rail's detailed behavior and SSH connection architecture are separate designs; this document records the vault's product contract.

## Manager window

Use a dedicated non-modal credential manager, allowing terminals to remain usable. One vault is shared across Moray windows. The rail's key icon and toolbar vault-status control open the same manager rather than creating duplicate managers.

The user's supplied [manager and dialog mocks](../../design/credential-manager/README.md) are the visual authority and supersede the earlier proposed list-left/editor-right layout. Preserve the dedicated non-modal manager and agreed vault behavior.

- **Window and toolbar:** centered “Credential manager” title with native window controls. Toolbar has Search credentials, Add credential and Import key on the left; a settings gear and Lock vault on the right.
- **Navigation sidebar:** VAULT contains All credentials, Logins and SSH keys with counts. MANAGEMENT contains Settings and Import key. Preserve the separate sidebar and toolbar entry points; they invoke shared actions.
- **Main content:** a credential table above a lower details/editor area. Table columns are Name, Username, Authentication and Used by. Use outline type icons, a clear selected-row background, thin row separators and a vertical scrollbar.
- **Lower login editor:** selected name and icon at top left, Login type at top right. Name, Username, SSH key selector and Password fields occupy the left portion. Password has reveal/copy controls; show “Not set” when absent. Show key algorithm and login reuse count below. Associated hosts occupy the right portion. Revert and Save sit at the lower right; editing uses explicit save, not per-keystroke persistence.
- **Status strip:** credential count and current auto-lock setting on the left; remembered device access expiration on the right. Render actual state, including disabled auto-lock or absent/expired device access, rather than the screenshot's example values.

The Add credential dialog is owned by the manager. Follow the supplied dialog layout: title and close control; aligned labels and fields for Login name, Username, SSH key (initially None), and Password; Import SSH key… at bottom left; Cancel and Add login at bottom right. Focus Login name initially. Both supplied dialog images depict the same form and are retained as references. Cancel/close dismisses without creating the login. Import opens the key-import flow and makes the imported key available to the selector. Password entry is masked.

Match the reference's proportions, spacing, typography hierarchy, outline icons, neutral dark surfaces, borders and selected states using Swing/FlatLaf and UI scaling. Keep purple, classic dark and light theme support. Native traffic lights are window decoration, not painted mock controls. Screenshot pixels are a visual reference, not an assumed logical-pixel scale.

The sample credential names, usernames, host associations, counts, key algorithm and September 20 expiration are demonstration data, not seed records or fixed defaults. “Shared by 3 logins” describes key reuse inside this vault, not the deferred sharing feature. A locked manager clears credential details and presents the agreed unlock screen. The screenshots do not specify unlock, settings, SSH-key details or key-import screens; their design must retain the established product behavior without treating these images as specifications for unseen screens.

## Credentials and imported keys

Logins and SSH keys are separate entries. A login contains a username and password and/or a reference to a stored SSH key. Multiple logins may reuse one key without duplicating it. A host can select a login, or select a key and supply its own username.

Importing a private key creates a vault-owned copy inside the encrypted vault. SSH uses that copy; moving or removing the original file must not break the imported credential. Leave the source file untouched. Key entries carry stable IDs, names, fingerprints and public keys. Show the fingerprint during import and detect duplicate keys. Hosts and logins reference stable IDs, so renaming preserves associations.

This ownership model supports later sharing. No sharing UI, synchronization, export protocol or access-control scheme is included in this delivery.

## Unlock and remembered device access

Creating the vault establishes a master password. Moray starts with the vault locked, including when remembered device access is valid. No automatic unlock occurs at startup or on opening the manager.

The Unlock button uses valid remembered device access without asking for the master password. If remembered access is absent or expired, it requests the master password. Store remembered unlock material in the operating system's credential store, not in ordinary settings. The exact platform integration requires implementation design; TermLab's IntelliJ PasswordSafe integration cannot be copied directly into Moray.

Remember on this device is optional, defaults to a seven-day duration when enabled, and has a configurable expiration. Its expiration is fixed from the master-password unlock that establishes or renews remembered access. Using remembered access or interacting with Moray does not extend that deadline. Display its expiration on the unlock screen. Reaching the deadline makes remembered access unusable; it is distinct from the inactivity policy for a currently unlocked vault.

## Locking behavior

| Trigger | Vault state | Remembered access | Existing SSH sessions |
|---|---|---|---|
| Application startup | Locked | Retained if unexpired | No session restoration promised |
| Unlock with valid remembered access | Unlocked | Original deadline retained | Unchanged |
| Unlock without valid remembered access | Master password required | Optional enrollment after password unlock | Unchanged |
| Inactivity timeout | Locked | Retained if unexpired | Remain connected |
| Explicit Lock vault | Locked | Cleared; next unlock requires master password | Remain connected |

Automatic locking defaults to 15 minutes without user interaction in Moray. It is configurable and can be disabled. Activity in any Moray window counts; terminal output and background work do not reset the timer. Locking clears displayed credential details and prevents new vault credential use. It does not terminate established SSH sessions.

Connecting to a host while the vault is locked presents the unlock flow and resumes the pending connection after successful unlock. Cancelling unlock cancels that connection attempt. The host editor offers a credential picker with New credential access.

## Reuse boundary

The user supplied `/Users/dustin/projects/TermLab/plugins/vault` as an internal implementation reference. Its models, persistence, crypto, lock states and tests inform the technical design. Its dialogs, services and credential-provider wiring depend on IntelliJ and must be adapted to Moray's ownership and threading. Its current key model stores file paths; that behavior is explicitly superseded by the user's vault-owned encrypted import requirement.

Reference implementation comments are evidence about existing behavior, not additional user requirements. Review the reusable implementation before selecting algorithms, file format, platform credential-store adapters and failure policies. These are technical-design work, not claims that reuse is already verified. Do not access or migrate real user credentials during development.

## Verification required by the implementation plan

Use temporary vault files, synthetic credentials and controlled clocks to verify encrypted import remains usable after removing the source fixture; stable references survive rename; duplicate imports are detected; incorrect passwords and damaged files preserve existing data; expired or missing device access falls back to password; remembered unlock never slides expiration; startup remains locked; and explicit lock clears device access while auto-lock retains it.

Exercise inactivity across windows, background-output exclusion, locked UI clearing, cancelled unlock, resumed connection, and preservation of established sessions. Test unavailable OS credential stores without silently persisting unlock material elsewhere. Verify theme, keyboard and accessible control behavior with headless UI tests and rendered previews. Native OS credential-store and window checks are explicit user-run acceptance items. Never launch the GUI or benchmark unattended.

## Next design work

After review of this consolidated product contract, specify the vault's technical design and first runnable implementation plan, including supported credential-store platforms, encrypted key import formats, atomic persistence, credential deletion with references, and asynchronous cancellation/locking races. These details must be resolved before implementation. Rail and SSH plans follow their own concrete integration contracts; no implementation plan is represented as complete here.
