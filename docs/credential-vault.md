# Credential Vault

Credential Vault is bundled with Jasper. Press **Cmd+Shift+\\** (Ctrl+Alt+Shift+\\ on Windows and Linux), click the vault button in the rail, or
choose **Open Vault...** in the command palette. First use creates a vault with a master password
and an optional device binding. Later use unlocks the vault and opens its manager window. Creating and unlocking
are modal overlays inside the window that asked: it dims, and the prompt fades up over it (instantly with animations
off). While the manager is open, it unlocks in place.

The manager uses a single list of logins, SSH keys and secure notes. Search names, usernames or
fingerprints and narrow the list with **All Types**. Each row shows its name, secondary details
and type. Double-click a row or press Enter to edit it. The icon actions add, edit, delete, copy a
public key and lock the vault; each has a tooltip and accessible name.

The **+** menu adds a login, imports an SSH key, adds a secure note, or generates a new SSH key. The palette's
**Add Login...**, **Add SSH Key...** and **Add Secure Note...** open the manager on the same sheets, creating or unlocking the vault first.
**File → Vault Plugin Grants...** (or the palette) opens the manager on the saved plugin grants, and **File → Change
Vault Master Password...** asks for a new master password over it; both unlock first. Grant revocation has a **Back to
entries** button. **Cancel** and **Okay** both close the manager; changes made in
individual editors are saved immediately and are not rolled back by closing the manager.
The footer retains device-secret source, auto-lock setting and vault path, with full text in its
tooltip when the window is narrow.

Add a login with a password, a key path and optional passphrase, or both. Import an existing SSH
key by choosing its private and public paths; its public key supplies the algorithm and SHA-256
fingerprint. Every path field has a **Browse...** button that opens the native file chooser; you
can still type or paste a path. Cancelling keeps the current value. Secure note contents appear
only in their editor and are cleared when that form closes.

**Generate SSH Key...** in the + menu supports Ed25519, ECDSA P-256/P-384 and RSA 3072/4096. Generated files live in
`plugins/dev.jasper.vault/data/keys/` unless `keys_directory` selects another directory.
The generator can also create a login account for a supplied username. Generated private keys
are encrypted when you enter and confirm a passphrase. Leave both fields empty to create an
unencrypted key. If you also create a login account, its passphrase is saved inside the encrypted
vault. For a standalone key, keep the passphrase yourself; the key entry stores only paths and
metadata. Key files remain separate, and locking the vault does not change their encryption. **Copy public key** copies the selected public key. Deleting a key
entry offers a separate choice to delete its two files; the default keeps them.

**Change Vault Master Password...** requires the old password and matching new passwords. Cancel or close
during password derivation to abandon the change. Once writing starts, the button becomes
**Close** and the form says closing will not cancel. A failed write keeps the old password and
can be retried; failures are reported even if the form was closed. **Lock** removes
secrets from the open vault and clears its editor forms. The window stays open with an Unlock button that shows the shared unlock form in place.
The padlock in the status bar unlocks/opens a locked vault and locks an unlocked vault; its tooltip
shows the remaining inactivity time. Already connected SSH sessions are unaffected by locking.

**Search Vault Logins** (Cmd+\\, or Ctrl+Shift+\\ on Windows and Linux) opens the command palette on the Vault scope;
`vault >` in the palette does the same. It lists logins, searched by name or username; SSH keys stay in the manager.
Enter copies a login's password, Cmd/Ctrl+Enter copies its username, and Shift+Enter opens the selected entry in the
manager. A card confirms each copy, here and from the manager's copy buttons (username, password, secure note and
public key), naming the entry; turn these off in the **Vault copies** group under **Settings › Notifications › Groups**. While the vault is locked the scope shows one row, whose Enter unlocks the vault and returns
to the search; with no vault yet, Enter creates one in the manager. Both password and username
copies are secret copies to Jasper's clipboard (SDK `Platform.copySecret`): Jasper clears them after 30 seconds (retrying
briefly while another program holds the clipboard), and tries once when the vault plugin stops and once when Jasper
quits, in each case only if the clipboard still contains that copy. A later copy, by the user or a plugin, is left
alone. The vault never types clipboard contents into a
terminal.

Other plugins request credentials through `dev.jasper.vault.api.VaultApi`. The first use asks
whether to allow the named plugin once, always for that credential, or deny access. Choosing a
credential through the picker grants one use. Saved grants appear under **File → Vault Plugin Grants...**;
**Revoke** removes one. Deleting an entry removes all its saved grants. Notes are not exposed
through the consumer API.

The encrypted file is `plugins/dev.jasper.vault/data/vault.jv`. A device-bound vault uses the
platform keychain tool, with a file fallback shown in the manager's status line. An unbound vault
uses the master password alone. The master password cannot be recovered. Change the live plugin
settings in Settings › Tools › Credential Vault, or in the file through the Plugins manager's **Open Settings** action:

```toml
auto_lock_minutes = 15            # 0 to 1440; 0 disables inactivity locking
keys_directory = ""              # empty uses this plugin's data/keys directory
bind_new_vaults_to_device = true  # initial state of the create checkbox
```

The nine actions are `dev.jasper.vault.search` (Cmd+\\), `dev.jasper.vault.open` (Cmd+Shift+\\), `dev.jasper.vault.lock`
(Cmd+Shift+L), `dev.jasper.vault.generate_key`, `dev.jasper.vault.change_password`, `dev.jasper.vault.grants`,
`dev.jasper.vault.add_login`, `dev.jasper.vault.add_key` and `dev.jasper.vault.add_note`. Configure their shortcuts like
other contributed actions.

## Native acceptance

Headless tests cover the forms, persistence failures, lock transitions, service requests and
clipboard timing. The following checks need a user-run Jasper window:

1. Press Cmd+Shift+\\ and create a throwaway vault. Verify native keychain access, then lock and unlock it.
2. Add/edit a login and a multiline note; generate/import an SSH key and copy its public key.
   Generate a passphrase-protected key, confirm the optional login retains its passphrase, and
   Use Browse beside both SSH key paths and the login key path; verify the chooser opens above
   the editor, starts from its current path, and cancellation keeps the value. Then
   verify the key works with your SSH client. Leave both passphrase fields empty to test that path.
3. Press Cmd+Shift+\\ repeatedly; verify only one manager window opens. Lock with an editor open; verify
   its secret fields disappear and the manager offers inline unlock.
4. Lock the vault, press Cmd+\\ and press Enter on the locked row; after unlocking, the palette is back on the Vault
   scope. Copy a password and username, check each confirmation card, check 30-second clearing, then copy
   ordinary text before expiry and verify it survives.
5. Set `auto_lock_minutes = 1`, leave Jasper inactive and verify the padlock locks. Set it to 0
   and confirm the countdown disappears. Restore the desired timeout.
6. With a consumer plugin, exercise Allow once, Always and Deny; revoke its saved grant in the
   manager and verify the next fetch asks again.


7. Change the master password on the throwaway vault. Verify Cancel during derivation keeps the
   old password; verify a completed change unlocks with the new password after locking.

8. Search and filter the manager; verify two-line rows, empty results, double-click/Enter editing,
   and palette navigation revealing a row hidden by a prior filter. Open each + action and both Vault File-menu commands.
   Compare buttons, inputs and list colors in Slate Cobalt Light and Dark.
