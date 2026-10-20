package dev.jasper.sdk.ui;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
/** Opaque host-issued attachment authority for one live mounted control. UI thread only.
 * Hiding, disabling, removing, retyping, moving or closing its source permanently invalidates it.
 * It grants no data access and no authority to mount a foreign view. */
public interface UiAnchor extends WindowOwner, Subscription {
    /** Whether the source still permits attachment. @return live attachment state */
    boolean isOpen();
    /** Register invalidation; a closed anchor invokes immediately. @param handler cleanup @return registration */
    Subscription onClosed(Runnable handler);
}
