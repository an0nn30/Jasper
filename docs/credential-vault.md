# Credential Vault

Credential Vault is a bundled plugin (`dev.jasper.vault`), on by default and listed in Settings › Plugins
([Plugins](plugins.md#credential-vault)). It keeps
logins, SSH keys and secure notes in one encrypted file in Jasper home, optionally bound to this computer. Other
plugins, such as Remote and File Server, fetch a credential from it only after you allow that plugin to use it.

| What | Holds |
|---|---|
| Login | a name, a username and how it signs in: a password, a private key file (with an optional passphrase), or both |
| SSH key | a name and a key pair: a *path key* records the paths of key files kept on disk; a *stored key* (**Stored in Vault**) keeps the private key and its passphrase inside the vault |
| Secure note | a name and multi-line text, which no other plugin can read |

## Commands

| Command | Action id | macOS | Linux and Windows | Also in |
|---|---|---|---|---|
| **Open Vault...** | `dev.jasper.vault.open` | Cmd+Shift+\\ | Ctrl+Alt+Shift+\\ | File menu; the status item while the vault is not unlocked |
| **Search Vault Logins** | `dev.jasper.vault.search` | Cmd+\\ | Ctrl+Shift+\\ | |
| **Lock Vault** | `dev.jasper.vault.lock` | Cmd+Shift+L | Ctrl+Alt+Shift+L | the status item while unlocked; enabled only while unlocked |
| **Generate SSH Key...** | `dev.jasper.vault.generate_key` | none | none | the manager's add menu |
| **Change Vault Master Password...** | `dev.jasper.vault.change_password` | none | none | File menu |
| **Vault Plugin Grants...** | `dev.jasper.vault.grants` | none | none | File menu |
| **Add Login...** | `dev.jasper.vault.add_login` | none | none | |
| **Add SSH Key...** | `dev.jasper.vault.add_key` | none | none | |
| **Add Secure Note...** | `dev.jasper.vault.add_note` | none | none | |

Every command is in the command palette. Open Vault, the Add and Generate commands, Change Vault Master Password and
Vault Plugin Grants create the vault when there is none, or unlock it, then open the manager and go on from
there. Rebind them in Settings › Keymap, or by quoted id under `[keybindings]` in `config.toml`
([keyboard shortcuts](keyboard-shortcuts.md)).

The status bar's right side shows the vault's state: **Vault** with no vault yet (tooltip "No vault — click to create
one"), **Vault: Locked** ("Vault locked") or **Vault: Unlocked** ("Vault unlocked · locks in N min", or "Vault
unlocked" with auto-lock off). Clicking it locks an unlocked vault and otherwise runs Open Vault.

## Creating a vault

While there is no vault, Open Vault or another Vault command shows **Create Vault** over the window you were in: the
window dims and the prompt fades up over it (it appears at once with animations off). Enter the **Master password**
and **Confirm password**; the meter under the first field grows with its length. **Create Vault** is enabled once both
match and have at least 8 characters. The master password cannot be recovered: without it the vault cannot be opened.

**Bind to this device** (marked Recommended), on by default (setting `bind_new_vaults_to_device`), makes the vault open only on
this computer: its key is derived from the master password together with a device secret kept by the system keychain
(see [Device binding](#device-binding)). Without it the master password alone opens the vault. The choice is made once,
when the vault is created; changing the master password keeps it.

## Unlocking and locking

**Unlock Vault** asks for the master password over the window that needs the vault, or, while the manager is open,
inside the manager, which comes to the front without the window behind it. Every plugin and command that needs the
vault while it is locked waits on the same prompt. Enter in the field unlocks. A failed attempt says why in the form:

| Message | Cause |
|---|---|
| Wrong password | the password does not open the file, the file was changed, or this computer's device secret is not the one the vault was bound to |
| This vault was created on another machine and is bound to it; its device secret is not on this one | a bound vault on a computer that has no device secret |
| a message about the file, such as "The vault file is truncated" or "The file is not a Jasper vault" | the file is damaged or is not a vault |
| Could not open the vault: *message* | any other failure, such as an unreadable file |

**Lock Vault**, the status item or the manager's lock button locks the vault: Jasper erases every decrypted secret
and the key from memory, closes the manager's editor sheets, denies grant questions that were waiting and cancels
pickers and key imports. The manager stays open and shows **Vault locked** with **Unlock**. A plugin that
already holds a credential keeps its own copy, so connected SSH sessions stay connected; new connections ask for the
vault again.

The vault also locks after `auto_lock_minutes` (15 by default) without keyboard or mouse activity in Jasper's windows,
checked every 5 seconds; `0` turns this off. It locks when the plugin stops or Jasper quits.

## Device binding

The device secret is 32 random bytes, made the first time a bound vault is created and kept by the platform's own
tool:

| Platform | Where the device secret is kept |
|---|---|
| macOS | the keychain, through `security`: a generic password with service "Jasper Credential Vault" and account "device-secret" |
| Linux | the Secret Service, through `secret-tool`: label "Jasper Credential Vault", attributes `service jasper-credential-vault` and `account device-secret` |
| Windows | `device.dpapi` in the plugin's data folder, protected by DPAPI for the current user through PowerShell |

When the tool is missing or fails, the secret is written to `device.secret` in the plugin's
data folder instead, readable by your account only. The manager's footer names the store in use: **Keychain** or
**File (no keychain found)**. A copy of `vault.jv` opens only where its device secret is available.

## The manager

The manager is one modeless dialog, **Credential Vault**, over the main window, whichever window you open it from: the
main window comes forward first, restored if it was minimised. Opening it again brings it to the front, and it closes
with the main window. You can resize it, and Jasper remembers its size and position. Its first row has a lock button
(tooltip "Lock vault") and the add menu (tooltip "Add entry"): **Login**, **SSH Key**, **Secure Note** and **Generate
SSH Key...**.

Below are three columns:

- The categories: All Items, Logins, SSH Keys and Secure Notes, each with its count.
- The entries, under **Search vault**, sorted by type, then name. Each shows its name over a detail line: a login's
  "*username* · Password", "· Private key" or "· Private key and password"; a path key's "*algorithm* · *comment*" (its
  fingerprint when there is no comment); a stored key's "Stored in Vault · *fingerprint*". Typing matches, ignoring
  case, an entry's name, a login's username or a key's fingerprint. Double-click an entry or press Enter to edit it.
  "Nothing to show" means the vault is empty, "No matching entries" that the search or category hides everything.
- The details of the selected entry: its name and type, a delete button and **Edit**, then its fields. A login shows
  Username (with a copy button), Password (masked, with reveal and copy buttons), Private Key and the date it was
  modified; a path key Algorithm, Fingerprint, Private Key, Public Key, Comment and its creation date; a stored key
  Storage ("Stored in Vault"), Fingerprint and its creation date; a secure note its text, masked with reveal and copy
  buttons. SSH keys have **Copy Public Key**.

The footer shows the device-secret store, the auto-lock time ("Auto-lock 15 min" or "Auto-lock off") and the path of
`vault.jv`. **Done** or Escape closes the manager. Each change is saved as you make it, so closing undoes nothing.

### Under a classic look

Under a classic look, one whose [kit file starts from the classic base](looks.md#kit-and-base) as the
[Enterprise look](enterprise.md)'s does, the manager is laid out classically:

- A toolbar row: **New** (the add menu), **Edit…**, **Delete**, **Copy Username** (a login), **Copy Password** (a login
  with a password), **Lock Vault** with its default shortcut as a caption, Cmd+Shift+L on macOS and Ctrl+Alt+Shift+L on
  Linux and Windows, which does not follow a rebinding, and **Search vault** at the trailing edge.
- A Categories tree, All Items over Logins, SSH Keys and Secure Notes, with counts.
- A table with **Name**, **Username** ("—" for other entries), **Type** (a login's sign-in method, else the kind) and
  **Modified** (a key's creation date). Click a header to sort by it, again to reverse. The footer counts what shows,
  "N of M items".
- **Details**: the name over its type and its sign-in method, algorithm or "Stored in Vault", a **Fields** box in which every field has
  **Copy** (Password and Note also **Show**), the Properties **Modified** and **Used by** (the plugins with a saved
  grant for the entry, such as "Remote", or "Not used"), and **Edit…**, **Delete…** and, for SSH keys, **Copy Public
  Key**.
- A status row: "Locked" or "Unlocked · *store*" (such as "Unlocked · Keychain"), "Auto-lock: 15 min" (or "off") and
  the vault's path.

Login, SSH key and secure note sheets show their kind's colour swatch in the dialog's title strip. The Unlock Vault
dialog shows its lock beside the intro and ends with "Auto-locks after N min idle" when auto-lock is on; the unlock
form inside the manager has neither.

## Logins, SSH keys and secure notes

The add menu and the palette's Add commands open a sheet over the manager; Edit opens the same sheet filled in.
**Save** is enabled once the required fields are filled. A problem shows on its field, or in a line under the fields
when it is not one field's (such as an unreadable key file or a failed save). Every path field has **Browse…**, which
opens Jasper's [file chooser](workspace.md#the-file-chooser) at the typed path; cancelling it keeps the field as it
was. Type an absolute path: `~` is not expanded, and a relative path is taken from Jasper's working directory. Sheets
erase the secrets typed into them when they close.

**Add Login** asks for **Name** and **Username**, then **Authentication**: **Password**, or **Private Key** with the key
file's path, an optional **Passphrase** and **Also use password** to add a password as well. Under a classic look
the choice is labelled **Method**.

**Add SSH Key** records an existing key pair as a path key: **Name**, **Private Key** and **Public Key** paths and an
optional **Comment**. Saving checks that the private key file exists and is readable, and reads the public key file
(at most 64 KiB, one OpenSSH public key line whose type word matches the key data) for the algorithm, such as
`ssh-ed25519`, and the SHA-256 fingerprint. The private key never enters the vault: other plugins receive its path.
Stored keys come from a plugin's key import, such as Remote's **Import from ~/.ssh/config...** (see
[below](#other-plugins-and-grants) and [Remote](remote.md)); their editor, **Edit stored SSH key**, only renames them.

**Add Secure Note** asks for a **Name** and the note's text.

**Delete** asks "Remove this entry? *name*". For a path key it also offers **Delete private and public key files too**
(off by default) and lists both paths, noting that other accounts using these paths lose the files as well. Deleting an
entry removes every saved grant for it.

**Copy Public Key** copies the public key line: the `.pub` file's text for a path key, the stored line for a stored
key.

### Generating an SSH key

**Generate SSH Key...** asks for:

| Field | Values |
|---|---|
| **Name** | required |
| **Algorithm** | **Ed25519 (recommended)** (the default), **ECDSA P-256**, **ECDSA P-384**, **RSA 3072**, **RSA 4096** |
| **Comment** | one line, optional; written at the end of the public key line |
| **Passphrase (optional)**, **Confirm passphrase** | must match; empty for an unencrypted key |
| **Also add a login account**, **Account username** | optional |

**Generate Key** writes two files into the keys folder (setting `keys_directory`; by default
`plugins/dev.jasper.vault/data/keys/` in Jasper home), named after the algorithm and eight random hex digits:
`id_ed25519_1a2b3c4d` and `id_ed25519_1a2b3c4d.pub`, or `id_ecdsa_p256_…`, `id_ecdsa_p384_…`, `id_rsa_3072_…`,
`id_rsa_4096_…`. On file systems with POSIX permissions the folder is set to `rwx------` and the private key to
`rw-------`. With a passphrase the private key is written in OpenSSH's format, encrypted with `aes256-ctr` under a
`bcrypt` key derivation of 24 rounds. Without one, an Ed25519 key is in OpenSSH's format, an ECDSA key a PEM
`EC PRIVATE KEY` and an RSA key a PEM `RSA PRIVATE KEY` with public exponent 65537.

The vault records the new key as a path key. With **Also add a login account**, it also adds a login named
"*key name* (*username*)" that signs in with the private key and keeps the passphrase in the vault; a key without a
login keeps only its paths and details, so keep its passphrase yourself. Locking the vault leaves key files as they
are. If saving fails, the generated files are deleted.

## Changing the master password

**Change Vault Master Password...** opens **Change Master Password** over the manager: **Current master password**,
**New master password** and **Confirm new master password** (at least 8 characters, matching). **Change Password**
checks the current password, then re-encrypts the vault under the new one with a new salt. Cancelling or closing the
sheet before the file is written abandons the change. Once writing starts, the sheet says "Changing password; closing
will not cancel." and its Cancel button reads **Close**. A failure leaves the old password in place: the sheet says
why, or, if the sheet was closed while the vault was being written, a "Master password was not changed" notification
reports it.

## Copying and the clipboard

Copying a password, username or secure note is a secret copy: Jasper clears the clipboard 30 seconds later (retrying
for a few seconds while another program holds it), and tries once more when the plugin stops and when Jasper quits,
each time only if the clipboard still holds that text. A later copy, yours or a plugin's, is left alone. Public keys,
and under a classic look the other Details fields (paths, algorithm, fingerprint, comment, storage), are ordinary
copies that stay on the clipboard. The vault only copies: it never types a secret into a terminal.

Each copy shows a card naming what was copied and from which entry, such as "Copied the password for prod-db", never
the value; a secret copy's card adds "Copied to Clipboard for 30 seconds". The cards belong to the **Vault copies**
group (`dev.jasper.vault.copies`), which you can change or turn off under Settings › Appearance & Behavior ›
Notifications › Groups ([notification groups](notifications.md#groups)). Vault's errors use its own group,
**Credential Vault** (`dev.jasper.vault`).

## Logins in the command palette

**Search Vault Logins** opens the command palette with the **Vault** chip set ("Search logins"); typing `vault >` in
the palette does the same. While the palette is open the command's shortcut switches to that chip, and pressing it
again closes the card (the Run window takes the keyboard instead). Searching All leaves Vault logins out. The chip
lists logins only, each with its username and a tag, `password`, `key` or `key+password`; every word you type must
appear in the name or the username.

| Key | Verb |
|---|---|
| Enter | **Copy password** (logins with a password) |
| Cmd+Enter (Ctrl+Enter on Linux and Windows) | **Copy username** |
| Shift+Enter | **Open in Vault**: the manager with that login selected, clearing a search or category that hid it |

While the vault is locked the chip has one row, "Vault is locked — press Enter to unlock"; after unlocking, the
palette opens with the Vault chip again. With no vault yet the row reads "No vault yet — press Enter to create one".

## Other plugins and grants

A plugin that uses the vault ([For plugin authors](#for-plugin-authors)) can list, while the vault is unlocked, the
names of logins and SSH keys with their usernames or fingerprints; it can never list or read secure notes. To read a
secret it asks for one credential, and the vault unlocks first if needed. Unless the plugin already has a grant for
that credential, a dialog, "Allow *plugin* to use *credential*?", asks:

| Button | Effect |
|---|---|
| **Allow once** | this request only |
| **Always allow for *plugin*** | saves a grant in the vault; later requests for this credential by this plugin need no answer |
| **Deny** | the plugin gets nothing; Escape and closing the dialog deny too |

Enter does not press any of them. Requests from one plugin for one credential share one dialog.

The credential fields of Remote's host editor and File Server's server editor open the vault's picker on the field:
**Search credentials**, **None** where the field may be cleared, every login and SSH key captioned "Password · *user*",
"Account key · *user*", "Key and password · *user*", "Stored key · *fingerprint*" or "Path key · *fingerprint*", and
**New Credential…** at the bottom, which offers **Login** or **SSH path key** and opens that sheet; the new entry is
then in the list to choose. Choosing a credential lets that plugin read it once without asking.

Remote's **Import from ~/.ssh/config...** hands the selected private keys to the vault, which creates or unlocks the
vault first and shows **Import SSH keys**. It reads each key (at most 1 MiB; a `.pub` file beside it must match), asks
for a key's **Passphrase** when it is encrypted (**Unlock key**, or Enter in the field), then lists the keys with their
fingerprints, each marked `new` or `reuse`. **Import and use** stores each key and its passphrase inside the vault: a
stored key with the same fingerprint is reused, and a path key with the same fingerprint becomes a stored key and keeps
its grants. The importing plugin gets a saved grant for every key and for each credential chosen for hosts without a
key file. Escape or **Cancel** stops the import until saving starts. If the import's window closed during the save, the
keys stay in the vault and a notice asks you to re-import the hosts.

**Vault Plugin Grants...** shows the saved grants as "*plugin id* — *entry name*"; select one and press **Revoke** to
make that plugin ask again, or **Back to entries**. One-time permissions end when the vault locks.

## Settings

Change these in Settings › Tools › Credential Vault, or in `plugins/dev.jasper.vault/dev.jasper.vault.toml` in Jasper
home. Changes apply at once.

| Key | Label | Default | Values |
|---|---|---|---|
| `auto_lock_minutes` | Lock after idle minutes | `15` | 0 to 1440 minutes without keyboard or mouse activity; `0` never locks automatically; a value out of range uses the default |
| `keys_directory` | Generated keys folder | `""` | the absolute path of the folder Generate SSH Key writes to (`~` is not expanded); empty means `data/keys` in the plugin's folder |
| `bind_new_vaults_to_device` | Bind new vaults to this device | `true` | the initial state of **Bind to this device** in Create Vault |

## Files

Under `plugins/dev.jasper.vault/` in Jasper home:

| Path | What |
|---|---|
| `dev.jasper.vault.toml` | the settings above |
| `data/vault.jv` | the encrypted vault |
| `data/vault.jv.tmp` | the next version of `vault.jv` while it is being written |
| `data/vault.jv.v1-backup` | a copy of the encrypted file as it was before the first stored key was saved; made once and never replaced; a save that cannot make it fails |
| `data/device.secret` | the device secret, when no keychain tool could keep it |
| `data/device.dpapi` | the device secret on Windows, protected by DPAPI |
| `data/keys/` | generated SSH keys, unless `keys_directory` names another folder |

Path keys and key-based logins refer to key files wherever they are; the vault never copies them.

## File format and cryptography

`vault.jv` is a 40-byte header followed by the ciphertext:

| Bytes | Field |
|---|---|
| 9 | `JASPERVLT` in ASCII |
| 2 | file format version, little-endian: `1` |
| 1 | flags: `1` when the vault is bound to a device |
| 16 | salt |
| 12 | nonce |
| rest | AES-256-GCM ciphertext with its 128-bit tag; the 40-byte header is the additional authenticated data, so the flags cannot change without the key |

The 32-byte key is derived with Argon2id (version 1.3, 64 MiB of memory, 3 iterations, 4 lanes) from the 16-byte salt
over the master password in UTF-8 followed by the 32-byte device secret, or over the password alone for an unbound
vault. A new random salt is made when the vault is created and when the master password changes; every write uses a
new random nonce.

The decrypted contents start with a 2-byte content version: `1`, or `2` from the first stored key on. Then come five
lists, each a 4-byte count followed by its entries (the stored keys only in version 2):

| List | Each entry |
|---|---|
| Logins | id, name, username, a sign-in byte (`0` password, `1` key, `2` key and password) followed by the key path and passphrase and the password as it has them, creation time, modification time |
| Path keys | id, name, algorithm, fingerprint, comment, private key path, public key path, creation time |
| Secure notes | id, name, text, modification time |
| Saved grants | plugin id, credential id |
| Stored keys | id, name, algorithm, fingerprint, public key line, private key bytes (a 4-byte length first), passphrase, creation time |

Numbers are big-endian, text is a 4-byte length and UTF-8, a missing passphrase has length `-1`, ids are 16-byte UUIDs
and times are seconds since 1970. A vault holds at most 4096 stored keys, each private key at most 1 MiB and 64 MiB in
all.

Every change is made on a copy of the decrypted vault, which is checked, encrypted and written to `vault.jv.tmp`, then
moved over `vault.jv` (atomically where the file system supports it). Only then does the manager show the change, so a
failed write leaves the vault as it was. `vault.jv`, its temporary file and backup and `device.secret` are readable
and writable by their owner only: `rw-------` with POSIX permissions, otherwise an access list naming only the owner.

## For plugin authors

Credential Vault exports `dev.jasper.vault.api`; Remote and File Server use it through an optional requirement,
`{ id = "dev.jasper.vault", version = ">=0.2", optional = true }`.
[Plugin APIs](plugin-apis.md#credential-vault-api) describes
[`VaultApi`](sdk-reference.html#dev.jasper.vault.api.VaultApi), its grants and the types it returns.
