package dev.jasper.app;

import javax.swing.Icon;

/** One bubble's text: a required title, an optional detail line and an optional glyph at the right edge. */
record BuddyBubbleContent(String title, String detail, Icon glyph) {
    BuddyBubbleContent {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A bubble needs a non-blank title");
    }

    /** The menu variant: one clickable line, no detail and no glyph. */
    static BuddyBubbleContent menu(String title) {
        return new BuddyBubbleContent(title, null, null);
    }
}
