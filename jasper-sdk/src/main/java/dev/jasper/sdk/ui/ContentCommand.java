package dev.jasper.sdk.ui;

import java.util.Objects;
import javax.swing.KeyStroke;

/** Command belonging only to an active content tab.
 * @param id content-local command identity, such as save; while the tab is selected, Jasper's Copy, Paste, Find, Find
 *     Next, Find Previous, Increase Font Size, Decrease Font Size and Reset Font Size run the enabled command whose id
 *     is copy, paste, find, find-next, find-previous, font-bigger, font-smaller or font-reset (see
 *     {@link ContentTab#setCommands})
 * @param title literal menu text
 * @param shortcut key binding or null
 * @param enabled whether the command is available
 * @param run UI-thread callback
 * @since 0.8.5
 */
public record ContentCommand(String id,String title,KeyStroke shortcut,boolean enabled,Runnable run) {
    /** Validates required values. */
    public ContentCommand {Objects.requireNonNull(id);Objects.requireNonNull(title);Objects.requireNonNull(run);}
}
