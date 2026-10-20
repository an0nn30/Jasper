package dev.jasper.vault.api;
import dev.jasper.sdk.ui.UiAnchor;
import java.util.*;
/** Attachment and display state; neither metadata nor this request authorizes a credential grant. */
public record CredentialPickRequest(UiAnchor anchor,Optional<UUID> current,boolean allowClear) {
    public CredentialPickRequest {Objects.requireNonNull(anchor);Objects.requireNonNull(current);}
}
