# Credential Vault

Credential Vault is bundled with Jasper. Press **F8**, click the vault button in the rail, or
choose **Open Vault...** in the command palette. First use creates a vault with a master password
and an optional device binding. Later use unlocks the vault and opens its manager window.

The manager uses a single list of logins, SSH keys and secure notes. Search names, usernames or
fingerprints and narrow the list with **All Types**. Each row shows its name, secondary details
and type. Double-click a row or press Enter to edit it. The icon actions add, edit, delete, copy a
public key and lock the vault; each has a tooltip and accessible name.

The **+** menu adds a login, imports an SSH key, adds a secure note, or generates a new SSH key.
The **More (...)** menu opens saved plugin grants or changes the master password. Grant revocation
has a **Back to entries** button. **Cancel** and **Okay** both close the manager; changes made in
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

**Change Master Password...** in the More menu requires the old password and matching new passwords. Cancel or close
during password derivation to abandon the change. Once writing starts, the button becomes
**Close** and the form says closing will not cancel. A failed write keeps the old password and
can be retried; failures are reported even if the form was closed. **Lock** removes
secrets from the open vault and clears its editor forms. The window stays open with an Unlock button that shows the shared unlock form in place.
The padlock in the status bar unlocks/opens a locked vault and locks an unlocked vault; its tooltip
shows the remaining inactivity time. Already connected SSH sessions are unaffected by locking.

In the command palette, select **Vault**, or enter `>vault` / `>cred`. Search by entry name,
username or key fingerprint. Enter copies an account's password, Cmd/Ctrl+Enter copies its
username, and Shift+Enter opens the selected entry in the manager. Both password and username
copies are cleared after 30 seconds if the clipboard still contains that copy. A later user copy
is left alone. The vault never types clipboard contents into a terminal.

Other plugins request credentials through `dev.jasper.vault.api.VaultApi`. The first use asks
whether to allow the named plugin once, always for that credential, or deny access. Choosing a
credential through the picker grants one use. Saved grants appear under **More (...) → Saved plugin grants...**;
**Revoke** removes one. Deleting an entry removes all its saved grants. Notes are not exposed
through the consumer API.

The encrypted file is `plugins/dev.jasper.vault/data/vault.jv`. A device-bound vault uses the
platform keychain tool, with a file fallback shown in the manager's status line. An unbound vault
uses the master password alone. The master password cannot be recovered. Change the live plugin
settings through the Plugins manager's **Open Settings** action:

```toml
auto_lock_minutes = 15            # 0 disables inactivity locking
keys_directory = ""              # empty uses this plugin's data/keys directory
bind_new_vaults_to_device = true  # initial state of the create checkbox
```

The three actions are `dev.jasper.vault.open` (F8), `dev.jasper.vault.lock` and
`dev.jasper.vault.generate_key`. Configure their shortcuts like other contributed actions.

## Native acceptance

Headless tests cover the forms, persistence failures, lock transitions, service requests and
clipboard timing. The following checks need a user-run Jasper window:

1. Open F8 and create a throwaway vault. Verify native keychain access, then lock and unlock it.
2. Add/edit a login and a multiline note; generate/import an SSH key and copy its public key.
   Generate a passphrase-protected key, confirm the optional login retains its passphrase, and
   Use Browse beside both SSH key paths and the login key path; verify the chooser opens above
   the editor, starts from its current path, and cancellation keeps the value. Then
   verify the key works with your SSH client. Leave both passphrase fields empty to test that path.
3. Open F8 repeatedly; verify only one manager window opens. Lock with an editor open; verify
   its secret fields disappear and the manager offers inline unlock.
4. Copy a password and username through the Vault scope. Check 30-second clearing, then copy
   ordinary text before expiry and verify it survives.
5. Set `auto_lock_minutes = 1`, leave Jasper inactive and verify the padlock locks. Set it to 0
   and confirm the countdown disappears. Restore the desired timeout.
6. With a consumer plugin, exercise Allow once, Always and Deny; revoke its saved grant in the
   manager and verify the next fetch asks again.


7. Change the master password on the throwaway vault. Verify Cancel during derivation keeps the
   old password; verify a completed change unlocks with the new password after locking.

8. Search and filter the manager; verify two-line rows, empty results, double-click/Enter editing,
   and palette navigation revealing a row hidden by a prior filter. Open each + and More action.
   Compare buttons, inputs and list colors with IntelliJ Light and Jasper Dark.
