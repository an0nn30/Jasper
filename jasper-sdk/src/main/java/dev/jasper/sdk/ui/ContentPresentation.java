package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;

/** Host-rendered document metadata; all text is literal.
 * @param title file name or content title
 * @param detail optional short context, such as remote host
 * @param icon host-provided icon, or null; the tab shows it while it has no image, or when its image does not decode
 * @param dirty whether the host shows an unsaved marker
 * @param location full local or remote path, never converted to a local path by the host
 * @param host remote host label, empty for local content
 * @param image (since 0.27.0) the image the tab shows in place of its icon, such as a web page's favicon; empty for none
 * @since 0.8.5
 */
public record ContentPresentation(String title,String detail,String icon,boolean dirty,String location,String host,Optional<PixelImage> image) {
    /** Requires all strings; empty detail/location/host are allowed. */
    public ContentPresentation {Objects.requireNonNull(title);Objects.requireNonNull(detail);Objects.requireNonNull(location);Objects.requireNonNull(host);Objects.requireNonNull(image);}

    /**
     * A presentation without an image: the tab shows its icon.
     *
     * @param title file name or content title
     * @param detail optional short context, such as remote host
     * @param icon host-provided icon, or null
     * @param dirty whether the host shows an unsaved marker
     * @param location full local or remote path, never converted to a local path by the host
     * @param host remote host label, empty for local content
     */
    public ContentPresentation(String title,String detail,String icon,boolean dirty,String location,String host) {this(title,detail,icon,dirty,location,host,Optional.empty());}

    /**
     * This presentation with an image: the tab shows the image, drawn in its icon's box, instead of the icon, which
     * stays the fallback for an image that does not decode.
     *
     * @param image the image
     * @return the presentation with {@code image}
     * @since 0.27.0
     */
    public ContentPresentation withImage(PixelImage image) {return new ContentPresentation(title,detail,icon,dirty,location,host,Optional.of(image));}
}
