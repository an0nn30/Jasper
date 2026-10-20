package dev.jasper.vault.ui;

import dev.jasper.sdk.ui.UiKit;
import dev.jasper.vault.crypto.SecureBytes;
import dev.jasper.vault.lock.LockManager;
import java.util.Arrays;
import java.util.function.Consumer;

/** Changing the master password requires the existing password and a matching replacement. */
public final class ChangePasswordForm extends EditorForm {
    static final String CURRENT = "current", REPLACEMENT = "replacement", CONFIRM = "confirm";
    private boolean closed, committing;
    public ChangePasswordForm(UiKit kit, LockManager lock, Runnable close) {
        this(kit, lock, close, failure -> { });
    }
    public ChangePasswordForm(UiKit kit, LockManager lock, Runnable close, Consumer<Throwable> detachedCompletion) {
        super(kit, close);
        identity("lock", dev.jasper.sdk.ui.Nodes.Hue.BLUE);
        secret(CURRENT, "Current master password", "current");
        secret(REPLACEMENT, "New master password", "replacement");
        secret(CONFIRM, "Confirm new master password", "replacement");
        setSaveLabel("Change Password");
        readyWhen(() -> {
            char[] old=copySecret(CURRENT), next=copySecret(REPLACEMENT), again=copySecret(CONFIRM);
            try { return old.length>0 && next.length>=PasswordPanel.MIN_LENGTH && Arrays.equals(next,again); }
            finally { SecureBytes.zero(old);SecureBytes.zero(next);SecureBytes.zero(again); }
        });
        submit(() -> {
            char[] old = copySecret(CURRENT), next = copySecret(REPLACEMENT), again = copySecret(CONFIRM);
            try {
                if (old.length == 0) throw invalid(CURRENT, "Enter the current master password");
                if (next.length < PasswordPanel.MIN_LENGTH) throw invalid(REPLACEMENT, "Use at least " + PasswordPanel.MIN_LENGTH + " characters");
                if (!Arrays.equals(next, again)) throw invalid(CONFIRM, "The passwords do not match");
                return lock.changePassword(old, next, () -> closed, () -> {
                    committing = true;
                    setCancelLabel("Close");
                    setMessage("Changing password; closing will not cancel.");
                }).whenComplete((ignored, failure) -> {
                    if (closed && committing) detachedCompletion.accept(failure);
                    committing = false;
                    setCancelLabel("Cancel");
                });
            } finally { SecureBytes.zero(old); SecureBytes.zero(next); SecureBytes.zero(again); }
        });
    }
    @Override public void close() { closed = true; super.close(); }
}
