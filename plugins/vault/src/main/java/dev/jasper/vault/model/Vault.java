package dev.jasper.vault.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The decrypted contents. Mutable, edited on the UI thread only, saved by the lock manager after each edit. */
public final class Vault {
    private final List<Account> accounts = new ArrayList<>();
    private final List<SshKey> keys = new ArrayList<>();
    private final List<Note> notes = new ArrayList<>();
    private final Set<Grant> grants = new LinkedHashSet<>();

    public List<Account> accounts() { return accounts; }
    public List<SshKey> keys() { return keys; }
    public List<Note> notes() { return notes; }
    public Set<Grant> grants() { return grants; }

    public Optional<Account> account(UUID id) { return accounts.stream().filter(account -> account.id().equals(id)).findFirst(); }
    public Optional<SshKey> key(UUID id) { return keys.stream().filter(key -> key.id().equals(id)).findFirst(); }

    /** Removes the account or key with {@code id} (zeroing an account's secrets) and every grant for it. */
    public void remove(UUID id) {
        accounts.removeIf(account -> { if (!account.id().equals(id)) return false; account.auth().zero(); return true; });
        keys.removeIf(key -> key.id().equals(id));
        notes.removeIf(note -> { if (!note.id().equals(id)) return false; note.zero(); return true; });
        grants.removeIf(grant -> grant.credentialId().equals(id));
    }

    /** Zeroes every secret and empties the vault; called at lock. */
    public void zero() {
        accounts.forEach(account -> account.auth().zero());
        notes.forEach(Note::zero);
        accounts.clear(); keys.clear(); notes.clear(); grants.clear();
    }
}
