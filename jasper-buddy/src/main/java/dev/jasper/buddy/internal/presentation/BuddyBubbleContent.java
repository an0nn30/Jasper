package dev.jasper.buddy.internal.presentation;

import javax.swing.Icon;

/** One bubble's text: a required title, an optional detail line, an optional glyph at the right edge, and its style. */
record BuddyBubbleContent(String title, String detail, Icon glyph, Style style) {
    /** MENU is the compact regular-weight pill for clickable entries; MESSAGE is the bold-title status bubble. */
    enum Style { MENU, MESSAGE }

    BuddyBubbleContent {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A bubble needs a non-blank title");
        if (style == null) throw new IllegalArgumentException("A bubble needs a style");
    }

    /** A status message: bold title, optional detail and glyph. */
    BuddyBubbleContent(String title, String detail, Icon glyph) {
        this(title, detail, glyph, Style.MESSAGE);
    }

    /** The menu variant: one small clickable line, no detail and no glyph. */
    static BuddyBubbleContent menu(String title) {
        return new BuddyBubbleContent(title, null, null, Style.MENU);
    }

    /** A status message for later phases: bold title, grey detail, glyph at the right. */
    static BuddyBubbleContent message(String title, String detail, Icon glyph) {
        return new BuddyBubbleContent(title, detail, glyph, Style.MESSAGE);
    }
}
