# Self-contained Vault-backed SSH import

Status: approved and implemented natively on `codex/retro-metal`; final verification
and independent review are in progress. See the [implementation plan](../plans/2026-09-23-jasper-vault-backed-ssh-import.md)
for completion evidence and implementation rulings.

## Intent and authority

Importing SSH configuration must produce saved hosts whose selected disk identities
are owned by Jasper's encrypted Vault. After import, those hosts must authenticate
through the built-in SSH client without the system agent or the original key files.
Re-import must be able to repair the user's already-imported agent hosts.

This amends the Remote design's import fallback and the Vault design's path-only SSH
key representation. Existing manually configured agent and path-backed Vault entries
remain supported. No agent socket server, agent forwarding, external ssh process,
or automatic connection during import is introduced. macOS, Linux and Windows,
modern and retro appearances, use the same flow.

## Current behavior and chosen approach

Remote currently reads one IdentityFile, matches its .pub fingerprint to an unlocked
Vault descriptor, and otherwise saves Agent authentication. Vault records private-key
paths, not the key contents; standalone key entries do not retain passphrases.

Merely registering those paths would still depend on ~/.ssh. Copying keys to a plain
application directory would not put the secrets under Vault encryption. The selected
approach stores managed key material in the existing encrypted Vault and gives Remote
an owned, short-lived credential copy for its existing in-process SSH client.

## Import flow

1. Parse config and show a preview of concrete host entries, their endpoints, ordered
   identity paths, and whether each entry is new or an update. Existing matches are
   unchecked until selected. Previewing alone does not read private-key contents.
2. Selecting Import starts the Vault import flow for the selected hosts' identities.
   A missing Vault opens its existing setup flow; a locked Vault prompts for unlock.
   Cancellation leaves saved hosts unchanged. If the Vault plugin is unavailable,
   explain that this import requires it and leave hosts unchanged.
3. Vault reads and validates each distinct selected private-key file in background
   work. It prompts for encrypted-key passphrases, offering retry or cancellation.
   Derive the public key and fingerprint from the private key; a .pub companion is
   optional, and a supplied mismatching companion is reported rather than trusted.
4. Present the batch under the requesting plugin's name, showing new/reused keys and
   fingerprints. The final Import and use action authorizes that plugin to use these
   selected keys through the existing per-plugin grants. No grant for other entries
   or plugins is added. Original files are never modified or removed.
5. Persist the Vault keys and grants successfully before saving any host references.
   Then save selected hosts together and show imported/updated/skipped results.
   Imported identities always use Vault-backed authentication, with no system-agent
   fallback. No SSH connection is made until the user chooses Connect.

If a selected key is missing, unreadable, malformed, unsupported, or cannot be
unlocked, keep the import editable and report the affected hosts. The user can
correct the problem or deselect those hosts. Do not save a host as ready while
quietly omitting one of its configured identities.

A host with no IdentityFile must be assigned a Vault credential in the preview or
left unselected. Do not scan and import unrelated default keys or fall back to Agent.
The preview's credential picker restricts selection to managed keys or password-only
credentials; a legacy key credential requires the same managed-key import first.

## Config interpretation and re-import

Preserve scalar first-value precedence and collect multiple IdentityFile directives
in their effective order, deduplicating repeated identities. IdentityFile none
explicitly means no identity. Resolve ~/ and relative identity paths against the
user's home and expand %% and the standard home/user/host/port tokens (%d, %u, %r,
%h, %p) using the resolved entry. Unsupported token/variable expressions are reported
per host, never silently treated as literal filenames or expanded by a shell.

Existing concrete Host/global/Host * and one-level Include support remains the
parser's supported scope. Continue listing unsupported Match/wildcard directives;
this is not a full OpenSSH configuration engine. Unresolved ProxyJump references
must block the affected selection instead of silently turning it into a direct
connection. Selected or existing jump hosts must also be Vault-backed for an import
to be described as self-contained; offer to include/update a referenced agent host.

Match existing hosts by the established case-insensitive alias rule. An explicitly
selected update preserves UUID, created timestamp, group and favorite, updates the
endpoint/user/jump/authentication from the preview, and refreshes updated timestamp.
Do not overwrite concurrent changes since preview: refresh and ask the user to
review the conflicting rows. Open sessions remain attached to their original
endpoint and credential; subsequent connections use the updated host.

## Vault storage and compatibility

Add a managed SSH-key model alongside the existing path-backed SshKey model. It owns
private-key bytes, the optional passphrase, public-key metadata, name, UUID and
creation time. Preserve original encoded bytes inside Vault encryption, avoiding
a new private-key serialization format. The managed entry owns mutable secret
arrays and clears them on deletion, replacement and Vault lock.

Extend the inner VaultCodec payload to version 2, appending a bounded managed-key
collection. Read existing version 1 files with unchanged UUIDs, records and grants;
write version 2 when managed entries are introduced. Continue using the existing
VaultFileFormat envelope, encryption, device binding, ordered writes and atomic
file replacement. Preserve a restricted-permission encrypted version-1 backup before
the first version-2 commit; abort that commit if backup creation fails. Do not
rewrite a version-1 file merely to inspect or unlock it. Older plugins must reject
new payloads clearly, and documentation must explain the backup for downgrade.

