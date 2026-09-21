package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import javax.swing.Icon;

/**
 * A status bar item the application renders in every window. One handle drives them all; per-window
 * content is not supported. Mutators are UI-thread only and do nothing after {@link #close()}.
 */
public interface StatusItem extends Subscription {
    /**
     * Sets the text; line breaks become spaces.
     *
     * @param text the text, or empty for an icon-only item
     */
    void setText(String text);

    /**
     * Sets the icon.
     *
     * @param icon the icon, or null for none
     */
    void setIcon(Icon icon);

    /**
     * Sets the tooltip.
     *
     * @param text plain text, or null for none
     */
    void setTooltip(String text);

    /**
     * Makes the item clickable.
     *
     * @param actionId an action this plugin registered, or null to make the item inert
     * @throws IllegalArgumentException when this plugin has not registered the action
     */
    void setAction(String actionId);

    /**
     * Shows or hides the item.
     *
     * @param visible whether the item is shown
     */
    void setVisible(boolean visible);
}
