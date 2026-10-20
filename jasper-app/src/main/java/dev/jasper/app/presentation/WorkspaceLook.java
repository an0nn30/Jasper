package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * The workspace's projection (spec 9.1), read by the workspace's chrome factory.
 *
 * @param menus the menu bar's composition
 * @param header the header's composition
 * @param stripes how tool windows are reached
 * @param tabs the editor tabs
 * @param status the status bar
 * @param runItem whether the status bar leads with "▶ Run…"
 * @param frames how tool-window frames are drawn
 * @param icons the workspace chrome's icon set
 */
public record WorkspaceLook(Menus menus, Header header, Stripes stripes, Tabs tabs, Status status, boolean runItem, FrameStyle frames,
                            Icons icons) {
    /** The standard menus, or the classic ones with a Window menu. */
    public enum Menus { STANDARD, CLASSIC }
    /** The unified header, or the classic toolbar row. */
    public enum Header { UNIFIED, CLASSIC }
    /** Icon rails, or labelled stripes. */
    public enum Stripes { RAILS, LABELLED }
    /** Equal tabs, or classic tabs. */
    public enum Tabs { EQUAL, CLASSIC }
    /** Slash segments, or ruled cells. */
    public enum Status { SLASH, RULED }
    /** Today's frames, or flush frames with header strips. */
    public enum FrameStyle { STANDARD, FLUSH }
    /** IntelliJ's New UI chrome icons, or its classic ones (spec 5.2's [chrome] icons). */
    public enum Icons { NEW_UI, CLASSIC }

    /** Every part is required. */
    public WorkspaceLook {
        Objects.requireNonNull(menus); Objects.requireNonNull(header); Objects.requireNonNull(stripes);
        Objects.requireNonNull(tabs); Objects.requireNonNull(status); Objects.requireNonNull(frames);
        Objects.requireNonNull(icons);
    }
}