Fingerprint deduplication happens inside Vault, including across concurrent import
requests. Reuse an existing managed key. If an existing standalone path-backed SSH
key has that fingerprint, upgrade it to managed storage while retaining its UUID
and grants, after validating the selected source. Path-backed account credentials
remain unchanged. Do not deduplicate merely by filename or trusting .pub text.

All parsing/size checks are bounded (maximum 1 MiB per private-key source, with an
explicit error above the limit); decoding rejects truncated or oversized managed
fields and clears partially decoded secrets. Managed bytes and passphrases never
appear in hosts.toml, diagnostics, logs, labels, toString output, or temporary files.
Serialization scratch buffers and discarded import results must be cleared too.

Vault UI lists both key types, labels managed keys as stored in Vault, and supports
rename and delete without requiring file paths. Deleting a managed key never touches
its source file. Export and conversion of unrelated existing credentials are outside
this change. Existing account/generate-key workflows retain their behavior.

## Plugin API and authentication

Extend only dev.jasper.vault.api; the general Jasper SDK does not need new types.
Provide an asynchronous batch import method taking an owner window and ordered,
named source-path requests, returning durable credential descriptors/bindings or a
cancelled result. Vault owns setup/unlock, private-key reads, passphrase prompts,
validation, deduplication, persistence and scoped grant consent. API values are JDK
and SDK types only; Remote never accesses Vault internals or its storage file.

Extend Credential additively with an optional owned byte-array representation for
managed keys. Preserve the existing constructor and keyPath accessor for legacy
callers. A credential has exactly one key source: path or bytes. close() clears
managed bytes and passphrase as well as existing password data, and prevents later
access. Each request returns independent secret ownership.

Remote supports an ordered set of Vault key IDs for a host in addition to the
existing single credential representation. Existing hosts.toml entries remain
readable; the new multi-key form contains IDs only. Resolve every selected credential
through Vault, offer keys in order, and close all acquired copies on success,
denial, cancellation and partial acquisition failure. Parse managed material from
memory using MINA; never write a decrypted temporary file. Do not configure an agent
factory or default disk-key provider for these connections. Password-only Vault
credentials and older path-based authentication continue to work.

Vault adds the matching Apache MINA sshd-common 2.19.0 parsing dependency alongside
its existing BouncyCastle support. This avoids introducing a second key parser;
MINA types remain internal to the plugins. Validate dependency staging and isolated
classloaders using the installed distribution. No new platform-specific process or
filesystem-permission dependency is needed for managed material inside vault.jv.

Bump Vault and Remote to 0.2.0. Remote's optional Vault dependency requires >=0.2,
while Agent-only legacy use still works with Vault absent. The new VaultApi import
method has a default unsupported result for older third-party implementations.
Update the Remote test fake and contract examples. Jasper SDK remains 0.7.4.

## Lifetime and failure behavior

Work belongs to the initiating window and plugin. Closing the window, stopping the
plugin, cancelling, or locking Vault before commit invalidates outstanding reads
and prompts; late results are discarded and cleared. Blocking I/O and private-key
parsing run off the EDT; state changes and public future completions return to it.

Coordinate import writes with Vault manager edits and password changes. Revalidate
Vault generation and deduplication at commit, serialize mutations, and roll back the
in-memory batch if its durable save fails. A second import cannot publish duplicate
fingerprints while the first is committing. Tests must cover the failure paths,
not just assume that ordered file writes serialize in-memory edits.

The Vault file and hosts file are not a single atomic transaction. Persist keys
first so a saved host never references an undurable credential. If host saving
fails after key persistence, retain valid keys, report that hosts were not saved,
and let retry reuse those keys. Do not delete keys potentially shared by other
hosts. Once a durable commit starts it completes even if the initiating UI closes;
report partial completion on the next relevant view rather than claim cancellation
undid a completed write.

Normal Vault lock behavior is preserved: new authentication requires unlock and
authorization; established SSH sessions keep running. A credential already granted
to an in-flight authentication retains the existing owned-copy lifetime contract.

## Verification and completion criteria

- Import generated plain and passphrase-protected Ed25519 and RSA fixtures; exercise
  supported ECDSA/PEM parsing and a clear unsupported-key result as appropriate.
- Authenticate against loopback SSH with the system agent absent and original key
  files removed after import, including after Vault lock/reload and app-style restart.
- Multiple hosts share one managed key; ordered multiple identities can succeed with
  the second key; wrong passphrases, missing .pub, and mismatched .pub are covered.
- Repair a saved Agent host in place and retain its identity, groups and favorites;
  test conflicting edits, jump dependencies, no-key entries and partial host-save failure.
- Read a version-1 fixture, upgrade safely, decrypt/reload version 2, preserve legacy
  credentials/grants, and verify backup/write failure and truncated-file handling.
- Check zeroing and owned copies on lock, cancellation, parser failure and credential
  close; concurrent import/edit/rekey tests enforce the lifetime rules above.
- Exercise the actual Vault API through isolated plugin loading, both skins' import
  and passphrase controls, plugin stop/window close, and source-file independence.
- Run ./gradlew check :jasper-app:installDist, architecture guards, source hygiene,
  and one independent final review. Native GUI and real-host acceptance remain user-run.

No user private-key bytes or production Vault contents are needed for automated
verification. Fixtures are generated in temporary directories. No merge to main,
push, GUI launch, or alteration of the user's SSH configuration is part of execution.
