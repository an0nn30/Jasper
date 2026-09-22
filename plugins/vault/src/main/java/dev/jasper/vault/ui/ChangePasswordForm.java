package dev.jasper.vault.ui;

import dev.jasper.vault.crypto.SecureBytes;
import dev.jasper.vault.lock.LockManager;
import java.util.Arrays;
import java.util.function.Consumer;

/** Changing the master password requires the existing password and a matching replacement. */
public final class ChangePasswordForm extends EditorForm {
    final SecretDocument current = secret(), replacement = secret(), confirm = secret();
    private boolean closed, committing;
    public ChangePasswordForm(LockManager lock, Runnable close) {
        this(lock, close, failure -> { });
    }
    public ChangePasswordForm(LockManager lock, Runnable close, Consumer<Throwable> detachedCompletion) {
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
                return lock.changePassword(old, next, () -> closed, () -> {
                    committing = true;
                    cancel.setText("Close");
                    error.setText("Changing password; closing will not cancel.");
                }).whenComplete((ignored, failure) -> {
                    if (closed && committing) detachedCompletion.accept(failure);
                    committing = false;
                    cancel.setText("Cancel");
                });
            } finally { SecureBytes.zero(old); SecureBytes.zero(next); SecureBytes.zero(again); }
        });
    }
    @Override public void close() { closed = true; super.close(); }
}
