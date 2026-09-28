package dev.jasper.sdk.ui;

import java.util.Objects;
import javax.swing.KeyStroke;

/** Command belonging only to an active content tab.
 * @param id content-local command identity, e.g. save, find, copy, paste
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
