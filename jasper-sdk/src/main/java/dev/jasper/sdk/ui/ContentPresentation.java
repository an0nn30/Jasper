package dev.jasper.sdk.ui;

import java.util.Objects;

/** Host-rendered document metadata; all text is literal.
 * @param title file name or content title
 * @param detail optional short context, such as remote host
 * @param icon host-provided icon, or null
 * @param dirty whether the host shows an unsaved marker
 * @param location full local or remote path, never converted to a local path by the host
 * @param host remote host label, empty for local content
 * @since 0.8.5
 */
public record ContentPresentation(String title,String detail,String icon,boolean dirty,String location,String host) {
    /** Requires all strings; empty detail/location/host are allowed. */
    public ContentPresentation {Objects.requireNonNull(title);Objects.requireNonNull(detail);Objects.requireNonNull(location);Objects.requireNonNull(host);}
}
