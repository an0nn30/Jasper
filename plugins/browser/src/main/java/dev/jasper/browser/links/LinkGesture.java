package dev.jasper.browser.links;

import dev.jasper.browser.page.LastPress;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;

/**
 * Where a clicked link goes, from the press that clicked it (spec section 8). The menu key is Cmd on macOS and Ctrl on
 * Windows and Linux: with it a link opens in a new tab next to this one, with Shift as well in a split, and a middle
 * click opens a new tab too. Every other click, Shift-click and Alt-click among them, loads the link in the same item.
 * The menu key and the middle button decide before Shift and Alt, as in Chromium.
 */
public final class LinkGesture {
    /** Where a link goes. */
    public enum Where {
        /** Loaded in the item that has the link. */
        SAME_ITEM,
        /** In a new item, in a new tab right after this item's tab. */
        NEXT_TAB,
        /** In a new item, in a pane split from this item's: to the right when the page is wider than tall, else below. */
        SPLIT
    }

    private LinkGesture() { }

    /**
     * Where a link clicked with {@code press} goes.
     *
     * @param press the press that clicked it
     * @param macOs whether the platform is macOS, where the menu key is Cmd rather than Ctrl
     * @return where the link goes
     */
    public static Where of(LastPress press, boolean macOs) {
        Objects.requireNonNull(press, "press");
        if (press.button() == MouseEvent.BUTTON2) return Where.NEXT_TAB;
        if (press.button() != MouseEvent.BUTTON1) return Where.SAME_ITEM;
        int keys = press.modifiersEx();
        if ((keys & (macOs ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK)) == 0) return Where.SAME_ITEM;
        return (keys & InputEvent.SHIFT_DOWN_MASK) != 0 ? Where.SPLIT : Where.NEXT_TAB;
    }
}
