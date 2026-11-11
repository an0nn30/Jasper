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
 * @param search the chooser's starting search, stripped: it opens listing only the credentials whose name or caption
 *     contains it, ignoring case, until the user edits it; empty for every credential
 */
public record CredentialPickRequest(UiAnchor anchor,Optional<UUID> current,boolean allowClear,String search) {
    /**
     * A request whose chooser starts with an empty search.
     *
     * @param anchor the control the chooser's popover attaches to
     * @param current the credential chosen now; empty for none
     * @param allowClear whether the chooser offers a None row while its search is empty
     */
    public CredentialPickRequest(UiAnchor anchor,Optional<UUID> current,boolean allowClear){this(anchor,current,allowClear,"");}
    /** Rejects a null anchor, current or search, and strips the search. */
    public CredentialPickRequest {Objects.requireNonNull(anchor);Objects.requireNonNull(current);search=search.strip();}
}
