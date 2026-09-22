package dev.jasper.vault.ui;

import dev.jasper.vault.crypto.SecureBytes;
import dev.jasper.vault.lock.LockManager;
import java.util.Arrays;

/** Changing the master password requires the existing password and a matching replacement. */
public final class ChangePasswordForm extends EditorForm {
    final SecretDocument current = secret(), replacement = secret(), confirm = secret();
    public ChangePasswordForm(LockManager lock, Runnable close) {
        super(close);
        field("Current master password", passwordField(current));
        field("New master password", passwordField(replacement));
        field("Confirm new master password", passwordField(confirm));
        save.setText("Change Password");
        submit(() -> {
            char[] old = current.snapshot(), next = replacement.snapshot(), again = confirm.snapshot();
            try {
                if (old.length == 0) throw new IllegalArgumentException("Enter the current master password");
                if (next.length < 8) throw new IllegalArgumentException("Use at least 8 characters");
                if (!Arrays.equals(next, again)) throw new IllegalArgumentException("The passwords do not match");
                return lock.changePassword(old, next);
            } finally { SecureBytes.zero(old); SecureBytes.zero(next); SecureBytes.zero(again); }
        });
    }
}
