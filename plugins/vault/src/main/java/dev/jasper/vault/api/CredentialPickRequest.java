package dev.jasper.vault.api;
import dev.jasper.sdk.ui.UiAnchor;
import java.util.*;
/**
 * Where {@link VaultApi#pick(dev.jasper.sdk.terminal.WindowHandle, CredentialPickRequest)} shows its chooser and what
 * it shows. The request grants nothing, whatever it names; only the user's choice does.
 *
 * @param anchor the control the chooser's popover attaches to, from {@link dev.jasper.sdk.ui.UiView#anchor(String)};
 *     when it closes, the pick answers {@link CredentialPickResult.Cancelled}
 * @param current the credential chosen now, drawn with a check mark; empty for none
 * @param allowClear whether the chooser offers a None row while its search is empty; choosing it answers
 *     {@link CredentialPickResult.Cleared}
 */
public record CredentialPickRequest(UiAnchor anchor,Optional<UUID> current,boolean allowClear) {
    /** Rejects a null anchor or current. */
    public CredentialPickRequest {Objects.requireNonNull(anchor);Objects.requireNonNull(current);}
}
